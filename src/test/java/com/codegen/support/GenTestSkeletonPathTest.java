package com.codegen.support;

import com.codegen.entity.GenTable;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V6.7 测试骨架落点：schema/table-*.sql 必须落在 {@code src/test/resources/schema}，
 * 不能把 {@code src/main/java} 后的包路径尾巴带进 resources——
 * 否则 application-test.yml 的 {@code classpath*:schema/table-*.sql} 一个也扫不到，
 * 产物测试会以「表不存在」失败。
 */
class GenTestSkeletonPathTest {

    private static GenTable table(String backendPath) {
        GenTable table = new GenTable();
        table.setTableName("demo_banner");
        table.setClassName("DemoBanner");
        table.setPackageName("com.demo");
        table.setModuleName("demo_banner");
        table.setBackendPath(backendPath);
        return table;
    }

    @Test
    @DisplayName("多模块：resources 基路径丢包路径，java 基路径保留包路径")
    void multiModule() {
        Map<String, Object> model = GenTemplateModel.build(
                table("demo-server/src/main/java/com/demo"), new ArrayList<>(GenTemplateModel.sampleCols()));
        assertEquals("demo-server/src/test/resources", model.get("testResBase"));
        assertEquals("demo-server/src/test/java/com/demo", model.get("testJavaBase"));
    }

    @Test
    @DisplayName("单模块：resources 基路径就是 src/test/resources")
    void singleModule() {
        Map<String, Object> model = GenTemplateModel.build(
                table("src/main/java/com/demo"), new ArrayList<>(GenTemplateModel.sampleCols()));
        assertEquals("src/test/resources", model.get("testResBase"));
        assertEquals("src/test/java/com/demo", model.get("testJavaBase"));
    }

    @Test
    @DisplayName("非标准 backendPath：保留原回落，不抛异常")
    void fallback() {
        Map<String, Object> model = GenTemplateModel.build(
                table("demo-server"), List.of());
        assertEquals("demo-server-test/resources", model.get("testResBase"));
    }
}
