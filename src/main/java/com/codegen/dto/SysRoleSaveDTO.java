package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色保存 DTO（新增/修改）
 */
@Data
public class SysRoleSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，新增时为空
     */
    private Long id;
    private String code;
    private String name;
    private Integer status;
}
