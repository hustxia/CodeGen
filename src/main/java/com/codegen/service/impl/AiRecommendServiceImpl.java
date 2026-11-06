package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenAiRecommendApplyDTO;
import com.codegen.dto.GenAiRecommendTableDTO;
import com.codegen.entity.GenPipeline;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenPipelineMapper;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.AiRecommendService;
import com.codegen.service.DictService;
import com.codegen.support.AiRecommendSanitizer;
import com.codegen.util.GenUtils;
import com.codegen.vo.DictTypeVO;
import com.codegen.vo.GenAiRecommendPreviewVO;
import com.codegen.vo.GenAiRecommendVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V7.0 AI 页面与菜单推荐实现：按模块分批调 AI → 白名单清洗 → 合并产物。
 *
 * <p>分批沿用 model/modules 两段式思想：列级/表级按模块分批（单批输出预算可控，
 * 40 张表也不会截断），菜单组织是跨表归纳，用一次只带表名/注释的轻量调用收尾。
 *
 * <p>HTTP 调用骨架与 {@link AiModelServiceImpl} 同款（OpenAI 兼容 /chat/completions，
 * response_format=json_object）。两处 prompt/解析完全不同，共享的只是 HTTP 骨架，
 * 为不动已验证的建模链路，这里各持一份；第三次出现时再抽公共 client。
 */
@Slf4j
@Service
public class AiRecommendServiceImpl implements AiRecommendService {

    /** 单批最多多少张表：列级建议是输出大户，10 张表 ≈ 100 列，本地小模型也能完整吐完 */
    static final int MAX_TABLES_PER_BATCH = 10;

    private static final String TABLE_SYSTEM_PROMPT = """
            你是资深的后台管理系统页面配置专家。根据表结构元数据，为每张表推荐页面配置。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"tables":[{"tableName":"原样照抄","moduleName":"模块key","treeParentColumn":"","treeLabelColumn":"","masterTableName":"","relationType":"","columns":[{"columnName":"原样照抄","isQuery":0,"queryType":"EQ","isList":1,"isForm":1,"htmlType":"input","dictType":"","sort":1}]}]}
            3. tableName / columnName 必须原样照抄输入，不允许发明新表、新列。
            4. htmlType 只能用：input,textarea,number,select,radio,switch,date,datetime,image,file,multiImage。queryType 只能用：EQ,LIKE,GE,LE。
            5. isQuery/isList/isForm 只能是 0 或 1。dictType 只能用我给你的字典编码，不确定就留空字符串。
            6. moduleName 用英文小写模块 key（如 stock、order、system），按业务相近程度归纳。
            7. treeParentColumn/treeLabelColumn 只对自关联树形表（如部门、分类）给值，其他留空字符串。
            8. masterTableName 只对明细/行项目类子表给值（值为主表的 tableName），relationType 只对多对多中间表给 middle，其他留空字符串。
            9. 主键列不参与查询/表单：isQuery=0、isForm=0、isList=0。
            10. 拿不准的字段不要输出该字段（只输出有把握的建议）。
            11. 直接给出结果，不要逐步推理。/no_think
            """;

    private static final String MENU_SYSTEM_PROMPT = """
            你是资深的后台管理系统信息架构师。根据系统的表清单，组织菜单目录。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"menus":[{"moduleName":"模块key","dirLabel":"中文目录名","icon":"el-icon-xxx","sort":1,"tables":["表名"]}]}
            3. 按业务相近程度把表组织成若干目录，每张表恰好属于一个目录。
            4. moduleName 用英文小写模块 key；dirLabel 用 2-6 个汉字；icon 用 Element UI 图标类名（如 el-icon-box、el-icon-tickets、el-icon-setting）。
            5. sort 从 1 开始按业务重要性递增；tables 里的表名必须来自输入清单。
            6. 直接给出结果，不要逐步推理。/no_think
            """;

    @Autowired
    private GenPipelineProperties properties;

    @Autowired
    private GenTableMapper tableMapper;

    @Autowired
    private GenTableColumnMapper columnMapper;

    @Autowired
    private DictService dictService;

