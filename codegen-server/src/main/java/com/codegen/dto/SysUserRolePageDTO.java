package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户角色关联分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserRolePageDTO extends PageQuery {

    private Long userId;
    private Long roleId;
}
