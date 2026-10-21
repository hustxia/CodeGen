package com.codegen.service.impl;

import com.codegen.config.GenMenuProperties;
import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenMenuRegisterDTO;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenMenuService;
import com.codegen.vo.GenMenuRegisterVO;
import com.codegen.vo.GenMenuVO;
import com.codegen.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 菜单注册实现。
 *
 * <p>两种出口（{@code gen.menu.outlet}）：
 * <ul>
 *   <li><b>artifact</b>（默认）：平台独立部署，目标工程是外部系统，不写任何外部库。
 *       每张主表产出一段 {@code sys_menu} 插入 SQL（含四个按钮与授权），
 *       连同前端路由片段一起落到 {@code {backend-root}/.gen-artifacts/<时间戳>/}。</li>
 *   <li><b>internal</b>：仅当生成目标就是平台自身时使用，直接写平台 sys_menu 并授权。</li>
 * </ul>
 *
 * <p>两种出口都会重建目标前端的 {@code src/router/gen-routes.js}（内容一致则不重写）。
 */
@Service
public class GenMenuServiceImpl implements GenMenuService {

    private static final Logger log = LoggerFactory.getLogger(GenMenuServiceImpl.class);

    /**
     * 生成页的固定子路由，与菜单 path 的 /list 后缀对应
     */
    private static final String LEAF = "list";

    /**
     * 按钮定义：中文名 + 权限动作后缀（用于给存量菜单补权限码）
     */
    private static final List<String[]> BUTTONS = List.of(
            new String[]{"查询", "query"},
            new String[]{"新增", "add"},
            new String[]{"修改", "edit"},
            new String[]{"删除", "remove"});

    private static final DateTimeFormatter ARTIFACT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenOutputProperties outputProperties;

    @Autowired
    private GenMenuProperties menuProperties;

    /**
     * 菜单计划：artifact 出口下用于生成 SQL 与路由的中间结构
     */
    private record PlannedMenu(GenTable table, String path, String component,
                               String listPerm, List<String[]> buttons) {
    }

