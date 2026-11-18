package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.config.GenOutputProperties;
import com.codegen.dto.GenColumnDTO;
import com.codegen.dto.GenTableBatchDTO;
import com.codegen.dto.GenTablePageDTO;
import com.codegen.dto.GenTableSaveDTO;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenMenuService;
import com.codegen.service.GenTableColumnService;
import com.codegen.service.DictDetectService;
import com.codegen.service.GenTableService;
import com.codegen.support.GenCascadeMode;
import com.codegen.support.GenSwitchDefaults;
import com.codegen.util.GenUtils;
import com.codegen.vo.GenColumnSyncVO;
import com.codegen.vo.GenDbTableVO;
import com.codegen.vo.GenImportVO;
import com.codegen.vo.GenTableVO;
import com.codegen.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 代码生成器-表配置 Service
 */
@Service
public class GenTableServiceImpl extends ServiceImpl<GenTableMapper, GenTable> implements GenTableService {

    private static final Logger log = LoggerFactory.getLogger(GenTableServiceImpl.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w+$");
    /**
     * 联动规则的触发字段名：只允许 Java 属性名形态，杜绝把任意内容拼进生成的 JS。
     */
    private static final Pattern LINK_FIELD_NAME = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final List<String> SYSTEM_SCHEMAS = List.of(
            "information_schema", "mysql", "performance_schema", "sys");
    private static final List<String> GEN_EXCLUDED_TABLES = List.of("gen_table", "gen_table_column");

    /**
     * 由 BaseEntity 管理的列：实体自动映射，导入时也刻意跳过，不参与漂移比对，
     * 否则每张表都会报「新增 create_time/create_by/deleted」这种假漂移。
     */
    private static final Set<String> BASE_MANAGED_COLUMNS = Set.of(
            "id", "create_time", "update_time", "create_by", "update_by", "deleted");

    @Autowired
    private GenTableColumnService columnService;

    @Autowired
    private DictDetectService dictDetectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenOutputProperties outputProperties;

    @Autowired
    private GenMenuService genMenuService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private com.codegen.mapper.SysProjectMapper projectMapper;

    /**
     * 表所属系统，未绑定/已删返回 null。单次请求内缓存，避免批量导入时
     * 几十张表把同一个系统重复查几十次。
     */
    private final Map<Long, com.codegen.entity.SysProject> projectCache = new HashMap<>();

    private com.codegen.entity.SysProject projectOf(Long projectId) {
        if (projectId == null) {
            return null;
        }
        if (projectCache.containsKey(projectId)) {
            return projectCache.get(projectId);
        }
        com.codegen.entity.SysProject project = projectMapper.selectById(projectId);
        projectCache.put(projectId, project);
        return project;
    }

    @Override
    public IPage<GenTableVO> pageList(GenTablePageDTO dto) {
        String status = dto.getGenStatus();
        boolean driftOnly = "drift".equals(status);

        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getTableName()), GenTable::getTableName, dto.getTableName());
        wrapper.like(StringUtils.hasText(dto.getTableComment()), GenTable::getTableComment, dto.getTableComment());
        // V3.0 系统筛选：-1 是前端约定的「未绑定系统」，其余按 id 精确匹配。
        // 用 -1 而不是 0 当哨兵：0 可能是合法但尚未分配的 id。
        if (dto.getProjectId() != null) {
            if (dto.getProjectId() == -1L) {
                wrapper.isNull(GenTable::getProjectId);
            } else {
                wrapper.eq(GenTable::getProjectId, dto.getProjectId());
            }
        }
        if ("generated".equals(status)) {
            wrapper.isNotNull(GenTable::getLastGenTime);
        } else if ("ungenerated".equals(status)) {
            wrapper.isNull(GenTable::getLastGenTime);
        } else if ("menu".equals(status)) {
            wrapper.isNotNull(GenTable::getMenuId);
        }
        wrapper.orderByDesc(GenTable::getId);

