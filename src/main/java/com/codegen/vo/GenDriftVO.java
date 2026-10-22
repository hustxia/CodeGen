package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单文件漂移检测结果（V4.2）
 *
 * <p>回答一个问题：磁盘上这份文件，还是上次生成时那份吗？
 * 台账里的 {@code md5} 是「写完那一刻」的内容指纹，磁盘实算的 md5 与它不一致，
 * 说明中间有人手改过（或被别的工具改过）。
 */
@Data
public class GenDriftVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;

    private String relPath;

    private String absPath;

    /**
     * 取的是哪一批次的记录（每个路径取最新一条有 md5 的台账）
     */
    private String batchNo;

    /**
     * 该条台账的写盘动作：WRITTEN / OVERWRITTEN / MERGED
     */
    private String action;

    /**
     * 台账记录的内容 MD5
     */
    private String expectedMd5;

    /**
     * 磁盘实算 MD5（文件不存在为空）
     */
    private String actualMd5;

    /**
     * SAME 一致 / DRIFTED 已被手改 / MISSING 文件不存在 / UNKNOWN 无台账或读不了
     */
    private String status;

    private Long fileSize;

    /**
     * 磁盘最后修改时间（yyyy-MM-dd HH:mm:ss）
     */
    private String lastModified;

    private String message;
}
