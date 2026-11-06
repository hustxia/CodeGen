package com.codegen.support;

import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.entity.GenPipeline;
import com.codegen.entity.GenTable;
import com.codegen.service.GenDdlService;
import com.codegen.service.GenTableService;
import com.codegen.util.GenTemplateModel;
import com.codegen.vo.GenSeedVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * V5.2 seed 步规则生成引擎（唯一实现）。
 *
 * <p>职责：按台账表集 + information_schema 真实结构，为 0 行业务表生成中文示例数据 INSERT。
 * 纯函数内核（列元数据 → 取值）静态化便于单测；DB 访问只做元数据读取与执行。
 *
 * <p>约束保证：外键拓扑先父后子（成环降级台账顺序）、枚举值取自注释解析
 * （复用 {@link GenTemplateModel#parseEnumOptions}）、单列唯一加序号后缀、
 * 单次插数总上限 {@link #GLOBAL_ROW_CAP} 行（超限表记待补数）。
 */
@Slf4j
@Component
public class GenSeedGenerator {

    /** 单次 seed 插入总行数上限（V5.2 修订记录：防 CPU 机器夜跑超时） */
    public static final int GLOBAL_ROW_CAP = 2000;

    /** 主数据表前缀 → 12 行 */
    private static final int MASTER_ROWS = 12;
    /** 普通表 8 行 */
    private static final int NORMAL_ROWS = 8;
    /** 明细表跟随主表：每父行 2~4 行，封顶 24 */
    private static final int CHILD_ROWS_CAP = 24;

    private final JdbcTemplate jdbcTemplate;
    private final GenDdlService ddlService;
    private final GenTableService tableService;
    private final GenPipelineProperties props;

    public GenSeedGenerator(JdbcTemplate jdbcTemplate, GenDdlService ddlService,
                            GenTableService tableService, GenPipelineProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.ddlService = ddlService;
        this.tableService = tableService;
        this.props = props;
    }

    // ==================== 入口 ====================

    /** 流水线 seed 步调用（规则词库，V5.2 行为基线） */
    public GenSeedVO seedPipeline(GenPipeline pipeline) {
        return seedPipeline(pipeline, SeedValuePools.defaults());
    }

    /**
     * V7.1 AI 增强：pools 为业务贴合的语义值池。
     * 结构约束（外键区间/唯一后缀/枚举/长度）仍由规则引擎保证，AI 只换取值来源。
     */
    public GenSeedVO seedPipeline(GenPipeline pipeline, SeedValuePools pools) {
        return seedFor(pipeline.getProjectId(), pipeline.getDbName(), pipeline.getPipelineNo(), pools);
    }

    /** 独立补数（规则词库） */
    public GenSeedVO seedFor(Long projectId, String schema, String pipelineNo) {
        return seedFor(projectId, schema, pipelineNo, SeedValuePools.defaults());
    }

    /**
     * 独立补数：只处理台账中且目标库里 0 行的表。
     *
     * @param pipelineNo 非 null 时把 seed.sql 落盘到 logs/pipeline/{no}/ 供人工重放
     * @param pools      V7.1 语义值池，null 时回落默认词库
     */
    public GenSeedVO seedFor(Long projectId, String schema, String pipelineNo, SeedValuePools pools) {
        return buildSeed(projectId, schema, pipelineNo, pools, null, true).vo();
    }

    /**
     * V7.3 P0-2：只生成 seed SQL、不连库执行，供手动写盘/下载的 SQL 交付物使用。
     *
     * <p>结构与取值规则复用同一实现，手工出口和流水线出口的 seed 内容不会漂移。
     *
     * @param onlyTables 仅生成这些表（null = 台账全部表）
     */
    public String renderSeedSql(Long projectId, String schema, java.util.Collection<String> onlyTables) {
        return buildSeed(projectId, schema, null, SeedValuePools.defaults(), onlyTables, false).sql();
    }

    private record SeedBuild(GenSeedVO vo, String sql) {
    }

    private SeedBuild buildSeed(Long projectId, String schema, String pipelineNo, SeedValuePools pools,
                                java.util.Collection<String> onlyTables, boolean execute) {
        SeedValuePools vp = pools == null ? SeedValuePools.defaults() : pools;
        Random rnd = new Random();
        List<GenTable> ledger = tableService.lambdaQuery()
                .eq(GenTable::getProjectId, projectId)
                .orderByAsc(GenTable::getId)
                .list();
        Set<String> ledgerNames = new HashSet<>();
        List<String> ordered = new ArrayList<>();
        Map<String, GenTable> ledgerByLower = new HashMap<>();
        for (GenTable t : ledger) {
            String name = t.getTableName() == null ? "" : t.getTableName().trim().toLowerCase();
            if (name.isEmpty() || !ledgerNames.add(name)) {
                continue;
            }
            ledgerByLower.put(name, t);
            ordered.add(name);
        }

        // 只保留目标库真实存在的表
        Set<String> real = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?", String.class, schema));
        Set<String> only = onlyTables == null ? null : onlyTables.stream()
                .map(n -> n == null ? "" : n.trim().toLowerCase())
                .filter(n -> !n.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
        List<String> tables = ordered.stream()
                .filter(real::contains)
                .filter(t -> only == null || only.contains(t))
                .toList();

        // 外键依赖：table -> 指向的父表列表（仅表集内）
        Map<String, List<String>> parents = fkParents(schema, tables);

        GenSeedVO vo = new GenSeedVO();
        List<String> topo = topoOrder(tables, parents);
        for (String t : tables) {
            if (!topo.contains(t)) {
                vo.getWarn().add("外键成环：" + t + "（已降级按台账顺序处理）");
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- seed.sql 由 V5.2 seed 步自动生成，可人工重放（幂等：仅 0 行表）\n");
        sql.append("USE `").append(schema).append("`;\n");

        Map<String, Integer> parentFirstId = new HashMap<>();
        Map<String, Integer> parentRows = new HashMap<>();
        int budget = GLOBAL_ROW_CAP;
        int totalRows = 0;

        for (String table : topo) {
            GenTable lt = ledgerByLower.get(table);
            String comment = lt == null ? "" : (lt.getTableComment() == null ? "" : lt.getTableComment());
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + schema + "`.`" + table + "`", Integer.class);
            if (cnt != null && cnt > 0) {
                vo.getSkipped().add(skip(table, "已有数据 " + cnt + " 行"));
                continue;
            }
            List<ColMeta> cols = loadColumns(schema, table);
            if (cols.isEmpty()) {
                vo.getSkipped().add(skip(table, "无列信息"));
                continue;
            }
            int rows = rowsFor(table, parents.getOrDefault(table, List.of()), parentRows, rnd);
            if (rows > budget) {
                rows = budget;
            }
            if (rows <= 0) {
                vo.getSkipped().add(skip(table, "总行数超上限 " + GLOBAL_ROW_CAP + "，待补数"));
                continue;
            }
            budget -= rows;

            TablePlan plan = buildTablePlan(schema, table, comment, cols,
                    parents.getOrDefault(table, List.of()), parentFirstId, parentRows, rows, rnd, vp);
            if (plan.error != null) {
                vo.getSkipped().add(skip(table, plan.error));
                continue;
            }
            sql.append(plan.sql);
            parentFirstId.put(table, plan.firstId);
            parentRows.put(table, rows);
            vo.setInsertedTables(vo.getInsertedTables() + 1);
            totalRows += rows;
        }

        if (totalRows > 0) {
            if (pipelineNo != null && !pipelineNo.isBlank()) {
                writeFile(pipelineNo, sql.toString());
            }
            if (execute) {
                GenDdlExecuteDTO exec = new GenDdlExecuteDTO();
                exec.setDdl(sql.toString());
                exec.setSchema(schema);
                exec.setAutoImport(false);
                var r = ddlService.execute(exec);
                log.info("seed 执行完成: success={} failed={}", r.getSuccessCount(), r.getFailedCount());
            }
        }

        vo.setTotalRows(totalRows);
        vo.setSummary("共 " + vo.getInsertedTables() + " 表 " + totalRows + " 行"
                + (vo.getSkipped().isEmpty() ? "" : "；跳过 " + vo.getSkipped().size() + " 张"));
        return new SeedBuild(vo, sql.toString());
    }

    // ==================== 行数策略 ====================

    static int rowsFor(String table, List<String> parentTables,
                       Map<String, Integer> parentRows, Random rnd) {
        if (parentTables.isEmpty()) {
            return (table.startsWith("mdm_") || table.startsWith("sys_") || table.startsWith("base_"))
                    ? MASTER_ROWS : NORMAL_ROWS;
        }
        for (String p : parentTables) {
            Integer pr = parentRows.get(p);
            if (pr != null && pr > 0) {
                return Math.min(CHILD_ROWS_CAP, pr * (2 + rnd.nextInt(3)));
            }
        }
        // 父表本轮都被跳过（已有数据）：按普通表处理
        return NORMAL_ROWS;
    }

    // ==================== 外键拓扑 ====================

    private Map<String, List<String>> fkParents(String schema, List<String> tables) {
        Set<String> inSet = new HashSet<>(tables);
        Map<String, List<String>> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT table_name, referenced_table_name FROM information_schema.key_column_usage"
                        + " WHERE table_schema = ? AND referenced_table_name IS NOT NULL",
                rs -> {
                    String t = rs.getString(1).toLowerCase();
                    String p = rs.getString(2).toLowerCase();
                    if (inSet.contains(t) && inSet.contains(p) && !t.equals(p)) {
                        map.computeIfAbsent(t, k -> new ArrayList<>()).add(p);
                    }
                }, schema);
        return map;
    }

    /**
     * Kahn 拓扑排序：被依赖的父表在前。成环的表降级追加到尾部（按入参顺序）并告警。
     * 纯函数，单测覆盖。
     */
    static List<String> topoOrder(List<String> tables, Map<String, List<String>> parents) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (String t : tables) {
            indegree.putIfAbsent(t, 0);
        }
        for (Map.Entry<String, List<String>> e : parents.entrySet()) {
            for (String p : new HashSet<>(e.getValue())) {
                if (indegree.containsKey(p)) {
                    children.computeIfAbsent(p, k -> new ArrayList<>()).add(e.getKey());
                    indegree.merge(e.getKey(), 1, Integer::sum);
                }
            }
        }
        List<String> out = new ArrayList<>();
        List<String> queue = tables.stream().filter(t -> indegree.get(t) == 0).toList();
        while (!queue.isEmpty()) {
            out.addAll(queue);
            List<String> next = new ArrayList<>();
            for (String t : queue) {
                for (String c : children.getOrDefault(t, List.of())) {
                    int d = indegree.merge(c, -1, Integer::sum);
                    if (d == 0) {
                        next.add(c);
                    }
                }
            }
            queue = next;
        }
        List<String> cyclic = tables.stream().filter(t -> !out.contains(t)).toList();
        if (!cyclic.isEmpty()) {
            out.addAll(cyclic);
        }
        return out;
    }

    // ==================== 单表计划 ====================

    record TablePlan(String sql, int firstId, String error) {
    }

    private TablePlan buildTablePlan(String schema, String table, String tableComment,
                                     List<ColMeta> cols, List<String> parentTables,
                                     Map<String, Integer> parentFirstId,
                                     Map<String, Integer> parentRows, int rows, Random rnd,
                                     SeedValuePools pools) {
        // 唯一列判定（information_schema.statistics，单列唯一）
        Map<String, Boolean> uniqueCols = new HashMap<>();
        List<Map<String, Object>> urows = jdbcTemplate.queryForList(
                "SELECT column_name AS col_name, COUNT(*) AS col_cnt"
                        + " FROM information_schema.statistics WHERE table_schema = ? AND table_name = ?"
                        + " AND non_unique = 0 GROUP BY index_name, column_name",
                schema, table);
        for (Map<String, Object> row : urows) {
            if (((Number) row.get("col_cnt")).intValue() == 1) {
                uniqueCols.put(String.valueOf(row.get("col_name")).toLowerCase(), true);
            }
        }

        // 自增主键起点：子表外键值必须落在父表实际会分配的 id 区间
        int firstId = 1;
        Long autoInc = jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Long.class, schema, table);
        if (autoInc != null && autoInc > 0) {
            firstId = autoInc.intValue();
        }

        List<ColMeta> fillCols = cols.stream().filter(GenSeedGenerator::fillable).toList();
        if (fillCols.isEmpty()) {
            return new TablePlan(null, firstId, "无可填充列（全部自增/有默认值）");
        }

        List<String> colNames = fillCols.stream().map(c -> "`" + c.name() + "`").toList();
        List<FkRef> fkRefs = loadFkRefs(schema, table);
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            List<String> values = new ArrayList<>();
            for (ColMeta c : fillCols) {
                values.add(valueFor(c, r, rnd, table, uniqueCols.containsKey(c.name().toLowerCase()), pools));
            }
            // 外键列：指向表集内父表的列用父表实际 id 区间
            for (FkRef fk : fkRefs) {
                if (!parentFirstId.containsKey(fk.parentTable())) {
                    continue;
                }
                Integer first = parentFirstId.get(fk.parentTable());
                Integer pr = parentRows.get(fk.parentTable());
                int idx = first == null ? 0 : Math.min(pr == null ? 0 : pr - 1, r);
                setColumnValue(values, colNames, fk.column(), String.valueOf(first + idx));
            }
            sb.append("INSERT INTO `").append(table).append("` (")
                    .append(String.join(", ", colNames)).append(") VALUES (")
                    .append(String.join(", ", values)).append(");\n");
        }
        return new TablePlan(sb.toString(), firstId, null);
    }

    private void setColumnValue(List<String> values, List<String> colNames, String col, String val) {
        for (int i = 0; i < colNames.size(); i++) {
            if (colNames.get(i).equalsIgnoreCase("`" + col + "`")) {
                values.set(i, val);
                return;
            }
        }
    }

    record FkRef(String column, String parentTable) {
    }

    private List<FkRef> loadFkRefs(String schema, String table) {
        List<FkRef> out = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT column_name, referenced_table_name FROM information_schema.key_column_usage"
                        + " WHERE table_schema = ? AND table_name = ? AND referenced_table_name IS NOT NULL"
                        + " ORDER BY ordinal_position",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        out.add(new FkRef(rs.getString(1), rs.getString(2).toLowerCase())), schema, table);
        return out;
    }
    // ==================== 列元数据 ====================

    record ColMeta(String name, String dataType, String columnType, boolean nullable, boolean hasDefault,
                   boolean autoInc, boolean pk, String comment, int charLen, int numPrecision, int numScale) {
    }

    private List<ColMeta> loadColumns(String schema, String table) {
        List<ColMeta> out = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT column_name, data_type, column_type, is_nullable, column_default, extra,"
                        + " column_key, column_comment, character_maximum_length, numeric_precision, numeric_scale"
                        + " FROM information_schema.columns WHERE table_schema = ? AND table_name = ?"
                        + " ORDER BY ordinal_position",
                rs -> {
                    String extra = rs.getString(6) == null ? "" : rs.getString(6).toLowerCase();
                    out.add(new ColMeta(rs.getString(1), rs.getString(2).toLowerCase(), rs.getString(3),
                            "YES".equalsIgnoreCase(rs.getString(4)), rs.getString(5) != null,
                            extra.contains("auto_increment"), "PRI".equalsIgnoreCase(rs.getString(7)),
                            rs.getString(8),
                            rs.getObject(9) == null ? 0 : rs.getInt(9),
                            rs.getObject(10) == null ? 0 : rs.getInt(10),
                            rs.getObject(11) == null ? 0 : rs.getInt(11)));
                }, schema, table);
        return out;
    }

    /** 自增/有默认值/非自增主键以外的列才参与填充 */
    static boolean fillable(ColMeta c) {
        if (c.autoInc()) {
            return false;
        }
        if (c.pk() && !c.hasDefault()) {
            return true; // 业务主键（如 varchar 编码），按唯一列生成
        }
        return !c.hasDefault();
    }

    // ==================== 取值引擎（纯函数，单测覆盖） ====================
    // V7.1：通用词库迁移到 SeedValuePools.defaults()（与原数组逐字一致，降级零行为差）；
    // AI 增强时换业务值池，本类继续负责枚举/外键/唯一/长度等结构约束

    /** 语义类别（按列名/注释关键词识别） */
    /** 语义类别（按列名/注释关键词识别） */
    enum Semantic {
        NONE, PERSON, COMPANY, ADDR, PHONE, EMAIL, DEPT, PRODUCT, AMOUNT, QTY, REMARK, CODE, TITLE, SPEC
    }

    static Semantic semanticOf(String name, String comment) {
        String n = (name == null ? "" : name.toLowerCase()) + " " + (comment == null ? "" : comment);
        if (n.contains("姓名") || n.contains("联系人") || n.contains("负责人") || n.contains("收货人")
                || n.contains("经办人") || n.contains("患者") || n.contains("医生") || n.contains("领用人")) {
            return Semantic.PERSON;
        }
        if (n.contains("公司") || n.contains("供应商") || n.contains("厂商") || n.contains("企业")
                || n.contains("单位") || n.contains("客户名")) {
            return Semantic.COMPANY;
        }
        if (n.contains("城市") || n.contains("地址") || n.contains("住址")) {
            return Semantic.ADDR;
        }
        if (n.contains("手机") || n.contains("电话") || n.contains("mobile") || n.contains("phone") || n.contains("tel")) {
            return Semantic.PHONE;
        }
        if (n.contains("邮箱") || n.contains("email") || n.contains("mail")) {
            return Semantic.EMAIL;
        }
        if (n.contains("部门") || n.contains("科室")) {
            return Semantic.DEPT;
        }
        if (n.contains("金额") || n.contains("价格") || n.contains("单价") || n.contains("费用")
                || n.contains("amount") || n.contains("price") || n.contains("fee")) {
            return Semantic.AMOUNT;
        }
        if (n.contains("数量") || n.contains("库存") || n.contains("qty") || n.contains("stock")
                || n.contains("人数") || n.contains("天数")) {
            return Semantic.QTY;
        }
        if (n.contains("备注") || n.contains("说明") || n.contains("描述") || n.contains("remark")
                || n.contains("comment") || n.contains("desc")) {
            return Semantic.REMARK;
        }
        if (n.contains("编号") || n.contains("编码") || n.contains("单号") || n.contains("code")
                || n.endsWith("no")) {
            return Semantic.CODE;
        }
        if (n.contains("规格") || n.contains("型号")) {
            return Semantic.SPEC;
        }
        if (n.contains("物料") || n.contains("商品") || n.contains("品名") || n.contains("产品")
                || n.contains("货物") || n.contains("材料") || n.contains("耗材") || n.contains("药品")
                || n.contains("项目名") || n.contains("名称") || n.contains("标题") || n.contains("name")
                || n.contains("title")) {
            return Semantic.PRODUCT;
        }
        return Semantic.NONE;
    }

    static String pick(String[] arr, Random rnd) {
        return arr[rnd.nextInt(arr.length)];
    }

    static String pick(List<String> arr, Random rnd) {
        return arr.get(rnd.nextInt(arr.size()));
    }

    // ==================== V7.1：表元数据（AI 值池提示词素材） ====================

    /** 简要列信息：只给 AI 看名称与注释，不给类型（类型约束由规则引擎负责） */
    public record ColBrief(String name, String comment) {
    }

    public record SeedTableMeta(String table, String tableComment, List<ColBrief> columns) {
    }

    /** 单次提示词允许的最大表数 */
    public static final int META_TABLE_CAP = 60;

    /**
     * 读取 schema 全部基表的表名/注释与列名/注释（上限 {@value #META_TABLE_CAP} 张），
     * 供 AI 生成业务贴合值池用。读失败抛异常由调用方降级。
     */
    public List<SeedTableMeta> loadTableMeta(String schema) {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = ? AND table_type = 'BASE TABLE'"
                        + " ORDER BY table_name LIMIT " + META_TABLE_CAP, String.class, schema);
        Map<String, List<ColBrief>> byTable = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT table_name, column_name, column_comment FROM information_schema.columns"
                        + " WHERE table_schema = ? ORDER BY table_name, ordinal_position",
                rs -> {
                    byTable.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new ColBrief(rs.getString(2), rs.getString(3)));
                }, schema);
        List<SeedTableMeta> out = new ArrayList<>();
        for (String t : tables) {
            String comment = jdbcTemplate.queryForObject(
                    "SELECT table_comment FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                    String.class, schema, t);
            out.add(new SeedTableMeta(t, comment == null ? "" : comment,
                    byTable.getOrDefault(t, List.of())));
        }
        return out;
    }

    static String q(String v) {
        return "'" + v.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    static String valueFor(ColMeta c, int seq, Random rnd, String tableName, boolean unique) {
        return valueFor(c, seq, rnd, tableName, unique, SeedValuePools.defaults());
    }

    /**
     * 单列取值：枚举（注释解析）→ 列级 AI 覆盖（仅文本列）→ 语义词池 → 类型兜底。
     *
     * @param seq     行内序号（0 起），唯一列用它保证不重复
     * @param unique  是否单列唯一
     * @param pools   V7.1 语义值池
     */
    static String valueFor(ColMeta c, int seq, Random rnd, String tableName, boolean unique,
                           SeedValuePools pools) {
        // 1. 枚举：注释「1启用0停用」等（复用模板模型唯一实现）
        List<String[]> opts = GenTemplateModel.parseEnumOptions(c.comment(), c.name(), "String");
        if (!opts.isEmpty()) {
            return q(opts.get(rnd.nextInt(opts.size()))[0]);
        }

        String dt = c.dataType();
        boolean numeric = dt.equals("int") || dt.equals("bigint") || dt.equals("tinyint")
                || dt.equals("smallint") || dt.equals("mediumint") || dt.equals("integer");
        boolean decimal = dt.equals("decimal") || dt.equals("numeric") || dt.equals("float") || dt.equals("double");
        Semantic sem = semanticOf(c.name(), c.comment());

        // 2. 数值语义
        if (numeric) {
            if (sem == Semantic.QTY) {
                return String.valueOf(1 + rnd.nextInt(500));
            }
            if (dt.equals("tinyint") || c.columnType().startsWith("tinyint(1)")) {
                return String.valueOf(rnd.nextInt(2));
            }
            return String.valueOf(1 + rnd.nextInt(100));
        }
        if (decimal) {
            int scale = Math.max(0, c.numScale() <= 0 ? 2 : Math.min(c.numScale(), 4));
            double v = 10 + rnd.nextDouble() * 9890;
            return String.format("%." + scale + "f", v);
        }

        // 3. 时间语义
        switch (dt) {
            case "datetime", "timestamp" -> {
                int day = rnd.nextInt(30), hour = rnd.nextInt(10), min = rnd.nextInt(60);
                return q(String.format("2026-10-%02d %02d:%02d:00", 1 + day, 8 + hour, min));
            }
            case "date" -> {
                return q(String.format("2026-10-%02d", 1 + rnd.nextInt(30)));
            }
            case "time" -> {
                return q(String.format("%02d:%02d:00", 8 + rnd.nextInt(10), rnd.nextInt(60)));
            }
            case "year" -> {
                return q("2026");
            }
            default -> {
            }
        }

        // 4. 原生 enum/set：从 column_type 提取合法值
        if (dt.equals("enum") || dt.equals("set")) {
            String t = c.columnType();
            int a = t.indexOf('('), b = t.lastIndexOf(')');
            if (a > 0 && b > a) {
                String[] parts = t.substring(a + 1, b).split(",");
                String v = parts[rnd.nextInt(parts.length)].trim().replace("'", "");
                if (!v.isEmpty()) {
                    return q(v);
                }
            }
            return q("");
        }

        // 5. 字符串语义
        if (dt.equals("varchar") || dt.equals("char") || dt.equals("text") || dt.equals("tinytext")
                || dt.equals("mediumtext") || dt.equals("longtext")) {
            int max = c.charLen() > 0 ? c.charLen() : 255;
            // 5a. 列级 AI 覆盖：仅对文本列生效，类型安全由本分支保证
            List<String> ov = pools.columnValues(tableName, c.name());
            if (!ov.isEmpty()) {
                // 先按列长预留后缀空间截断，再拼唯一后缀——顺序反了后缀会被截掉（单测抓过）
                String sfx = unique && max > 5 ? "-" + String.format("%04d", seq + 1) : "";
                String b = ov.get(rnd.nextInt(ov.size()));
                if (b.length() > max - sfx.length()) {
                    b = b.substring(0, max - sfx.length());
                }
                return q(b + sfx);
            }
            String base = textFor(sem, c, seq, rnd, tableName, unique, pools);
            // 汉字按 utf8mb4 是 1 字符计（MySQL varchar(N) 是字符数），直接截断
            return q(base.length() > max ? base.substring(0, max) : base);
        }

        // 6. 二进制/json 等兜底
        if (dt.equals("json")) {
            return "'{}'";
        }
        if (dt.equals("bit")) {
            return "b'1'";
        }
        return c.nullable() ? "NULL" : "''";
    }

    private static String textFor(Semantic sem, ColMeta c, int seq, Random rnd, String tableName,
                                  boolean unique) {
        return textFor(sem, c, seq, rnd, tableName, unique, SeedValuePools.defaults());
    }

    private static String textFor(Semantic sem, ColMeta c, int seq, Random rnd, String tableName,
                                  boolean unique, SeedValuePools pools) {
        String suffix = unique ? "-" + String.format("%04d", seq + 1) : "";
        return switch (sem) {
            case PERSON -> pick(pools.getPersons(), rnd) + suffix;
            case COMPANY -> pick(pools.getCompanies(), rnd) + suffix;
            case ADDR -> pick(pools.getCities(), rnd) + pick(pools.getStreets(), rnd)
                    + (1 + rnd.nextInt(200)) + "号" + suffix;
            case PHONE -> "138" + String.format("%08d", 10000000 + rnd.nextInt(90000000)) + (unique ? "-" + (seq + 1) : "");
            case EMAIL -> "user" + String.format("%03d", seq + 1) + "@example.com";
            case DEPT -> pick(pools.getDepts(), rnd) + suffix;
            case PRODUCT -> pick(pools.getProducts(), rnd) + suffix;
            case TITLE -> pick(pools.getTitles(), rnd) + (unique ? "-" + (seq + 1) : "") + "系列" + (unique ? "" : "");
            case SPEC -> pick(pools.getSpecs(), rnd) + "-A" + (1 + rnd.nextInt(9));
            case REMARK -> {
                String r = pick(pools.getRemarks(), rnd);
                yield r.isEmpty() && !unique ? "无" : r + suffix;
            }
            case CODE -> {
                String abbr = tableName.length() > 6 ? tableName.substring(0, 6) : tableName;
                yield (abbr + "2026" + String.format("%05d", seq + 1)).toUpperCase();
            }
            default -> pick(pools.getTitles(), rnd) + "示例" + (seq + 1) + suffix;
        };
    }

    // ==================== 工具 ====================

    private GenSeedVO.SkipItem skip(String table, String reason) {
        GenSeedVO.SkipItem item = new GenSeedVO.SkipItem();
        item.setTable(table);
        item.setReason(reason);
        return item;
    }

    private void writeFile(String pipelineNo, String sql) {
        try {
            Path dir = Paths.get(props.getLogDir(), pipelineNo);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("seed.sql"), sql, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("seed.sql 落盘失败（不阻断）: {}", e.getMessage());
        }
    }
}
