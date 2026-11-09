package com.codegen.service;

import java.util.Set;

/**
 * 权限码查询：把「当前用户 → 角色 → 菜单 → perms」这条链路收敛到一个地方，
 * 供登录信息下发与前端按钮校验使用。
 */
public interface PermissionService {

    /**
     * 超级管理员通配权限码
     */
    String WILDCARD = "*:*:*";

    /**
     * 当前用户的权限码集合；超级管理员返回 {@code ["*:*:*"]}
     */
    Set<String> permissionsOf(Long userId);

    /**
     * 是否超级管理员（角色 code 为 admin/super_admin，或角色 id = 1）
     */
    boolean isSuperAdmin(Long userId);
}
