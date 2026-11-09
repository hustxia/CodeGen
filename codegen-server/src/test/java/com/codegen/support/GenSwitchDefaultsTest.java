package com.codegen.support;

import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 生成开关默认值：{@code 表级 → 系统 → 硬编码 1}。
 *
 * <p>要点不是「能取到值」，而是两个边界：
 * 1) 系统配置为 NULL 时必须回落到 1，不能写空（DB 是 NOT NULL）；
 * 2) 已经有值的开关不能被系统默认值冲掉——否则用户点了「仅后端」预设，
 *    保存时被系统默认值顶回全开，表现为「预设没生效」。
 */
@DisplayName("GenSwitchDefaults 开关默认值")
class GenSwitchDefaultsTest {

    private static SysProject project(Integer log, Integer frontend) {
        SysProject p = new SysProject();
        p.setProjectCode("mkt");
        p.setDefaultGenLog(log);
        p.setDefaultGenFrontend(frontend);
        return p;
    }

    @Test
    @DisplayName("表级有值 → 用表级")
    void tableWins() {
        assertEquals(0, GenSwitchDefaults.resolve(0, 1));
        assertEquals(1, GenSwitchDefaults.resolve(1, 0));
    }

    @Test
    @DisplayName("表级为空 → 用系统")
    void projectWins() {
        assertEquals(0, GenSwitchDefaults.resolve(null, 0));
    }

    @Test
    @DisplayName("表级与系统都为空 → 硬编码 1")
    void hardcodedFallback() {
        assertEquals(1, GenSwitchDefaults.resolve(null, null));
    }

    @Test
    @DisplayName("fillMissing 只补空值，不覆盖已有意图")
    void fillMissingKeepsExplicitValues() {
        GenTable table = new GenTable();
        // 模拟用户点了「仅后端」预设：前端相关显式关掉
        table.setGenFrontend(0);
        table.setGenApiJs(0);
        table.setGenLog(null);

        GenSwitchDefaults.fillMissing(table, project(0, 1));

        assertEquals(0, table.getGenFrontend(), "用户显式关掉的前端不能被系统默认值打开");
        assertEquals(0, table.getGenApiJs());
        assertEquals(0, table.getGenLog(), "为空的开关取系统默认值 0");
        assertEquals(1, table.getGenEntity(), "系统没配的开关回落 1");
    }

    @Test
    @DisplayName("resetToProject 全部重设成系统默认值")
    void resetToProjectOverwrites() {
        GenTable table = new GenTable();
        table.setGenLog(1);
        table.setGenFrontend(1);

        GenSwitchDefaults.resetToProject(table, project(0, 0));

        assertEquals(0, table.getGenLog());
        assertEquals(0, table.getGenFrontend());
        assertEquals(1, table.getGenDeleteBtn());
    }

    @Test
    @DisplayName("未绑定系统时 fillMissing 仍然写满 17 个字段（DB 列是 NOT NULL）")
    void fillMissingWithoutProject() {
        GenTable table = new GenTable();
        GenSwitchDefaults.fillMissing(table, null);

        Map<String, Integer> values = GenSwitchDefaults.defaultsOf(null);
        assertEquals(17, values.size());
        for (Integer value : values.values()) {
            assertEquals(1, value, "没有系统配置时一律 1");
        }
        assertEquals(1, table.getGenEntity());
        assertEquals(1, table.getGenDeleteBtn());
    }
}
