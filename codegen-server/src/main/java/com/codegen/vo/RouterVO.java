package com.codegen.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 前端动态路由节点。
 *
 * <p>只下发"菜单要什么"，不下发"组件怎么加载"：{@code component} 是相对 {@code src/views}
 * 的字符串（如 {@code demo_goods/index}），由前端用 {@code require.context} 解析成
 * 异步组件。这样后端新增菜单不需要前端重新打包，也不会把 import 语句拼进 JSON。
 */
@Data
public class RouterVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单 ID（前端报错定位用）
     */
    private Long id;

    private Long parentId;

    /**
     * 菜单名称，同时作为侧边栏标题
     */
    private String title;

    /**
     * 路由路径（绝对路径，如 /product/spu）
     */
    private String path;

    /**
     * 组件路径，type=1 目录为 null
     */
    private String component;

    /**
     * 图标（Element icon class）
     */
    private String icon;

    /**
     * 1目录 2菜单
     */
    private Integer type;

    /**
     * 权限码，菜单级为 xxx:list，没有则 null
     */
    private String perms;

    /**
     * 同级排序
     */
    private Integer sort;

    private List<RouterVO> children;
}
