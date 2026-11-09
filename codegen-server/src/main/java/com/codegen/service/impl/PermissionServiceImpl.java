package com.codegen.service.impl;

import com.codegen.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限码查询实现（JdbcTemplate 直查关联表，避免多实体联查）。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Set<String> permissionsOf(Long userId) {
        Set<String> perms = new LinkedHashSet<>();
        if (userId == null) {
            return perms;
        }
        if (isSuperAdmin(userId)) {
            perms.add(WILDCARD);
            return perms;
        }
        String sql = "SELECT DISTINCT m.perms FROM sys_menu m "
                + "JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.deleted = 0 "
                + "JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.deleted = 0 "
                + "WHERE ur.user_id = ? AND m.deleted = 0 "
                + "AND m.perms IS NOT NULL AND m.perms <> ''";
        List<String> rows = jdbcTemplate.queryForList(sql, String.class, userId);
        perms.addAll(rows);
        return perms;
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM sys_user_role ur "
                + "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 "
                + "WHERE ur.user_id = ? "
                + "AND (r.code IN ('admin', 'super_admin') OR r.id = 1)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }
}
