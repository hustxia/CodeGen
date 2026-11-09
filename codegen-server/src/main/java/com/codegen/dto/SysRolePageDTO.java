package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRolePageDTO extends PageQuery {

    private String code;
    private String name;
    private Integer status;
}
