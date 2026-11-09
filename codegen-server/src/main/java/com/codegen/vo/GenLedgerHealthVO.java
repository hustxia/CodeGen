package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 台账健康检查报告（V4.2 P1-1）
 *
 * <p>只报告不自动修：孤儿记录分不清「用户故意删的文件」和「异常丢失」，
 * 自动清理会把用户的正常操作当成错误修掉，制造二次错误。
 */
@Data
public class GenLedgerHealthVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 台账总行数（含 SKIPPED/ERROR/ROLLBACK）
     */
    private int totalRecords;

    /**
     * 去重后的产物路径数
     */
    private int uniquePaths;

    /**
     * 孤儿记录：台账有、磁盘无
     */
    private int orphanCount;

    /**
     * 备份缺失：台账记了 backup_path，但备份文件不在
     */
    private int backupMissingCount;

    /**
     * 重复记录：同批次 + 同路径出现多行
     */
    private int duplicateCount;

    /**
     * 漂移文件数
     */
    private int driftedCount;

    private List<Issue> orphan = new ArrayList<>();

    private List<Issue> backupMissing = new ArrayList<>();

    private List<Issue> duplicate = new ArrayList<>();

    @Data
    public static class Issue implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String batchNo;

        private String tableName;

        private String relPath;

        private String path;

        private String detail;
    }
}
