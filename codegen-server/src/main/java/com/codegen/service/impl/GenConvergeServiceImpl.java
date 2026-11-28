package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenConvergeItem;
import com.codegen.entity.GenConvergeReport;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenConvergeItemMapper;
import com.codegen.mapper.GenConvergeReportMapper;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.GenConvergeService;
import com.codegen.service.GenSpecService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converge 规格对账服务实现（V1.30 第一期）
 * <p>
 * 比对规则（先窄后宽，第一版只做三类）：
 * <ul>
 *   <li>TABLE：期望表名集合 vs gen_table（按项目）实际表名集合</li>
 *   <li>FIELD：期望"表名.列名"集合 vs gen_table_column 实际列集合（仅期望表范围内）</li>
 *   <li>ENUM：期望字典编码集合 vs 实际 gen_table_column.dictType 非空集合</li>
 * </ul>
 * missing == 0 且 extra == 0 时报告置 CONVERGED。
 */
@Service
public class GenConvergeServiceImpl implements GenConvergeService {

    private static final Logger log = LoggerFactory.getLogger(GenConvergeServiceImpl.class);

    @Autowired
    private GenSpecService genSpecService;
    @Autowired
    private GenTableMapper genTableMapper;
    @Autowired
    private GenTableColumnMapper genTableColumnMapper;
    @Autowired
    private GenConvergeReportMapper reportMapper;
    @Autowired
    private GenConvergeItemMapper itemMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenConvergeReport run(Long projectId) {
        GenSpecArtifact spec = genSpecService.latestEffectiveSpec(projectId);
        if (spec == null) {
            throw new BusinessException(400, "项目暂无可用 SPEC 工件，请先完成 Step2 模块规格并确认生效");
        }

        // 1) 解析期望清单
        Expectation expect = parseExpectation(spec.getStructuredJson());

        // 2) 采集实际产物（最小列查询：对账只依赖 tableName/columnName/dictType 等比对键，
        //    不做全列 select——gen_table 后续版本加列不会让对账跟着碎）
        List<GenTable> actualTables = genTableMapper.selectList(new LambdaQueryWrapper<GenTable>()
                .select(GenTable::getId, GenTable::getTableName, GenTable::getProjectId)
                .eq(GenTable::getProjectId, projectId));
        Map<String, GenTable> actualTableMap = actualTables.stream()
                .collect(Collectors.toMap(GenTable::getTableName, Function.identity(), (a, b) -> a));

        List<GenTableColumn> actualColumns = actualTables.isEmpty() ? List.of()
                : genTableColumnMapper.selectList(new LambdaQueryWrapper<GenTableColumn>()
                        .select(GenTableColumn::getId, GenTableColumn::getTableId,
                                GenTableColumn::getColumnName, GenTableColumn::getDictType)
                        .in(GenTableColumn::getTableId,
                                actualTables.stream().map(GenTable::getId).toList()));
        Set<String> actualFieldKeys = actualColumns.stream()
                .map(c -> c.getTableId() + "|" + c.getColumnName())
                .collect(Collectors.toSet());
        // 实际枚举：dictType 非空的列
        Set<String> actualEnums = actualColumns.stream()
                .map(GenTableColumn::getDictType)
                .filter(d -> d != null && !d.isBlank())
                .collect(Collectors.toSet());
        // 列名 → 表名 映射（判断实际字段归属）
        Map<Long, String> tableNameById = actualTables.stream()
                .collect(Collectors.toMap(GenTable::getId, GenTable::getTableName, (a, b) -> a));
        Set<String> actualFieldKeysNamed = actualColumns.stream()
                .map(c -> tableNameById.get(c.getTableId()) + "." + c.getColumnName())
                .collect(Collectors.toSet());

        // 3) 三类比对
        List<GenConvergeItem> items = new ArrayList<>();
        compareTables(expect, actualTableMap.keySet(), items);
        compareFields(expect, actualTableMap, actualFieldKeysNamed, items);
        compareEnums(expect, actualEnums, items);

        // 4) 汇总落库
        GenConvergeReport report = new GenConvergeReport();
        report.setProjectId(projectId);
        report.setArtifactId(spec.getId());
        report.setSpecVersion(spec.getVersion());
        int ok = (int) items.stream().filter(i -> GenConvergeItem.KIND_OK.equals(i.getKind())).count();
        int missing = (int) items.stream().filter(i -> GenConvergeItem.KIND_MISSING.equals(i.getKind())).count();
        int extra = (int) items.stream().filter(i -> GenConvergeItem.KIND_EXTRA.equals(i.getKind())).count();
        report.setOkCount(ok);
        report.setMissingCount(missing);
        report.setExtraCount(extra);
        report.setStatus(missing == 0 && extra == 0
                ? GenConvergeReport.STATUS_CONVERGED
                : GenConvergeReport.STATUS_UNCONVERGED);
        reportMapper.insert(report);
        items.forEach(i -> i.setReportId(report.getId()));
        items.forEach(itemMapper::insert);
        log.info("[converge] project={} specV{} ok={} missing={} extra={} status={}",
                projectId, spec.getVersion(), ok, missing, extra, report.getStatus());
        return report;
    }

