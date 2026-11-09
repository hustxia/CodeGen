package com.codegen.support;

/**
 * V4.4 删除级联模式（gen_table.delete_cascade_mode / sys_project.default_delete_cascade_mode）。
 *
 * <p>只对<b>主子表的主表</b>生效，单表/树形没有子行，三种模式等价于 {@code removeById}。
 * 独立于 {@link GenSwitchDefaults}：那套开关的兜底语义是「默认 1=生成」，
 * 而级联模式是行为选择（删什么/能不能删），默认级联是 2026-10-23 的产品决定，
 * 硬编码兜底 {@link #CASCADE}，不随开关体系联动。
 *
 * <p>回落顺序：<b>表级字段 → 所属系统默认 → {@link #CASCADE}</b>，
 * 与 V3.0 的配置三级回落保持同构。
 */
public final class GenCascadeMode {

    /** 阻止：存在未删除子行时拒删，返回业务异常提示 */
    public static final int BLOCK = 0;

    /** 级联：同事务删除本表及全部子表（含三级）行，默认模式 */
    public static final int CASCADE = 1;

    /** 不处理：维持 V4.2 前现状，只删主行（存量行为的逃生门） */
    public static final int NONE = 2;

    private GenCascadeMode() {
    }

    /**
     * 三级回落解析：表级 → 系统默认 → 硬编码级联。
     *
     * @param tableMode   gen_table.delete_cascade_mode，可为 null（未配置）
     * @param projectMode sys_project.default_delete_cascade_mode，可为 null
     * @return 解析后的模式，恒为 0/1/2 之一
     */
    public static int resolve(Integer tableMode, Integer projectMode) {
        if (tableMode != null && tableMode >= BLOCK && tableMode <= NONE) {
            return tableMode;
        }
        if (projectMode != null && projectMode >= BLOCK && projectMode <= NONE) {
            return projectMode;
        }
        return CASCADE;
    }

    /** 越界值按未配置处理，防止脏数据逃进模板 */
    public static Integer sanitize(Integer mode) {
        return (mode != null && mode >= BLOCK && mode <= NONE) ? mode : null;
    }

    /** 面向用户提示文案 */
    public static String describe(int mode) {
        return switch (mode) {
            case BLOCK -> "阻止（存在明细时拒删）";
            case NONE -> "不处理（仅删主表）";
            default -> "级联（同步删除全部明细）";
        };
    }
}
