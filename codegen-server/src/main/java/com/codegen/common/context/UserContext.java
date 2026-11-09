package com.codegen.common.context;

import java.util.List;

/**
 * 当前登录用户上下文（ThreadLocal）
 * 由鉴权过滤器写入，业务代码只读。
 *
 * <p>除用户身份外还携带角色编码与数据权限范围，供数据权限拦截器
 * 在运行时改写 SQL 时使用。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<List<String>> ROLES_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> DATA_SCOPE_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<Long> DEPT_ID_HOLDER = new ThreadLocal<>();

    /**
     * 命中这些角色的用户不受数据权限限制。
     */
    private static final List<String> SUPER_ROLES = List.of("admin", "super_admin");

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void setUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    public static void setRoles(List<String> roles) {
        ROLES_HOLDER.set(roles);
    }

    public static List<String> getRoles() {
        List<String> roles = ROLES_HOLDER.get();
        return roles == null ? List.of() : roles;
    }

    /**
     * 数据权限范围：all / dept / self。
     * 未显式设置时按角色推导——有超级管理员角色即 all，否则 self（最小权限兜底）。
     */
    public static String getDataScope() {
        String scope = DATA_SCOPE_HOLDER.get();
        if (scope != null && !scope.isBlank()) {
            return scope;
        }
        return isSuper() ? "all" : "self";
    }

    public static void setDataScope(String dataScope) {
        DATA_SCOPE_HOLDER.set(dataScope);
    }

    public static void setDeptId(Long deptId) {
        DEPT_ID_HOLDER.set(deptId);
    }

    public static Long getDeptId() {
        return DEPT_ID_HOLDER.get();
    }

    public static boolean isSuper() {
        for (String role : getRoles()) {
            if (role != null && SUPER_ROLES.contains(role.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
        ROLES_HOLDER.remove();
        DATA_SCOPE_HOLDER.remove();
        DEPT_ID_HOLDER.remove();
    }
}
