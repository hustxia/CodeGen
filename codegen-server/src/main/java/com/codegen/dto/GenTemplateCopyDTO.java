package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 复制为表级模板入参
 */
@Data
public class GenTemplateCopyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源模板 id
     */
    private Long id;

    /**
     * 目标表 id（gen_table.id）
     */
    private Long tableId;
}
