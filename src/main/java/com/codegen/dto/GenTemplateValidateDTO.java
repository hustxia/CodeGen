package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模板语法校验入参
 */
@Data
public class GenTemplateValidateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板内容
     */
    private String content;

    /**
     * 模板编码（用于定位校验上下文）
     */
    private String tplCode;
}
