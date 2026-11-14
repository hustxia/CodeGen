package com.codegen.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V9.2 用例变量上下文：三级作用域 {@code env} / {@code case} / {@code var}。
 *
 * <p>占位符形如 {@code ${case.login.token}}；查找时先按全键匹配，再按去掉前缀的短名匹配，
 * 这样同一份用例在「手工试跑」和「套件执行」下都能复用。
 * 未命中的占位符原样保留，便于在失败信息里直接看出缺哪个变量。
 */
public final class TestVarContext {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.]*)}");

    private final Map<String, Object> scopes = new LinkedHashMap<>();

    public static TestVarContext create() {
        return new TestVarContext();
    }

    public TestVarContext putEnv(String key, Object value) {
        put("env." + key, value);
        return this;
    }

    public TestVarContext putCase(String key, Object value) {
        put("case." + key, value);
        return this;
    }

    public TestVarContext putVar(String key, Object value) {
        put("var." + key, value);
        return this;
    }

    private void put(String fullKey, Object value) {
        if (value != null) {
            scopes.put(fullKey, value);
        }
    }

    /** 按全键 → 短名顺序取值（短名按 case / var / env 优先级匹配） */
    public Object get(String key) {
        if (key == null) {
            return null;
        }
        Object v = scopes.get(key);
        if (v != null) {
            return v;
        }
        int dot = key.indexOf('.');
        if (dot < 0) {
            for (String scope : new String[]{"case.", "var.", "env."}) {
                Object hit = scopes.get(scope + key);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        return scopes.get(key.substring(dot + 1));
    }

    /** 替换字符串中的 {@code ${...}}，未命中保持原样 */
    public String resolve(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按 extract 配置从响应体提取变量，写入 case 作用域。
     *
     * @param extracts 形如 {@code {"token": "$.data.token"}}
     */
    public void extract(Map<String, Object> extracts, String responseBody) {
        if (extracts == null || extracts.isEmpty() || responseBody == null) {
            return;
        }
        JsonNode root;
        try {
            root = JSON.readTree(responseBody);
        } catch (Exception e) {
            return;
        }
        extracts.forEach((name, path) -> {
            Object value = TestAssertEngine.nodeValue(
                    TestAssertEngine.resolve(root, path == null ? null : String.valueOf(path)));
            putCase(name, value);
        });
    }
}
