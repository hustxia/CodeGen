package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 数据库表批量导入请求 DTO
 */
@Data
public class GenDbImportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据库名
     */
    private String schema;

    /**
     * 选中的表名列表
     */
    private List<String> tableNames;
}
