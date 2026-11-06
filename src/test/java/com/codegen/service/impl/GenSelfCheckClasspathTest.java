package com.codegen.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.0 自检回归：产物测试源识别、编译 classpath 兜底平台自身依赖。
 *
 * <p>V7.3 P0-1 起测试源不再只计数：单独用测试 classpath 编译（见 {@link GenSelfCheckTestCompileTest}），
 * 不混入主编译——测试类依赖 JUnit/MockMvc 等 test-scope 包，基座快照里没有。
 *
 * <p>同理，V6.4 基座 AuthController 引入 BCrypt，但 demo-lib 快照可能缺
 * spring-security-crypto；平台自身依赖里已经带了该 jar，自检 classpath 需要兜底合入。
 */
class GenSelfCheckClasspathTest {

    @Test
    @DisplayName("src/test/java 下的产物测试被识别为测试源，src/main 不算")
    void detectTestSource() {
        assertTrue(GenSelfCheckServiceImpl.isGeneratedTestSource(
                "wms_warehouse-server/src/test/java/com/wms/WmsZoneControllerTest.java"));
        assertTrue(GenSelfCheckServiceImpl.isGeneratedTestSource(
                "wms_warehouse-server\\src\\test\\java\\com\\wms\\WmsZoneControllerTest.java"),
                "Windows 反斜杠路径同样要识别");
        assertTrue(GenSelfCheckServiceImpl.isGeneratedTestSource(
                "src/test/java/com/demo/LoginStateTestSupport.java"), "顶层相对路径也要识别");
        assertFalse(GenSelfCheckServiceImpl.isGeneratedTestSource(
                "wms_warehouse-server/src/main/java/com/wms/WmsZoneController.java"));
        assertFalse(GenSelfCheckServiceImpl.isGeneratedTestSource(
                "wms_warehouse-web/src/views/wms/zone.vue"));
        assertFalse(GenSelfCheckServiceImpl.isGeneratedTestSource(
                "wms_warehouse-server/src/main/resources/test.sql"), "只是名字含 test 不算");
    }

    @Test
    @DisplayName("自检 classpath 合并平台自身 classpath：基座在前、平台在后")
    void mergeAppClasspath() {
        String configured = "demo-lib/*";
        String appClasspath = "app.jar";
        String merged = GenSelfCheckServiceImpl.mergeWithAppClasspath(configured, appClasspath);
        int sep = merged.indexOf(java.io.File.pathSeparator);
        assertTrue(sep > 0, "两个 classpath 段要拼接：" + merged);
        assertEquals("demo-lib/*", merged.substring(0, sep), "基座 classpath 优先");
        assertTrue(merged.endsWith("app.jar"), "平台 classpath 兜底在后");
    }

    @Test
    @DisplayName("平台 classpath 为空/未配置时不重复拼接")
    void mergeEdgeCases() {
        assertEquals("demo-lib/*",
                GenSelfCheckServiceImpl.mergeWithAppClasspath("demo-lib/*", ""), "无平台 classpath 原样返回");
        assertEquals("demo-lib/*",
                GenSelfCheckServiceImpl.mergeWithAppClasspath("demo-lib/*", null));
        assertEquals("app.jar",
                GenSelfCheckServiceImpl.mergeWithAppClasspath("", "app.jar"), "基座为空时直接用平台 classpath");
    }
}
