package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AC6 防漂移：审计列定义的金样断言。
 *
 * <p>这些字符串是 V5.0 buildDdl / fixColumns 的原样输出——V5.1 抽出
 * {@link GenAuditColumns} 单一实现后，AI 模式产物必须逐字节一致。
 * 改列定义只许改 GenAuditColumns，并同步更新本文件的金样。
 */
class GenAuditColumnsTest {

    @Test
    @DisplayName("columnDef 金样：与 V5.0 buildDdl 内联定义逐字节一致")
    void columnDefGolden() {
        assertEquals("`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'", GenAuditColumns.columnDef("id"));
        assertEquals("`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'",
                GenAuditColumns.columnDef("create_time"));
        assertEquals("`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'",
                GenAuditColumns.columnDef("update_time"));
        assertEquals("`create_by` bigint NULL COMMENT '创建人'", GenAuditColumns.columnDef("create_by"));
        assertEquals("`update_by` bigint NULL COMMENT '更新人'", GenAuditColumns.columnDef("update_by"));
        assertEquals("`deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除'", GenAuditColumns.columnDef("deleted"));
    }

    @Test
    @DisplayName("alterFragment 金样：与 V5.0 fixColumns 输出逐字节一致")
    void alterFragmentGolden() {
        assertEquals("ADD COLUMN `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST",
                GenAuditColumns.alterFragment("id"));
        assertEquals("ADD COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'",
                GenAuditColumns.alterFragment("create_time"));
        assertEquals("ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除'",
                GenAuditColumns.alterFragment("deleted"));
        // 未知列回落与原实现一致
        assertEquals("ADD COLUMN `weird_col` varchar(255) NULL", GenAuditColumns.alterFragment("weird_col"));
    }

    @Test
    @DisplayName("missingIn：按 COLS 顺序返回缺失列，全齐返回空")
    void missingIn() {
        assertEquals(java.util.List.of(),
                GenAuditColumns.missingIn(java.util.List.of("id", "create_time", "update_time",
                        "create_by", "update_by", "deleted")));
        assertEquals(java.util.List.of("update_time", "deleted"),
                GenAuditColumns.missingIn(java.util.List.of("id", "create_time", "create_by", "update_by")));
    }
}
