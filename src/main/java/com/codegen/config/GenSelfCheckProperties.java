package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生成自检配置。
 *
 * <p>{@code classpath} 解决的问题：自检要在内存里编译生成的 Java 产物，需要一份
 * "项目全部依赖"的 classpath。开发态直接取 {@code java.class.path} 是对的，
 * 但打成 Spring Boot 肥 jar 部署后，这个值是 {@code app.jar} 本身，javac 无法
 * 从"装着 jar 的 jar"里解析依赖，自检会退化成满屏 cannot find symbol。
 * 部署环境把依赖目录（或 {@code *.jar} 通配符）配到这里即可恢复完整自检。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen.selfcheck")
public class GenSelfCheckProperties {

    /**
     * 编译检查用的 classpath（分号或冒号分隔，支持 dir/* 通配符）；留空取当前进程
     */
    private String classpath;

    /**
     * 产物测试（src/test/java）编译额外 classpath（分号或冒号分隔，支持 dir/* 通配符）；留空沿用主编译 classpath。
     *
     * <p>V7.3 P0-1：JUnit/MockMvc/H2 等 test-scope 包通常不在基座快照里。不配这里时，
     * 产物测试编译会把「缺少测试依赖」降级为 WARNING，而不是误报成生成器 ERROR。
     */
    private String testClasspath;

    /**
     * 注解处理器路径（含 lombok 等）；留空则回退到 classpath。
     *
     * <p>JDK 21 起注解处理不再隐式开启，且只从 {@code -classpath} 探测处理器时，
     * 有时找不到 lombok 的 processor，表现为"类在 classpath 里，却报 cannot find symbol getXxx()"。
     */
    private String processorPath;

    /**
     * 解析注解处理器路径：显式配置优先，其次取全局 classpath 里含 lombok 的那段
     */
    public String resolveProcessorPath() {
        if (StringUtils.hasText(processorPath)) {
            return expandWildcards(processorPath.trim());
        }
        return lombokSegmentsOf(resolveClasspath());
    }

    /**
     * 解析产物测试 classpath：未配置返回空串（沿用主编译 classpath）。
     */
    public String resolveTestClasspath() {
        return StringUtils.hasText(testClasspath) ? expandWildcards(testClasspath.trim()) : "";
    }

    /**
     * 从给定 classpath 里挑出注解处理器（lombok / mapstruct）相关段。
     *
     * <p>多系统下编译用的是「系统自带 classpath」（如 demo-lib/*），全局 classpath
     * 反而可能没有 lombok（肥 jar 启动时 java.class.path 只有一根 jar）——
     * 只按全局找处理器，系统自带 classpath 里的 lombok 就被漏掉，
     * 基座类源码一参与编译就满屏 cannot find symbol getCode()。
     */
    public String lombokSegmentsOf(String full) {
        if (!StringUtils.hasText(full)) {
            return "";
        }
        String sep = java.io.File.pathSeparator;
        StringBuilder sb = new StringBuilder();
        for (String part : full.split(java.util.regex.Pattern.quote(sep))) {
            String lower = part.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("lombok") || lower.contains("mapstruct")) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    /**
     * 是否启用 Java 内存编译（关闭后只做结构/权限/契约检查）
     */
    private Boolean compileEnabled = Boolean.TRUE;

    /**
     * 解析实际使用的 classpath
     *
     * <p>注意：{@code dir/*} 这种通配符只有 JVM 自己的 {@code -cp} 会展开，
     * 这里是把字符串直接喂给 javac 的 {@code -classpath}，通配符会被当成字面量，
     * 结果就是"配了等于没配、依然满屏 cannot find symbol"。所以必须自己展开。
     */
    public String resolveClasspath() {
        if (StringUtils.hasText(classpath)) {
            return expandWildcards(classpath.trim());
        }
        String fromProperty = System.getProperty("java.class.path");
        return fromProperty == null ? "" : fromProperty;
    }

    /**
     * 把 {@code dir/*} 展开成该目录下全部 jar 的绝对路径
     */
    public static String expandWildcards(String raw) {
        String sep = java.io.File.pathSeparator;
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(java.util.regex.Pattern.quote(sep))) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.endsWith("*")) {
                java.io.File dir = new java.io.File(entry.substring(0, entry.length() - 1));
                java.io.File[] jars = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
                if (jars == null || jars.length == 0) {
                    // 目录不存在或没有 jar：保留原样，让 javac 报出可读的错误
                    appendEntry(out, entry, sep);
                    continue;
                }
                java.util.Arrays.sort(jars);
                for (java.io.File jar : jars) {
                    appendEntry(out, jar.getAbsolutePath(), sep);
                }
            } else {
                appendEntry(out, entry, sep);
            }
        }
        return out.toString();
    }

    private static void appendEntry(StringBuilder sb, String entry, String sep) {
        if (sb.length() > 0) {
            sb.append(sep);
        }
        sb.append(entry);
    }

    /**
     * 当前 classpath 是否"看起来不可用"（单文件 jar 启动，依赖被嵌套打包）
     */
    public boolean classpathLooksNested() {
        if (StringUtils.hasText(classpath)) {
            return false;
        }
        String cp = System.getProperty("java.class.path");
        if (cp == null) {
            return true;
        }
        String[] parts = cp.split(java.io.File.pathSeparator);
        return parts.length == 1 && parts[0].toLowerCase(java.util.Locale.ROOT).endsWith(".jar");
    }
}
