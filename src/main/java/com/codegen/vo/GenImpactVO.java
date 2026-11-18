package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * V6.8 重生成影响面：dryRun 时逐文件告诉调用方「会新增什么、会改什么、哪些按受保护跳过」。
 *
 * <p>计数与 {@link #files} 同源（都由差异引擎逐文件算出），不允许两处各算一遍——
 * 否则会出现「汇总说 3 个新增、明细只有 2 个」的经典不一致。
 */
@Data
public class GenImpactVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 参与比对的文件总数 */
    private int total;

    /** 磁盘上不存在、会被新增的文件数 */
    private int newCount;

    /** 已存在且内容不同（会被覆盖，除非受保护）的文件数 */
    private int modifiedCount;

    /** 已存在且内容一致（无需写盘）的文件数 */
    private int unchangedCount;

    /** 已存在但无台账快照（可能手改过）→ 默认跳过，需 force 才覆盖的文件数 */
    private int protectedCount;

    private List<File> files = new ArrayList<>();

    @Data
    public static class File implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String tableName;

        private String path;

        /** NEW / MODIFIED / UNCHANGED（内容事实，与写盘策略无关） */
        private String status;

        /** 该文件是否受保护（既有文件 + 无台账快照） */
        private boolean protectedFile;

        private int additions;

        private int deletions;
    }
}
