package com.codegen.support;

/**
 * V9.18 子表保存模式（gen_table.child_save_mode）。
 *
 * <p>只对<b>主子表的主表</b>生效：单表/树形没有子行，两种模式等价。
 * 与 {@link GenCascadeMode} 的差别在可空性：级联模式是三态（NULL=未配置回落系统默认），
 * 保存模式只有两档且默认明确，所以库列是 {@code NOT NULL DEFAULT 0}，
 * 解析时 null 一律落到 {@link #FULL_REPLACE}——保证存量行为不变。
 *
 * <p>红线：{@link #FULL_REPLACE} 分支的产物必须与 V9.18 之前逐字节一致，
 * 增量逻辑只能出现在 {@link #INCREMENTAL} 分支里。
 */
public final class ChildSaveMode {

    /** 全量替换：先按外键删旧行再逐行插入（默认，存量行为） */
    public static final int FULL_REPLACE = 0;

    /** 增量合并：按主键三路合并（一对一 upsert / 多对多中间表差集） */
    public static final int INCREMENTAL = 1;

    private ChildSaveMode() {
    }

    /**
     * 解析生效模式：null 或越界值一律落到全量替换，保证存量表行为不变。
     */
    public static int resolve(Integer mode) {
        return mode != null && mode == INCREMENTAL ? INCREMENTAL : FULL_REPLACE;
    }

    /** 合法值校验：null（未提交）视为合法，只有 0/1 是明确可接受值 */
    public static boolean isValid(Integer mode) {
        return mode == null || mode == FULL_REPLACE || mode == INCREMENTAL;
    }

    /** 入库归一：null/越界 → 0，避免 NOT NULL 列写入 null 或脏值逃进模板 */
    public static Integer sanitize(Integer mode) {
        return resolve(mode);
    }

    /** 面向用户提示文案 */
    public static String describe(int mode) {
        return mode == INCREMENTAL ? "增量合并（按主键三路合并）" : "全量替换（先删后插）";
    }
}
