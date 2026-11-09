package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 写盘台账分页查询 DTO
 */
@Data
public class GenOutputPageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /**
     * 表名（模糊）
     */
    private String tableName;

    /**
     * 批次号（精确）
     */
    private String batchNo;

    /**
     * 动作:WRITTEN/OVERWRITTEN/MERGED/SKIPPED/ERROR
     */
    private String action;
}
