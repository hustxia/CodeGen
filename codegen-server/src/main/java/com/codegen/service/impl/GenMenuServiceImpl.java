package com.codegen.service.impl;

import com.codegen.config.GenMenuProperties;
import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenMenuRegisterDTO;
import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.service.GenMenuService;
import com.codegen.support.GenNaming;
import com.codegen.support.GenPathResolver;
import com.codegen.util.GenMenuGroup;
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
import java.util.LinkedHashSet;
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

    @Autowired
    private SysProjectMapper projectMapper;

    /**
     * 单次注册内按 projectId 缓存系统，避免一批表把同一个系统重复查十几次
     */
    private final Map<Long, SysProject> projectCache = new java.util.HashMap<>();

    /**
     * 菜单计划：artifact 出口下用于生成 SQL 与路由的中间结构
     */
    private record PlannedMenu(GenTable table, String path, String component,
                               String listPerm, List<String[]> buttons,
                               String projectCode, String projectName,
                               String groupCode, String groupLabel) {
    }

    @Override
    @Transactional
    public GenMenuRegisterVO register(GenMenuRegisterDTO dto) {
        // 与 GenCodeServiceImpl.writeToDisk 同款修法（坑 51）：注册前清系统配置缓存，
        // 避免管理页改完 sys_project 后菜单注册仍读旧配置
        projectCache.clear();
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException(400, "请先勾选要注册菜单的表");
        }
        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun());
        boolean internal = menuProperties.isInternal();

        GenMenuRegisterVO result = new GenMenuRegisterVO();
        result.setOutlet(internal ? "internal" : "artifact");

        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        // direct/internal 出口：支持一次授给多个角色；artifact 出口仍只记录一个角色用于生成 SQL
        List<Long> roleIds = internal ? resolveRoleIds(dto) : List.of(resolveRoleId(dto.getRoleId()));
        Long roleId = roleIds.get(0);
        result.setRoleId(roleId);

        String parentPath = internal && parentId > 0 ? pathOf(parentId) : "";
        List<PlannedMenu> plans = new ArrayList<>();
        // 目录菜单（系统层 / 分组层）本次注册内复用，避免逐表重复查询、重复插入
        Map<String, Long> dirCache = new java.util.HashMap<>();
        // 表配置一次查回：注册一批菜单时逐表 selectById 会随勾选数量线性增长
        Map<Long, GenTable> tableById = new java.util.HashMap<>();
        for (GenTable t : genTableMapper.selectBatchIds(dto.getIds())) {
            tableById.put(t.getId(), t);
        }

        for (Long id : dto.getIds()) {
            GenTable table = tableById.get(id);
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
                registerInternal(result, table, parentId, parentPath, roleIds, dryRun, dirCache);
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
     * internal 出口：写平台自身 sys_menu（原行为）。
     *
     * <p>菜单按「系统 → 业务域 → 页面」三级落库：以前所有页面直接挂在父菜单下，
     * 80 张表就是 80 个平级兄弟节点，找不到东西。现在中间加一层业务域目录，
     * 目录节点同名复用，反复注册不会堆出重复层级。</p>
     */
    private void registerInternal(GenMenuRegisterVO result, GenTable table, long parentId,
                                  String parentPath, List<Long> roleIds, boolean dryRun,
                                  Map<String, Long> dirCache) {
        if (table.getMenuId() != null && menuExists(table.getMenuId())) {
            String backfilled = backfillPerms(table);
            String fixedComponent = backfillComponent(table);
            boolean reEnabled = !dryRun && enableIfDisabled(table.getMenuId());
            if (reEnabled) {
                grantAll(roleIds, table.getMenuId());
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
        String projectCode = projectCodeOf(table.getProjectId());
        SysProject project = projectOf(table.getProjectId());
        GenMenuGroup.Group group = GenMenuGroup.of(table.getTableName(), table.getTableComment());
        String systemPath = joinPath(parentPath, projectCode, null, null);
        String groupPath = joinPath(systemPath, null, group.code(), null);
        String pagePath = joinPath(groupPath, null, table.getModuleName(), LEAF);
        if (dryRun) {
            result.getMenus().add(new GenMenuVO(table.getTableName(), null, table.getTableComment(),
                    pagePath, 2, "PLANNED", "预览，未落库"));
            return;
        }
        try {
            String systemName = project != null && project.getProjectName() != null
                    && !project.getProjectName().isBlank()
                    ? project.getProjectName()
                    : (projectCode == null || projectCode.isBlank() ? "业务系统" : projectCode + "系统");
            long systemId = ensureDir(parentId, systemName, systemPath, dirCache);
            long groupId = ensureDir(systemId, group.label(), groupPath, dirCache);
            String component = componentOf(table);
            Long menuId = insertMenu(groupId, table.getTableComment(), pagePath, component,
                    permsPrefix(table) + ":list", 2, nextSort(groupId));
            long sort = 1;
            for (String[] btn : buttonsOf(table)) {
                Long btnId = insertMenu(menuId, btn[0], null, null,
                        permsPrefix(table) + ":" + btn[1], 3, sort++);
                grantAll(roleIds, btnId);
            }
            grantAll(roleIds, menuId);
            jdbcTemplate.update("UPDATE gen_table SET menu_id = ? WHERE id = ?", menuId, table.getId());
            result.getMenus().add(new GenMenuVO(table.getTableName(), menuId, table.getTableComment(),
                    pagePath, 2, "REGISTERED",
                    "已注册到「" + systemName + " / " + group.label() + "」，含 " + (sort - 1)
                            + " 个按钮权限，授权 " + roleIds.size() + " 个角色 " + roleIds));
        } catch (Exception e) {
            log.warn("register menu failed for {}: {}", table.getTableName(), e.getMessage());
            result.getMenus().add(new GenMenuVO(table.getTableName(), null, table.getTableComment(),
                    pagePath, 2, "ERROR", e.getMessage()));
        }
    }

    /**
     * 确保目录菜单（系统层 / 分组层）存在并返回其 ID。
     *
     * <p>同名同父即复用：注册菜单是幂等操作，重复执行不能一层层堆目录。
     * 已存在的目录不修正 path，避免把用户手工调整过的路径覆盖掉。</p>
     */
    private long ensureDir(long parentId, String name, String path, Map<String, Long> cache) {
        String key = parentId + "|" + name;
        Long cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Long> exists = jdbcTemplate.queryForList(
                "SELECT id FROM sys_menu WHERE parent_id = ? AND name = ? AND type = 1 AND deleted = 0 "
                        + "ORDER BY id LIMIT 1", Long.class, parentId, name);
        long id = exists.isEmpty()
                ? insertMenu(parentId, name, path, null, null, 1, nextSort(parentId))
                : exists.get(0);
        cache.put(key, id);
        return id;
    }


    /**
     * artifact 出口：只做计划，SQL 与路由片段随后落到目标工程
     */
    private void registerArtifact(GenMenuRegisterVO result, GenTable table, long parentId,
                                  String parentPath, List<PlannedMenu> plans, boolean dryRun) {
        String projectCode = projectCodeOf(table.getProjectId());
        SysProject project = projectOf(table.getProjectId());
        String projectName = project != null && project.getProjectName() != null
                && !project.getProjectName().isBlank()
                ? project.getProjectName()
                : (projectCode == null || projectCode.isBlank() ? "业务系统" : projectCode + "系统");
        GenMenuGroup.Group group = GenMenuGroup.of(table.getTableName(), table.getTableComment());
        String systemPath = joinPath(parentPath, projectCode, null, null);
        String groupPath = joinPath(systemPath, null, group.code(), null);
        String pagePath = joinPath(groupPath, null, table.getModuleName(), LEAF);
        String component = componentOf(table);
        List<String[]> buttons = buttonsOf(table);
        if (!dryRun) {
            plans.add(new PlannedMenu(table, pagePath, component,
                    permsPrefix(table) + ":list", buttons,
                    projectCode, projectName, group.code(), group.label()));
        }
        result.getMenus().add(new GenMenuVO(table.getTableName(), table.getMenuId(),
                table.getTableComment(), pagePath, 2,
                dryRun ? "PLANNED" : "GENERATED",
                (dryRun ? "预览" : "已产出") + "菜单 SQL 与 " + buttons.size() + " 个按钮权限（"
                        + projectName + " / " + group.label() + "，父菜单 "
                        + parentId + "，角色 " + result.getRoleId() + "）"));
    }

    /**
     * 重建前端路由文件：由 sys_menu + gen_table（internal）或仅 gen_table（artifact）派生，
     * 整文件覆盖（该文件属于生成物）。
     *
     * <p>V3.1 起**按前端工程分组写**：两套系统各有自己的前端仓库时，
     * 一个 {@code gen-routes.js} 装不下两份路由——旧实现只写全局 frontend-root，
     * B 系统的页面永远同步不到 B 的前端工程。分组键就是解析出来的前端工程根。
     */
    private void fillRoute(GenMenuRegisterVO result, boolean dryRun, boolean internal) {
        Map<Path, List<Map<String, Object>>> groups =
                internal ? groupRowsByWebBase(loadRowsFromDb()) : groupRowsByWebBase(loadRowsFromTables());

        if (groups.isEmpty()) {
            result.setRouteContent(renderRoutes(List.of(), List.of()));
            result.setMessage("没有可生成路由的表（需 gen_frontend=1 且 gen_vue_page=1）");
            return;
        }

        List<String> written = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        boolean multi = groups.size() > 1;
        for (Map.Entry<Path, List<Map<String, Object>>> group : groups.entrySet()) {
            Path base = group.getKey();
            List<String> skipped = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            Path viewsRoot = base.resolve("src/views");
            for (Map<String, Object> row : group.getValue()) {
                String module = String.valueOf(row.get("module_name"));
                // 页面没落盘的模块不生成路由，否则前端 import 直接编译失败
                if (!Files.exists(viewsRoot.resolve(module).resolve("index.vue"))) {
                    if (!skipped.contains(module)) {
                        skipped.add(module);
                    }
                    continue;
                }
                rows.add(row);
            }
            String groupContent = renderRoutes(rows, skipped);
            if (multi) {
                content.append("// ==== ").append(base.resolve("src/router/gen-routes.js")).append('\n');
            }
            content.append(groupContent);
            if (!skipped.isEmpty()) {
                appendMessage(result, "已跳过 " + skipped.size() + " 个页面未落盘的模块（" + base.getFileName()
                        + "）：" + String.join("、", skipped));
            }

            Path target = base.resolve("src/router/gen-routes.js");
            written.add(target.toString());
            if (dryRun) {
                continue;
            }
            writeRouteFile(result, target, groupContent);
        }
        result.setRouteContent(content.toString());
        result.setRouteFile(String.join("、", written));
        if (dryRun) {
            appendMessage(result, "dryRun：未写入路由文件，内容已返回");
        }
    }

    /**
     * 写单个路由文件。内容一致就不重写——注册菜单是幂等操作，
     * 每次刷 mtime 会让 dev server 无谓热重载。
     */
    private void writeRouteFile(GenMenuRegisterVO result, Path target, String content) {
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            String old = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : null;
            if (content.equals(old)) {
                // 内容一致是幂等时的正常结果，不是问题：不写盘、也不塞 message
                // （message 在前端会渲染成警示条，把「一切正常」显示成黄条会误导）
                result.setRouteSynced(true);
                return;
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
            result.setRouteSynced(true);
        } catch (Exception e) {
            appendMessage(result, "路由文件写入失败（" + target + "）：" + e.getMessage());
        }
    }

    private void appendMessage(GenMenuRegisterVO result, String text) {
        result.setMessage(result.getMessage() == null ? text : result.getMessage() + "；" + text);
    }

    /**
     * internal 出口的行：菜单表里的 path 才是权威来源
     */
    private List<Map<String, Object>> loadRowsFromDb() {
        String sql = "SELECT t.table_comment, t.table_name, t.class_name, t.module_name, t.project_id, t.gen_print, m.path "
                + "FROM gen_table t JOIN sys_menu m ON m.id = t.menu_id "
                + "WHERE t.deleted = 0 AND m.deleted = 0 AND m.status = 1 "
                + "AND t.gen_frontend = 1 AND t.gen_vue_page = 1 "
                + "ORDER BY m.sort, t.id";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
            decorateGroup(copy);
            rows.add(copy);
        }
        return rows;
    }

    /**
     * artifact 出口的行：目标工程没有菜单表，path 由「系统编码 + 业务域 + 模块名」推导
     */
    private List<Map<String, Object>> loadRowsFromTables() {
        String sql = "SELECT table_name, table_comment, class_name, module_name, project_id, gen_print FROM gen_table "
                + "WHERE deleted = 0 AND master_table_id IS NULL "
                + "AND gen_frontend = 1 AND gen_vue_page = 1 ORDER BY id";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
            decorateGroup(copy);
            rows.add(copy);
        }
        return rows;
    }

    /**
     * 补齐「系统 + 业务域」两组字段，并把它们并进页面 path。
     *
     * <p>路由必须是三段：{@code /<系统>/<业务域>/<模块>/list}。菜单树和前端路由共用同一份
     * 推导结果，否则菜单显示三级、点进去却是另一条路径，两边会对不上。</p>
     */
    private void decorateGroup(Map<String, Object> row) {
        Object projectId = row.get("project_id");
        String code = projectCodeOf(projectId == null ? null : ((Number) projectId).longValue());
        SysProject project = projectOf(projectId == null ? null : ((Number) projectId).longValue());
        String projectName = project != null && project.getProjectName() != null
                && !project.getProjectName().isBlank()
                ? project.getProjectName()
                : (code == null || code.isBlank() ? "业务系统" : code + "系统");
        GenMenuGroup.Group group = GenMenuGroup.of(
                String.valueOf(row.get("table_name")), String.valueOf(row.get("table_comment")));
        row.put("project_code", code == null ? "" : code);
        row.put("project_name", projectName);
        row.put("group_code", group.code());
        row.put("group_label", group.label());
        if (row.get("path") == null) {
            row.put("path", "/" + (code == null ? "" : code) + "/" + group.code() + "/"
                    + row.get("module_name") + "/" + LEAF);
        }
    }

    /**
     * 按前端工程根分组。前端根解析不出来（未配置 frontend-root）的行直接丢弃并提示，
     * 因为不知道往哪儿写，留着只会让调用方以为同步成功了。
     */
    private Map<Path, List<Map<String, Object>>> groupRowsByWebBase(List<Map<String, Object>> rows) {
        Map<Path, List<Map<String, Object>>> groups = new java.util.LinkedHashMap<>();
        boolean dropped = false;
        for (Map<String, Object> row : rows) {
            Object projectId = row.get("project_id");
            SysProject project = projectId == null ? null : projectOf(((Number) projectId).longValue());
            Path base = webBaseOf(project);
            if (base == null) {
                dropped = true;
                continue;
            }
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(row);
        }
        if (dropped) {
            log.warn("有表未解析出前端工程根（未配置 gen.output.frontend-root 且系统未配 frontend-root）");
        }
        return groups;
    }

    /**
     * 前端工程根 = 系统/全局 frontend-root + 系统/模块/全局 frontend-path。
     *
     * <p>与写盘用同一个解析器（{@link GenPathResolver}）：页面落在哪儿，路由就必须写到哪儿。
     * 这两处一旦各算一遍，就会出现「文件写进了 A 工程、路由写进了 B 工程」的静默错位。
     */
    private Path webBaseOf(SysProject project) {
        String root = GenPathResolver.frontendRoot(null, project, outputProperties);
        if (!StringUtils.hasText(root)) {
            return null;
        }
        Path base = Paths.get(root).toAbsolutePath().normalize();
        String sub = GenPathResolver.frontendPath(null, project, outputProperties);
        if (StringUtils.hasText(sub)) {
            Path resolved = base.resolve(sub).normalize();
            // 越界（.. 逃出前端根）时忽略子目录，别把路由写到工程外面去
            if (resolved.startsWith(base)) {
                base = resolved;
            }
        }
        return base;
    }

    private String renderRoutes(List<Map<String, Object>> rows, List<String> skipped) {
        if (!skipped.isEmpty()) {
            log.warn("路由重建跳过未落盘的页面模块：{}", skipped);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("/* eslint-disable */\n");
        sb.append("// 该文件由代码生成器自动维护，请勿手工编辑。\n");
        sb.append("// 注册/删除菜单后会自动重建；要移除某个生成页面，请先删除表配置。\n");
        sb.append("// 结构为三级：系统 → 业务域 → 页面，菜单树与路由共用同一套分组推导。\n");
        if (!skipped.isEmpty()) {
            sb.append("// 以下模块的页面尚未落盘，暂不生成路由（生成后重新执行「重建路由」即可纳入）：")
                    .append(String.join("、", skipped)).append("\n");
        }
        if (!rows.isEmpty()) {
            sb.append("import Layout from '@/layout/index.vue'\n");
            // Vue3 的 render 不再注入 h，且 h('router-view') 只会生成同名原生自定义元素
            // （vnode.type 是字符串），子路由不会渲染。必须显式 import h + RouterView 组件对象。
            sb.append("import { h } from 'vue'\n");
            sb.append("import { RouterView } from 'vue-router'\n");
            // 业务域分组层是纯容器：只透传 router-view，绝不能再挂 Layout，
            // 否则系统内每层都会渲染一份侧边栏，页面出现双重菜单
            sb.append("const GroupView = { name: 'GroupView', render: () => h(RouterView) }\n\n");
        }
        sb.append("export default [\n");

        // 系统 → 业务域 → 页面，用 LinkedHashMap 保住 gen_table 的原始顺序
        java.util.Map<String, java.util.Map<String, List<Map<String, Object>>>> tree =
                new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String system = str(row.get("project_name"), "业务系统");
            String group = str(row.get("group_label"), "其他");
            tree.computeIfAbsent(system, k -> new java.util.LinkedHashMap<>())
                    .computeIfAbsent(group, k -> new ArrayList<>())
                    .add(row);
        }

        for (Map.Entry<String, java.util.Map<String, List<Map<String, Object>>>> system : tree.entrySet()) {
            String systemCode = "";
            String systemPath = "";
            for (List<Map<String, Object>> groupRows : system.getValue().values()) {
                if (!groupRows.isEmpty()) {
                    systemCode = str(groupRows.get(0).get("project_code"), "");
                    systemPath = "/" + systemCode;
                    break;
                }
            }
            // V8.0 菜单国际化：每级菜单挂一个稳定 i18nKey，layout 运行时按当前语言解析，
            // 缺译文时回落到 title（中文）。支持任意语言（含未预置译文的 198 种）回落中文。
            String systemKey = "menu." + (systemCode.isEmpty() ? "sys" : systemCode);
            sb.append("  {\n");
            sb.append("    path: '").append(jsEscape(systemPath)).append("',\n");
            sb.append("    component: Layout,\n");
            sb.append("    meta: { title: '").append(jsEscape(system.getKey())).append("', i18nKey: '").append(systemKey).append("' },\n");
            sb.append("    children: [\n");
            boolean firstGroup = true;
            for (Map.Entry<String, List<Map<String, Object>>> group : system.getValue().entrySet()) {
                String groupCode = str(group.getValue().get(0).get("group_code"), "other");
                String groupKey = systemKey + "." + (groupCode.isEmpty() ? "group" : groupCode);
                if (!firstGroup) {
                    sb.append(",\n");
                }
                firstGroup = false;
                sb.append("      {\n");
                sb.append("        path: '").append(jsEscape(groupCode)).append("',\n");
                sb.append("        component: GroupView,\n");
                sb.append("        meta: { title: '").append(jsEscape(group.getKey())).append("', i18nKey: '").append(groupKey).append("' },\n");
                sb.append("        children: [\n");
                boolean firstPage = true;
                for (Map<String, Object> row : group.getValue()) {
                    String path = String.valueOf(row.get("path"));
                    String module = String.valueOf(row.get("module_name"));
                    String className = String.valueOf(row.get("class_name"));
                    String title = String.valueOf(row.get("table_comment"));
                    // 组内每页都要带自己的模块名，否则同组页面全是 path: 'list'，vue-router 只认第一条
                    String leafPath = path.endsWith("/" + LEAF)
                            ? module + "/" + LEAF
                            : module;
                    String pageKey = groupKey + "." + module;
                    if (!firstPage) {
                        sb.append(",\n");
                    }
                    firstPage = false;
                    sb.append("          {\n");
                    sb.append("            path: '").append(jsEscape(leafPath)).append("',\n");
                    sb.append("            name: '").append(className).append("',\n");
                    sb.append("            component: () => import('@/views/").append(module)
                            .append("/index.vue'),\n");
                    sb.append("            meta: { title: '").append(jsEscape(title)).append("', i18nKey: '").append(pageKey).append("' }\n");
                    sb.append("          }");
                }
                sb.append("\n        ]\n");
                sb.append("      }");
            }
            sb.append("\n    ]\n");
            sb.append("  },\n");
        }

        // V4.5 打印单据：无 layout 顶层路由。打印页必须脱离后台导航壳，
        // 否则 window.print 会把侧边栏一起打出来；挂在文件末尾避免嵌进系统/业务域层级
        List<Map<String, Object>> printRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object gp = row.get("gen_print");
            if (gp instanceof Number n && n.intValue() == 1) {
                printRows.add(row);
            }
        }
        if (!printRows.isEmpty()) {
            sb.append("  // V4.5 打印单据：无 layout 顶层路由\n");
            for (Map<String, Object> row : printRows) {
                String module = String.valueOf(row.get("module_name"));
                String className = String.valueOf(row.get("class_name"));
                String title = jsEscape(String.valueOf(row.get("table_comment")));
                String printProjectCode = str(row.get("project_code"), "");
                String printKey = "menu." + (printProjectCode.isEmpty() ? "sys" : printProjectCode) + ".print." + module;
                sb.append("  {\n");
                sb.append("    path: '/print/").append(module).append("/:id',\n");
                sb.append("    name: 'Print").append(className).append("',\n");
                sb.append("    component: () => import('@/views/").append(module).append("/print.vue'),\n");
                sb.append("    meta: { title: '").append(title).append("打印', i18nKey: '").append(printKey).append("' }\n");
                sb.append("  },\n");
            }
        }
        sb.append("]\n");
        return sb.toString();
    }

    private String str(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value);
        return text.isBlank() ? fallback : text;
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
        sb.append("-- 代码生成器产出的菜单注册 SQL（三级结构：系统 → 业务域 → 页面）\n");
        sb.append("-- 目标工程需存在 sys_menu / sys_role_menu（字段：parent_id,name,path,component,perms,type,sort,status,deleted）\n");
        sb.append("-- 生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("-- 父菜单 ID：").append(parentId).append("    授权角色 ID：").append(roleId).append("\n");
        sb.append("-- ============================================================\n\n");
        sb.append("START TRANSACTION;\n\n");
        // 目录层先落：同名同父复用，重复执行本 SQL 不会堆出重复目录
        java.util.Map<String, String> dirVars = new java.util.LinkedHashMap<>();
        for (PlannedMenu plan : plans) {
            String systemName = plan.projectName();
            String systemPath = "/" + (plan.projectCode() == null ? "" : plan.projectCode());
            dirVars.computeIfAbsent("SYS|" + systemName, k -> {
                String var = "dir_sys_" + sanitizeVar(plan.projectCode());
                sb.append("-- 【一级】").append(systemName).append('\n');
                sb.append("SET @").append(var).append(" = (SELECT id FROM sys_menu WHERE parent_id = ")
                        .append(parentId).append(" AND name = ").append(sqlStr(systemName))
                        .append(" AND type = 1 AND deleted = 0 LIMIT 1);\n");
                sb.append("INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)\n");
                sb.append("SELECT ").append(parentId).append(", ").append(sqlStr(systemName)).append(", ")
                        .append(sqlStr(systemPath)).append(", NULL, NULL, 1, 1, 1, 0 FROM DUAL\n");
                sb.append("WHERE @").append(var).append(" IS NULL;\n");
                sb.append("SET @").append(var).append(" = COALESCE(@").append(var)
                        .append(", LAST_INSERT_ID());\n\n");
                return var;
            });
            String groupKey = "GRP|" + plan.projectName() + "|" + plan.groupLabel();
            dirVars.computeIfAbsent(groupKey, k -> {
                String var = "dir_grp_" + sanitizeVar(plan.groupCode());
                String sysVar = dirVars.get("SYS|" + plan.projectName());
                sb.append("-- 【二级】").append(plan.groupLabel()).append('\n');
                sb.append("SET @").append(var).append(" = (SELECT id FROM sys_menu WHERE parent_id = @")
                        .append(sysVar).append(" AND name = ").append(sqlStr(plan.groupLabel()))
                        .append(" AND type = 1 AND deleted = 0 LIMIT 1);\n");
                sb.append("INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)\n");
                sb.append("SELECT @").append(sysVar).append(", ").append(sqlStr(plan.groupLabel())).append(", ")
                        .append(sqlStr("/" + plan.projectCode() + "/" + plan.groupCode()))
                        .append(", NULL, NULL, 1, 1, 1, 0 FROM DUAL\n");
                sb.append("WHERE @").append(var).append(" IS NULL;\n");
                sb.append("SET @").append(var).append(" = COALESCE(@").append(var)
                        .append(", LAST_INSERT_ID());\n\n");
                return var;
            });
        }
        sb.append("-- ===== 三级：页面 =====\n");
        for (PlannedMenu plan : plans) {
            GenTable table = plan.table();
            String var = "menu_" + table.getTableName().replaceAll("[^A-Za-z0-9_]", "_");
            String grpVar = dirVars.get("GRP|" + plan.projectName() + "|" + plan.groupLabel());
            sb.append("-- ").append(plan.projectName()).append(" / ").append(plan.groupLabel()).append(" / ")
                    .append(table.getTableComment()).append("（").append(table.getTableName()).append("）\n");
            sb.append("INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)\n");
            sb.append("VALUES (@").append(grpVar).append(", ").append(sqlStr(table.getTableComment())).append(", ")
                    .append(sqlStr(plan.path())).append(", ").append(sqlStr(plan.component())).append(", ")
                    .append(sqlStr(plan.listPerm())).append(", 2, 1, 1, 0);\n");
            sb.append("SET @").append(var).append(" = LAST_INSERT_ID();\n");
            long sort = 1;
            for (String[] btn : plan.buttons()) {
                sb.append("INSERT INTO sys_menu (parent_id, name, path, component, perms, type, sort, status, deleted)\n");
                sb.append("VALUES (@").append(var).append(", ").append(sqlStr(btn[0])).append(", NULL, NULL, ")
                        .append(sqlStr(permsPrefix(table) + ":" + btn[1])).append(", 3, ").append(sort++)
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
     * 权限点前缀：{@code 系统编码_表名}（未绑定系统时就是表名）。
     *
     * <p>为什么要带系统编码：两个系统都有 {@code sys_user} 时，
     * 权限点 {@code sys_user:add} 会重名。角色授权时勾了 A 系统的用户新增，
     * B 系统的按钮也会亮起来——权限串了但系统不报错，属于最难查的一类问题。
     *
     * <p>项目里权限码还出现在 {@code :*} 通配和按钮码上，所以统一从这里出，
     * 不要在各处手写字符串拼接，否则漏一处就是一处越权。
     */
    private String permsPrefix(GenTable table) {
        return GenNaming.permsPrefix(projectCodeOf(table.getProjectId()), table.getTableName());
    }

    /**
     * 系统编码，查不到返回 null（未绑定系统的存量表就是这种情况）
     */
    private String projectCodeOf(Long projectId) {
        SysProject project = projectOf(projectId);
        return project == null ? null : project.getProjectCode();
    }

    /**
     * 表所属系统，未绑定或系统已删返回 null。
     * 用 Map 手动缓存而不是 computeIfAbsent：系统被删时 selectById 返回 null，
     * computeIfAbsent 不缓存 null，会退化成每次调用查一次库。
     */
    private SysProject projectOf(Long projectId) {
        if (projectId == null) {
            return null;
        }
        if (projectCache.containsKey(projectId)) {
            return projectCache.get(projectId);
        }
        SysProject project;
        try {
            project = projectMapper.selectById(projectId);
        } catch (Exception e) {
            // 旧库可能还没有 sys_project 表，此时等同于「未绑定系统」
            project = null;
        }
        projectCache.put(projectId, project);
        return project;
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
                    permsPrefix(table) + ":" + btn[1], table.getMenuId(), btn[0]);
        }
        jdbcTemplate.update("UPDATE sys_menu SET perms = ? WHERE id = ? AND (perms IS NULL OR perms = '')",
                permsPrefix(table) + ":list", table.getMenuId());
        return permsPrefix(table) + ":*";
    }

    private void grant(Long roleId, Long menuId) {
        jdbcTemplate.update("INSERT INTO sys_role_menu(role_id, menu_id, deleted) VALUES(?, ?, 0) "
                + "ON DUPLICATE KEY UPDATE deleted = 0", roleId, menuId);
    }

    /**
     * 批量授权：把同一条菜单授给多个角色（direct 出口用）。
     * 幂等，重复授权只会把 deleted 复位。
     */
    private void grantAll(List<Long> roleIds, Long menuId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            grant(roleId, menuId);
        }
    }

    /**
     * 解析目标角色列表：显式 roleIds > grantAllRoles > roleId > 管理员角色。
     * 结果去重且过滤掉不存在的角色，避免插入脏授权。
     */
    private List<Long> resolveRoleIds(GenMenuRegisterDTO dto) {
        List<Long> ids = new ArrayList<>();
        if (dto.getRoleIds() != null) {
            for (Long id : dto.getRoleIds()) {
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        if (Boolean.TRUE.equals(dto.getGrantAllRoles())) {
            ids.addAll(jdbcTemplate.queryForList(
                    "SELECT id FROM sys_role WHERE deleted = 0 AND status = 1 ORDER BY id", Long.class));
        }
        if (ids.isEmpty()) {
            ids.add(resolveRoleId(dto.getRoleId()));
        }
        // 去重 + 过滤不存在的角色
        List<Long> valid = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_role WHERE id = ? AND deleted = 0", Integer.class, id);
            if (cnt != null && cnt > 0 && !valid.contains(id)) {
                valid.add(id);
            }
        }
        return valid.isEmpty() ? List.of(resolveRoleId(dto.getRoleId())) : valid;
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

    /**
     * 菜单路径拼装：{@code /{系统编码}/{模块}/{list}}。
     *
     * <p>系统编码这一段是 V3.1 加的。平台的 {@code sys_menu} 是所有系统共用的一张表，
     * 两个系统各有一张 {@code goods} 时，旧规则会拼出两条一模一样的
     * {@code /goods/list}——后注册的把先注册的覆盖掉，或者前端路由出现重复 path，
     * vue-router 只会静默取第一条，表现为「点 A 系统的菜单进了 B 系统的页面」。
     *
     * <p>系统编码只在最外层加：{@code parentPath} 非空说明挂在已有父菜单下
     * （internal 出口的「指定父级」功能），此时父级路径已经是权威前缀，
     * 再插一段会变成 {@code /sys/default/goods/list}。
     */
    private String joinPath(String parentPath, String projectCode, String module, String leaf) {
        String base = parentPath == null ? "" : parentPath.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String scope = projectCode == null ? "" : projectCode.trim().toLowerCase(java.util.Locale.ROOT);
        String mid = module == null ? "" : module.trim();
        List<String> parts = new ArrayList<>();
        if (!base.isEmpty()) {
            parts.add(base);
        } else if (!scope.isEmpty()) {
            parts.add(scope);
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

    /**
     * SQL 变量名只允许字母数字下划线，系统编码里出现横杠/中文时不能直接拼进 {@code @var}
     */
    private String sanitizeVar(String raw) {
        if (raw == null || raw.isBlank()) {
            return "x";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
        return Character.isDigit(cleaned.charAt(0)) ? "v_" + cleaned : cleaned;
    }
}
