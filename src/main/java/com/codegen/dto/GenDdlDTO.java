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
}
