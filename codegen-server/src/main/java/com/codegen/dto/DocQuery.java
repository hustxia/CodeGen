package com.codegen.dto;

import lombok.Data;

/**
 * 文档分页查询条件（V6.9）
 */
@Data
public class DocQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /** 原始文件名，模糊匹配 */
    private String name;

    /** 扩展名，精确匹配（不带点、小写） */
    private String ext;

    /** 仅看某一类：pdf / word / excel / ppt，null 为全部 */
    private String category;
}
