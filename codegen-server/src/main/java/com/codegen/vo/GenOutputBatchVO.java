package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 写盘批次摘要 VO（按批次汇总 gen_output，F13-2）
 */
@Data
public class GenOutputBatchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String batchNo;

    /**
     * 涉及表数（去重表名）
     */
    private Integer tables;

    /**
     * 文件数（台账行数）
     */
    private Integer files;

    private Integer written;

    private Integer overwritten;

    private Integer merged;

    private Integer skipped;

    private Integer errors;

    /**
     * 实际写盘总字节（WRITTEN/OVERWRITTEN/MERGED 之和）
     */
    private Long totalBytes;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 最后写盘时间
     */
    private LocalDateTime lastTime;
}
