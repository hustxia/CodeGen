package com.codegen.dto;

import com.codegen.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysMenuPageDTO extends PageQuery {

    private String name;
    private Long parentId;
    private Integer type;
    private Integer status;
}
