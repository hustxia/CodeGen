package com.codegen.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.3 P0-1：产物测试独立编译的纯函数分支。
 *
 * <p>测试类通常是包级私有（{@code class XxxControllerTest}），不能复用只认 public 的类名正则；
 * JUnit/MockMvc/H2 等 test-scope 包缺失必须降级为 WARNING，而不是把基座缺依赖误报成生成器缺陷。
 */
class GenSelfCheckTestCompileTest {

    @Test
    @DisplayName("包级私有测试类也能解析出全限定类名")
    void classNameOfPackagePrivateTest() {
        assertEquals("com.wms.WmsZoneControllerTest", GenSelfCheckServiceImpl.classNameOfTestSource(
                "package com.wms;\n\nclass WmsZoneControllerTest extends LoginStateTestSupport {\n}\n"));
    }

    @Test
    @DisplayName("public / 带修饰符的测试类同样可解析")
    void classNameOfPublicTest() {
        assertEquals("com.wms.LoginStateTestSupport", GenSelfCheckServiceImpl.classNameOfTestSource(
                "package com.wms;\n\npublic final class LoginStateTestSupport {\n}\n"));
    }

    @Test
    @DisplayName("没有类型声明时返回 null，由调用方报 ERROR")
    void classNameMissing() {
        assertNull(GenSelfCheckServiceImpl.classNameOfTestSource("package com.wms;\n// no type\n"));
    }

    @Test
    @DisplayName("测试源码根定位兼容模块前缀与顶层两种布局")
    void srcTestJavaStart() {
        assertEquals(0, GenSelfCheckServiceImpl.srcTestJavaStart("src/test/java/com/wms/XTest.java"));
        assertEquals(21, GenSelfCheckServiceImpl.srcTestJavaStart(
                "wms_warehouse-server/src/test/java/com/wms/XTest.java"), "模块前缀下标");
        assertEquals(-1, GenSelfCheckServiceImpl.srcTestJavaStart(
                "wms_warehouse-server/src/main/java/com/wms/X.java"));
    }

    @Test
    @DisplayName("test-scope 依赖缺失降级为 WARNING")
    void testDependencyGap() {
        assertTrue(GenSelfCheckServiceImpl.isTestDependencyGap("package org.junit.jupiter.api does not exist"));
        assertTrue(GenSelfCheckServiceImpl.isTestDependencyGap("cannot find symbol: class MockMvc"),
                "MockMvc 是 spring-test 的 API");
        assertTrue(GenSelfCheckServiceImpl.isTestDependencyGap("package com.h2database does not exist"));
    }

    @Test
    @DisplayName("真实语法错误不降级：仍然按 ERROR 报出")
    void syntaxErrorIsNotGap() {
        assertFalse(GenSelfCheckServiceImpl.isTestDependencyGap("';' expected"));
        assertFalse(GenSelfCheckServiceImpl.isTestDependencyGap(
                "cannot find symbol: method save(com.wms.WmsZone)"));
        assertFalse(GenSelfCheckServiceImpl.isTestDependencyGap(null));
    }
}
