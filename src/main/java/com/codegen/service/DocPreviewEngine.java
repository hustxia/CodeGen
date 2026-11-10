package com.codegen.service;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.DocProperties;
import com.codegen.entity.SysDoc;
import com.codegen.monitor.MonitorMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 文档预览引擎（V6.9）：决定「这份文档用哪种方式渲染」，以及 Word/Excel/PPT 的服务端转 PDF。
 *
 * <p>唯一实现原则：回落判据只写在 {@link #modeOf(SysDoc)} 一处，
 * 列表页徽标、预览接口、前端分派都读它的结果，不在别处再写一遍 if-else。
 *
 * <p>LibreOffice 的两个坑：单实例锁（同一 profile 并发会互相打死）→ 每次转换独立
 * {@code -env:UserInstallation}；冷启动慢 → 结果按 {id}-{size}-{mtime} 缓存。
 */
@Component
public class DocPreviewEngine implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DocPreviewEngine.class);

    /** 预览方式（与前端约定的字符串） */
    public enum Mode {
        PDF("pdf"),
        BROWSER_DOCX("browser-docx"),
        UNSUPPORTED("unsupported");

        private final String code;

        Mode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    @Autowired
    private DocProperties props;

    @Autowired
    private MonitorMetrics monitorMetrics;

    @Autowired
    private DocStorage storage;

    /** 探测到的 soffice 可执行文件；null = 未探测到 */
    private volatile String sofficePath;

    /** 探测到的版本号（日志与接口提示用） */
    private volatile String sofficeVersion;

    private Semaphore semaphore;

    @Override
    public void afterPropertiesSet() {
        semaphore = new Semaphore(Math.max(1, props.getPreview().getMaxConcurrency()));
        if ("browser".equals(engineConfig())) {
            log.info("文档预览：引擎=browser（跳过 LibreOffice 探测），Word 走浏览器简易渲染");
            return;
        }
        detectSoffice();
        if (sofficePath == null) {
            String msg = "文档预览：未探测到 soffice（LibreOffice）。Word/Excel/PPT 将降级为浏览器简易渲染或仅下载；"
                    + "需要高保真请安装 LibreOffice 并配置 doc.preview.soffice-path";
            if ("libreoffice".equals(engineConfig())) {
                // 强制指定却没探测到：必须让运维看见，否则会以为高保真生效了
                log.warn(msg);
            } else {
                log.info(msg);
            }
        }
    }

    private String engineConfig() {
        String e = props.getPreview().getEngine();
        return e == null ? "auto" : e.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 当前生效引擎名（接口返回给前端展示）。
     */
    public String engineName() {
        if ("browser".equals(engineConfig())) {
            return "browser";
        }
        return sofficePath == null ? "browser" : "libreoffice";
    }

    public boolean previewEnabled() {
        return props.getPreview().isEnabled();
    }

    /**
     * 该文档的渲染方式（唯一判据）。
     */
    public Mode modeOf(SysDoc doc) {
        String ext = doc.getExt() == null ? "" : doc.getExt().toLowerCase(Locale.ROOT);
        if ("pdf".equals(ext)) {
            return Mode.PDF;
        }
        boolean loAvailable = sofficePath != null && !"browser".equals(engineConfig());
        if ("docx".equals(ext)) {
            if ("browser".equals(engineConfig())) {
                return Mode.BROWSER_DOCX;
            }
            return loAvailable ? Mode.PDF : Mode.BROWSER_DOCX;
        }
        // doc / xls / xlsx / ppt / pptx：只有 LibreOffice 能渲染
        return loAvailable ? Mode.PDF : Mode.UNSUPPORTED;
    }

    /**
     * 不可预览时的原因文案（只在 UNSUPPORTED 时调用）。
     */
    public String unsupportedReason(SysDoc doc) {
        String ext = doc.getExt() == null ? "" : doc.getExt().toLowerCase(Locale.ROOT);
        if ("libreoffice".equals(engineConfig()) && sofficePath == null) {
            return "已配置 LibreOffice 引擎，但未探测到 soffice，无法转换。请安装 LibreOffice 或把 doc.preview.engine 改为 auto/browser";
        }
        if ("doc".equals(ext)) {
            return "老版本 .doc 需要 LibreOffice 转换才能预览；当前为浏览器引擎，请下载查看";
        }
        return "该格式（." + ext + "）需要 LibreOffice 转换才能预览；当前引擎不支持，请下载查看";
    }

    /**
     * 取可交给 pdf.js 的 PDF 路径：原件是 PDF 时直接返回原件，否则走转换。
     */
    public Path pdfOf(SysDoc doc) {
        String ext = doc.getExt() == null ? "" : doc.getExt().toLowerCase(Locale.ROOT);
        if ("pdf".equals(ext)) {
            return storage.resolve(doc.getRelativePath());
        }
        Mode mode = modeOf(doc);
        if (mode != Mode.PDF) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), unsupportedReason(doc));
        }
        return convertToPdf(doc);
    }

    /**
     * V6.10 埋点包装：缓存命中 / 实际转换 / 失败三个出口都要可见，
     * LibreOffice 是已知慢点，没有数据时只能靠猜。
     */
    private Path convertToPdf(SysDoc doc) {
        boolean hit = Files.isRegularFile(cachePathOf(doc));
        Timer.Sample sample = monitorMetrics.start();
        String result = "failed";
        try {
            Path pdf = doConvertToPdf(doc);
            result = hit ? "hit" : "converted";
            return pdf;
        } finally {
            monitorMetrics.stop(sample, "doc.convert", "result", result);
        }
    }

    /**
     * 转 PDF 并缓存。缓存键含 size+mtime：记录不可变（重传即新行），故无需失效逻辑。
     */
    private Path doConvertToPdf(SysDoc doc) {
        Path cached = cachePathOf(doc);
        if (Files.isRegularFile(cached)) {
            return cached;
        }
        Path src = storage.resolve(doc.getRelativePath());
        Path tmpOut = null;
        Path profile = null;
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(props.getPreview().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档转换排队超时，请稍后重试");
            }
            tmpOut = Files.createTempDirectory("doc-conv-out-");
            profile = Files.createTempDirectory("doc-conv-profile-");
            List<String> cmd = new ArrayList<>();
            cmd.add(sofficePath);
            cmd.add("--headless");
            cmd.add("--norestore");
            cmd.add("--nolockcheck");
            cmd.add("--nodefault");
            cmd.add("--nofirststartwizard");
            // 每次独立 profile：规避 LibreOffice 单实例锁导致的并发互杀
            cmd.add("-env:UserInstallation=" + profile.toUri());
            cmd.add("--convert-to");
            cmd.add("pdf");
            cmd.add("--outdir");
            cmd.add(tmpOut.toString());
            cmd.add(src.toString());

            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output;
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + " | " + b);
            }
            boolean finished = process.waitFor(props.getPreview().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档转换超时（>" + props.getPreview().getTimeoutSeconds() + "s）");
            }
            if (process.exitValue() != 0) {
                log.warn("soffice 转换失败 exit={} output={}", process.exitValue(), output);
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档转换失败，请下载原件查看");
            }
            String baseName = stripExt(src.getFileName().toString());
            Path produced = tmpOut.resolve(baseName + ".pdf");
            if (!Files.isRegularFile(produced)) {
                log.warn("soffice 未产出预期文件：{}（输出：{}）", produced, output);
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档转换失败，请下载原件查看");
            }
            Files.createDirectories(cached.getParent());
            Files.copy(produced, cached, StandardCopyOption.REPLACE_EXISTING);
            return cached;
        } catch (IOException e) {
            log.warn("文档转换 IO 异常: {}", e.getMessage());
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档转换失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档转换被中断");
        } finally {
            if (acquired) {
                semaphore.release();
            }
            deleteDirQuietly(tmpOut);
            deleteDirQuietly(profile);
        }
    }

    private Path cachePathOf(SysDoc doc) {
        String key = doc.getId() + "-" + doc.getFileSize() + "-" + doc.getUpdateTime();
        return storage.cacheDir().resolve(key + ".pdf");
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static void deleteDirQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响业务，交给操作系统回收
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    /**
     * 探测 soffice：配置路径 → PATH → 常见安装目录（Windows）。
     */
    private void detectSoffice() {
        List<String> candidates = new ArrayList<>();
        String configured = props.getPreview().getSofficePath();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured.trim());
        }
        candidates.add("soffice");
        candidates.add("soffice.exe");
        candidates.add("C:\\Program Files\\LibreOffice\\program\\soffice.exe");
        candidates.add("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe");
        candidates.add("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        candidates.add("/usr/bin/soffice");
        candidates.add("/opt/libreoffice/program/soffice");

        for (String candidate : candidates) {
            String version = probeVersion(candidate);
            if (version != null) {
                sofficePath = candidate;
                sofficeVersion = version;
                log.info("文档预览：引擎=libreoffice，soffice={}，版本={}", candidate, version);
                return;
            }
        }
    }

    private String probeVersion(String candidate) {
        try {
            Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
            String out;
            try (var reader = p.inputReader(StandardCharsets.UTF_8)) {
                out = reader.lines().findFirst().orElse("");
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 && !out.isBlank() ? out.trim() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public String sofficeVersion() {
        return sofficeVersion;
    }
}
