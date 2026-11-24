package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * V9.19 模板市场：行业模板外部包目录与导入限制。
 *
 * <p>预置模板仍随 jar 发布在 {@code classpath:industry-templates/*.json}；
 * 用户自定义包放在 {@code gen.template.external-dir}，启动时与预置包合并，
 * 同名 key 由用户包覆盖（见 {@code IndustryTemplateRegistry}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen.template")
public class GenTemplatePackProperties {

    /** 用户包目录；支持 ${user.home} 与 ~ 前缀，默认 ~/.codegen/industry-templates */
    private String externalDir = "${user.home}/.codegen/industry-templates";

    /** 单包大小上限（MB，含 ZIP 解压后总量），防炸内存 */
    private int maxPackSizeMb = 5;

    /** 单包表数量上限 */
    private int maxTables = 200;

    /** 模板 key 最大长度（不含首字母后的 0 基索引） */
    private int maxKeyLength = 48;

    /** 归一化的外部目录绝对路径；占位符与 ~ 在这里兜底解析，便于纯单测直接构造使用 */
    public Path resolvedExternalDir() {
        String dir = externalDir == null ? "" : externalDir.trim();
        String home = System.getProperty("user.home", ".");
        dir = dir.replace("${user.home}", home);
        if (dir.startsWith("~/") || dir.startsWith("~\\")) {
            dir = home + dir.substring(1);
        }
        if (dir.isBlank()) {
            dir = home + "/.codegen/industry-templates";
        }
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    public long maxPackBytes() {
        return Math.max(0, maxPackSizeMb) * 1024L * 1024L;
    }
}
