package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 写盘结果 VO
 */
@Data
public class GenWriteVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;
    private String path;
    private String status;
    private String message;

    /**
     * 覆盖前的备份路径；文件原本不存在（首次生成）时为空
     */
    private String backupPath;

    /**
     * 写盘批次号（四期起直调写盘也生成 GEN+yyyyMMddHHmmss）
     */
    private String batchNo;

    public GenWriteVO() {
    }

    public GenWriteVO(String tableName, String path, String status, String message) {
        this(tableName, path, status, message, null);
    }

    public GenWriteVO(String tableName, String path, String status, String message, String backupPath) {
        this.tableName = tableName;
        this.path = path;
        this.status = status;
        this.message = message;
        this.backupPath = backupPath;
    }
}
