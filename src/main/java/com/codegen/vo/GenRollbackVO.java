package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 回滚结果 / 预览（V4.2 P0-2）
 *
 * <p>预览与执行返回同一个结构：{@code executed=false} 是预览，{@code true} 是执行完。
 * 这样前端「预览 → 确认 → 执行」两步可以用同一个渲染器，不会出现
 * 「预览说会还原 3 个、执行结果字段名不一样」这种对不上的情况。
 */
@Data
public class GenRollbackVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被回滚的原始批次
     */
    private String batchNo;

    /**
     * 本次回滚产生的批次号（回滚动作也记台账，因此可以再回滚）
     */
    private String rollbackBatch;

    private boolean executed;

    private int total;

    /**
     * 将还原 / 已还原
     */
    private int restore;

    /**
     * 将删除 / 已删除
     */
    private int remove;

    /**
     * 将跳过 / 已跳过
     */
    private int skip;

    /**
     * 其中磁盘已被手改的文件数（预览里要显眼标出来）
     */
    private int drifted;

    private List<Item> items = new ArrayList<>();

    private List<String> problems = new ArrayList<>();

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String ledgerId;

        private String tableId;

        private String tableName;

        /**
         * backend / frontend（回滚台账沿用原归属）
         */
        private String fileGroup;

        private String relPath;

        private String absPath;

        /**
         * 台账原始动作：WRITTEN / OVERWRITTEN / MERGED / ROLLBACK
         */
        private String action;

        /**
         * 本次要做的动作：RESTORE 还原 / DELETE 删除 / SKIP 跳过
         */
        private String op;

        private String reason;

        /**
         * 磁盘内容与台账不一致（生成后被手改）
         */
        private boolean drifted;

        /**
         * 判定出的来源路径（RESTORE 时是备份文件路径），仅后端内部与展示用
         */
        private String reasonPath;

        /**
         * 是否真的执行了（预览时恒 false）
         */
        private boolean applied;

        /**
         * 执行后的结果说明
         */
        private String message;
    }
}
