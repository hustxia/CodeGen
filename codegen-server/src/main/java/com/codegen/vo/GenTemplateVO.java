package com.codegen.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代码生成器-模板配置 VO
 */
@Data
public class GenTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tplCode;
    private String tplName;
    private String fileGroup;
    private String content;
    private String pathPattern;
    private Long tableId;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
