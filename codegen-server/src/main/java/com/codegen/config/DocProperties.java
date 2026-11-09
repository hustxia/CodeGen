package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * V6.9 平台文档中心配置。
 *
 * <p>白名单刻意不含 svg / html / md：平台不做富文本渲染、也不开放免鉴权
 * 静态直链，但仍然拒绝一切可执行脚本的载体（见坑库 spring-boot-3.md 坑 62）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "doc")
public class DocProperties {

    /** 上传目录（相对或绝对路径，启动时归一为绝对路径） */
    private String uploadDir = "./uploads/doc";

    /** 单文件大小上限（MB），与 spring.servlet.multipart 的 max-file-size 是两层校验 */
    private int maxSizeMb = 30;

    /** 白名单扩展名（逗号分隔、不带点、小写口径） */
    private String allowedExts = "pdf,doc,docx,xls,xlsx,ppt,pptx";

    private Preview preview = new Preview();

    @Data
    public static class Preview {

        /** 关闭后前端只给下载，不调预览接口 */
        private boolean enabled = true;

        /** auto | browser | libreoffice */
        private String engine = "auto";

        /** soffice 可执行文件路径；空 = 依次探测 PATH 与常见安装目录 */
        private String sofficePath = "";

        /** 转换缓存目录；空 = upload-dir/.preview */
        private String cacheDir = "";

        /** 单次转换超时（秒） */
        private int timeoutSeconds = 60;

        /** 同时转换数上限（LibreOffice 重，且每次独立 profile） */
        private int maxConcurrency = 2;
    }

    /**
     * 白名单解析结果（小写、不带点）。
     *
     * <p>每次调用都重新解析而不是缓存：配置量极小（7 个），
     * 缓存反而会在测试里改配置后不生效（V6.6 A-06 的取舍）。
     */
    public Set<String> allowedExtSet() {
        Set<String> set = new LinkedHashSet<>();
        for (String raw : allowedExts.split(",")) {
            String ext = raw.trim().toLowerCase(Locale.ROOT);
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty()) {
                set.add(ext);
            }
        }
        return set;
    }

    public Set<String> previewableExtSet() {
        return new LinkedHashSet<>(Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"));
    }
}
