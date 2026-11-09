package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项保存 DTO
 */
@Data
public class DictDataSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String dictCode;
    private String dictLabel;
    private String dictValue;
    private Integer sortNo;
    private Integer status;
    private String remark;
}
