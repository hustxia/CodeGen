package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 代码生成器-模板配置保存 DTO（新增/修改）
 */
@Data
public class GenTemplateSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，新增时为空
     */
    private Long id;

    private String tplCode;
    private String tplName;
    private String fileGroup;
    private String content;
    private String pathPattern;
    private Long tableId;
    private Integer status;
    private String remark;
}