    @Override
    public List<GenConvergeReport> reports(Long projectId) {
        return reportMapper.selectList(new LambdaQueryWrapper<GenConvergeReport>()
                .eq(GenConvergeReport::getProjectId, projectId)
                .orderByDesc(GenConvergeReport::getCreateTime));
    }

    @Override
    public ReportDetail reportDetail(Long reportId) {
        GenConvergeReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(400, "对账报告不存在: " + reportId);
        }
        List<GenConvergeItem> items = itemMapper.selectList(new LambdaQueryWrapper<GenConvergeItem>()
                .eq(GenConvergeItem::getReportId, reportId));
        items.sort(Comparator
                .comparing((GenConvergeItem i) -> kindOrder(i.getKind()))
                .thenComparing(GenConvergeItem::getItemType)
                .thenComparing(GenConvergeItem::getItemKey));
        return new ReportDetail(report, items);
    }

    // ---------- 比对逻辑 ----------

    private void compareTables(Expectation expect, Set<String> actualTables, List<GenConvergeItem> items) {
        TreeSet<String> all = new TreeSet<>();
        all.addAll(expect.tableNames);
        all.addAll(actualTables);
        for (String t : all) {
            GenConvergeItem item = base(GenConvergeItem.TYPE_TABLE, t);
            if (expect.tableNames.contains(t) && !actualTables.contains(t)) {
                item.setKind(GenConvergeItem.KIND_MISSING);
                item.setSourceRef(expect.tableRefs.getOrDefault(t, "spec"));
                item.setSuggestion("规格已声明该表但未生成，追加建表任务重跑");
            } else if (!expect.tableNames.contains(t)) {
                item.setKind(GenConvergeItem.KIND_EXTRA);
                item.setSuggestion("规格外产物：删除，或补入 spec 后重新 CONFIRM");
            } else {
                item.setKind(GenConvergeItem.KIND_OK);
            }
            items.add(item);
        }
    }

    private void compareFields(Expectation expect, Map<String, GenTable> actualTableMap,
                               Set<String> actualFieldKeysNamed, List<GenConvergeItem> items) {
        Set<String> expectedFieldKeys = new LinkedHashSet<>();
        expect.tableNames.forEach(t -> expect.fields.getOrDefault(t, List.of())
                .forEach(f -> expectedFieldKeys.add(t + "." + f)));
        TreeSet<String> all = new TreeSet<>();
        all.addAll(expectedFieldKeys);
        all.addAll(actualFieldKeysNamed.stream()
                // 实际字段只对账期望表范围内的（规格外整表由 TABLE 类负责）
                .filter(k -> expect.tableNames.contains(k.substring(0, k.indexOf('.'))))
                .toList());
        for (String k : all) {
            GenConvergeItem item = base(GenConvergeItem.TYPE_FIELD, k);
            String tableName = k.substring(0, k.indexOf('.'));
            boolean expected = expectedFieldKeys.contains(k);
            boolean actual = actualFieldKeysNamed.contains(k);
            if (expected && !actual) {
                item.setKind(GenConvergeItem.KIND_MISSING);
                item.setSourceRef(expect.fieldRefs.getOrDefault(k, "spec"));
                item.setSuggestion("规格已声明该字段但表结构缺失，追加字段变更任务");
            } else if (!expected && actual) {
                item.setKind(GenConvergeItem.KIND_EXTRA);
                item.setSuggestion("规格外字段：删除，或补入 spec 后重新 CONFIRM");
            } else {
                item.setKind(GenConvergeItem.KIND_OK);
            }
            items.add(item);
        }
        // 占位防未用告警：actualTableMap 预留给后续按表分组校验
        if (actualTableMap.isEmpty() && !expectedFieldKeys.isEmpty()) {
            log.warn("[converge] 项目无实际表，但规格声明了 {} 个字段", expectedFieldKeys.size());
        }
    }

    private void compareEnums(Expectation expect, Set<String> actualEnums, List<GenConvergeItem> items) {
        TreeSet<String> all = new TreeSet<>();
        all.addAll(expect.enums);
        all.addAll(actualEnums);
        for (String e : all) {
            GenConvergeItem item = base(GenConvergeItem.TYPE_ENUM, e);
            if (expect.enums.contains(e) && !actualEnums.contains(e)) {
                item.setKind(GenConvergeItem.KIND_MISSING);
                item.setSourceRef(expect.enumRefs.getOrDefault(e, "spec §三"));
                item.setSuggestion("规格已声明字典但无字段绑定 dictType，检查字段配置");
            } else if (!expect.enums.contains(e) && !actualEnums.contains(e)) {
                continue; // 理论不可达
            } else if (!expect.enums.contains(e)) {
                item.setKind(GenConvergeItem.KIND_EXTRA);
                item.setSuggestion("规格外字典：确认是否需要补入 spec §三");
            } else {
                item.setKind(GenConvergeItem.KIND_OK);
            }
            items.add(item);
        }
    }

    private GenConvergeItem base(String type, String key) {
        GenConvergeItem item = new GenConvergeItem();
        item.setItemType(type);
        item.setItemKey(key);
        item.setSourceRef("");
        item.setSuggestion("");
        return item;
    }

    private int kindOrder(String kind) {
        return switch (kind) {
            case GenConvergeItem.KIND_MISSING -> 0;
            case GenConvergeItem.KIND_EXTRA -> 1;
            default -> 2;
        };
    }

    // ---------- 期望清单解析 ----------

    private Expectation parseExpectation(String structuredJson) {
        Expectation e = new Expectation();
        if (structuredJson == null || structuredJson.isBlank()) {
            return e;
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            JsonNode tables = root.path("tables");
            if (tables.isArray()) {
                for (JsonNode t : tables) {
                    String name = t.path("name").asText("").trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    e.tableNames.add(name);
                    e.tableRefs.put(name, t.path("sourceRef").asText("spec §二"));
                    JsonNode fields = t.path("fields");
                    if (fields.isArray()) {
                        for (JsonNode f : fields) {
                            String fn = f.asText("").trim();
                            if (!fn.isEmpty()) {
                                e.fields.computeIfAbsent(name, k -> new ArrayList<>()).add(fn);
                                e.fieldRefs.put(name + "." + fn, t.path("sourceRef").asText("spec §二"));
                            }
                        }
                    }
                }
            }
            JsonNode enums = root.path("enums");
            if (enums.isArray()) {
                for (JsonNode en : enums) {
                    String code = en.asText("").trim();
                    if (!code.isEmpty()) {
                        e.enums.add(code);
                        e.enumRefs.put(code, "spec §三");
                    }
                }
            }
        } catch (Exception ex) {
            throw new BusinessException(500, "SPEC 工件 structured_json 解析失败: " + ex.getMessage());
        }
        return e;
    }

    /**
     * 期望清单内存结构
     */
    private static class Expectation {
        final Set<String> tableNames = new LinkedHashSet<>();
        final Map<String, String> tableRefs = new java.util.HashMap<>();
        final Map<String, List<String>> fields = new java.util.HashMap<>();
        final Map<String, String> fieldRefs = new java.util.HashMap<>();
        final Set<String> enums = new LinkedHashSet<>();
        final Map<String, String> enumRefs = new java.util.HashMap<>();
    }
}
