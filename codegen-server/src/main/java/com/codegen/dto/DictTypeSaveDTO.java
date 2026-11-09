package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典类型保存 DTO
 */
@Data
public class DictTypeSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 字典编码：生成页里 dict_type 引用的就是它，只允许字母数字下划线
     */
    private String dictCode;

    private String dictName;

    private Integer status;
}
