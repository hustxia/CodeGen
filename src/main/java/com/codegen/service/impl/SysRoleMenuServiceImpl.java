package com.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.entity.SysRoleMenu;
import com.codegen.mapper.SysRoleMenuMapper;
import com.codegen.service.SysRoleMenuService;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色菜单关联 Service 实现
 */
@Service
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements SysRoleMenuService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<Long> menuIdsOf(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        return jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND deleted = 0", Long.class, roleId);
    }

    /**
     * 整体覆盖角色菜单。
     *
     * <p>两道保护：
     * <ul>
     *   <li>超管角色（id=1 或 code 为 admin/super_admin）权限固定为全量，不允许在界面上删减——
     *       否则一旦把自己锁在外面，只能连库恢复；</li>
     *   <li>菜单 ID 先对 sys_menu 做存在性过滤，避免前端传来脏 ID 产生悬空关联。</li>
     * </ul>
     */
    @Override
    @Transactional
    public int replaceMenus(Long roleId, List<Long> menuIds) {
        if (roleId == null) {
            throw new BusinessException(400, "roleId 不能为空");
        }
        String roleCode = jdbcTemplate.query("SELECT code FROM sys_role WHERE id = ? AND deleted = 0",
                rs -> rs.next() ? rs.getString(1) : null, roleId);
        if (roleCode == null) {
            throw new BusinessException(404, "角色不存在：" + roleId);
        }
        if (roleId == 1L || "admin".equals(roleCode) || "super_admin".equals(roleCode)) {
            throw new BusinessException(400, "超管角色固定拥有全部权限，不允许修改（防止把自己锁在系统外）");
        }
        Set<Long> wanted = new LinkedHashSet<>();
        if (menuIds != null) {
            for (Long id : menuIds) {
                if (id != null && id > 0) {
                    wanted.add(id);
                }
            }
        }
        Set<Long> valid = new LinkedHashSet<>();
        if (!wanted.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(wanted.size(), "?"));
            valid.addAll(jdbcTemplate.queryForList(
                    "SELECT id FROM sys_menu WHERE deleted = 0 AND id IN (" + placeholders + ")",
                    Long.class, wanted.toArray()));
        }
        jdbcTemplate.update("UPDATE sys_role_menu SET deleted = 1 WHERE role_id = ?", roleId);
        int written = 0;
        for (Long menuId : valid) {
            jdbcTemplate.update("INSERT INTO sys_role_menu(role_id, menu_id, deleted) VALUES(?, ?, 0) "
                    + "ON DUPLICATE KEY UPDATE deleted = 0", roleId, menuId);
            written++;
        }
        return written;
    }
}
