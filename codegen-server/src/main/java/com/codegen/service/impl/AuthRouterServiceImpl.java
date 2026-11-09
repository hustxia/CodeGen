package com.codegen.service.impl;

import com.codegen.service.AuthRouterService;
import com.codegen.service.PermissionService;
import com.codegen.vo.AuthRoutersVO;
import com.codegen.vo.RouterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态路由实现。
 *
 * <p>用 JdbcTemplate 而不是实体：{@code sys_role} 是租户版结构，实体列名与库表不完全一致
 * （如 {@code code} / {@code deleted}），直接走 SQL 更省事，也和
 * {@link PermissionServiceImpl} 的取权逻辑保持同一套口径。
 *
 * <p>只下发 type=1（目录）与 type=2（菜单）；按钮（type=3）只贡献权限码，不参与路由。
 */
@Service
public class AuthRouterServiceImpl implements AuthRouterService {

    /**
     * 菜单基础列，顺序与 {@link #ROUTER_MAPPER} 一致
     */
    private static final String COLUMNS =
            "m.id, m.parent_id, m.name, m.path, m.component, m.icon, m.type, m.perms, m.sort";

    private static final RowMapper<RouterVO> ROUTER_MAPPER = (rs, rowNum) -> {
        RouterVO vo = new RouterVO();
        vo.setId(rs.getLong("id"));
        long parentId = rs.getLong("parent_id");
        vo.setParentId(rs.wasNull() ? 0L : parentId);
        vo.setTitle(rs.getString("name"));
        vo.setPath(rs.getString("path"));
        vo.setComponent(rs.getString("component"));
        vo.setIcon(rs.getString("icon"));
        int type = rs.getInt("type");
        vo.setType(rs.wasNull() ? null : type);
        vo.setPerms(rs.getString("perms"));
        int sort = rs.getInt("sort");
        vo.setSort(rs.wasNull() ? 0 : sort);
        return vo;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PermissionService permissionService;

    @Override
    public AuthRoutersVO routersOf(Long userId) {
        AuthRoutersVO result = new AuthRoutersVO();
        boolean superAdmin = permissionService.isSuperAdmin(userId);
        result.setSuperAdmin(superAdmin);
        result.setPermissions(permissionService.permissionsOf(userId));
        result.setAllPaths(allPaths());

        List<RouterVO> granted = superAdmin ? allMenus() : grantedMenus(userId);
        if (granted.isEmpty()) {
            return result;
        }
        // 兜底：只授权了子菜单没授权父目录时，把父目录补进来，否则整枝菜单在侧边栏消失
        Map<Long, RouterVO> byId = new HashMap<>();
        for (RouterVO vo : granted) {
            byId.put(vo.getId(), vo);
        }
        Set<Long> missingParents = new LinkedHashSet<>();
        for (RouterVO vo : granted) {
            Long pid = vo.getParentId();
            if (pid != null && pid > 0 && !byId.containsKey(pid)) {
                missingParents.add(pid);
            }
        }
        if (!missingParents.isEmpty()) {
            byId.putAll(loadMenusByIds(new ArrayList<>(missingParents)));
        }

        Map<Long, List<RouterVO>> childrenOf = new HashMap<>();
        List<RouterVO> roots = new ArrayList<>();
        for (RouterVO vo : byId.values()) {
            vo.setChildren(new ArrayList<>());
            Long pid = vo.getParentId() == null ? 0L : vo.getParentId();
            if (pid == 0L || !byId.containsKey(pid)) {
                roots.add(vo);
            } else {
                childrenOf.computeIfAbsent(pid, k -> new ArrayList<>()).add(vo);
            }
        }
        Comparator<RouterVO> bySort = Comparator
                .comparing((RouterVO v) -> v.getSort() == null ? 0 : v.getSort())
                .thenComparing(RouterVO::getId);
        for (RouterVO vo : byId.values()) {
            List<RouterVO> children = childrenOf.get(vo.getId());
            if (children != null) {
                children.sort(bySort);
                vo.setChildren(children);
            }
        }
        roots.sort(bySort);
        result.setRouters(roots);
        return result;
    }

    private List<RouterVO> allMenus() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM sys_menu m "
                + "WHERE m.deleted = 0 AND m.status = 1 AND m.type IN (1, 2) "
                + "ORDER BY m.parent_id, m.sort, m.id", ROUTER_MAPPER);
    }

    private List<RouterVO> grantedMenus(Long userId) {
        return jdbcTemplate.query("SELECT DISTINCT " + COLUMNS + " FROM sys_menu m "
                + "JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.deleted = 0 "
                + "JOIN sys_user_role ur ON ur.role_id = rm.role_id "
                + "WHERE ur.user_id = ? AND m.deleted = 0 AND m.status = 1 AND m.type IN (1, 2) "
                + "ORDER BY m.parent_id, m.sort, m.id", ROUTER_MAPPER, userId);
    }

    private Map<Long, RouterVO> loadMenusByIds(List<Long> ids) {
        Map<Long, RouterVO> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<RouterVO> list = jdbcTemplate.query("SELECT " + COLUMNS + " FROM sys_menu m "
                + "WHERE m.deleted = 0 AND m.id IN (" + placeholders + ")", ROUTER_MAPPER, ids.toArray());
        for (RouterVO vo : list) {
            map.put(vo.getId(), vo);
        }
        return map;
    }

    /**
     * 全部启用菜单的路径：前端用它把"越权访问"和"路径不存在"区分开
     */
    private List<String> allPaths() {
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_menu WHERE deleted = 0 AND status = 1 "
                        + "AND type IN (1, 2) AND path IS NOT NULL AND path <> '' "
                        + "ORDER BY parent_id, sort, id", String.class);
        Set<String> unique = new LinkedHashSet<>(paths);
        return new ArrayList<>(unique);
    }
}
