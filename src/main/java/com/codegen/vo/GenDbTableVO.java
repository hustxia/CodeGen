package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据库表 VO（库表列表）
 */
@Data
public class GenDbTableVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表名
     */
    private String name;

    /**
     * 表注释
     */
    private String comment;
}
