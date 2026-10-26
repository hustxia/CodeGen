package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色菜单关联 VO
 */
@Data
public class SysRoleMenuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long roleId;
    private Long menuId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
