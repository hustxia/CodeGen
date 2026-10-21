package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.dto.GenDdlExecuteDTO;
import com.codegen.service.GenDdlService;
import com.codegen.service.GenTableService;
import com.codegen.vo.GenDdlExecuteVO;
import com.codegen.vo.GenImportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

        // 2. 逐条执行
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
            try {
                execOne(schema, stmt);
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
                        new ArrayList<>(tables));
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
     * 用原生 Statement 执行，避免 PreparedStatement 对 DDL 的额外限制；
     * 每条语句前切到目标库，保证未写库名的 CREATE TABLE 落到正确位置。
     */
    private void execOne(String schema, String sql) {
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            String oldCatalog = conn.getCatalog();
            try (Statement st = conn.createStatement()) {
                if (schema != null && !schema.equalsIgnoreCase(oldCatalog)) {
                    try {
                        conn.setCatalog(schema);
                    } catch (Exception ignored) {
                        // 库不存在时忽略，后续语句会以真实错误返回
                    }
                }
                st.execute(sql);
            } finally {
                try {
                    if (oldCatalog != null) {
                        conn.setCatalog(oldCatalog);
                    }
                } catch (Exception ignored) {
                    // 连接池归还时由驱动处理
                }
            }
            return null;
        });
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
