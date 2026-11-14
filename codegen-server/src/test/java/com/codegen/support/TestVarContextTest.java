package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V9.2 变量上下文单测：作用域查找、占位符替换、响应提取。
 */
class TestVarContextTest {

    @Test
    @DisplayName("全键与短名都能取值")
    void scopeLookup() {
        TestVarContext ctx = TestVarContext.create().putCase("token", "abc").putVar("orderId", 9L);
        assertEquals("abc", ctx.get("case.token"));
        assertEquals("abc", ctx.get("token"));
        assertEquals(9L, ctx.get("orderId"));
        assertNull(ctx.get("missing"));
    }

    @Test
    @DisplayName("占位符替换：命中替换、未命中保留原样")
    void resolve() {
        TestVarContext ctx = TestVarContext.create().putCase("token", "T1").putEnv("baseUrl", "http://x");
        assertEquals("Bearer T1", ctx.resolve("Bearer ${case.token}"));
        assertEquals("Bearer T1", ctx.resolve("Bearer ${token}"));
        assertEquals("${unknown}", ctx.resolve("${unknown}"));
        assertEquals("http://x/order", ctx.resolve("${env.baseUrl}/order"));
    }

    @Test
    @DisplayName("extract 从响应体提取变量并可被后续用例引用")
    void extract() {
        TestVarContext ctx = TestVarContext.create();
        ctx.extract(Map.of("orderId", "$.data.id"), "{\"data\":{\"id\":42}}");
        assertEquals(42L, ctx.get("case.orderId"));
        assertEquals("/order/42", ctx.resolve("/order/${orderId}"));
    }

    @Test
    @DisplayName("响应体不是 JSON 时提取静默跳过")
    void extractBadBody() {
        TestVarContext ctx = TestVarContext.create();
        ctx.extract(Map.of("x", "$.a"), "not-json");
        assertNull(ctx.get("case.x"));
    }
}
