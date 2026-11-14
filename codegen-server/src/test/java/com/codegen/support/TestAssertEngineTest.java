package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.2 断言引擎单测：5 类断言 × 7 种 op 的核心分支。
 *
 * <p>断言引擎是整个测试中心唯一的「判定真源」，它错了所有报告都不可信，
 * 所以这里只测纯函数，不启动 Spring。
 */
class TestAssertEngineTest {

    private static final String BODY = """
            {"code":200,"msg":"ok","data":{"id":8,"amount":99.5,"tags":["a","b"],
             "records":[{"id":1},{"id":2}]}}
            """;

    private static List<TestAssertEngine.Outcome> run(List<Map<String, Object>> asserts) {
        return TestAssertEngine.evaluate(asserts, 200, Map.of("Content-Type", "application/json"), BODY, 120);
    }

    /** 单条断言，op/path 传 null 表示使用默认值 */
    private static TestAssertEngine.Outcome single(String type, String path, String op, Object expect) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        if (path != null) {
            a.put("path", path);
        }
        if (op != null) {
            a.put("op", op);
        }
        a.put("expect", expect);
        return run(List.of(a)).get(0);
    }

    private static boolean pass(String type, String path, String op, Object expect) {
        return single(type, path, op, expect).pass();
    }

    @Test
    @DisplayName("status 断言：相等通过、不等失败")
    void statusAssert() {
        assertTrue(pass("status", null, null, 200));
        assertFalse(pass("status", null, null, 500));
    }

    @Test
    @DisplayName("jsonpath：嵌套字段 / 数组下标 / 字符串键")
    void jsonPathVariants() {
        assertTrue(pass("jsonpath", "$.data.id", null, 8));
        assertTrue(pass("jsonpath", "$.data.records[1].id", null, 2));
        assertTrue(pass("jsonpath", "$['data']['amount']", null, 99.5));
        assertTrue(pass("jsonpath", "$.data.tags[0]", null, "a"));
    }

    @Test
    @DisplayName("jsonpath：缺失字段的 eq / exists 判定")
    void jsonPathMissing() {
        assertFalse(pass("jsonpath", "$.data.none", null, "x"));
        assertTrue(pass("jsonpath", "$.data.none", "exists", false));
        assertFalse(pass("jsonpath", "$.data.none", "exists", true));
    }

    @Test
    @DisplayName("op：ne / contains / regex / gt / lt")
    void operators() {
        assertTrue(pass("jsonpath", "$.code", "ne", 500));
        assertTrue(pass("jsonpath", "$.msg", "contains", "o"));
        assertTrue(pass("jsonpath", "$.data.amount", "gt", 99));
        assertTrue(pass("jsonpath", "$.data.amount", "lt", 100));
        assertTrue(pass("jsonpath", "$.data.tags", "contains", "b"));
        assertTrue(pass("jsonpath", "$.msg", "regex", "^o[a-z]$"));
    }

    @Test
    @DisplayName("duration 与 header 断言")
    void durationAndHeader() {
        assertTrue(pass("duration", null, "lt", 800));
        assertFalse(pass("duration", null, "lt", 100));
        assertTrue(pass("header", "content-type", "contains", "json"));
    }

    @Test
    @DisplayName("不支持的断言类型必须失败并给出原因，不能静默通过")
    void unsupportedType() {
        TestAssertEngine.Outcome outcome = single("schema", null, null, "x.json");
        assertFalse(outcome.pass());
        assertTrue(outcome.message().contains("不支持的断言类型"));
    }

    @Test
    @DisplayName("非 JSON 响应：jsonpath 断言失败但不抛异常")
    void nonJsonBody() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "jsonpath");
        a.put("path", "$.code");
        a.put("expect", 200);
        List<TestAssertEngine.Outcome> outcomes = TestAssertEngine.evaluate(
                List.of(a), 200, Map.of(), "<html>oops</html>", 10);
        assertFalse(outcomes.get(0).pass());
        assertEquals(1, outcomes.size());
    }
}
