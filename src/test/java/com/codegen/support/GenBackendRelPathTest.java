package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.8：产物归属判定（后端 / 前端）。
 *
 * <p>踩过的坑：测试产物基路径是 {@code src/test/java/<pkg>}，与 {@code backendBase} 的
 * {@code src/main/java/<pkg>} 不是同一个字符串。早先只判 {@code contains(backendBase + "/")}，
 * 测试类与 schema 资源被打成前端产物，写进前端仓库的模块目录下。
 */
class GenBackendRelPathTest {

    private static final String MULTI_BASE = "demo-server/src/main/java/com/demo";
    private static final String SINGLE_BASE = "src/main/java/com/oms";

    @Test
    @DisplayName("多模块：src/main 下的类算后端")
    void multiMain() {
        assertTrue(GenPathResolver.isBackendRelPath(
                "demo-server/src/main/java/com/demo/entity/DemoBanner.java", MULTI_BASE));
    }

    @Test
    @DisplayName("多模块：src/test/java 下的测试类算后端（曾被误判前端）")
    void multiTestJava() {
        assertTrue(GenPathResolver.isBackendRelPath(
                "demo-server/src/test/java/com/demo/DemoBannerControllerTest.java", MULTI_BASE));
    }

    @Test
    @DisplayName("多模块：src/test/resources 下的建表脚本算后端")
    void multiTestResources() {
        assertTrue(GenPathResolver.isBackendRelPath(
                "demo-server/src/test/resources/com/demo/schema/table-demo_banner.sql", MULTI_BASE));
    }

    @Test
    @DisplayName("单模块：后端路径直接从 src/main 起头")
    void singleMain() {
        assertTrue(GenPathResolver.isBackendRelPath("src/main/java/com/oms/entity/Order.java", SINGLE_BASE));
    }

    @Test
    @DisplayName("单模块：src/test 起头同样算后端")
    void singleTest() {
        assertTrue(GenPathResolver.isBackendRelPath("src/test/resources/application-test.yml", SINGLE_BASE));
    }

    @Test
    @DisplayName("前端产物不算后端：src/views、src/api，以及 src/main.js 这种近似名")
    void frontendPaths() {
        assertFalse(GenPathResolver.isBackendRelPath("src/views/banner/index.vue", MULTI_BASE));
        assertFalse(GenPathResolver.isBackendRelPath("src/api/banner.js", SINGLE_BASE));
        assertFalse(GenPathResolver.isBackendRelPath("src/main.js", SINGLE_BASE));
    }

    @Test
    @DisplayName("空入参不抛异常，按非后端处理")
    void blankSafe() {
        assertFalse(GenPathResolver.isBackendRelPath(null, MULTI_BASE));
        assertFalse(GenPathResolver.isBackendRelPath("", MULTI_BASE));
    }
}
