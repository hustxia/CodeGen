package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.dto.GenDdlImportDTO;
import com.codegen.dto.GenColumnDTO;
import com.codegen.dto.GenTableSaveDTO;
import com.codegen.service.GenDdlService;
import com.codegen.service.DictDetectService;
import com.codegen.service.DictService;
import com.codegen.service.GenTableService;
import com.codegen.support.GenDdlPatcher;
import com.codegen.util.GenUtils;
import com.codegen.vo.DictDetectItemVO;
import com.codegen.vo.GenDdlExecuteVO;
import com.codegen.vo.GenDdlImportVO;
import com.codegen.vo.GenImportVO;
import com.codegen.vo.GenTableVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 建库建表一键导入。
 *
 * <p>痛点：demo-target 的 4 张表此前是手工在客户端里执行 DDL 再回平台导入配置，
 * 两步之间容易忘、也容易执行到错的库。
 *
 * <p>安全策略：
 * <ul>
 *   <li>默认拦截 DROP / TRUNCATE / ALTER ... DROP，需要 allowDestructive=true 显式放行；</li>
 *   <li>支持 dryRun 先看将要执行什么；</li>
 *   <li>逐条执行、逐条回报，单条失败不影响已成功部分（DDL 在 MySQL 里无法回滚，只能如实报告）。</li>
 * </ul>
 */
@Slf4j
@Service
public class GenDdlServiceImpl implements GenDdlService {

