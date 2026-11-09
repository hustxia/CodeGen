package com.codegen.service.impl;

import com.codegen.entity.GenTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.0 分批策略（AC-6：40 表系统分批调用，单批输出不超预算，建议完整无截断）。
 *
 * <p>分批是纯函数：按现有 moduleName 分组，组内再按上限切。
 * 单批规模有界 → 单次 AI 输出 token 有界 → 不会因截断产生非法 JSON。
 */
class AiRecommendBatchTest {

    private GenTable table(String name, String module) {
        GenTable t = new GenTable();
        t.setTableName(name);
        t.setModuleName(module);
        return t;
    }

    @Test
    @DisplayName("40 张无模块表：切成 4 批每批 10 张，表不丢不重")
    void fortyTablesChunked() {
        List<GenTable> tables = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            tables.add(table("t_" + i, null));
        }
        List<List<GenTable>> batches = AiRecommendServiceImpl.batches(tables, 10);

        assertEquals(4, batches.size());
        assertTrue(batches.stream().allMatch(b -> b.size() <= 10), "单批不超上限");
        assertEquals(40, batches.stream().mapToInt(List::size).sum(), "总表数不丢");
        assertEquals(40, batches.stream().flatMap(List::stream).map(GenTable::getTableName).distinct().count(),
                "表不重复");
    }

    @Test
    @DisplayName("按模块分组：不同模块不混批，同模块超上限再切")
    void groupedByModule() {
        List<GenTable> tables = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            tables.add(table("stock_" + i, "stock"));
        }
        for (int i = 1; i <= 3; i++) {
            tables.add(table("order_" + i, "order"));
        }
        List<List<GenTable>> batches = AiRecommendServiceImpl.batches(tables, 10);

        // stock 12 张 → 10+2 两批；order 3 张 → 一批；共 3 批
        assertEquals(3, batches.size());
        for (List<GenTable> batch : batches) {
            long distinct = batch.stream().map(GenTable::getModuleName).distinct().count();
            assertEquals(1, distinct, "同批只含一个模块，prompt 上下文内聚");
        }
        assertEquals(15, batches.stream().mapToInt(List::size).sum());
    }

    @Test
    @DisplayName("单表系统：一批跑完（边界）")
    void singleTable() {
        List<List<GenTable>> batches = AiRecommendServiceImpl.batches(List.of(table("t1", null)), 10);
        assertEquals(1, batches.size());
        assertEquals(1, batches.get(0).size());
    }
}
