package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色菜单关联保存 DTO（新增/修改）
 */
@Data
public class SysRoleMenuSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，新增时为空
     */
    private Long id;
    private Long roleId;
    private Long menuId;
}