    /** CREATE TABLE [IF NOT EXISTS] `db`.`name` / name */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`?([\\w$]+)`?\\s*\\.\\s*)?`?([\\w$]+)`?",
            Pattern.CASE_INSENSITIVE);

    /** CREATE DATABASE / SCHEMA [IF NOT EXISTS] name */
    private static final Pattern CREATE_DB = Pattern.compile(
            "CREATE\\s+(?:DATABASE|SCHEMA)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([\\w$]+)`?",
            Pattern.CASE_INSENSITIVE);

    /** USE name */
    private static final Pattern USE_DB = Pattern.compile(
            "USE\\s+`?([\\w$]+)`?", Pattern.CASE_INSENSITIVE);

    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "^\\s*(DROP\\s+(TABLE|DATABASE|SCHEMA|INDEX)|TRUNCATE\\s+TABLE|TRUNCATE\\s+|ALTER\\s+TABLE\\s+.*\\s+DROP\\s+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 这些系统库一律不允许执行破坏性语句 */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenTableService genTableService;

    @Autowired
    private DictDetectService dictDetectService;

    @Autowired
    private DictService dictService;

    @Override
    public List<String> splitStatements(String ddl) {
        return split(ddl);
    }

    /**
     * 按分号切分，但跳过：单引号、双引号、反引号内部的分号；-- 与 # 行注释；/* *&#47; 块注释。
     * 这样 {@code COMMENT 'a;b'} 不会被截断。
     *
     * <p>同时剥离 {@code DELIMITER} 指令：它是 mysql 客户端语法，JDBC 直接执行会报语法错误。
     * 遇到 DELIMITER 时只改变后续切分符，不把该行当成语句。
     */
    static List<String> split(String ddl) {
        List<String> out = new ArrayList<>();
        if (ddl == null || ddl.isBlank()) {
            return out;
        }
        ddl = stripDelimiterDirectives(ddl);
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        int n = ddl.length();
        for (int i = 0; i < n; i++) {
            char c = ddl.charAt(i);
            char next = i + 1 < n ? ddl.charAt(i + 1) : 0;

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    cur.append(c);
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && quote != '`' && i + 1 < n) {
                    // 转义字符，连同下一个字符一起吞掉
                    cur.append(ddl.charAt(++i));
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '#') {
                lineComment = true;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                cur.append(c);
                continue;
            }
            if (c == ';') {
                String stmt = cur.toString().trim();
                if (!stmt.isEmpty()) {
                    out.add(stmt);
                }
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    /**
     * 去掉 DELIMITER 行与结尾的 $$。
     *
     * <p>很多人写迁移脚本习惯用存储过程做幂等判断，脚本里带 {@code DELIMITER $$}。
     * 这份东西只有 mysql 命令行认，JDBC 会当成普通 SQL 报语法错误，
     * 所以这里把指令行删掉、把 {@code $$} 统一换回 {@code ;}。
     */
    static String stripDelimiterDirectives(String ddl) {
        if (ddl == null || ddl.isEmpty()) {
            return ddl;
        }
        // 1. 删除 DELIMITER xxx 整行
        String cleaned = ddl.replaceAll("(?im)^\\s*DELIMITER\\s+\\S+\\s*$", "");
        // 2. 行尾的自定义结束符还原成普通分号（只匹配行尾，避免误伤字符串里的 $$）
        cleaned = cleaned.replaceAll("(?m)\\$\\$\\s*$", ";");
        return cleaned;
    }

    /**
     * 幂等类错误：列/索引/表已存在、重复键。
     * 迁移脚本重复执行时这些错误属于「预期内」，按跳过处理而不是失败。
     */
    static boolean isIdempotentError(Throwable e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        // MySQL 1060 Duplicate column / 1061 Duplicate key name / 1050 Table already exists
        return msg.contains("duplicate column") || msg.contains("duplicate key name")
                || msg.contains("already exists") || msg.contains("duplicate entry");
    }

    @Override
    public GenDdlExecuteVO execute(GenDdlExecuteDTO dto) {
        List<String> statements = split(dto.getDdl());
        if (statements.isEmpty()) {
            throw new BusinessException(400, "DDL 为空或无法解析出有效语句");
        }

        GenDdlExecuteVO vo = new GenDdlExecuteVO();
        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun());
        boolean allowDestructive = Boolean.TRUE.equals(dto.getAllowDestructive());
        vo.setDryRun(dryRun);

        // 1. 先确定目标库：入参 > DDL 里的 USE / CREATE DATABASE > 当前连接库
        String schema = firstNonBlank(dto.getSchema(), detectSchema(statements), currentSchema());
        validateSchema(schema);
        vo.setSchema(schema);

        // 目标库预检（V5.1.4 事故）：库不存在且 DDL 也不建库时必须拒绝执行——
        // 否则下面逐条语句前的 USE 静默失败（catch ignored）会回落到连接当前库，
        // 把业务表全部误建进当前连接所在的库（实测 43 张表建进了平台库 gen_platform）
        if (!dryRun && !GenDdlPatcher.createsDatabase(dto.getDdl())) {
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?",
                    Integer.class, schema);
            if (dbCount == null || dbCount == 0) {
                throw new BusinessException(400, "目标库 `" + schema + "` 不存在，且 DDL 未包含 CREATE DATABASE 语句，已拒绝执行"
                        + "（防止 USE 回落把表误建进其他库）。请在 DDL 开头补 CREATE DATABASE，或先确认库名拼写");
            }
        }

        // 2. 逐条执行 —— 必须单连接：HikariCP 每次借还连接都会把 catalog 重置回默认库，
        //    且 Connector/J 的 setCatalog 在本环境下静默无效（实测建表落错库）；
        //    同一条连接上用 USE 切库是唯一可靠做法，整段脚本一起执行完再归还。
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            // 记住原库：执行完必须切回去，否则连接带着目标库回池（Hikari 感知不到裸 USE），
            // 后续请求复用到该连接就会打错库（实测登录报 Unknown database）
            String originalDb = null;
            try (Statement st0 = conn.createStatement();
                 java.sql.ResultSet rs = st0.executeQuery("SELECT DATABASE()")) {
                if (rs.next()) {
                    originalDb = rs.getString(1);
                }
            }
            try {
                try (Statement st = conn.createStatement()) {
                int idx = 0;
                for (String stmt : statements) {
                    idx++;
                    String summary = summarize(stmt);
                    if (isDestructive(stmt) && !allowDestructive) {
                        vo.getStatements().add(new GenDdlExecuteVO.Statement(idx, summary, "BLOCKED",
                                "危险语句已拦截（DROP/TRUNCATE/ALTER DROP）。确认无误请勾选「允许破坏性语句」后重试"));
                        vo.setBlockedCount(vo.getBlockedCount() + 1);
                        continue;
                    }
                    if (dryRun) {
                        vo.getStatements().add(new GenDdlExecuteVO.Statement(idx, summary, "DRY", null));
                        vo.setSuccessCount(vo.getSuccessCount() + 1);
                        continue;
                    }
                    // 每条语句前切库：目标库可能由本脚本前一条 CREATE DATABASE 刚创建，
                    // 库未建时 USE 失败不致命（回落当前库执行，下一条语句前会再切）
                    try {
                        st.execute("USE `" + schema + "`");
                    } catch (Exception ignored) {
                        // ignore
                    }
                    try {
                        st.execute(stmt);
                        vo.getStatements().add(new GenDdlExecuteVO.Statement(idx, summary, "OK", null));
                        vo.setSuccessCount(vo.getSuccessCount() + 1);
                    } catch (Exception e) {
                        String msg = rootMessage(e);
                        if (isIdempotentError(e)) {
                            // 迁移脚本重复执行：列/表已存在属于预期内，不算失败
                            vo.getStatements().add(new GenDdlExecuteVO.Statement(idx, summary, "SKIPPED",
                                    "已存在，跳过：" + msg));
                            vo.setSkippedCount(vo.getSkippedCount() + 1);
                        } else {
                            vo.getStatements().add(new GenDdlExecuteVO.Statement(idx, summary, "FAILED", msg));
                            vo.setFailedCount(vo.getFailedCount() + 1);
                            log.warn("DDL 执行失败 [{}]: {} - {}", idx, summary, msg);
                        }
                    }
                }
                }
            } finally {
                // 归还前切回原库，别让带毒连接回池
                if (originalDb != null && !originalDb.isBlank()) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("USE `" + originalDb + "`");
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }
            return null;
        });

        // 3. 收集 DDL 里出现的表名
        Set<String> tables = new LinkedHashSet<>();
        for (String stmt : statements) {
            Matcher m = CREATE_TABLE.matcher(stmt);
            while (m.find()) {
                tables.add(m.group(2));
            }
        }
        vo.getCreatedTables().addAll(tables);

        // 4. 自动导入配置（仅在真正执行、且至少成功一条时）
        if (!dryRun && Boolean.TRUE.equals(dto.getAutoImport()) && !tables.isEmpty()) {
            try {
                List<GenImportVO> imported = genTableService.importTables(schema,
                        new ArrayList<>(tables), dto.getProjectId());
                for (GenImportVO r : imported) {
                    if ("SAVED".equals(r.getStatus())) {
                        vo.getImportedTables().add(r.getTableName());
                    }
                }
            } catch (Exception e) {
                vo.getStatements().add(new GenDdlExecuteVO.Statement(0, "自动导入表配置", "FAILED",
                        rootMessage(e)));
            }
        }
        return vo;
    }

    /**
     * DDL 文本一键导入：不执行 DDL，直接从建表语句解析表/列/注释注册生成配置。
     *
     * <p>与 {@link #execute} 的 autoImport（执行到库后从 information_schema 回读）互补：
     * 这里走 {@link GenUtils#parseDdl} 纯文本解析，DDL 尚未落到目标库也能先把
     * 表配置和字典准备好。逐表回报、单表失败不影响其他表；已注册的表跳过并标 EXISTS。
     *
     * <p>「确保字典信息正确导入」的双重校验：
     * <ol>
     *   <li>回读落库列数，与 DDL 解析列数比对；</li>
     *   <li>导入前从同一份 DDL 识别字典候选，导入后回读各候选列的 dict_type 是否已回填，
     *       未回填的列入 dictMissingColumns 预警。</li>
     * </ol>
     */
    @Override
    public List<GenDdlImportVO> importFromDdl(GenDdlImportDTO dto) {
        List<String> statements = split(dto.getDdl());
        boolean applyDict = !Boolean.FALSE.equals(dto.getApplyDict());
        List<GenDdlImportVO> report = new ArrayList<>();
        for (String stmt : statements) {
            Matcher m = CREATE_TABLE.matcher(stmt);
            if (!m.find()) {
                continue; // 忽略 SET / USE / CREATE DATABASE 等非建表语句
            }
            report.add(importOneFromDdl(m.group(2), stmt, dto.getProjectId(), applyDict));
        }
        if (report.isEmpty()) {
            throw new BusinessException(400, "DDL 中未解析到 CREATE TABLE 语句");
        }
        return report;
    }

    private GenDdlImportVO importOneFromDdl(String tableName, String stmt, Long projectId, boolean applyDict) {
        GenDdlImportVO vo = new GenDdlImportVO();
        vo.setTableName(tableName);
        try {
            if (genTableService.existsByTableName(tableName)) {
                vo.setStatus("EXISTS");
                vo.setMessage("表已注册过生成配置，跳过");
                return vo;
            }
            GenTableSaveDTO parsed = GenUtils.parseDdl(stmt);
            parsed.setTableName(tableName);
            // 与 DB 导入 importOne 对齐：包名/路径留空，走「表级 → 系统 → 全局」回落链
            parsed.setPackageName("");
            parsed.setBackendPath("");
            parsed.setGatewayPrefix("");
            parsed.setBackendRoot("");
            parsed.setFrontendRoot("");
            parsed.setProjectId(projectId);
            parsed.setOverwrite(false);

            // 导入前快照：缺列注释清单、表注释是否缺失。
            // parseColumn 对缺注释的列会回落填充列名，因此「注释 == 列名」即视为缺失
            // （列名是 ASCII 标识符，真实注释恰好等于列名的概率可忽略）。
            List<String> missingComments = new ArrayList<>();
            for (GenColumnDTO c : parsed.getColumns()) {
                if (!StringUtils.hasText(c.getColumnComment())
                        || c.getColumnComment().equals(c.getColumnName())) {
                    missingComments.add(c.getColumnName());
                }
            }
            boolean noTableComment = !StringUtils.hasText(parsed.getTableComment())
                    || parsed.getTableComment().equals(tableName);
            vo.setTableComment(parsed.getTableComment());

            // 字典候选：与落库用同一份 DDL 文本识别，供导入后回读比对
            List<DictDetectItemVO> candidates = applyDict
                    ? dictDetectService.detectFromDdl(tableName, stmt)
                    : List.of();

            Long tableId = genTableService.saveConfig(parsed);

            int dictCount = 0;
            if (applyDict && !candidates.isEmpty()) {
                try {
                    dictCount = dictDetectService.apply(tableId, null);
                } catch (Exception e) {
                    vo.getWarnings().add("自动建字典失败（已跳过）：" + rootMessage(e));
                }
            }

            // ==== 正确性校验：回读落库结果，与 DDL 逐项比对 ====
            GenTableVO saved = genTableService.detail(tableId);
            List<GenColumnDTO> savedCols = saved.getColumns() == null ? List.of() : saved.getColumns();
            vo.setColumnCount(savedCols.size());
            if (savedCols.size() != parsed.getColumns().size()) {
                vo.getWarnings().add("落库列数(" + savedCols.size() + ") 与 DDL 解析列数("
                        + parsed.getColumns().size() + ") 不一致，请检查表配置");
            }
            if (!StringUtils.hasText(saved.getTableComment())) {
                vo.getWarnings().add("落库后表注释为空");
            }
            vo.setMissingCommentColumns(missingComments);
            if (!missingComments.isEmpty()) {
                vo.getWarnings().add(missingComments.size() + " 个字段缺列注释："
                        + String.join(", ", missingComments));
            }
            if (noTableComment) {
                vo.getWarnings().add("表缺少 COMMENT 注释");
            }

            // 字典回填校验：候选列的 dict_type 是否都已落库
            java.util.Map<String, String> colDictType = new java.util.HashMap<>();
            for (GenColumnDTO c : savedCols) {
                colDictType.put(c.getColumnName(), c.getDictType());
            }
            List<String> dictMissing = new ArrayList<>();
            for (DictDetectItemVO it : candidates) {
                if (!StringUtils.hasText(colDictType.get(it.getColumnName()))) {
                    dictMissing.add(it.getColumnName());
                }
            }
            vo.setDictCount(dictCount);
            vo.setDictMissingColumns(dictMissing);
            if (!dictMissing.isEmpty()) {
                vo.getWarnings().add("疑似枚举但字典未落库：" + String.join(", ", dictMissing)
                        + "，请到字典识别里手工确认");
            }

            // 空字典绑定校验：*_status 这类列会被 inferDictType 预设绑定 sys_status 等通用字典，
            // 但预设不会创建字典项——绑了个空字典，生成的下拉框就是空的，注释里的真实枚举也被跳过。
            // 这里把「绑定了字典但字典没有任何选项」的列点名列出，确保字典信息真正可用。
            java.util.Set<String> boundDicts = new java.util.LinkedHashSet<>();
            for (GenColumnDTO c : savedCols) {
                if (StringUtils.hasText(c.getDictType())) {
                    boundDicts.add(c.getDictType());
                }
            }
            List<String> emptyDictColumns = new ArrayList<>();
            for (String code : boundDicts) {
                boolean hasItems;
                try {
                    hasItems = !dictService.listData(code).isEmpty();
                } catch (Exception e) {
                    hasItems = true; // 查询失败不误报，交给其它校验兜底
                }
                if (!hasItems) {
                    for (GenColumnDTO c : savedCols) {
                        if (code.equals(c.getDictType())) {
                            emptyDictColumns.add(c.getColumnName());
                        }
                    }
                }
            }
            if (!emptyDictColumns.isEmpty()) {
                vo.getWarnings().add("以下列绑定的字典没有任何选项（注释枚举可能未被采纳）："
                        + String.join(", ", emptyDictColumns) + "，请到字典管理里补齐或改绑专属字典");
            }

            vo.setStatus("SAVED");
            vo.setMessage("导入成功" + (dictCount > 0 ? "，自动识别 " + dictCount + " 个字典" : ""));
        } catch (Exception e) {
            vo.setStatus("FAILED");
            vo.setMessage(rootMessage(e));
            log.warn("DDL 导入表 {} 失败：{}", tableName, rootMessage(e));
        }
        return vo;
    }

    private String detectSchema(List<String> statements) {
        for (String s : statements) {
            Matcher use = USE_DB.matcher(s);
            if (use.find()) {
                return use.group(1);
            }
            Matcher create = CREATE_DB.matcher(s);
            if (create.find()) {
                return create.group(1);
            }
        }
        return null;
    }

    private String currentSchema() {
        try {
            return jdbcTemplate.execute((ConnectionCallback<String>) Connection::getCatalog);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDestructive(String sql) {
        return DESTRUCTIVE.matcher(sql).find();
    }

    private void validateSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new BusinessException(400, "无法确定目标库，请显式指定 schema");
        }
        if (!schema.matches("[A-Za-z0-9_$]+")) {
            throw new BusinessException(400, "非法库名：" + schema);
        }
        if (SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, "禁止操作系统库：" + schema);
        }
    }

    private String summarize(String sql) {
        String one = sql.replaceAll("\\s+", " ").trim();
        return one.length() > 160 ? one.substring(0, 160) + " ..." : one;
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
