package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.1 P0-1 手动 DDL 改写补齐：只增不改 + 解析不了降级阻断。
 */
class GenDdlPatcherTest {

    private static final String FULL_DDL = """
            CREATE TABLE `biz_order` (
              `order_no` varchar(64) NOT NULL COMMENT '单号',
              `amount` decimal(18,2) NULL COMMENT '金额',
              PRIMARY KEY (`order_no`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';
            """;

    @Test
    @DisplayName("AC2 全齐：逐字节原样返回，明细全 SKIPPED")
    void noPatchWhenAllPresent() {
        String ddl = """
                CREATE TABLE `t_complete` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `name` varchar(64) NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `create_by` bigint NULL,
                  `update_by` bigint NULL,
                  `deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl);
        assertEquals(ddl, r.getDdl());
        assertEquals(1, r.getItems().size());
        assertTrue(r.getItems().get(0).getPatchedCols().isEmpty());
    }

    @Test
    @DisplayName("AC1 全缺：补齐六列，已有主键(order_no)时注入非自增可空 id，业务列逐字保留")
    void patchAllMissing() {
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(FULL_DDL);
        String out = r.getDdl();
        // 业务列与原文逐字一致
        assertTrue(out.contains("`order_no` varchar(64) NOT NULL COMMENT '单号'"));
        assertTrue(out.contains("`amount` decimal(18,2) NULL COMMENT '金额'"));
        assertTrue(out.contains("PRIMARY KEY (`order_no`)"));
        // 表已有显式主键：注入非自增可空 id（BaseEntity ASSIGN_ID 应用层生成），
        // 不追加 PRIMARY KEY(id)（MySQL 1068 不允许双主键）
        assertTrue(out.contains(GenAuditColumns.idDefPlain() + ","));
        assertFalse(out.contains("PRIMARY KEY (`id`)"));
        assertFalse(out.contains("AUTO_INCREMENT"));
        // 五个审计列齐
        for (String col : new String[]{"create_time", "update_time", "create_by", "update_by", "deleted"}) {
            assertTrue(out.contains("`" + col + "`"), "缺 " + col);
        }
        // 明细：PATCHED 六列
        assertEquals(1, r.getItems().size());
        assertEquals(6, r.getItems().get(0).getPatchedCols().size());
        // 语法完整性：插入后仍是合法列定义序列（尾逗号处理正确）
        assertFalse(out.contains(",\n  ,"));
        assertFalse(out.contains(",,"));
    }

    @Test
    @DisplayName("无主键无自增：注入自增 id + PRIMARY KEY(id)（原行为）")
    void autoIdWhenNoPkNoAutoInc() {
        String ddl = """
                CREATE TABLE `t_plain` (
                  `name` varchar(64) NULL
                ) ENGINE=InnoDB;
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl);
        String out = r.getDdl();
        assertTrue(out.contains("`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',"));
        assertTrue(out.contains("PRIMARY KEY (`id`)"));
        assertEquals(6, r.getItems().get(0).getPatchedCols().size());
    }

    @Test
    @DisplayName("自增业务主键（MES 场景）：不再注入第二个 AUTO_INCREMENT，补非自增可空 id")
    void plainIdWhenBusinessAutoIncPk() {
        String ddl = """
                CREATE TABLE `md_work_center` (
                  `wc_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工位ID',
                  `wc_code` varchar(32) NOT NULL COMMENT '工位编码',
                  `wc_name` varchar(64) NOT NULL COMMENT '工位名称',
                  PRIMARY KEY (`wc_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl);
        String out = r.getDdl();
        // 全表只允许一个 AUTO_INCREMENT（MySQL 1075）
        assertEquals(1, out.split("AUTO_INCREMENT", -1).length - 1, "只能有 wc_id 一个自增列");
        // id 以非自增可空定义注入段首
        assertTrue(out.contains(GenAuditColumns.idDefPlain() + ","));
        assertTrue(out.indexOf(GenAuditColumns.idDefPlain()) < out.indexOf("`wc_id`"), "id 应插在业务列之前");
        // 业务主键原样保留，不追加第二个 PRIMARY KEY
        assertTrue(out.contains("PRIMARY KEY (`wc_id`)"));
        assertFalse(out.contains("PRIMARY KEY (`id`)"));
        // 其余审计列照常补齐
        assertTrue(out.contains("`create_time`"));
        assertTrue(out.contains("`deleted`"));
        assertEquals(6, r.getItems().get(0).getPatchedCols().size());
    }

    @Test
    @DisplayName("AC3 部分缺：只补缺列，已有审计列类型/注释不动")
    void patchPartial() {
        String ddl = """
                CREATE TABLE `t_partial` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `code` varchar(32) NULL,
                  `create_time` datetime DEFAULT NULL COMMENT '业务自己写的创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl);
        String out = r.getDdl();
        // 用户的 create_time 定义原样保留（不改错：类型差异只提示不纠正）
        assertTrue(out.contains("`create_time` datetime DEFAULT NULL COMMENT '业务自己写的创建时间'"));
        // 只补 create_by/update_by/deleted
        assertTrue(out.contains("`create_by` bigint NULL COMMENT '创建人'"));
        assertTrue(out.contains("`update_by` bigint NULL COMMENT '更新人'"));
        assertTrue(out.contains("`deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除'"));
        assertEquals(3, r.getItems().get(0).getPatchedCols().size());
        assertFalse(r.getItems().get(0).getPatchedCols().contains("id"));
        assertFalse(r.getItems().get(0).getPatchedCols().contains("create_time"));
    }

    @Test
    @DisplayName("只缺 create_by：无 PRIMARY KEY 追加，补丁无前导逗号问题")
    void patchSingleCol() {
        String ddl = """
                CREATE TABLE `t_one` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `name` varchar(64) NULL
                ) ENGINE=InnoDB;
                """;
        // 注意：上面少 5 列；再造一个只缺 1 列且最后一列无尾逗号的
        String ddl2 = """
                CREATE TABLE `t_one` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `name` varchar(64) NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `update_by` bigint NULL,
                  `deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB;
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl2);
        String out = r.getDdl();
        assertTrue(out.contains("`create_by` bigint NULL COMMENT '创建人'"));
        assertEquals(1, r.getItems().get(0).getPatchedCols().size());
        // PRIMARY KEY 子句之后补列：逗号衔接正确
        assertTrue(out.contains("PRIMARY KEY (`id`),\n  `create_by`"));
        // 原文其余部分逐字节保留
        assertTrue(out.contains("`name` varchar(64) NULL,"));
        assertFalse(ddl2.equals(out));
    }

    @Test
    @DisplayName("缺 id 且已有非自增主键：id 列插段首（非自增定义），不追加 PK 子句")
    void patchMissingId() {
        String ddl = """
                CREATE TABLE `t_noid` (
                  `code` varchar(32) NOT NULL,
                  PRIMARY KEY (`code`)
                ) ENGINE=InnoDB;
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl);
        String out = r.getDdl();
        int idIdx = out.indexOf(GenAuditColumns.idDefPlain());
        int codeIdx = out.indexOf("`code` varchar(32)");
        assertTrue(idIdx >= 0 && codeIdx > idIdx, "id 应在业务列之前");
        assertFalse(out.contains("PRIMARY KEY (`id`)"), "已有主键时不追加 PK(id)");
        assertEquals(6, r.getItems().get(0).getPatchedCols().size());
    }

    @Test
    @DisplayName("多表混合：一表 PATCHED 一表 SKIPPED，互不串扰")
    void patchMultipleTables() {
        // biz_order 全缺、t_two 只缺 5 个审计列（有 id）
        String two = FULL_DDL + "\nCREATE TABLE IF NOT EXISTS `t_two` (\n"
                + "  `id` bigint NOT NULL AUTO_INCREMENT,\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB;\n";
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(two);
        assertEquals(2, r.getItems().size());
        assertEquals("biz_order", r.getItems().get(0).getTable());
        assertEquals(6, r.getItems().get(0).getPatchedCols().size());
        assertEquals("t_two", r.getItems().get(1).getTable());
        assertEquals(5, r.getItems().get(1).getPatchedCols().size());
        assertTrue(r.getDdl().contains("biz_order"));
        assertTrue(r.getDdl().contains("`t_two`"));
    }

    @Test
    @DisplayName("解析不了的 CREATE TABLE（缺 ENGINE）降级阻断，不改 SQL")
    void blockOnUnparseable() {
        String bad = """
                CREATE TABLE `t_bad` (
                  `id` bigint NOT NULL AUTO_INCREMENT
                );
                """;
        BusinessException e = assertThrows(BusinessException.class, () -> GenDdlPatcher.patch(bad));
        assertTrue(e.getMessage().contains("无法解析"));
        // 混合：一条可解析 + 一条不可解析，同样阻断（不能只补能解析的留下暗坑）
        assertThrows(BusinessException.class, () -> GenDdlPatcher.patch(bad + FULL_DDL));
    }

    @Test
    @DisplayName("CREATE DATABASE 前缀与注释文本不影响改写")
    void tolerantToPrefixAndComments() {
        String ddl = """
                -- 老库导出 2024-03-01
                CREATE TABLE `t_c` (
                  `id` bigint NOT NULL AUTO_INCREMENT, -- 行内注释
                  `name` varchar(64) NULL
                ) ENGINE=InnoDB COMMENT='测试';
                """;
        GenDdlPatcher.PatchResult r = GenDdlPatcher.patch(ddl);
        assertTrue(r.getDdl().startsWith("-- 老库导出"));
        assertTrue(r.getDdl().contains("-- 行内注释"));
        assertEquals(5, r.getItems().get(0).getPatchedCols().size());
    }

    // ------------------------------------------------------------------
    // 坑 38：建库/USE 判断必须语句级，子串 contains 会误判
    // ------------------------------------------------------------------

    @Test
    @DisplayName("坑38复现：无反引号的 mdm_warehouse 不能误判为自带 USE（事故根因）")
    void warehouseSubstringMustNotCountAsUse() {
        // 事故 DDL 无反引号："mdm_warehouse (" 含子串 "use "（wareh**ouse (**）
        String ddl = """
                CREATE TABLE mdm_warehouse (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `name` varchar(64) NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB COMMENT='仓库';
                """;
        assertTrue(ddl.toLowerCase().contains("use "), "旧判断 contains(\"use \") 确实被 'house (' 误命中——这就是事故根因");
        assertFalse(GenDdlPatcher.hasUseStatement(ddl), "语句级判断不误判");
        assertFalse(GenDdlPatcher.createsDatabase(ddl));
    }

    @Test
    @DisplayName("语句级识别：CREATE DATABASE / CREATE SCHEMA / 行首 USE")
    void recognizesCreateDatabaseAndUseStatements() {
        assertTrue(GenDdlPatcher.createsDatabase("CREATE DATABASE IF NOT EXISTS `scm_data` DEFAULT CHARACTER SET utf8mb4;"));
        assertTrue(GenDdlPatcher.createsDatabase("create schema `x`;"));
        assertTrue(GenDdlPatcher.hasUseStatement("USE `scm_data`;\nCREATE TABLE t(a int) ENGINE=InnoDB;"));
        assertTrue(GenDdlPatcher.hasUseStatement("  use scm_data;"));
    }

    @Test
    @DisplayName("注释/普通词不误判：-- 使用说明、username、user 列")
    void commentsAndIdentifiersDoNotFoolDetection() {
        String ddl = """
                -- 使用说明：先在测试库执行
                CREATE TABLE `t_user` (
                  `username` varchar(64) NULL
                ) ENGINE=InnoDB COMMENT='用户';
                """;
        assertFalse(GenDdlPatcher.hasUseStatement(ddl));
        assertFalse(GenDdlPatcher.createsDatabase(ddl));
    }
}
