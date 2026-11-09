package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * P3-2 中间表标记入参
 */
@Data
public class RelationApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库名，默认 shop */
    private String schema;

    /** 要标记为中间表的表名 */
    private List<String> middleTables;
}
