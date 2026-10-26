package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码生成下载请求 DTO
 */
@Data
public class GenDownloadDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表配置 ID 列表
     */
    private List<Long> ids;
}