    @Autowired
    private GenPipelineMapper pipelineMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenAiRecommendVO recommend(Long projectId) {
        List<GenTable> tables = tablesOfProject(projectId).values().stream()
                .sorted(java.util.Comparator.comparing(GenTable::getId))
                .toList();
        if (tables.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "系统下没有表配置，无法推荐");
        }
        return doRecommend(tables, true);
    }

    /**
     * 推荐主体：给定表集合出建议。
     *
     * @param withMenu 是否追加「菜单组织」跨表归纳。表级入口（V7.3 P0-3）只看一部分表，
     *                 归纳不出可靠分组，传 false——moduleName 的表级建议已经覆盖分组诉求。
     */
    private GenAiRecommendVO doRecommend(List<GenTable> tables, boolean withMenu) {
        List<Long> tableIds = tables.stream().map(GenTable::getId).toList();
        List<GenTableColumn> columns = columnMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenTableColumn>()
                        .in(GenTableColumn::getTableId, tableIds)
                        .orderByAsc(GenTableColumn::getSort));
        Map<Long, List<GenTableColumn>> columnsOf = columns.stream()
                .collect(Collectors.groupingBy(GenTableColumn::getTableId, LinkedHashMap::new, Collectors.toList()));

        // 白名单校验基准：表 → 列名集合、平台已知字典编码
        Map<String, Set<String>> tableColumns = new LinkedHashMap<>();
        for (GenTable t : tables) {
            tableColumns.put(t.getTableName(), columnsOf.getOrDefault(t.getId(), List.of())
                    .stream().map(GenTableColumn::getColumnName).collect(Collectors.toSet()));
        }
        Set<String> knownDictCodes = new HashSet<>();
        for (DictTypeVO t : dictService.listTypes(null)) {
            if (StringUtils.hasText(t.getDictCode())) {
                knownDictCodes.add(t.getDictCode());
            }
        }
        String dictHint = knownDictCodes.isEmpty()
                ? "（平台暂无字典，所有 dictType 留空字符串）"
                : knownDictCodes.stream().sorted().collect(Collectors.joining(","));

        GenAiRecommendVO merged = new GenAiRecommendVO();
        List<List<GenTable>> batches = batches(tables, MAX_TABLES_PER_BATCH);
        log.info("AI 推荐：{} 张表分 {} 批", tables.size(), batches.size());
        for (List<GenTable> batch : batches) {
            String userContent = buildTableUserContent(batch, columnsOf, dictHint);
            String json = chatJson(TABLE_SYSTEM_PROMPT, userContent);
            GenAiRecommendVO part = AiRecommendSanitizer.sanitize(
                    parseJson(json), tableColumns, knownDictCodes);
            merged.getTables().addAll(part.getTables());
            merged.getDrops().addAll(part.getDrops());
        }
        if (withMenu) {
            // 菜单组织：跨表归纳，一次轻量调用（只带表名/注释，输出预算小）
            try {
                String menuJson = chatJson(MENU_SYSTEM_PROMPT, buildMenuUserContent(tables));
                GenAiRecommendVO menuPart = AiRecommendSanitizer.sanitize(
                        parseJson(menuJson), tableColumns, knownDictCodes);
                merged.getMenus().addAll(menuPart.getMenus());
                merged.getDrops().addAll(menuPart.getDrops());
            } catch (BusinessException e) {
                // 菜单层失败不株连列/表建议：记一条丢弃原因，产物主体仍可用
                log.warn("AI 菜单组织建议失败（列/表建议保留）: {}", e.getMessage());
                merged.getDrops().add("菜单组织建议生成失败：" + e.getMessage());
            }
        }
        return merged;
    }

    /**
     * 分批：按现有 moduleName 分组（未分组的归一桶），组内再按 maxPerBatch 切。
     * 静态纯函数，AC-6（40 表分批不截断）的可测核心。
     */
    static List<List<GenTable>> batches(List<GenTable> tables, int maxPerBatch) {
        Map<String, List<GenTable>> byModule = new LinkedHashMap<>();
        for (GenTable t : tables) {
            String key = StringUtils.hasText(t.getModuleName()) ? t.getModuleName() : "";
            byModule.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<List<GenTable>> result = new ArrayList<>();
        for (List<GenTable> group : byModule.values()) {
            for (int i = 0; i < group.size(); i += maxPerBatch) {
                result.add(group.subList(i, Math.min(i + maxPerBatch, group.size())));
            }
        }
        return result;
    }

    // ==================== 预览（AC-3：只读） ====================

    @Override
    public GenAiRecommendPreviewVO preview(Long pipelineId) {
        GenPipeline pipeline = requirePipeline(pipelineId);
        Path artifact = artifactPath(pipeline);
        if (!Files.exists(artifact)) {
            return new GenAiRecommendPreviewVO();
        }
        return toPreview(readArtifact(artifact), pipeline.getProjectId());
    }

    /** 产物 → 可勾选 diff 视图。流水线与表级入口共用（V7.3 P0-3）。 */
    private GenAiRecommendPreviewVO toPreview(GenAiRecommendVO suggest, Long projectId) {
        GenAiRecommendPreviewVO vo = new GenAiRecommendPreviewVO();
        vo.setExists(true);
        vo.setMenus(suggest.getMenus());
        vo.setDrops(suggest.getDrops());

        Map<String, GenTable> tablesByName = tablesOfProject(projectId);
        Map<Long, Map<String, GenTableColumn>> columnsByTable = columnsOfTables(tablesByName.values());
        Map<Long, String> tableNameById = tablesByName.values().stream()
                .collect(Collectors.toMap(GenTable::getId, GenTable::getTableName));

        for (GenAiRecommendVO.TableSuggest ts : suggest.getTables()) {
            GenTable table = tablesByName.get(ts.getTableName());
            if (table == null) {
                continue; // 产物生成后表被删了，跳过
            }
            GenAiRecommendPreviewVO.TablePreview tp = new GenAiRecommendPreviewVO.TablePreview();
            tp.setTableId(table.getId());
            tp.setTableName(table.getTableName());
            tp.setTableComment(table.getTableComment());

            addTableFieldDiff(tp, "moduleName", table.getModuleName(), ts.getModuleName(),
                    !Objects.equals(table.getModuleName(), GenUtils.inferModuleName(table.getTableName())));
            addTableFieldDiff(tp, "treeParentColumn", table.getTreeParentColumn(), ts.getTreeParentColumn(),
                    StringUtils.hasText(table.getTreeParentColumn()));
            addTableFieldDiff(tp, "treeLabelColumn", table.getTreeLabelColumn(), ts.getTreeLabelColumn(),
                    StringUtils.hasText(table.getTreeLabelColumn()));
            String currentMaster = table.getMasterTableId() == null ? null : tableNameById.get(table.getMasterTableId());
            addTableFieldDiff(tp, "masterTable", currentMaster, ts.getMasterTableName(),
                    table.getMasterTableId() != null);
            addTableFieldDiff(tp, "relationType", table.getRelationType(), ts.getRelationType(),
                    StringUtils.hasText(table.getRelationType()));

            Map<String, GenTableColumn> cols = columnsByTable.getOrDefault(table.getId(), Map.of());
            for (GenAiRecommendVO.ColumnSuggest cs : ts.getColumns()) {
                GenTableColumn col = cols.get(cs.getColumnName());
                if (col == null) {
                    continue; // 列被删了
                }
                GenAiRecommendPreviewVO.ColumnPreview cp = new GenAiRecommendPreviewVO.ColumnPreview();
                cp.setColumnName(col.getColumnName());
                cp.setColumnComment(col.getColumnComment());
                String ruleHtml = GenUtils.inferHtmlType(col.getColumnType(), col.getJavaType(), col.getColumnName());
                addColumnFieldDiff(cp, "isQuery", col.getIsQuery(), cs.getIsQuery(),
                        !Objects.equals(col.getIsQuery(), 0));
                addColumnFieldDiff(cp, "queryType", col.getQueryType(), cs.getQueryType(),
                        !Objects.equals(col.getQueryType(), "EQ"));
                addColumnFieldDiff(cp, "isList", col.getIsList(), cs.getIsList(),
                        !Objects.equals(col.getIsList(), 1));
                addColumnFieldDiff(cp, "isForm", col.getIsForm(), cs.getIsForm(),
                        !Objects.equals(col.getIsForm(), 1));
                addColumnFieldDiff(cp, "htmlType", col.getHtmlType(), cs.getHtmlType(),
                        !Objects.equals(col.getHtmlType(), ruleHtml));
                addColumnFieldDiff(cp, "dictType", col.getDictType(), cs.getDictType(),
                        !Objects.equals(col.getDictType(), GenUtils.inferDictType(ruleHtml, col.getColumnName())));
                addColumnFieldDiff(cp, "sort", col.getSort(), cs.getSort(), false);
                if (!cp.getFields().isEmpty()) {
                    tp.getColumns().add(cp);
                }
            }
            if (!tp.getTableFields().isEmpty() || !tp.getColumns().isEmpty()) {
                vo.getTables().add(tp);
            }
        }
        return vo;
    }

    /** 只装有差异的项：建议为空或与现值一致都不进 diff */
    private void addTableFieldDiff(GenAiRecommendPreviewVO.TablePreview tp, String field,
                                   Object current, Object suggested, boolean manual) {
        if (suggested == null || Objects.equals(current, suggested)) {
            return;
        }
        GenAiRecommendPreviewVO.FieldDiff d = new GenAiRecommendPreviewVO.FieldDiff();
        d.setField(field);
        d.setCurrent(current);
        d.setSuggested(suggested);
        d.setManual(manual);
        tp.getTableFields().add(d);
    }

    private void addColumnFieldDiff(GenAiRecommendPreviewVO.ColumnPreview cp, String field,
                                    Object current, Object suggested, boolean manual) {
        if (suggested == null || Objects.equals(current, suggested)) {
            return;
        }
        GenAiRecommendPreviewVO.FieldDiff d = new GenAiRecommendPreviewVO.FieldDiff();
        d.setField(field);
        d.setCurrent(current);
        d.setSuggested(suggested);
        d.setManual(manual);
        cp.getFields().add(d);
    }

    // ==================== 应用（AC-4：勾选才写库） ====================

    @Override
    public int apply(Long pipelineId, GenAiRecommendApplyDTO dto) {
        GenPipeline pipeline = requirePipeline(pipelineId);
        Path artifact = artifactPath(pipeline);
        if (!Files.exists(artifact)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建议产物不存在（ai_recommend 步骤未成功执行）");
        }
        return applyInternal(readArtifact(artifact), pipeline.getProjectId(), dto, pipeline.getPipelineNo());
    }

    /** 应用主体：勾选项写回 gen_table / gen_table_column，未勾选一律不动。两个入口共用。 */
    private int applyInternal(GenAiRecommendVO suggest, Long projectId,
                              GenAiRecommendApplyDTO dto, String label) {
        Map<String, GenAiRecommendVO.TableSuggest> suggestByTable = suggest.getTables().stream()
                .collect(Collectors.toMap(GenAiRecommendVO.TableSuggest::getTableName, Function.identity(), (a, b) -> a));

        Map<String, GenTable> tablesByName = tablesOfProject(projectId);
        Map<Long, Map<String, GenTableColumn>> columnsByTable = columnsOfTables(tablesByName.values());

        int applied = 0;
        for (GenAiRecommendApplyDTO.TablePick pick : dto.getTables()) {
            GenTable table = tablesByName.get(pick.getTableName());
            GenAiRecommendVO.TableSuggest ts = suggestByTable.get(pick.getTableName());
            if (table == null || ts == null) {
                continue; // 表被删或产物里没有，跳过
            }
            boolean tableDirty = false;
            for (String field : pick.getFields()) {
                boolean ok = switch (field) {
                    case "moduleName" -> setIf(table::setModuleName, ts.getModuleName());
                    case "treeParentColumn" -> setIf(table::setTreeParentColumn, ts.getTreeParentColumn());
                    case "treeLabelColumn" -> setIf(table::setTreeLabelColumn, ts.getTreeLabelColumn());
                    case "relationType" -> setIf(table::setRelationType, ts.getRelationType());
                    case "masterTable" -> applyMaster(table, ts, tablesByName);
                    default -> {
                        log.warn("AI 建议应用：忽略未知表级字段 {}", field);
                        yield false;
                    }
                };
                tableDirty |= ok;
                applied += ok ? 1 : 0;
            }
            if (tableDirty) {
                tableMapper.updateById(table);
            }

            Map<String, GenAiRecommendVO.ColumnSuggest> suggestByColumn = ts.getColumns().stream()
                    .collect(Collectors.toMap(GenAiRecommendVO.ColumnSuggest::getColumnName, Function.identity(), (a, b) -> a));
            Map<String, GenTableColumn> cols = columnsByTable.getOrDefault(table.getId(), Map.of());
            for (GenAiRecommendApplyDTO.ColumnPick cp : pick.getColumns()) {
                GenTableColumn col = cols.get(cp.getColumnName());
                GenAiRecommendVO.ColumnSuggest cs = suggestByColumn.get(cp.getColumnName());
                if (col == null || cs == null) {
                    continue;
                }
                boolean dirty = false;
                for (String field : cp.getFields()) {
                    boolean ok = switch (field) {
                        case "isQuery" -> setIf(col::setIsQuery, cs.getIsQuery());
                        case "queryType" -> setIf(col::setQueryType, cs.getQueryType());
                        case "isList" -> setIf(col::setIsList, cs.getIsList());
                        case "isForm" -> setIf(col::setIsForm, cs.getIsForm());
                        case "htmlType" -> setIf(col::setHtmlType, cs.getHtmlType());
                        case "dictType" -> setIf(col::setDictType, cs.getDictType());
                        case "sort" -> setIf(col::setSort, cs.getSort());
                        default -> {
                            log.warn("AI 建议应用：忽略未知列级字段 {}", field);
                            yield false;
                        }
                    };
                    dirty |= ok;
                    applied += ok ? 1 : 0;
                }
                if (dirty) {
                    columnMapper.updateById(col);
                }
            }
        }

        // 菜单勾选：成员表 module_name 归到该模块（目录名/图标为参考，不落库）
        Map<String, GenAiRecommendVO.MenuSuggest> menusByModule = suggest.getMenus().stream()
                .collect(Collectors.toMap(GenAiRecommendVO.MenuSuggest::getModuleName, Function.identity(), (a, b) -> a));
        for (String moduleName : dto.getMenus()) {
            GenAiRecommendVO.MenuSuggest menu = menusByModule.get(moduleName);
            if (menu == null) {
                continue;
            }
            for (String tableName : menu.getTables()) {
                GenTable table = tablesByName.get(tableName);
                if (table != null && !Objects.equals(table.getModuleName(), menu.getModuleName())) {
                    table.setModuleName(menu.getModuleName());
                    tableMapper.updateById(table);
                    applied++;
                }
            }
        }
        log.info("AI 建议应用 {}: {} 个字段写库", label, applied);
        return applied;
    }

    /** 主子建议：按主表名解析 id 并推 masterDepth（主表是孙表则本表再深一级） */
    private boolean applyMaster(GenTable table, GenAiRecommendVO.TableSuggest ts,
                                Map<String, GenTable> tablesByName) {
        GenTable master = ts.getMasterTableName() == null ? null : tablesByName.get(ts.getMasterTableName());
        if (master == null) {
            return false;
        }
        table.setMasterTableId(master.getId());
        int masterDepth = master.getMasterDepth() == null ? 0 : master.getMasterDepth();
        table.setMasterDepth(masterDepth + 1);
        return true;
    }

    /** null 建议不落（未建议/被白名单丢弃的字段保持现值） */
    private <T> boolean setIf(java.util.function.Consumer<T> setter, T suggested) {
        if (suggested == null) {
            return false;
        }
        setter.accept(suggested);
        return true;
    }

    // ==================== 表级入口（V7.3 P0-3） ====================

    /**
     * 表级产物 token：{@code AIT<projectId>-<yyyyMMddHHmmss>-<8位hex>}。
     * 正则先行既做格式校验，也是路径穿越防线（token 直接参与文件名拼接）。
     */
    private static final java.util.regex.Pattern TABLE_TOKEN =
            java.util.regex.Pattern.compile("^AIT(\\d+)-(\\d{14})-([0-9a-f]{8})$");

    /** 同一系统最多保留多少份表级产物，超出按文件名（含时间戳）排序删最旧 */
    private static final int TABLE_ARTIFACT_KEEP = 5;

    @Override
    public GenAiRecommendPreviewVO recommendTables(GenAiRecommendTableDTO dto) {
        if (dto == null || dto.getProjectId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "projectId 不能为空");
        }
        Map<String, GenTable> byName = tablesOfProject(dto.getProjectId());
        if (byName.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "系统下没有表配置，无法推荐");
        }
        List<GenTable> picked;
        if (dto.getTableIds() == null || dto.getTableIds().isEmpty()) {
            picked = byName.values().stream()
                    .sorted(java.util.Comparator.comparing(GenTable::getId)).toList();
        } else {
            Set<Long> wanted = new HashSet<>(dto.getTableIds());
            picked = byName.values().stream()
                    .filter(t -> wanted.contains(t.getId()))
                    .sorted(java.util.Comparator.comparing(GenTable::getId))
                    .toList();
            if (picked.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "选中的表不属于该系统");
            }
        }
        GenAiRecommendVO suggest = doRecommend(picked, false);
        String token = writeTableArtifact(dto.getProjectId(), suggest);
        GenAiRecommendPreviewVO vo = toPreview(suggest, dto.getProjectId());
        vo.setToken(token);
        return vo;
    }

    @Override
    public int applyTables(String token, GenAiRecommendApplyDTO dto) {
        Path artifact = resolveTableArtifact(token);
        if (!Files.exists(artifact)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建议已过期或不存在，请重新推荐");
        }
        return applyInternal(readArtifact(artifact), tableTokenProjectId(token), dto, token);
    }

    String writeTableArtifact(Long projectId, GenAiRecommendVO suggest) {
        String token = "AIT" + projectId + "-"
                + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .format(java.time.LocalDateTime.now())
                + "-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Path file = resolveTableArtifact(token);
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writeValue(file.toFile(), suggest);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 建议产物写入失败：" + e.getMessage());
        }
        pruneTableArtifacts(projectId);
        return token;
    }

    Path resolveTableArtifact(String token) {
        if (token == null || !TABLE_TOKEN.matcher(token).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "AI 建议 token 非法");
        }
        return Path.of(properties.getLogDir(), "ai-recommend", token + ".json");
    }

    Long tableTokenProjectId(String token) {
        java.util.regex.Matcher m = TABLE_TOKEN.matcher(token == null ? "" : token);
        if (!m.matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "AI 建议 token 非法");
        }
        return Long.parseLong(m.group(1));
    }

    /** 清旧产物：目录里按文件名排（带时间戳）保留最新 N 份，失败不影响本次推荐 */
    void pruneTableArtifacts(Long projectId) {
        Path dir = Path.of(properties.getLogDir(), "ai-recommend");
        String prefix = "AIT" + projectId + "-";
        try (var stream = Files.list(dir)) {
            List<Path> mine = stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .toList();
            for (int i = 0; i < mine.size() - TABLE_ARTIFACT_KEEP; i++) {
                Files.deleteIfExists(mine.get(i));
            }
        } catch (Exception e) {
            log.debug("清理表级 AI 产物失败（忽略）: {}", e.getMessage());
        }
    }

    // ==================== 产物与台账 ====================

    private GenPipeline requirePipeline(Long pipelineId) {
        GenPipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流水线不存在：" + pipelineId);
        }
        return pipeline;
    }

    private Path artifactPath(GenPipeline pipeline) {
        return Path.of(properties.getLogDir(), pipeline.getPipelineNo(), "ai_recommend.json");
    }

    private GenAiRecommendVO readArtifact(Path artifact) {
        try {
            return objectMapper.readValue(Files.readString(artifact), GenAiRecommendVO.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 建议产物读取失败：" + e.getMessage());
        }
    }

    private Map<String, GenTable> tablesOfProject(Long projectId) {
        return tableMapper.selectList(new LambdaQueryWrapper<GenTable>()
                        .eq(GenTable::getProjectId, projectId))
                .stream().collect(Collectors.toMap(GenTable::getTableName, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
    }

    private Map<Long, Map<String, GenTableColumn>> columnsOfTables(java.util.Collection<GenTable> tables) {
        if (tables.isEmpty()) {
            return Map.of();
        }
        List<Long> tableIds = tables.stream().map(GenTable::getId).toList();
        Map<Long, Map<String, GenTableColumn>> result = new HashMap<>();
        for (GenTableColumn c : columnMapper.selectList(new LambdaQueryWrapper<GenTableColumn>()
                .in(GenTableColumn::getTableId, tableIds))) {
            result.computeIfAbsent(c.getTableId(), k -> new HashMap<>()).put(c.getColumnName(), c);
        }
        return result;
    }

    // ==================== prompt 组装 ====================

    private String buildTableUserContent(List<GenTable> batch,
                                         Map<Long, List<GenTableColumn>> columnsOf,
                                         String dictHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("平台字典编码：").append(dictHint).append('\n');
        sb.append("表结构：\n[");
        for (int i = 0; i < batch.size(); i++) {
            GenTable t = batch.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"tableName\":\"").append(t.getTableName()).append('"');
            sb.append(",\"comment\":\"").append(escape(t.getTableComment())).append('"');
            sb.append(",\"columns\":[");
            List<GenTableColumn> cols = columnsOf.getOrDefault(t.getId(), List.of());
            for (int j = 0; j < cols.size(); j++) {
                GenTableColumn c = cols.get(j);
                if (j > 0) {
                    sb.append(',');
                }
                sb.append("{\"name\":\"").append(c.getColumnName()).append('"');
                sb.append(",\"comment\":\"").append(escape(c.getColumnComment())).append('"');
                sb.append(",\"type\":\"").append(c.getColumnType()).append('"');
                sb.append(",\"pk\":").append(c.getIsPk() != null && c.getIsPk() == 1);
                sb.append('}');
            }
            sb.append("]}");
        }
        sb.append("]\n为这些表推荐页面配置。/no_think");
        return sb.toString();
    }

    private String buildMenuUserContent(List<GenTable> tables) {
        StringBuilder sb = new StringBuilder("系统表清单：\n");
        for (GenTable t : tables) {
            sb.append("- ").append(t.getTableName());
            if (StringUtils.hasText(t.getTableComment())) {
                sb.append("（").append(t.getTableComment()).append(')');
            }
            sb.append('\n');
        }
        sb.append("组织菜单目录。/no_think");
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    // ==================== AI 调用（与 AiModelServiceImpl 同款骨架） ====================

    private String chatJson(String systemPrompt, String userContent) {
        GenPipelineProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || !StringUtils.hasText(ai.getBaseUrl()) || !StringUtils.hasText(ai.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 推荐未启用（gen.pipeline.ai.enabled=false 或未配置 key）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ai.getModel());
        body.put("temperature", 0.6);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        String resp;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(ai.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, ai.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String detail = response.body() == null ? "" : response.body();
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "AI 请求失败：HTTP " + response.statusCode() + " " + abbreviate(detail, 200));
            }
            resp = response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 推荐请求失败: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 请求失败（超时或网络异常）");
        }
        try {
            JsonNode root = objectMapper.readTree(resp);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("回复为空");
            }
            return extractJson(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 推荐回复提取失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 回复提取失败，可重试");
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("AI 建议 JSON 解析失败（可能截断）: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 建议解析失败（输出截断或非法 JSON）");
        }
    }

    private String extractJson(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("回复中找不到 JSON");
        }
        return s.substring(start, end + 1);
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
