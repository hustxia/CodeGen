package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DDL 解析请求 DTO
 */
@Data
public class GenDdlDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 建表语句
     */
    private String ddl;

    /**
     * 表名（可选）：字典编码前缀用。为空时从 DDL 的 CREATE TABLE 语句里取。
     */
    private String tableName;
}
