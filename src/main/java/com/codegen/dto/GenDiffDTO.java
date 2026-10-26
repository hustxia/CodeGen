package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 差异预览请求
 */
@Data
public class GenDiffDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Long> ids;

    /**
     * 是否把「文件已存在」也算进对比，默认 true；false 时只列新文件
     */
    private Boolean overwrite;

    /**
     * 是否只返回有改动的文件（默认 true，NEW/MODIFIED）
     */
    private Boolean onlyChanged = Boolean.TRUE;
}
