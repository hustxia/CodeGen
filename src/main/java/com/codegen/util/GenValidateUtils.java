package com.codegen.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 校验规则解析：把配置化的 validate_rule 翻译成后端 jakarta 注解与前端 Element 表单 rules。
 *
 * <p>规则语法（逗号分隔，顺序无关）：
 * <ul>
 *   <li>{@code required} —— 必填</li>
 *   <li>{@code maxlen=50} —— 字符串最大长度</li>
 *   <li>{@code min=1} / {@code max=9999} —— 数值范围</li>
 *   <li>{@code email} / {@code phone} / {@code url} —— 常用格式</li>
 *   <li>{@code regex=^\d+$} —— 自定义正则</li>
 * </ul>
 */
public final class GenValidateUtils {

    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    private GenValidateUtils() {
    }

    /**
     * 解析后的规则
     */
    public record Rule(boolean required, Integer maxLen, String min, String max,
                       boolean email, boolean phone, boolean url, String regex) {

        public boolean isEmpty() {
            return !required && maxLen == null && min == null && max == null
                    && !email && !phone && !url && regex == null;
        }
    }

    private static final Rule EMPTY = new Rule(false, null, null, null, false, false, false, null);

    /**
     * 解析规则串；非法片段忽略（不阻断生成，由自检报告提示）
     */
    public static Rule parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        boolean required = false;
        Integer maxLen = null;
        String min = null;
        String max = null;
        boolean email = false;
        boolean phone = false;
        boolean url = false;
        String regex = null;
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            String lower = t.toLowerCase(Locale.ROOT);
            if ("required".equals(lower)) {
                required = true;
            } else if (lower.startsWith("maxlen=")) {
                maxLen = toInt(t.substring(7));
            } else if (lower.startsWith("min=")) {
                min = t.substring(4).trim();
            } else if (lower.startsWith("max=")) {
                max = t.substring(4).trim();
            } else if ("email".equals(lower)) {
                email = true;
            } else if ("phone".equals(lower) || "mobile".equals(lower)) {
                phone = true;
            } else if ("url".equals(lower)) {
                url = true;
            } else if (lower.startsWith("regex=")) {
                regex = t.substring(6);
            }
        }
        return new Rule(required, maxLen, min, max, email, phone, url, regex);
    }

    /**
     * 后端字段注解串（保留原始顺序，便于阅读）
     */
    public static String javaAnnotations(String comment, String javaType, String raw) {
        Rule r = parse(raw);
        if (r.isEmpty()) {
            return "";
        }
        String label = label(comment);
        boolean isString = "String".equals(javaType);
        StringBuilder sb = new StringBuilder();
        if (r.required()) {
            sb.append(isString
                    ? "@NotBlank(message = \"" + label + "不能为空\")"
                    : "@NotNull(message = \"" + label + "不能为空\")");
        }
        if (r.maxLen() != null && isString) {
            sb.append(' ').append("@Size(max = ").append(r.maxLen())
                    .append(", message = \"").append(label).append("长度不能超过").append(r.maxLen()).append("\")");
        }
        if (r.min() != null) {
            sb.append(' ').append(numericMin(javaType, r.min(), label));
        }
        if (r.max() != null) {
            sb.append(' ').append(numericMax(javaType, r.max(), label));
        }
        if (r.email()) {
            sb.append(' ').append("@Email(message = \"").append(label).append("格式不正确\")");
        }
        if (r.phone()) {
            sb.append(' ').append("@Pattern(regexp = \"").append(escapeJava(PHONE_REGEX))
                    .append("\", message = \"").append(label).append("格式不正确\")");
        }
        if (r.url()) {
            sb.append(' ').append("@Pattern(regexp = \"^https?://.+\", message = \"")
                    .append(label).append("格式不正确\")");
        }
        if (r.regex() != null) {
            sb.append(' ').append("@Pattern(regexp = \"").append(escapeJava(r.regex()))
                    .append("\", message = \"").append(label).append("格式不正确\")");
        }
        return sb.toString().trim();
    }

    /**
     * 该字段需要导入的校验注解全类名
     */
    public static List<String> javaImports(String javaType, String raw) {
        Rule r = parse(raw);
        Set<String> imports = new LinkedHashSet<>();
        if (r.isEmpty()) {
            return List.of();
        }
        if (r.required()) {
            imports.add("String".equals(javaType)
                    ? "jakarta.validation.constraints.NotBlank"
                    : "jakarta.validation.constraints.NotNull");
        }
        if (r.maxLen() != null && "String".equals(javaType)) {
            imports.add("jakarta.validation.constraints.Size");
        }
        if (r.min() != null) {
            imports.add(needsDecimal(javaType, r.min()) ? "jakarta.validation.constraints.DecimalMin"
                    : "jakarta.validation.constraints.Min");
        }
        if (r.max() != null) {
            imports.add(needsDecimal(javaType, r.max()) ? "jakarta.validation.constraints.DecimalMax"
                    : "jakarta.validation.constraints.Max");
        }
        if (r.email()) {
            imports.add("jakarta.validation.constraints.Email");
        }
        if (r.phone() || r.url() || r.regex() != null) {
            imports.add("jakarta.validation.constraints.Pattern");
        }
        return new ArrayList<>(imports);
    }

    /**
     * 前端 Element 表单 rules 数组字面量；无规则返回 null
     */
    public static String vueRules(String comment, String htmlType, String raw) {
        Rule r = parse(raw);
        if (r.isEmpty()) {
            return null;
        }
        String label = label(comment);
        boolean pick = isPick(htmlType);
        List<String> items = new ArrayList<>();
        if (r.required()) {
            items.add("{ required: true, message: '" + escapeJs((pick ? "请选择" : "请输入") + label)
                    + "', trigger: '" + (pick ? "change" : "blur") + "' }");
        }
        if (r.maxLen() != null && !"number".equals(htmlType)) {
            items.add("{ max: " + r.maxLen() + ", message: '" + escapeJs(label + "长度不能超过" + r.maxLen() + "个字符")
                    + "', trigger: 'blur' }");
        }
        if (r.min() != null) {
            items.add("{ type: 'number', min: " + r.min() + ", message: '"
                    + escapeJs(label + "不能小于" + r.min()) + "', trigger: 'blur' }");
        }
        if (r.max() != null) {
            items.add("{ type: 'number', max: " + r.max() + ", message: '"
                    + escapeJs(label + "不能大于" + r.max()) + "', trigger: 'blur' }");
        }
        if (r.email()) {
            items.add("{ type: 'email', message: '" + escapeJs(label + "格式不正确") + "', trigger: 'blur' }");
        }
        if (r.phone()) {
            items.add("{ pattern: /^1[3-9]\\d{9}$/, message: '" + escapeJs(label + "格式不正确")
                    + "', trigger: 'blur' }");
        }
        if (r.url()) {
            items.add("{ type: 'url', message: '" + escapeJs(label + "格式不正确") + "', trigger: 'blur' }");
        }
        if (r.regex() != null) {
            items.add("{ pattern: new RegExp('" + escapeJs(r.regex()) + "'), message: '"
                    + escapeJs(label + "格式不正确") + "', trigger: 'blur' }");
        }
        return items.isEmpty() ? null : "[" + String.join(", ", items) + "]";
    }

    /**
     * 是否选择类控件（用于前端「请输入/请选择」措辞与 trigger）
     */
    public static boolean isPick(String htmlType) {
        return "select".equals(htmlType) || "radio".equals(htmlType)
                || "switch".equals(htmlType) || "date".equals(htmlType) || "datetime".equals(htmlType);
    }

    private static String numericMin(String javaType, String min, String label) {
        if (needsDecimal(javaType, min)) {
            return "@DecimalMin(value = \"" + min + "\", message = \"" + label + "不能小于" + min + "\")";
        }
        return "@Min(value = " + min + "L, message = \"" + label + "不能小于" + min + "\")";
    }

    private static String numericMax(String javaType, String max, String label) {
        if (needsDecimal(javaType, max)) {
            return "@DecimalMax(value = \"" + max + "\", message = \"" + label + "不能大于" + max + "\")";
        }
        return "@Max(value = " + max + "L, message = \"" + label + "不能大于" + max + "\")";
    }

    /**
     * 是否需要 Decimal 系列注解：字面量带小数点，或字段本身是浮点/定点类型。
     * 注解与 import 必须用同一判断，否则会生成「用了 @DecimalMin 却 import Min」。
     */
    private static boolean needsDecimal(String javaType, String num) {
        if ("BigDecimal".equals(javaType) || "Double".equals(javaType) || "Float".equals(javaType)) {
            return true;
        }
        return isDecimal(num);
    }

    private static boolean isDecimal(String num) {
        return num != null && num.contains(".");
    }

    private static Integer toInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 注释里常见的「标签名称(必填)」这类说明不影响文案，取第一段；
     * 形如「0禁用 1启用」的枚举说明不作为字段名，返回空串让文案退化为「不能为空」。
     */
    private static String label(String comment) {
        if (comment == null || comment.isBlank()) {
            return "";
        }
        String s = comment.trim();
        int idx = s.indexOf('(');
        if (idx > 0) {
            s = s.substring(0, idx);
        }
        idx = s.indexOf('（');
        if (idx > 0) {
            s = s.substring(0, idx);
        }
        // 冒号后是枚举说明（状态:1启用0停用），label 只取主标题
        idx = s.indexOf(':');
        if (idx > 0) {
            s = s.substring(0, idx);
        }
        idx = s.indexOf('：');
        if (idx > 0) {
            s = s.substring(0, idx);
        }
        // 「上级分类ID，0=顶级 1=一级」这类逗号枚举尾巴同样剥掉（逗号后紧跟数字才算）
        java.util.regex.Matcher tail = java.util.regex.Pattern
                .compile("^(.+?)[,，]\\s*\\d+\\s*[-=:：]?.+$").matcher(s);
        if (tail.matches() && !tail.group(1).isBlank()) {
            s = tail.group(1);
        }
        s = s.trim();
        return s.matches("^[0-9].*") ? "" : s;
    }

    private static String escapeJava(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeJs(String raw) {
        return raw.replace("\\", "\\\\").replace("'", "\\'");
    }
}
