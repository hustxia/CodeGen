package com.codegen.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * V9.2 测试中心的 JSON 小工具：序列化 / 宽松解析 / 快照截断。
 *
 * <p>用例里的 JSON 是用户可编辑的，解析失败必须当作「用例配置错误」返回，
 * 而不是抛到全局异常处理器变成 500，因此这里统一提供宽松解析（失败返回空集合）。
 */
public final class TestJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ASSERT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private TestJson() {
    }

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static List<Map<String, Object>> assertList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, ASSERT_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Map<String, Object> objectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, OBJECT_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static Map<String, String> stringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, STRING_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 超长内容截断并标记，防止单行撑爆 mediumtext / 前端卡死 */
    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "\n...[截断，共 " + s.length() + " 字符]";
    }
}
