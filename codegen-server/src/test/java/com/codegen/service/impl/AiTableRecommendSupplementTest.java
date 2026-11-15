package com.codegen.service.impl;

import com.codegen.vo.GenPipelineAiVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.5 补表差集单测（设计 §5.4「只推荐该系统缺失的表」）。
 *
 * <p>背景：补表（scope=supplement）曾与新建系统走同一条路径，plan() 完全不读 projectId，
 * 把已有表整批再推荐一遍，用户看到的就是「补表还是重复表」。这里锁住服务端兜底行为：
 * ① 已有表必须剔除；② 同一次推荐内跨模块重名只留一张；③ 表名大小写/空格归一；
 * ④ 模块映射按保留表裁剪，整模块被剔空则丢弃（避免建出空服务工程）。
 */
class AiTableRecommendSupplementTest {

    @Test
    void filterTables_dropsExistingTablesAndReports() {
        List<String> excluded = new ArrayList<>();
        Set<String> existing = Set.of("asset_device", "asset_category");

        List<GenPipelineAiVO.TableModel> kept = AiTableRecommendServiceImpl.filterTables(
                List.of(table("asset_device"), table("asset_supplier"), table("asset_category")),
                existing, excluded);

        assertEquals(1, kept.size(), "只应留下真正缺失的 asset_supplier");
        assertEquals("asset_supplier", kept.get(0).getTableName());
        assertEquals(List.of("asset_device（系统中已存在）", "asset_category（系统中已存在）"), excluded);
    }

    @Test
    void filterTables_dedupesCrossModuleDuplicates() {
        List<String> excluded = new ArrayList<>();

        List<GenPipelineAiVO.TableModel> kept = AiTableRecommendServiceImpl.filterTables(
                List.of(table("bus_order"), table("bus_order"), table("bus_order_item")),
                Set.of(), excluded);

        assertEquals(2, kept.size(), "重名表只留第一张");
        assertEquals(List.of("bus_order（推荐结果内重名）"), excluded);
    }

    @Test
    void filterTables_normalizesCaseAndWhitespace() {
        List<String> excluded = new ArrayList<>();

        List<GenPipelineAiVO.TableModel> kept = AiTableRecommendServiceImpl.filterTables(
                List.of(table(" Asset_Device ") , table("asset_device")),
                Set.of("asset_device"), excluded);

        assertTrue(kept.isEmpty(), "大小写/空格归一后都应判定为已有表");
        assertEquals(List.of("asset_device（系统中已存在）"), excluded, "排除明细不应重复刷屏");
    }

    @Test
    void filterTables_emptyInputIsSafe() {
        List<String> excluded = new ArrayList<>();
        assertTrue(AiTableRecommendServiceImpl.filterTables(List.of(), Set.of(), excluded).isEmpty());
        assertTrue(AiTableRecommendServiceImpl.filterTables(null, null, excluded).isEmpty());
        assertTrue(excluded.isEmpty());
    }

    @Test
    void pruneModules_keepsOnlyRemainingTablesAndDropsEmptyModule() {
        Map<String, List<String>> mods = new LinkedHashMap<>();
        mods.put("设备台账", List.of("asset_device", "asset_supplier"));
        mods.put("维修工单", List.of("asset_repair"));

        Set<String> kept = new LinkedHashSet<>(List.of("asset_supplier"));

        Map<String, List<String>> pruned = AiTableRecommendServiceImpl.pruneModules(mods, kept);

        assertEquals(1, pruned.size(), "整模块被剔空的应丢弃");
        assertEquals(List.of("asset_supplier"), pruned.get("设备台账"));
        assertTrue(!pruned.containsKey("维修工单"));
    }

    @Test
    void pruneModules_nullSafe() {
        assertTrue(AiTableRecommendServiceImpl.pruneModules(null, Set.of("a")).isEmpty());
        assertTrue(AiTableRecommendServiceImpl.pruneModules(Map.of(), Set.of("a")).isEmpty());
    }

    // ------------------------------------------------------------------

    private static GenPipelineAiVO.TableModel table(String name) {
        GenPipelineAiVO.TableModel t = new GenPipelineAiVO.TableModel();
        t.setTableName(name);
        t.setTableComment(name);
        t.setColumns(new ArrayList<>());
        return t;
    }
}
