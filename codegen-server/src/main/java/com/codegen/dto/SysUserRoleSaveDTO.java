package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户角色关联保存 DTO（新增/修改）
 */
@Data
public class SysUserRoleSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，新增时为空
     */
    private Long id;
    private Long userId;
    private Long roleId;
}
