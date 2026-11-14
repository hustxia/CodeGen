package com.codegen.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V9.2 断言引擎：纯函数，不依赖 Spring，便于单测覆盖「5 类断言 × 6 种 op」。
 *
 * <p>支持类型：{@code status} / {@code jsonpath} / {@code duration} / {@code header}；
 * op 支持 {@code eq} / {@code ne} / {@code contains} / {@code regex} / {@code gt} / {@code lt} / {@code exists}。
 * {@code schema} 留待 T2，遇到时返回失败并明确提示，不静默通过。
 *
 * <p>JSONPath 只实现常用子集：{@code $.a.b}、{@code $.a[0].b}、{@code $['a']}；
 * 不支持递归下降 {@code $..} 与通配 {@code [*]}——需要时再引第三方库，避免为局部语法增加依赖。
 */
public final class TestAssertEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TestAssertEngine() {
    }

    /** 单条断言结果 */
    public record Outcome(String type, String target, String op, Object expect, Object actual,
                          boolean pass, String message) {
    }

    public static List<Outcome> evaluate(List<Map<String, Object>> asserts, int status,
                                         Map<String, String> headers, String body, long durationMs) {
        List<Outcome> out = new ArrayList<>();
        if (asserts == null || asserts.isEmpty()) {
            return out;
        }
        JsonNode root = parseTree(body);
        Map<String, String> lowerHeaders = lowerKeys(headers);
        for (Map<String, Object> a : asserts) {
            if (a == null) {
                continue;
            }
            String type = text(a.get("type"));
            String lowerType = type == null ? "" : type.toLowerCase(Locale.ROOT);
            String op = a.get("op") == null ? "eq" : text(a.get("op")).toLowerCase(Locale.ROOT);
            Object expect = a.get("expect");
            String target = a.get("path") == null ? null : text(a.get("path"));

            Object actual;
            String label;
            boolean supported = true;
            switch (lowerType) {
                case "status" -> {
                    actual = status;
                    label = "status";
                }
                case "duration" -> {
                    actual = durationMs;
                    label = "duration";
                }
                case "jsonpath" -> {
                    actual = nodeValue(resolve(root, target));
                    label = target == null ? "" : target;
                }
                case "header" -> {
                    actual = target == null ? null : lowerHeaders.get(target.toLowerCase(Locale.ROOT));
                    label = "header " + target;
                }
                default -> {
                    actual = null;
                    label = String.valueOf(type);
                    supported = false;
                }
            }

            if (!supported) {
                out.add(new Outcome(type, label, op, expect, null, false,
                        "不支持的断言类型: " + type));
                continue;
            }
            boolean pass = compare(actual, op, expect);
            out.add(new Outcome(type, label, op, expect, actual, pass,
                    pass ? "ok" : "期望 " + expect + "，实际 " + actual));
        }
        return out;
    }

    /** 解析 JSONPath 子集，未命中返回 null */
    public static JsonNode resolve(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        String p = path.trim();
        if (!p.startsWith("$")) {
            return null;
        }
        JsonNode cur = root;
        int i = 1;
        while (i < p.length() && cur != null) {
            char c = p.charAt(i);
            if (c == '.') {
                int j = i + 1;
                while (j < p.length() && p.charAt(j) != '.' && p.charAt(j) != '[') {
                    j++;
                }
                String name = p.substring(i + 1, j);
                if (!name.isEmpty()) {
                    cur = cur.get(name);
                }
                i = j;
            } else if (c == '[') {
                int end = p.indexOf(']', i);
                if (end < 0) {
                    return null;
                }
                String token = p.substring(i + 1, end).trim();
                if (token.startsWith("'") || token.startsWith("\"")) {
                    cur = cur.get(token.substring(1, token.length() - 1));
                } else {
                    try {
                        int idx = Integer.parseInt(token);
                        cur = cur.isArray() && idx >= 0 && idx < cur.size() ? cur.get(idx) : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return cur;
    }

    /** JsonNode → Java 值（字符串/布尔/数字/null/JSON 文本） */
    public static Object nodeValue(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isIntegralNumber()) {
            return n.canConvertToLong() ? (Object) n.asLong() : n.bigIntegerValue();
        }
        if (n.isFloatingPointNumber()) {
            return n.decimalValue();
        }
        return n.toString();
    }

    private static boolean compare(Object actual, String op, Object expect) {
        return switch (op) {
            case "exists" -> (actual != null) == truthy(expect);
            case "eq" -> equalValue(actual, expect);
            case "ne" -> !equalValue(actual, expect);
            case "contains" -> contains(actual, expect);
            case "regex" -> actual != null && expect != null
                    && Pattern.compile(String.valueOf(expect)).matcher(String.valueOf(actual)).find();
            case "gt" -> {
                BigDecimal a = numeric(actual);
                BigDecimal b = numeric(expect);
                yield a != null && b != null && a.compareTo(b) > 0;
            }
            case "lt" -> {
                BigDecimal a = numeric(actual);
                BigDecimal b = numeric(expect);
                yield a != null && b != null && a.compareTo(b) < 0;
            }
            default -> false;
        };
    }

    private static boolean equalValue(Object actual, Object expect) {
        BigDecimal a = numeric(actual);
        BigDecimal b = numeric(expect);
        if (a != null && b != null) {
            return a.compareTo(b) == 0;
        }
        return actual != null && String.valueOf(actual).equals(String.valueOf(expect));
    }

    private static boolean contains(Object actual, Object expect) {
        if (actual == null) {
            return false;
        }
        if (actual instanceof List<?> list) {
            return list.stream().anyMatch(i -> equalValue(i, expect));
        }
        return expect != null && String.valueOf(actual).contains(String.valueOf(expect));
    }

    private static boolean truthy(Object expect) {
        if (expect instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(expect);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static BigDecimal numeric(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonNode parseTree(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> lowerKeys(Map<String, String> src) {
        Map<String, String> out = new LinkedHashMap<>();
        if (src != null) {
            src.forEach((k, v) -> {
                if (k != null) {
                    out.put(k.toLowerCase(Locale.ROOT), v);
                }
            });
        }
        return out;
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
