package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模板分页查询入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GenTemplatePageDTO extends PageQuery {

    /**
     * 模板编码（模糊）
     */
    private String tplCode;

    /**
     * 产物分组（精确）
     */
    private String fileGroup;

    /**
     * 归属表（精确；0=仅全局，不传=全部）
     */
    private Long tableId;
}
