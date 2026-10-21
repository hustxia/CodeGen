package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 角色授权 DTO：整体覆盖某个角色的菜单集合
 */
@Data
public class SysRoleMenuBatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;

    /**
     * 勾选的菜单 ID（含目录/菜单/按钮），传空数组表示清空授权
     */
    private List<Long> menuIds;
}
