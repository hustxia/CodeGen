package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型 VO（管理页用，含字典项数量）
 */
@Data
public class DictTypeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String dictCode;
    private String dictName;
    private Integer status;
    private Integer dataCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
