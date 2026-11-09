package com.codegen.service;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.DocProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 文档落盘与路径安全（V6.9）。
 *
 * <p>三条边界：落盘名只由服务端生成（uuid），原始名只进库；
 * 读文件一律经 {@link #resolve(String)} 做 normalize + 前缀校验，
 * 任何「库里被塞了 ../」的记录都读不出来。
 */
@Component
public class DocStorage implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DocStorage.class);

    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private DocProperties props;

    private Path base;

    private Path cacheBase;

    @Override
    public void afterPropertiesSet() throws IOException {
        base = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(base);
        String cache = props.getPreview().getCacheDir();
        cacheBase = cache == null || cache.isBlank()
                ? base.resolve(".preview")
                : Paths.get(cache).toAbsolutePath().normalize();
        Files.createDirectories(cacheBase);
        log.info("文档中心：上传目录={}（可写={}），预览缓存目录={}",
                base, Files.isWritable(base), cacheBase);
    }

    public Path baseDir() {
        return base;
    }

    public Path cacheDir() {
        return cacheBase;
    }

    /**
     * 取原始文件名里的小写扩展名（不带点）。
     *
     * <p>用 {@link Locale#ROOT}：土耳其语环境下 "GIF".toLowerCase() 会变成 "gıf"，
     * 导致白名单永远匹配不上（V6.6 A-18）。
     */
    public static String extOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 落盘：{yyyyMM}/{uuid}.{ext}，返回相对路径。
     */
    public String store(MultipartFile file, String ext) throws IOException {
        String month = LocalDate.now().format(MONTH_DIR);
        String storageName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path monthDir = base.resolve(month);
        Files.createDirectories(monthDir);
        Path target = monthDir.resolve(storageName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return month + "/" + storageName;
    }

    /**
     * 相对路径 → 绝对路径，并校验仍在上传目录内。
     */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        Path p = base.resolve(relativePath).normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return p;
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除文件失败（忽略）：{} - {}", path, e.getMessage());
        }
    }
}
