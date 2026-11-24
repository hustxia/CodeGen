package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建库 DDL 分批规划纯函数测试：只验切分边界，不碰数据库。
 */
class DdlBatchPlannerTest {

    private static List<String> stmts(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add("CREATE TABLE t" + i + " (id bigint)");
        }
        return list;
    }

    private static List<Integer> sizes(List<List<String>> batches) {
        List<Integer> sizes = new ArrayList<>();
        for (List<String> batch : batches) {
            sizes.add(batch.size());
        }
        return sizes;
    }

    @Test
    @DisplayName("整除：6 条按 2 切 → 3 批各 2 条")
    void exactDivision() {
        List<List<String>> batches = DdlBatchPlanner.split(stmts(6), 2);
        assertEquals(3, batches.size());
        assertEquals(List.of(2, 2, 2), sizes(batches));
        assertEquals("CREATE TABLE t1 (id bigint)", batches.get(0).get(0));
        assertEquals("CREATE TABLE t6 (id bigint)", batches.get(2).get(1));
    }

    @Test
    @DisplayName("有余数：7 条按 3 切 → 3/3/1，末批只剩 1 条")
    void remainder() {
        List<List<String>> batches = DdlBatchPlanner.split(stmts(7), 3);
        assertEquals(3, batches.size());
        assertEquals(List.of(3, 3, 1), sizes(batches));
        assertEquals("CREATE TABLE t7 (id bigint)", batches.get(2).get(0));
    }

    @Test
    @DisplayName("空列表：返回空批次列表，调用方无需判空")
    void emptyList() {
        assertTrue(DdlBatchPlanner.split(List.of(), 50).isEmpty());
        assertTrue(DdlBatchPlanner.split(null, 50).isEmpty());
    }

    @Test
    @DisplayName("size<=0 兜底：不分批，全部语句一批")
    void nonPositiveSizeFallsBackToSingleBatch() {
        assertEquals(List.of(5), sizes(DdlBatchPlanner.split(stmts(5), 0)));
        assertEquals(List.of(5), sizes(DdlBatchPlanner.split(stmts(5), -3)));
        assertEquals(1, DdlBatchPlanner.split(stmts(5), 0).size());
    }

    @Test
    @DisplayName("单批：语句数小于批大小 → 一批，顺序与内容不变")
    void singleBatch() {
        List<String> input = stmts(3);
        List<List<String>> batches = DdlBatchPlanner.split(input, 50);
        assertEquals(1, batches.size());
        assertEquals(input, batches.get(0));
    }

    @Test
    @DisplayName("语句数恰好等于批大小 → 一批，末尾不产生空批")
    void sizeEqualsBatchSize() {
        List<List<String>> batches = DdlBatchPlanner.split(stmts(50), 50);
        assertEquals(1, batches.size());
        assertEquals(50, batches.get(0).size());
    }

    @Test
    @DisplayName("失败摘要：不超 200 字符原样返回，超长截断并加省略号")
    void snippetTruncatesAt200() {
        String shortSql = "CREATE TABLE t1 (id bigint);";
        assertEquals(shortSql, DdlBatchPlanner.snippet(shortSql));

        String longSql = "CREATE TABLE t2 (" + "x".repeat(300) + ");";
        String snippet = DdlBatchPlanner.snippet(longSql);
        assertEquals(203, snippet.length(), "前 200 字符 + 3 个省略号");
        assertTrue(snippet.endsWith("..."));
        assertEquals("", DdlBatchPlanner.snippet(null));
    }
}
