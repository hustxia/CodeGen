package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 菜单保存 DTO（新增/修改）
 */
@Data
public class SysMenuSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，新增时为空
     */
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private Integer type;
    private Integer sort;
    private String icon;
    private Integer status;
    private String perms;
}
