package com.codegen.support;

import com.codegen.entity.GenTable;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 模板影响面预览的纯函数工具：模板路径/编码归一化、产物路径归一化、表形态提示。
 *
 * <p>只做字符串与实体判读，不读库、不渲染、不写盘，便于单元测试直接断言。
 * 影响面的「哪些表产出哪些文件」由渲染路径复用计算，不在这里重复实现。
 */
public final class GenTemplateImpactSupport {

    private static final String TEMPLATE_SUFFIX = ".ftl";

    private GenTemplateImpactSupport() {
    }

    /**
     * 路径归一化：反斜杠转正斜杠、去掉 ./ 前缀与开头斜杠、折叠重复斜杠。
     *
     * <p>模板相对路径与产物相对路径共用同一套归一化规则，避免「页面选的路径」和
     * 「渲染出的路径」只差一个斜杠就匹配不上。
     */
    public static String normalizeTemplatePath(String raw) {
        if (raw == null) {
            return null;
        }
        String path = raw.trim().replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        return path.isEmpty() ? null : path;
    }

    /** 产物相对路径归一化（与模板路径同一规则） */
    public static String normalizeOutputPath(String raw) {
        return normalizeTemplatePath(raw);
    }

    /**
     * 从资源地址截取模板相对路径，例如
     * {@code file:/D:/repo/target/classes/gen-templates/entity.ftl} → {@code gen-templates/entity.ftl}。
     * 无法识别（不在模板目录下）返回 null。
     */
    public static String templatePathFromResource(String rawPath) {
        String path = normalizeTemplatePath(rawPath);
        if (path == null) {
            return null;
        }
        // 从后往前找最后一个模板目录段，避免仓库路径里恰好有 scaffold-* 之类的同名目录时误截
        String[] segments = path.split("/");
        int start = -1;
        for (int i = 0; i < segments.length; i++) {
            if (isTemplateDirectory(segments[i])) {
                start = i;
            }
        }
        if (start < 0) {
            return null;
        }
        StringBuilder relative = new StringBuilder();
        for (int i = start; i < segments.length; i++) {
            if (relative.length() > 0) {
                relative.append('/');
            }
            relative.append(segments[i]);
        }
        String candidate = relative.toString();
        return candidate.toLowerCase(Locale.ROOT).endsWith(TEMPLATE_SUFFIX) ? candidate : null;
    }

    /** 是否是需求约定的模板目录：gen-templates、scaffold、scaffold-* */
    private static boolean isTemplateDirectory(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        return "gen-templates".equals(lower) || "scaffold".equals(lower) || lower.startsWith("scaffold-");
    }

    /**
     * 模板编码：{@code gen-templates/entity.ftl} → {@code entity}。
     * 非 .ftl 路径返回 null。
     */
    public static String templateCode(String templatePath) {
        String path = normalizeTemplatePath(templatePath);
        if (path == null) {
            return null;
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.length() <= TEMPLATE_SUFFIX.length()
                || !name.toLowerCase(Locale.ROOT).endsWith(TEMPLATE_SUFFIX)) {
            return null;
        }
        return name.substring(0, name.length() - TEMPLATE_SUFFIX.length());
    }

    /**
     * 表形态提示：多对多 / 主子表 / 树形 / 单表，按固定顺序去重。
     *
     * @param hasChildren 该表是否挂着子表（由调用方按 gen_table.master_table_id 统计）
     */
    public static List<String> modeHints(GenTable table, boolean hasChildren) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (table != null) {
            if ("middle".equalsIgnoreCase(trim(table.getRelationType()))) {
                hints.add("多对多");
            }
            if (table.getMasterTableId() != null && table.getMasterTableId() > 0) {
                hints.add("主子表（子表）");
            }
            if (hasChildren) {
                hints.add("主子表（主表）");
            }
            if (StringUtils.hasText(table.getTreeParentColumn())) {
                hints.add("树形");
            }
        }
        if (hints.isEmpty()) {
            hints.add("单表");
        }
        return new ArrayList<>(hints);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
