package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 前端组件路径（相对 src/views，不带扩展名）：type=2 必填，如 demo_goods/index
     */
    private String component;

    /**
     * 类型：1目录 2菜单 3按钮
     */
    private Integer type;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 图标
     */
    private String icon;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;

    /**
     * 权限标识：菜单为 xxx:list，按钮为 xxx:query / add / edit / remove
     */
    private String perms;
}
