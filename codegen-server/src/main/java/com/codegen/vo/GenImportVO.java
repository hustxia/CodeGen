package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量导入结果 VO
 */
@Data
public class GenImportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;
    private String status;
    private String message;

    public GenImportVO() {
    }

    public GenImportVO(String tableName, String status, String message) {
        this.tableName = tableName;
        this.status = status;
        this.message = message;
    }
}
