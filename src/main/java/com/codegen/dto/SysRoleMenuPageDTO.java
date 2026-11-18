package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色菜单关联分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRoleMenuPageDTO extends PageQuery {

    private Long roleId;
    private Long menuId;
}