        // 漂移需要先算出来才能筛，只能在内存里过滤：表数量在百级，代价可接受
        if (driftOnly) {
            List<GenTable> all = this.list(wrapper);
            List<GenTableVO> allVos = all.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
            fillDrift(allVos);
            List<GenTableVO> drifted = allVos.stream()
                    .filter(v -> v.getDriftCount() != null && v.getDriftCount() > 0)
                    .collect(java.util.stream.Collectors.toList());
            int from = Math.min((dto.getPageNum() - 1) * dto.getPageSize(), drifted.size());
            int to = Math.min(from + dto.getPageSize(), drifted.size());
            Page<GenTableVO> voPage = new Page<>(dto.getPageNum(), dto.getPageSize(), drifted.size());
            voPage.setRecords(drifted.subList(from, to));
            return voPage;
        }

        Page<GenTable> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        this.page(page, wrapper);
        Page<GenTableVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<GenTableVO> records = page.getRecords().stream().map(this::toVO)
                .collect(java.util.stream.Collectors.toList());
        if (!"ungenerated".equals(status)) {
            fillDrift(records);
        }
        voPage.setRecords(records);
        return voPage;
    }

    /**
     * 结构漂移：库里的列 vs 字段配置里的列，两个方向都算
     */
    private void fillDrift(List<GenTableVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<String> names = vos.stream().map(GenTableVO::getTableName)
                .filter(StringUtils::hasText).toList();
        if (names.isEmpty()) {
            return;
        }
        Map<String, Set<String>> dbColumns = new HashMap<>();
        jdbcTemplate.query("SELECT table_name, column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name IN (" + placeholders(names.size()) + ")",
                rs -> {
                    dbColumns.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>())
                            .add(rs.getString(2).toLowerCase());
                },
                names.toArray());

        List<Long> ids = vos.stream().map(GenTableVO::getId).filter(Objects::nonNull).toList();
        Map<Long, Set<String>> configColumns = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbcTemplate.query("SELECT table_id, column_name FROM gen_table_column "
                            + "WHERE deleted = 0 AND table_id IN (" + placeholders(ids.size()) + ")",
                    rs -> {
                        configColumns.computeIfAbsent(rs.getLong(1), k -> new LinkedHashSet<>())
                                .add(rs.getString(2).toLowerCase());
                    },
                    ids.toArray());
        }

        for (GenTableVO vo : vos) {
            Set<String> db = dbColumns.get(vo.getTableName());
            if (db == null || db.isEmpty()) {
                vo.setDriftCount(0);
                continue;
            }
            Set<String> config = configColumns.getOrDefault(vo.getId(), Set.of());
            List<String> added = db.stream()
                    .filter(c -> !BASE_MANAGED_COLUMNS.contains(c))
                    .filter(c -> !config.contains(c)).toList();
            List<String> removed = config.stream()
                    .filter(c -> !BASE_MANAGED_COLUMNS.contains(c))
                    .filter(c -> !db.contains(c)).toList();
            vo.setDriftNewColumns(added);
            vo.setDriftMissingColumns(removed);
            vo.setDriftCount(added.size() + removed.size());
        }
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    /**
     * 结构漂移同步：只增不删（删列必须显式 removeMissing=true）
     */
    @Override
    @Transactional
    public GenColumnSyncVO syncColumns(Long id, boolean removeMissing) {
        GenTable table = getById(id);
        if (table == null) {
            throw new BusinessException(404, "表配置不存在");
        }
        GenColumnSyncVO vo = new GenColumnSyncVO();
        vo.setTableName(table.getTableName());

        List<GenColumnDTO> dbColumns;
        try {
            // 库名不能写死：以前是 "shop"，换库后同步列会一直报「表不存在」
            dbColumns = loadColumnsFromDb(currentSchema(), table.getTableName());
        } catch (BusinessException e) {
            vo.setSkipped(e.getMessage());
            return vo;
        }
        Map<String, GenColumnDTO> dbMap = new LinkedHashMap<>();
        for (GenColumnDTO c : dbColumns) {
            dbMap.put(c.getColumnName().toLowerCase(), c);
        }

        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTableColumn::getTableId, id);
        wrapper.orderByAsc(GenTableColumn::getSort);
        List<GenTableColumn> configCols = columnService.list(wrapper);
        Map<String, GenTableColumn> configMap = new LinkedHashMap<>();
        int maxSort = 0;
        for (GenTableColumn c : configCols) {
            configMap.put(c.getColumnName().toLowerCase(), c);
            if (c.getSort() != null) {
                maxSort = Math.max(maxSort, c.getSort());
            }
        }

        // 1) 库里有、配置没有 → 新增（继承导入时的类型/控件推断）
        int sort = maxSort;
        // 先攒后批量插：一张宽表几十列，逐条 save 就是几十次往返
        List<GenTableColumn> toInsert = new ArrayList<>();
        for (Map.Entry<String, GenColumnDTO> entry : dbMap.entrySet()) {
            if (BASE_MANAGED_COLUMNS.contains(entry.getKey()) || configMap.containsKey(entry.getKey())) {
                continue;
            }
            GenColumnDTO src = entry.getValue();
            GenTableColumn entity = new GenTableColumn();
            BeanUtils.copyProperties(src, entity);
            entity.setId(null);
            entity.setTableId(id);
            entity.setSort(++sort);
            toInsert.add(entity);
            vo.getAdded().add(entry.getKey());
            vo.setAffected(vo.getAffected() + 1);
        }
        if (!toInsert.isEmpty()) {
            columnService.saveBatch(toInsert);
        }

        // 2) 两边都有但类型不一致 → 只提示，不自动改（改类型要人判断）
        for (Map.Entry<String, GenTableColumn> entry : configMap.entrySet()) {
            GenColumnDTO db = dbMap.get(entry.getKey());
            if (db == null || BASE_MANAGED_COLUMNS.contains(entry.getKey())) {
                continue;
            }
            String dbType = db.getColumnType() == null ? "" : db.getColumnType().toLowerCase();
            String cfgType = entry.getValue().getColumnType() == null ? "" : entry.getValue().getColumnType().toLowerCase();
            if (!dbType.equals(cfgType)) {
                vo.getChanged().add(entry.getKey() + "：" + cfgType + " → " + dbType);
            }
        }

        // 3) 配置里有、库里没有 → 默认保留，removeMissing=true 才删除
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<String, GenTableColumn> entry : configMap.entrySet()) {
            if (dbMap.containsKey(entry.getKey()) || BASE_MANAGED_COLUMNS.contains(entry.getKey())) {
                continue;
            }
            vo.getRemoved().add(entry.getKey());
            if (removeMissing) {
                toRemove.add(entry.getValue().getId());
                vo.setAffected(vo.getAffected() + 1);
            }
        }
        if (!toRemove.isEmpty()) {
            columnService.removeByIds(toRemove);
        }
        return vo;
    }

    @Override
    @Transactional
    public int batchUpdate(GenTableBatchDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException(400, "请先勾选要批量修改的表");
        }
        int affected = 0;
        for (Long id : dto.getIds()) {
            GenTable exist = getById(id);
            if (exist == null) {
                continue;
            }
            GenTable patch = new GenTable();
            patch.setId(id);
            if (dto.getProjectId() != null) {
                // 批量转移归属：目标系统必须存在且启用，防止把表绑到停用系统后生成路径静默回落
                com.codegen.entity.SysProject target = projectMapper.selectById(dto.getProjectId());
                if (target == null) {
                    throw new BusinessException(404, "目标系统不存在：" + dto.getProjectId());
                }
                if (!Integer.valueOf(1).equals(target.getEnabled())) {
                    throw new BusinessException(400, "目标系统已停用：" + target.getProjectName());
                }
                patch.setProjectId(dto.getProjectId());
            } else {
                // 必须把 projectId 原样带回去：GenTable.projectId 是 FieldStrategy.IGNORED
                // （为了支持「解绑系统」把 null 写进库），部分更新时若不显式带回，
                // 批量套预设会顺手把这批表的系统归属全部清空——生成路径当场回落到全局，
                // 表现为「套了个预设，文件全写到别的仓库去了」。V3.1 实机验证时踩到过。
                patch.setProjectId(exist.getProjectId());
            }
            if (StringUtils.hasText(dto.getModuleName())) {
                patch.setModuleName(dto.getModuleName().trim());
            }
            if (StringUtils.hasText(dto.getPackageName())) {
                patch.setPackageName(dto.getPackageName().trim());
            }
            if (StringUtils.hasText(dto.getAuthor())) {
                patch.setAuthor(dto.getAuthor().trim());
            }
            if (StringUtils.hasText(dto.getBackendRoot())) {
                patch.setBackendRoot(dto.getBackendRoot().trim());
            }
            if (StringUtils.hasText(dto.getFrontendRoot())) {
                patch.setFrontendRoot(dto.getFrontendRoot().trim());
            }
            if (StringUtils.hasText(dto.getGatewayPrefix())) {
                patch.setGatewayPrefix(dto.getGatewayPrefix().trim());
            }
            if (StringUtils.hasText(dto.getPreset())) {
                applyPreset(patch, dto.getPreset());
            }
            if (updateById(patch)) {
                affected++;
            }
        }
        return affected;
    }

    /**
     * 开关预设：把 18 个开关收敛成 3 个有语义的组合，避免逐个点
     */
    private void applyPreset(GenTable patch, String preset) {
        boolean backendOnly = "backend-only".equals(preset);
        boolean readonly = "readonly".equals(preset);

        patch.setGenEntity(1);
        patch.setGenMapper(1);
        patch.setGenService(1);
        patch.setGenController(1);
        patch.setGenPageApi(1);
        patch.setGenDetailApi(1);
        patch.setGenAddApi(readonly ? 0 : 1);
        patch.setGenEditApi(readonly ? 0 : 1);
        patch.setGenDeleteApi(readonly ? 0 : 1);

        patch.setGenFrontend(backendOnly ? 0 : 1);
        patch.setGenApiJs(backendOnly ? 0 : 1);
        patch.setGenVuePage(backendOnly ? 0 : 1);
        patch.setGenQueryForm(backendOnly ? 0 : 1);
        patch.setGenAddBtn(readonly || backendOnly ? 0 : 1);
        patch.setGenEditBtn(readonly || backendOnly ? 0 : 1);
        patch.setGenDeleteBtn(readonly || backendOnly ? 0 : 1);
    }

    @Override
    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要删除的表");
        }
        int count = 0;
        for (Long id : ids) {
            if (getById(id) == null) {
                continue;
            }
            deleteConfig(id);
            count++;
        }
        return count;
    }

    @Override
    public GenTableVO detail(Long id) {
        GenTable table = getById(id);
        if (table == null) {
            return null;
        }
        GenTableVO vo = toVO(table);
        // 系统默认值随详情下发：前端用它做「默认」标记和「恢复系统默认」按钮，
        // 免得前端为了 17 个数字再单独发一次请求
        vo.setProjectGenDefaults(GenSwitchDefaults.defaultsOf(projectOf(table.getProjectId())));
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTableColumn::getTableId, id);
        wrapper.orderByAsc(GenTableColumn::getSort);
        vo.setColumns(columnService.list(wrapper).stream().map(this::toColumnDTO).toList());
        return vo;
    }

    @Override
    @Transactional
    public Map<String, Integer> resetSwitchDefaults(Long tableId) {
        GenTable table = getById(tableId);
        if (table == null) {
            throw new BusinessException(404, "表配置不存在");
        }
        // 只构造开关字段的补丁对象：updateById 默认 NOT_NULL 策略，
        // 其余字段为 null 不会参与 SET，包名/路径/字段配置原样保留
        GenTable patch = new GenTable();
        patch.setId(tableId);
        // projectId 是 IGNORED 策略，null 会被写进库，必须原样带回（同 batchUpdate）
        patch.setProjectId(table.getProjectId());
        GenSwitchDefaults.resetToProject(patch, projectOf(table.getProjectId()));
        updateById(patch);
        return GenSwitchDefaults.defaultsOf(projectOf(table.getProjectId()));
    }

    @Override
    @Transactional
    public Long saveConfig(GenTableSaveDTO dto) {
        validate(dto);
        boolean overwrite = Boolean.TRUE.equals(dto.getOverwrite());
        GenTable exist = getByTableName(dto.getTableName());
        GenTable table = new GenTable();
        BeanUtils.copyProperties(dto, table);
        table.setId(null);
        table.setDeleteCascadeMode(GenCascadeMode.sanitize(dto.getDeleteCascadeMode()));
        // V3.1 P0-3：17 个开关留空的，按「所属系统默认值 → 1」补齐。
        // 放在入库前而不是生成时：生成阶段必须尊重表级值，否则用户手动关掉的开关
        // 会被系统默认值顶回去，表现为「关了还在生成」，极难排查。
        GenSwitchDefaults.fillMissing(table, projectOf(dto.getProjectId()));
        if (exist != null) {
            if (!overwrite) {
                throw new BusinessException(409, "表名已存在：" + dto.getTableName() + "，可选择覆盖");
            }
            table.setId(exist.getId());
            updateById(table);
            removeColumns(exist.getId());
        } else {
            // 逻辑删除残留行仍占用 table_name 唯一键，插入前需物理清理
            purgeDeletedResidual(dto.getTableName());
            save(table);
        }
        saveColumns(table.getId(), dto.getColumns());
        return table.getId();
    }

    @Override
    @Transactional
    public void updateConfig(GenTableSaveDTO dto) {
        validate(dto);
        if (dto.getId() == null) {
            throw new BusinessException(400, "修改时主键不能为空");
        }
        GenTable exist = getById(dto.getId());
        if (exist == null) {
            throw new BusinessException(404, "表配置不存在");
        }
        GenTable sameName = getByTableName(dto.getTableName());
        if (sameName != null && !sameName.getId().equals(dto.getId())) {
            throw new BusinessException(409, "表名已存在：" + dto.getTableName());
        }
        // 改名目标与逻辑删除残留行冲突时，同样需物理清理
        if (sameName == null) {
            purgeDeletedResidual(dto.getTableName());
        }
        GenTable table = new GenTable();
        BeanUtils.copyProperties(dto, table);
        // 越界值归一为 null（未配置），避免脏值逃进模板
        table.setDeleteCascadeMode(GenCascadeMode.sanitize(dto.getDeleteCascadeMode()));
        // V4.5 打印单据：只认 0/1，越界归一为 null（跟随系统）
        Integer genPrint = dto.getGenPrint();
        if (genPrint != null && genPrint != 0 && genPrint != 1) {
            genPrint = null;
        }
        table.setGenPrint(genPrint);
        updateById(table);
        // 「改回未配置」必须显式置 NULL：MyBatis-Plus 的 updateById 默认跳过 null 字段，
        // 表单清空级联下拉后旧值会永远删不掉，表现为「选了未配置、保存完还是原来的值」。
        // 不用实体上的 updateStrategy=IGNORED 兜底——本类还有 349/430 两处只改
        // 部分字段的 updateById，那样会把它们没带上的字段一并清空。
        if (table.getDeleteCascadeMode() == null || genPrint == null) {
            LambdaUpdateWrapper<GenTable> clearWrapper = new LambdaUpdateWrapper<GenTable>()
                    .eq(GenTable::getId, dto.getId());
            if (table.getDeleteCascadeMode() == null) {
                clearWrapper.set(GenTable::getDeleteCascadeMode, null);
            }
            if (genPrint == null) {
                clearWrapper.set(GenTable::getGenPrint, null);
            }
            update(clearWrapper);
        }
        removeColumns(dto.getId());
        saveColumns(dto.getId(), dto.getColumns());
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        GenTable table = getById(id);
        removeById(id);
        removeColumns(id);
        if (table != null && table.getMenuId() != null) {
            // 不清理会留下指向已删除页面的菜单，点进去就是 404
            cleanupMenu(table.getMenuId());
            try {
                genMenuService.syncRoute(false);
            } catch (Exception e) {
                log.warn("删除表配置后重建路由失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 删除菜单及其按钮，并回收授权
     */
    private void cleanupMenu(Long menuId) {
        List<Long> menuIds = new ArrayList<>();
        menuIds.add(menuId);
        menuIds.addAll(jdbcTemplate.queryForList(
                "SELECT id FROM sys_menu WHERE parent_id = ?", Long.class, menuId));
        for (Long id : menuIds) {
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id = ?", id);
            jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", id);
        }
    }

    private GenTable getByTableName(String tableName) {
        return lambdaQuery().eq(GenTable::getTableName, tableName).one();
    }

    /**
     * 物理删除同表名的逻辑删除残留行（残留行仍占用 table_name 唯一键，会阻断重新导入）
     */
    private void purgeDeletedResidual(String tableName) {
        List<Long> residualIds = jdbcTemplate.queryForList(
                "SELECT id FROM gen_table WHERE table_name = ? AND deleted = 1", Long.class, tableName);
        for (Long residualId : residualIds) {
            jdbcTemplate.update("DELETE FROM gen_table_column WHERE table_id = ?", residualId);
            jdbcTemplate.update("DELETE FROM gen_table WHERE id = ?", residualId);
        }
    }

    private void validate(GenTableSaveDTO dto) {
        if (!StringUtils.hasText(dto.getTableName())) {
            throw new BusinessException(400, "表名不能为空");
        }
        if (!StringUtils.hasText(dto.getClassName())) {
            throw new BusinessException(400, "实体类名不能为空");
        }
        if (!StringUtils.hasText(dto.getModuleName())) {
            throw new BusinessException(400, "前端模块名不能为空");
        }
        if (dto.getColumns() == null || dto.getColumns().isEmpty()) {
            throw new BusinessException(400, "字段配置不能为空");
        }
    }

    private void removeColumns(Long tableId) {
        LambdaQueryWrapper<GenTableColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenTableColumn::getTableId, tableId);
        columnService.remove(wrapper);
    }

    private void saveColumns(Long tableId, List<GenColumnDTO> columns) {
        for (GenColumnDTO col : columns) {
            GenTableColumn entity = new GenTableColumn();
            BeanUtils.copyProperties(col, entity);
            entity.setId(null);
            entity.setTableId(tableId);
            // 控件留空时按类型推断，避免旧客户端提交空串
            if (!StringUtils.hasText(entity.getHtmlType())) {
                entity.setHtmlType(GenUtils.inferHtmlType(entity.getColumnType(), entity.getJavaType(),
                        entity.getColumnName()));
            }
            if (!StringUtils.hasText(entity.getDictType())) {
                entity.setDictType(null);
            }
            if (!StringUtils.hasText(entity.getValidateRule())) {
                entity.setValidateRule(null);
            }
            // V1.4-4 关联下拉：表名留空表示普通字段，展示字段默认 name
            if (!StringUtils.hasText(entity.getRefTable())) {
                entity.setRefTable(null);
                entity.setRefLabel(null);
            } else if (!StringUtils.hasText(entity.getRefLabel())) {
                entity.setRefLabel("name");
            }
            // V1.4-5 Excel：新字段默认导出开、导入关
            if (entity.getExcelExport() == null) {
                entity.setExcelExport(1);
            }
            if (entity.getExcelImport() == null) {
                entity.setExcelImport(0);
            }
            // P1 新增列的默认值与校验：空串统一转 null，避免落库后模板判空失效
            if (!StringUtils.hasText(entity.getMaskType())) {
                entity.setMaskType(null);
            }
            if (entity.getUniqueFlag() == null) {
                entity.setUniqueFlag(0);
            }
            if (entity.getRelationKey() == null) {
                entity.setRelationKey(0);
            }
            if (!StringUtils.hasText(entity.getDataScope())) {
                entity.setDataScope(null);
            }
            entity.setLinkRule(normalizeLinkRule(col.getColumnName(), entity.getLinkRule()));
            columnService.save(entity);
        }
    }

    private GenTableVO toVO(GenTable table) {
        GenTableVO vo = new GenTableVO();
        BeanUtils.copyProperties(table, vo);
        return vo;
    }

    /**
     * 规范化 P1-4 联动规则 JSON。
     * <p>
     * 规则会被生成期原样内联进 Vue 页面源码，所以这里必须拒绝非法 JSON——
     * 否则生成出一份打不开的前端页面，用户要等到打开页面时才发现问题。
     * 只做「能不能用」的判定，不做业务语义推断。
     */
    private String normalizeLinkRule(String columnName, String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        try {
            java.util.Map<String, Object> rule = objectMapper.readValue(text,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                    });
            Object op = rule.get("op");
            Object effect = rule.get("effect");
            Set<String> ops = Set.of("eq", "ne", "in", "notIn", "notEmpty", "empty");
            Set<String> effects = Set.of("show", "hide", "enable", "disable", "required", "optional");
            if (op != null && !ops.contains(String.valueOf(op))) {
                throw new BusinessException(400, "字段 " + columnName + " 的联动条件不合法：" + op);
            }
            if (effect != null && !effects.contains(String.valueOf(effect))) {
                throw new BusinessException(400, "字段 " + columnName + " 的联动动作不合法：" + effect);
            }
            Object when = rule.get("when");
            if (when != null && !LINK_FIELD_NAME.matcher(String.valueOf(when)).matches()) {
                throw new BusinessException(400, "字段 " + columnName + " 的联动触发字段名不合法：" + when);
            }
            // 输出时去掉空白，模板里按单行内联
            return text.replaceAll("\\s*\\n\\s*", "");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "字段 " + columnName + " 的联动规则不是合法 JSON：" + text);
        }
    }

    private GenColumnDTO toColumnDTO(GenTableColumn column) {        GenColumnDTO dto = new GenColumnDTO();
        BeanUtils.copyProperties(column, dto);
        return dto;
    }

    // ==================== 二期：数据库表导入 ====================

    @Override
    public List<String> listDbSchemas() {
        String sql = "SELECT schema_name FROM information_schema.schemata "
                + "WHERE schema_name NOT IN ('information_schema','mysql','performance_schema','sys') "
                + "ORDER BY schema_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    @Override
    public boolean existsByTableName(String tableName) {
        return getByTableName(tableName) != null;
    }

    @Override
    public List<GenDbTableVO> listDbTables(String schema) {
        validateSchema(schema);
        String sql = "SELECT table_name, IFNULL(table_comment,'') AS table_comment FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE' "
                + "AND table_name NOT IN ('gen_table','gen_table_column') ORDER BY table_name";
        return jdbcTemplate.query(sql, (rs, i) -> {
            GenDbTableVO vo = new GenDbTableVO();
            vo.setName(rs.getString("table_name"));
            vo.setComment(rs.getString("table_comment"));
            return vo;
        }, schema);
    }

    @Override
    @Transactional
    public List<GenImportVO> importTables(String schema, List<String> tableNames, Long projectId) {
        validateSchema(schema);
        if (tableNames == null || tableNames.isEmpty()) {
            throw new BusinessException(400, "请先勾选要导入的表");
        }
        List<GenImportVO> results = new ArrayList<>();
        for (String tableName : tableNames) {
            if (tableName == null || !NAME_PATTERN.matcher(tableName).matches()) {
                results.add(new GenImportVO(tableName, "ERROR", "非法表名"));
                continue;
            }
            try {
                results.add(importOne(schema, tableName, projectId));
            } catch (Exception e) {
                results.add(new GenImportVO(tableName, "ERROR", e.getMessage()));
            }
        }
        return results;
    }

    private GenImportVO importOne(String schema, String tableName, Long projectId) {
        if (getByTableName(tableName) != null) {
            return new GenImportVO(tableName, "EXISTS", "已存在，跳过");
        }
        String tableComment = jdbcTemplate.queryForObject(
                "SELECT IFNULL(table_comment,'') FROM information_schema.tables WHERE table_schema=? AND table_name=?",
                String.class, schema, tableName);
        GenTableSaveDTO dto = new GenTableSaveDTO();
        dto.setTableName(tableName);
        dto.setTableComment(tableComment == null || tableComment.isBlank() ? tableName : tableComment);
        dto.setClassName(GenUtils.toPascal(tableName));
        dto.setModuleName(GenUtils.inferModuleName(tableName));
        // V3.0：包名/输出路径/网关前缀一律留空，交给「表级 → 系统 → 全局」回落链决定。
        // 早先这里填的是全局值，一旦表挂到系统上，表级非空就会盖住系统的包名，
        // 系统级配置等于失效——这正是多系统支持最容易踩的坑。
        dto.setPackageName("");
        dto.setBackendPath("");
        dto.setGatewayPrefix("");
        dto.setBackendRoot("");
        dto.setFrontendRoot("");
        dto.setProjectId(projectId);
        dto.setOverwrite(false);
        dto.setColumns(loadColumnsFromDb(schema, tableName));
        Long tableId = saveConfig(dto);
        // P2-3 全链路：导入时就把 enum/注释里的枚举建成字典并回填 dict_type，
        // 否则开发者还得自己再跑一遍「字典识别」。
        // 这里吞掉异常：建字典失败不应该让整张表的导入回滚。
        try {
            int dicts = dictDetectService.apply(tableId, null);
            if (dicts > 0) {
                return new GenImportVO(tableName, "SAVED", "导入成功，自动识别 " + dicts + " 个字典");
            }
        } catch (Exception e) {
            log.warn("导入表 {} 时自动建字典失败，已跳过：{}", tableName, e.getMessage());
        }
        return new GenImportVO(tableName, "SAVED", "导入成功");
    }

    /**
     * 当前连接实际所在的库。表配置里没存库名，只能以数据源为准。
     */
    private String currentSchema() {
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            throw new BusinessException(500, "无法确定当前数据库：" + e.getMessage());
        }
    }

    /**
     * 从 information_schema.columns 构建字段配置（跳过 BaseEntity 统一字段，主键除外）
     */
    private List<GenColumnDTO> loadColumnsFromDb(String schema, String tableName) {
        String sql = "SELECT column_name, column_type, IFNULL(column_comment,'') AS column_comment, "
                + "is_nullable, column_key FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, schema, tableName);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "表不存在：" + schema + "." + tableName);
        }

        List<GenColumnDTO> columns = new ArrayList<>();
        int sort = 0;
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("column_name"));
            boolean pk = "PRI".equalsIgnoreCase(String.valueOf(row.get("column_key")));
            if (!pk && BASE_EXCLUDED.contains(name)) {
                continue;
            }
            GenColumnDTO col = new GenColumnDTO();
            col.setColumnName(name);
            col.setColumnType(String.valueOf(row.get("column_type")));
            String colComment = String.valueOf(row.get("column_comment"));
            col.setColumnComment(colComment.isBlank() ? name : colComment);
            col.setJavaField(GenUtils.toCamel(name));
            col.setJavaType(GenUtils.resolveJavaType(col.getColumnType()));
            col.setIsPk(pk ? 1 : 0);
            col.setIsRequired("NO".equalsIgnoreCase(String.valueOf(row.get("is_nullable"))) ? 1 : 0);
            col.setIsQuery(0);
            col.setQueryType("EQ");
            col.setIsList(1);
            col.setIsForm(1);
            String htmlType = GenUtils.inferHtmlType(col.getColumnType(), col.getJavaType(), name);
            col.setHtmlType(htmlType);
            col.setDictType(GenUtils.inferDictType(htmlType, name));
            col.setValidateRule(GenUtils.inferValidateRule(col.getColumnType(), col.getJavaType(),
                    col.getIsRequired() != null && col.getIsRequired() == 1));
            col.setSort(++sort);
            columns.add(col);
        }
        if (columns.stream().noneMatch(c -> c.getIsPk() == 1)) {
            throw new BusinessException(400, "该表没有主键，无法导入");
        }
        return columns;
    }

    private static final List<String> BASE_EXCLUDED = List.of(
            "id", "deleted", "create_by", "create_time", "update_by", "update_time");

    private void validateSchema(String schema) {
        if (!StringUtils.hasText(schema) || !NAME_PATTERN.matcher(schema).matches()) {
            throw new BusinessException(400, "非法数据库名：" + schema);
        }
    }
}
