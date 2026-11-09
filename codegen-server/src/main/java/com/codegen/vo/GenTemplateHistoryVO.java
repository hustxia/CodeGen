package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模板版本快照 VO（列表不含内容，详情才带）
 */
@Data
public class GenTemplateHistoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long templateId;

    private String tplCode;

    private Long tableId;

    private Integer version;

    private String pathPattern;

    private String operator;

    private LocalDateTime createTime;

    /**
     * 仅详情接口返回
     */
    private String content;
}
