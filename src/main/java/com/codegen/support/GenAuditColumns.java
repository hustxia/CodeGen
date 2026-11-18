package com.codegen.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * BaseEntity 约定审计列的<strong>唯一定义源</strong>（V5.1 单一实现）。
 *
 * <p>三处消费方必须全部走这里，禁止各自再写一遍列定义：
 * <ul>
 *   <li>AI 模式 buildDdl（CREATE TABLE 内联列定义）</li>
 *   <li>手动 DDL 改写补齐 {@link GenDdlPatcher}（同上）</li>
 *   <li>已建表 lint/fix（ALTER TABLE ADD COLUMN 片段）</li>
 * </ul>
 * 改动列定义只改本类，AI/手动/fix 三条路径自动一致（AC6 防漂移）。
 */
public final class GenAuditColumns {

    /** BaseEntity 约定列：目标库每张业务表都必须有，否则生成物运行期才爆 SQL 错 */
    public static final String[] COLS = {
            "id", "create_time", "update_time", "create_by", "update_by", "deleted"
    };

    private GenAuditColumns() {
    }

    /**
     * 计算 present 中缺失的审计列（按 COLS 顺序返回）。
     */
    public static List<String> missingIn(Collection<String> present) {
        List<String> missing = new ArrayList<>();
        for (String need : COLS) {
            if (!present.contains(need)) {
                missing.add(need);
            }
        }
        return missing;
    }

    /**
     * CREATE TABLE 体内的列定义片段（不含 ADD COLUMN、不带行首缩进与结尾逗号）。
     * 默认分支与既有 fixColumns 行为一致：未知列给 varchar(255) NULL。
     */
    public static String columnDef(String col) {
        switch (col) {
            case "id": return "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'";
            case "create_time": return "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'";
            case "update_time": return "`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'";
            case "create_by": return "`create_by` bigint NULL COMMENT '创建人'";
            case "update_by": return "`update_by` bigint NULL COMMENT '更新人'";
            case "deleted": return "`deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除'";
            default: return "`" + col + "` varchar(255) NULL";
        }
    }

    /**
     * 非自增的 id 列定义：给「表已有自增业务主键（如 wc_id AUTO_INCREMENT）」的场景用。
     * MySQL 一表只允许一个 AUTO_INCREMENT（错误 1075），不能再注入自增 id；
     * BaseEntity 的 id 是 {@code @TableId(ASSIGN_ID)}——应用层雪花生成、不依赖数据库自增，
     * 所以可空普通 bigint 即可满足约定；用户种子 INSERT 不写 id 也不会违反 NOT NULL。
     */
    public static String idDefPlain() {
        return "`id` bigint NULL COMMENT '主键（应用层雪花ID）'";
    }

    /**
     * ALTER TABLE 的 ADD COLUMN 片段。id 列带 PRIMARY KEY FIRST：
     * 已有主键的表会执行失败并如实报错（只补缺、不改错的边界，见 V5.1 规划风险 2）。
     */
    public static String alterFragment(String col) {
        if ("id".equals(col)) {
            return "ADD COLUMN `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST";
        }
        return "ADD COLUMN " + columnDef(col);
    }
}