    @Override
    @Transactional
    public GenMenuRegisterVO register(GenMenuRegisterDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException(400, "请先勾选要注册菜单的表");
        }
        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun());
        boolean internal = menuProperties.isInternal();

        GenMenuRegisterVO result = new GenMenuRegisterVO();
        result.setOutlet(internal ? "internal" : "artifact");

        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        Long roleId = dto.getRoleId() != null && dto.getRoleId() > 0
                ? dto.getRoleId()
                : (internal ? resolveRoleId(null) : 1L);
        result.setRoleId(roleId);

        String parentPath = internal && parentId > 0 ? pathOf(parentId) : "";
        List<PlannedMenu> plans = new ArrayList<>();

        for (Long id : dto.getIds()) {
            GenTable table = genTableMapper.selectById(id);
            if (table == null) {
                result.getMenus().add(new GenMenuVO("?", null, "", "", 2, "ERROR", "表配置不存在"));
                continue;
            }
            if (table.getMasterTableId() != null && table.getMasterTableId() > 0) {
                // 从表没有独立页面（明细维护内嵌在主表弹窗里），注册菜单只会得到重复入口
                result.getMenus().add(new GenMenuVO(table.getTableName(), table.getMenuId(),
                        table.getTableComment(), "", 2, "SKIPPED",
                        "从表不单独建菜单（明细维护内嵌在主表页面里）"));
                continue;
            }
            if (internal) {
                registerInternal(result, table, parentId, parentPath, roleId, dryRun);
            } else {
                registerArtifact(result, table, parentId, parentPath, plans, dryRun);
            }
        }

        boolean syncRoute = dto.getSyncRoute() == null || Boolean.TRUE.equals(dto.getSyncRoute());
        if (syncRoute) {
            fillRoute(result, dryRun, internal);
        }
        if (!internal) {
            emitMenuSql(result, plans, parentId, roleId, dryRun);
        }
        return result;
    }

    @Override
    public GenMenuRegisterVO syncRoute(boolean dryRun) {
        GenMenuRegisterVO result = new GenMenuRegisterVO();
        boolean internal = menuProperties.isInternal();
        result.setOutlet(internal ? "internal" : "artifact");
        fillRoute(result, dryRun, internal);
        return result;
    }

    /**
     * internal 出口：写平台自身 sys_menu（原行为）
     */
    private void registerInternal(GenMenuRegisterVO result, GenTable table, long parentId,
                                  String parentPath, Long roleId, boolean dryRun) {
        if (table.getMenuId() != null && menuExists(table.getMenuId())) {
            String backfilled = backfillPerms(table);
            String fixedComponent = backfillComponent(table);
            boolean reEnabled = !dryRun && enableIfDisabled(table.getMenuId());
            if (reEnabled) {
                grant(roleId, table.getMenuId());
            }
            List<String> parts = new ArrayList<>();
            if (backfilled != null) {
                parts.add("补齐权限码：" + backfilled);
            }
            if (fixedComponent != null) {
                parts.add("补齐组件路径：" + fixedComponent);
            }
            if (reEnabled) {
                parts.add("菜单原为停用状态，已重新启用并授权");
            }
            String note;
            String status;
            if (parts.isEmpty()) {
                note = "已注册，跳过";
                status = "SKIPPED";
            } else {
                note = (reEnabled ? "已启用" : "已注册") + "：" + String.join("；", parts);
                status = reEnabled ? "REGISTERED" : "SKIPPED";
            }
            result.getMenus().add(new GenMenuVO(table.getTableName(), table.getMenuId(),
                    table.getTableComment(), pathOf(table.getMenuId()), 2, status, note));
            return;
        }
        String pagePath = joinPath(parentPath, table.getModuleName(), LEAF);
        if (dryRun) {
            result.getMenus().add(new GenMenuVO(table.getTableName(), null, table.getTableComment(),
                    pagePath, 2, "PLANNED", "预览，未落库"));
            return;
        }
        try {
            String component = componentOf(table);
            Long menuId = insertMenu(parentId, table.getTableComment(), pagePath, component,
                    table.getTableName() + ":list", 2, nextSort(parentId));
            long sort = 1;
            for (String[] btn : buttonsOf(table)) {
                Long btnId = insertMenu(menuId, btn[0], null, null,
                        table.getTableName() + ":" + btn[1], 3, sort++);
                grant(roleId, btnId);
            }
            grant(roleId, menuId);
            jdbcTemplate.update("UPDATE gen_table SET menu_id = ? WHERE id = ?", menuId, table.getId());
            result.getMenus().add(new GenMenuVO(table.getTableName(), menuId, table.getTableComment(),
                    pagePath, 2, "REGISTERED",
                    "已注册菜单与 " + (sort - 1) + " 个按钮权限，并授权角色 " + roleId));
        } catch (Exception e) {
            log.warn("register menu failed for {}: {}", table.getTableName(), e.getMessage());
            result.getMenus().add(new GenMenuVO(table.getTableName(), null, table.getTableComment(),
                    pagePath, 2, "ERROR", e.getMessage()));
        }
    }

    /**
     * artifact 出口：只做计划，SQL 与路由片段随后落到目标工程
     */
    private void registerArtifact(GenMenuRegisterVO result, GenTable table, long parentId,
                                  String parentPath, List<PlannedMenu> plans, boolean dryRun) {
        String pagePath = joinPath(parentPath, table.getModuleName(), LEAF);
        String component = componentOf(table);
        List<String[]> buttons = buttonsOf(table);
        if (!dryRun) {
            plans.add(new PlannedMenu(table, pagePath, component,
                    table.getTableName() + ":list", buttons));
        }
        result.getMenus().add(new GenMenuVO(table.getTableName(), table.getMenuId(),
                table.getTableComment(), pagePath, 2,
                dryRun ? "PLANNED" : "GENERATED",
                (dryRun ? "预览" : "已产出") + "菜单 SQL 与 " + buttons.size() + " 个按钮权限（父菜单 "
                        + parentId + "，角色 " + result.getRoleId() + "）"));
    }

    /**
     * 重建前端路由文件：由 sys_menu + gen_table（internal）或仅 gen_table（artifact）派生，
     * 整文件覆盖（该文件属于生成物）。
     */
    private void fillRoute(GenMenuRegisterVO result, boolean dryRun, boolean internal) {
        List<String> skipped = new ArrayList<>();
        String content = internal ? buildRouteContentFromDb(skipped) : buildRouteContentFromTables(skipped);
        result.setRouteContent(content);
        if (!skipped.isEmpty()) {
            result.setMessage("已跳过 " + skipped.size() + " 个页面未落盘的模块（不生成路由，避免前端编译失败）："
                    + String.join("、", skipped));
        }
        Path base = webBase();
        if (base == null) {
            result.setMessage("未配置 gen.output.frontend-root，路由文件未写入（内容已返回，可手工保存）");
            return;
        }
        Path target = base.resolve("src/router/gen-routes.js");
        result.setRouteFile(target.toString());
        if (dryRun) {
            result.setMessage("dryRun：未写入路由文件");
            return;
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            // 内容一致就不重写：注册菜单是幂等操作，每次刷 mtime 会让 dev server 无谓热重载
            String old = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : null;
            if (content.equals(old)) {
                result.setRouteSynced(true);
                if (result.getMessage() == null) {
                    result.setMessage("路由文件内容无变化，未重写");
                }
                return;
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
            result.setRouteSynced(true);
        } catch (Exception e) {
            result.setMessage("路由文件写入失败：" + e.getMessage());
        }
    }

    /**
     * internal 出口的路由内容：菜单表里的 path 才是权威来源
     */
    private String buildRouteContentFromDb(List<String> skipped) {
        String sql = "SELECT t.table_comment, t.class_name, t.module_name, m.path "
                + "FROM gen_table t JOIN sys_menu m ON m.id = t.menu_id "
                + "WHERE t.deleted = 0 AND m.deleted = 0 AND m.status = 1 "
                + "AND t.gen_frontend = 1 AND t.gen_vue_page = 1 "
                + "ORDER BY m.sort, t.id";
        List<Map<String, Object>> queried = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        Path viewsRoot = viewsRoot();
        for (Map<String, Object> row : queried) {
            String module = String.valueOf(row.get("module_name"));
            if (viewsRoot != null && !Files.exists(viewsRoot.resolve(module).resolve("index.vue"))) {
                if (!skipped.contains(module)) {
                    skipped.add(module);
                }
                continue;
            }
            rows.add(row);
        }
        return renderRoutes(rows, skipped);
    }

    /**
     * artifact 出口的路由内容：目标工程没有菜单表，path 由模块名推导
     */
    private String buildRouteContentFromTables(List<String> skipped) {
        String sql = "SELECT table_comment, class_name, module_name FROM gen_table "
                + "WHERE deleted = 0 AND master_table_id IS NULL "
                + "AND gen_frontend = 1 AND gen_vue_page = 1 ORDER BY id";
        List<Map<String, Object>> queried = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        Path viewsRoot = viewsRoot();
        for (Map<String, Object> row : queried) {
            String module = String.valueOf(row.get("module_name"));
            if (viewsRoot != null && !Files.exists(viewsRoot.resolve(module).resolve("index.vue"))) {
                if (!skipped.contains(module)) {
                    skipped.add(module);
                }
                continue;
            }
            Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
            copy.put("path", joinPath("", module, LEAF));
            rows.add(copy);
        }
        return renderRoutes(rows, skipped);
    }

    /**
     * 前端工程根（frontend-root + gen.output.frontend-path），前端在 monorepo 子目录时也能定位
     */
    private Path webBase() {
        String root = outputProperties.getFrontendRoot();
        if (!StringUtils.hasText(root)) {
            return null;
        }
        Path base = Paths.get(root).toAbsolutePath().normalize();
        String sub = outputProperties.getFrontendPath();
        if (StringUtils.hasText(sub)) {
            Path resolved = base.resolve(sub).normalize();
            if (resolved.startsWith(base)) {
                base = resolved;
            }
        }
        return base;
    }

    private Path viewsRoot() {
        Path base = webBase();
        return base == null ? null : base.resolve("src/views");
    }

    private String renderRoutes(List<Map<String, Object>> rows, List<String> skipped) {
        if (!skipped.isEmpty()) {
            log.warn("路由重建跳过未落盘的页面模块：{}", skipped);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("/* eslint-disable */\n");
        sb.append("// 该文件由代码生成器自动维护，请勿手工编辑。\n");
        sb.append("// 注册/删除菜单后会自动重建；要移除某个生成页面，请先删除表配置。\n");
        if (!skipped.isEmpty()) {
            sb.append("// 以下模块的页面尚未落盘，暂不生成路由（生成后重新执行「重建路由」即可纳入）：")
                    .append(String.join("、", skipped)).append("\n");
        }
        if (!rows.isEmpty()) {
            sb.append("import Layout from '@/layout/index.vue'\n\n");
        }
        sb.append("export default [\n");
        for (Map<String, Object> row : rows) {
            String path = String.valueOf(row.get("path"));
            String module = String.valueOf(row.get("module_name"));
            String className = String.valueOf(row.get("class_name"));
            String title = String.valueOf(row.get("table_comment"));
            String rootPath = path;
            String childPath = "";
            if (path.endsWith("/" + LEAF)) {
                rootPath = path.substring(0, path.length() - LEAF.length() - 1);
                childPath = LEAF;
            }
            sb.append("  {\n");
            sb.append("    path: '").append(jsEscape(rootPath)).append("',\n");
            sb.append("    component: Layout,\n");
            sb.append("    children: [\n");
            sb.append("      {\n");
            sb.append("        path: '").append(jsEscape(childPath)).append("',\n");
            sb.append("        name: '").append(className).append("',\n");
            sb.append("        component: () => import('@/views/").append(module).append("/index.vue'),\n");
            sb.append("        meta: { title: '").append(jsEscape(title)).append("' }\n");
            sb.append("      }\n");
            sb.append("    ]\n");
            sb.append("  },\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * artifact 出口：生成可在目标工程执行的菜单 SQL 并落盘
     */
    private void emitMenuSql(GenMenuRegisterVO result, List<PlannedMenu> plans, long parentId,
                             Long roleId, boolean dryRun) {
        if (plans.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-- ============================================================\n");
        sb.append("-- 代码生成器产出的菜单注册 SQL\n");
        sb.append("-- 目标工程需存在 sys_menu / sys_role_menu（字段：parent_id,name,path,component,perms,type,sort,status,deleted）\n");
        sb.append("-- 生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("-- 父菜单 ID：").append(parentId).append("    授权角色 ID：").append(roleId).append("\n");
        sb.append("-- ============================================================\n\n");
        sb.append("START TRANSACTION;\n\n");
        for (PlannedMenu plan : plans) {
            GenTable table = plan.table();
            String var = "menu_" + table.getTableName().replaceAll("[^A-Za-z0-9_]", "_");
            sb.append("-- ").append(table.getTableComment()).append("（").append(table.getTableName()).append("）\n");
            sb.append("INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)\n");
            sb.append("VALUES (").append(parentId).append(", ").append(sqlStr(table.getTableComment())).append(", ")
                    .append(sqlStr(plan.path())).append(", ").append(sqlStr(plan.component())).append(", ")
                    .append(sqlStr(plan.listPerm())).append(", 2, 1, 1, 0);\n");
            sb.append("SET @").append(var).append(" = LAST_INSERT_ID();\n");
            long sort = 1;
            for (String[] btn : plan.buttons()) {
                sb.append("INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)\n");
                sb.append("VALUES (@").append(var).append(", ").append(sqlStr(btn[0])).append(", NULL, NULL, ")
                        .append(sqlStr(table.getTableName() + ":" + btn[1])).append(", 3, ").append(sort++)
                        .append(", 1, 0);\n");
            }
            sb.append("INSERT INTO sys_role_menu (role_id, menu_id, deleted) VALUES (")
                    .append(roleId).append(", @").append(var).append(", 0);\n\n");
        }
        sb.append("COMMIT;\n");
        String sql = sb.toString();
        result.setMenuSql(sql);
        if (dryRun) {
            return;
        }
        String backendRoot = outputProperties.getBackendRoot();
        if (!StringUtils.hasText(backendRoot)) {
            return;
        }
        try {
            Path dir = Paths.get(backendRoot).toAbsolutePath().normalize()
                    .resolve(menuProperties.getArtifactDir())
                    .resolve(LocalDateTime.now().format(ARTIFACT_FORMAT));
            Files.createDirectories(dir);
            Path file = dir.resolve("menu.sql");
            Files.writeString(file, sql, StandardCharsets.UTF_8);
            result.setMenuSqlFile(file.toString());
        } catch (Exception e) {
            log.warn("菜单 SQL 落盘失败：{}", e.getMessage());
            result.setMessage("菜单 SQL 落盘失败：" + e.getMessage());
        }
    }

    /**
     * 生成本次要注册的按钮：按「是否有查询列 + 各按钮开关」推导，必须和页面里的按钮一一对应
     */
    private List<String[]> buttonsOf(GenTable table) {
        List<String[]> buttons = new ArrayList<>();
        if (hasQueryColumn(table.getId())) {
            buttons.add(new String[]{"查询", "query"});
        }
        if (isOn(table.getGenAddBtn())) {
            buttons.add(new String[]{"新增", "add"});
        }
        if (isOn(table.getGenEditBtn())) {
            buttons.add(new String[]{"修改", "edit"});
        }
        if (isOn(table.getGenDeleteBtn())) {
            buttons.add(new String[]{"删除", "remove"});
        }
        return buttons;
    }

    private boolean hasQueryColumn(Long tableId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gen_table_column WHERE table_id = ? AND deleted = 0 AND is_query = 1",
                Integer.class, tableId);
        return count != null && count > 0;
    }

    private boolean isOn(Integer flag) {
        return flag != null && flag == 1;
    }

    private Long insertMenu(long parentId, String name, String path, String component, String perms,
                            int type, long sort) {
        jdbcTemplate.update("INSERT INTO sys_menu(parent_id, name, path, component, perms, type, sort, status, deleted) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, 1, 0)",
                parentId, name, path, component, perms, type, sort);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null || id <= 0) {
            throw new BusinessException(500, "菜单写入失败：未取得自增主键");
        }
        return id;
    }

    /**
     * 给已注册但缺组件路径的菜单补齐（V2.0 动态路由之前注册的数据）
     */
    private String backfillComponent(GenTable table) {
        String component = componentOf(table);
        int updated = jdbcTemplate.update("UPDATE sys_menu SET component = ? WHERE id = ? AND deleted = 0 "
                + "AND (component IS NULL OR component = '')", component, table.getMenuId());
        return updated > 0 ? component : null;
    }

    /**
     * 生成页的组件路径：模块名/index，模块名缺失时退回表名
     */
    private String componentOf(GenTable table) {
        String module = StringUtils.hasText(table.getModuleName()) ? table.getModuleName() : table.getTableName();
        return module + "/index";
    }

    /**
     * 给已注册但缺权限码的菜单补齐（V1.2 之前的存量数据）
     */
    private String backfillPerms(GenTable table) {
        Integer missing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE parent_id = ? AND deleted = 0 "
                        + "AND type = 3 AND (perms IS NULL OR perms = '')", Integer.class, table.getMenuId());
        if (missing == null || missing == 0) {
            return null;
        }
        for (String[] btn : BUTTONS) {
            jdbcTemplate.update("UPDATE sys_menu SET perms = ? WHERE parent_id = ? AND type = 3 "
                            + "AND name = ? AND (perms IS NULL OR perms = '')",
                    table.getTableName() + ":" + btn[1], table.getMenuId(), btn[0]);
        }
        jdbcTemplate.update("UPDATE sys_menu SET perms = ? WHERE id = ? AND (perms IS NULL OR perms = '')",
                table.getTableName() + ":list", table.getMenuId());
        return table.getTableName() + ":*";
    }

    private void grant(Long roleId, Long menuId) {
        jdbcTemplate.update("INSERT INTO sys_role_menu(role_id, menu_id, deleted) VALUES(?, ?, 0) "
                + "ON DUPLICATE KEY UPDATE deleted = 0", roleId, menuId);
    }

    private long nextSort(long parentId) {
        Long max = jdbcTemplate.queryForObject(
                "SELECT IFNULL(MAX(sort), 0) + 1 FROM sys_menu WHERE parent_id = ?", Long.class, parentId);
        return max == null ? 1 : max;
    }

    private boolean menuExists(Long menuId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = ? AND deleted = 0", Integer.class, menuId);
        return count != null && count > 0;
    }

    /**
     * 菜单存在但被停用（status=0）时重新启用
     */
    private boolean enableIfDisabled(Long menuId) {
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM sys_menu WHERE id = ? AND deleted = 0", Integer.class, menuId);
        if (status != null && status == 0) {
            jdbcTemplate.update("UPDATE sys_menu SET status = 1 WHERE id = ? AND deleted = 0", menuId);
            return true;
        }
        return false;
    }

    private String pathOf(Long menuId) {
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_menu WHERE id = ?", String.class, menuId);
        if (paths.isEmpty() || paths.get(0) == null) {
            return "";
        }
        return paths.get(0);
    }

    /**
     * 角色：显式指定 > 管理员角色 > 1
     */
    private Long resolveRoleId(Long explicit) {
        if (explicit != null && explicit > 0) {
            return explicit;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE deleted = 0 "
                        + "AND (code IN ('admin', 'super_admin') OR name IN ('超级管理员', '管理员')) "
                        + "ORDER BY id LIMIT 1", Long.class);
        return ids.isEmpty() ? 1L : ids.get(0);
    }

    private String joinPath(String parentPath, String module, String leaf) {
        String base = parentPath == null ? "" : parentPath.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String mid = module == null ? "" : module.trim();
        List<String> parts = new ArrayList<>();
        if (!base.isEmpty()) {
            parts.add(base);
        }
        if (!mid.isEmpty()) {
            parts.add(mid);
        }
        if (leaf != null && !leaf.isEmpty()) {
            parts.add(leaf);
        }
        String joined = String.join("/", parts);
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    private String jsEscape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String sqlStr(String raw) {
        if (raw == null) {
            return "NULL";
        }
        return "'" + raw.replace("'", "''") + "'";
    }
}
