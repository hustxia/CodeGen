package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码生成写盘请求 DTO
 */
@Data
public class GenWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表配置 ID 列表
     */
    private List<Long> ids;

    /**
     * 同名文件是否覆盖（默认跳过）
     */
    private Boolean overwrite;
}
