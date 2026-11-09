package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项 VO（管理页用，含停用项）
 */
@Data
public class DictDataVO implements Serializable {

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
