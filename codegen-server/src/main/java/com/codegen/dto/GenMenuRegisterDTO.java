package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 菜单自动注册请求
 */
@Data
public class GenMenuRegisterDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表配置 ID 列表
     */
    private List<Long> ids;

    /**
     * 挂载的父菜单 ID，0 或空表示一级菜单
     */
    private Long parentId;

    /**
     * 授权角色 ID，为空时自动取管理员角色（取不到回落 1）
     */
    private Long roleId;

    /**
     * 授权角色 ID 列表（direct 出口用）。与 roleId 同时存在时合并去重。
     * 留空则只授权管理员角色。
     */
    private List<Long> roleIds;

    /**
     * 是否授权给全部启用角色（direct 出口用）。默认 false，避免新菜单默认全员可见。
     */
    private Boolean grantAllRoles;

    /**
     * 是否同步前端路由文件（默认是）
     */
    private Boolean syncRoute;

    /**
     * 只预览不落库
     */
    private Boolean dryRun;
}
