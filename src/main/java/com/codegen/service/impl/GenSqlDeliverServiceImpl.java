package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.config.GenMenuProperties;
import com.codegen.entity.GenPipeline;
import com.codegen.entity.GenTable;
import com.codegen.entity.SysProject;
import com.codegen.mapper.GenPipelineMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.mapper.SysProjectMapper;
import com.codegen.service.GenSqlDeliverService;
import com.codegen.support.GenSeedGenerator;
import com.codegen.vo.GenSqlDeliverVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SQL 交付物统一出口实现（V7.3 P0-2）。
 *
 * <p>命名与落点唯一：目标工程 {@code <projectCode>-server/db/} 下
 * {@code init_tables.sql}（业务表 DDL）、{@code init_seed.sql}（种子数据）、
 * {@code init_menu.sql}（菜单/按钮权限）。覆盖前先备份到
 * {@code <backendRoot>/.gen-backup/<batchNo>/}，与代码写盘同款可回滚语义。
 */
@Slf4j
@Service
public class GenSqlDeliverServiceImpl implements GenSqlDeliverService {

    private static final DateTimeFormatter BATCH_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SysProjectMapper projectMapper;
    private final GenTableMapper tableMapper;
    private final GenPipelineMapper pipelineMapper;
    private final JdbcTemplate jdbcTemplate;
    private final GenSeedGenerator seedGenerator;
    private final GenMenuProperties menuProperties;

    public GenSqlDeliverServiceImpl(SysProjectMapper projectMapper,
                                    GenTableMapper tableMapper,
                                    GenPipelineMapper pipelineMapper,
                                    JdbcTemplate jdbcTemplate,
                                    GenSeedGenerator seedGenerator,
                                    GenMenuProperties menuProperties) {
        this.projectMapper = projectMapper;
        this.tableMapper = tableMapper;
        this.pipelineMapper = pipelineMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.seedGenerator = seedGenerator;
        this.menuProperties = menuProperties;
    }

    @Override
    public List<SqlArtifact> buildProjectArtifacts(Long projectId, List<Long> tableIds) {
        if (projectId == null || tableIds == null || tableIds.isEmpty()) {
            return List.of();
        }
        SysProject project = projectMapper.selectById(projectId);
        if (project == null || !StringUtils.hasText(project.getBackendRoot())) {
            return List.of();
        }
        String schema = resolveSchema(projectId);
        if (!StringUtils.hasText(schema) || !schemaExists(schema)) {
            log.warn("SQL 交付物构建跳过：库 {} 不存在 projectId={}", schema, projectId);
            return List.of();
        }
        Map<String, String> actual = actualTables(schema, selectedTableNames(projectId, tableIds));
        List<SqlArtifact> artifacts = new ArrayList<>();
        if (!actual.isEmpty()) {
            String ddl = renderDdl(schema, new ArrayList<>(actual.values()));
            if (StringUtils.hasText(ddl)) {
                artifacts.add(new SqlArtifact("init_tables.sql", ddl));
            }
        }
        // seed：所选表都已有数据时只产出文件头，仍保证手工出口与流水线出口的文件齐全
        String seed = seedGenerator.renderSeedSql(projectId, schema, actual.values());
        if (StringUtils.hasText(seed)) {
            artifacts.add(new SqlArtifact("init_seed.sql", seed));
        }
        Path menuSql = latestMenuSql(Paths.get(project.getBackendRoot(), menuProperties.getArtifactDir()));
        if (menuSql != null) {
            try {
                String content = Files.readString(menuSql, StandardCharsets.UTF_8);
                if (StringUtils.hasText(content)) {
                    artifacts.add(new SqlArtifact("init_menu.sql", content));
                }
            } catch (Exception e) {
                log.warn("menu.sql 读取失败 {}: {}", menuSql, e.getMessage());
            }
        }
        return artifacts;
    }

    @Override
    public GenSqlDeliverVO deliver(Long projectId, String batchNo, List<SqlArtifact> artifacts) {
        GenSqlDeliverVO vo = new GenSqlDeliverVO();
        vo.setProjectId(projectId);
        SysProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || !StringUtils.hasText(project.getBackendRoot())
                || !StringUtils.hasText(project.getProjectCode())) {
            vo.setErrors(1);
            vo.getItems().add(item("", "ERROR", null, 0, "系统台账不存在或 backendRoot/projectCode 未配置"));
            return vo;
        }
        Path dir = dbDir(projectId);
        vo.setDbDir(dir == null ? null : dir.toString());
        if (artifacts == null || artifacts.isEmpty()) {
            return vo;
        }
        String safeBatch = safeBatchNo(batchNo);
        for (SqlArtifact artifact : artifacts) {
            String fileName = artifact == null || artifact.fileName() == null ? ""
                    : Paths.get(artifact.fileName()).getFileName().toString();
            String content = artifact == null ? null : artifact.content();
            if (!StringUtils.hasText(fileName) || !StringUtils.hasText(content)) {
                vo.getItems().add(item(fileName, "SKIPPED", null, 0, "内容为空"));
                continue;
            }
            Path target = dir.resolve(fileName);
            try {
                Files.createDirectories(dir);
                backupIfExists(project, safeBatch, target, fileName);
                Files.writeString(target, content, StandardCharsets.UTF_8);
                long bytes = content.getBytes(StandardCharsets.UTF_8).length;
                vo.getItems().add(item(fileName, "WRITTEN", target.toString(), bytes, null));
                vo.setWritten(vo.getWritten() + 1);
            } catch (Exception e) {
                vo.setErrors(vo.getErrors() + 1);
                vo.getItems().add(item(fileName, "ERROR", target.toString(), 0, e.getMessage()));
                log.warn("SQL 交付物落盘失败 {} -> {}: {}", fileName, target, e.getMessage());
            }
        }
        return vo;
    }

    @Override
    public Path dbDir(Long projectId) {
        SysProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || !StringUtils.hasText(project.getBackendRoot())
                || !StringUtils.hasText(project.getProjectCode())) {
            return null;
        }
        return Paths.get(project.getBackendRoot(), project.getProjectCode() + "-server", "db")
                .toAbsolutePath().normalize();
    }

    // ==================== 内容构建 ====================

    /**
     * 目标库：优先用所属系统最近一次流水线的库名；没有流水线时回落当前连接库。
     */
    private String resolveSchema(Long projectId) {
        GenPipeline latest = pipelineMapper.selectOne(new LambdaQueryWrapper<GenPipeline>()
                .eq(GenPipeline::getProjectId, projectId)
                .orderByDesc(GenPipeline::getId)
                .last("LIMIT 1"));
        if (latest != null && StringUtils.hasText(latest.getDbName())) {
            return latest.getDbName().trim();
        }
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean schemaExists(String schema) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?", Integer.class, schema);
        return count != null && count > 0;
    }

    /**
     * 本次勾选且在目标库真实存在的表：小写名 -> 库里的实际表名（MySQL 表名大小写敏感度因平台而异）。
     */
    private Map<String, String> actualTables(String schema, List<String> selected) {
        Map<String, String> actual = new LinkedHashMap<>();
        if (selected.isEmpty()) {
            return actual;
        }
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = ? AND table_type = 'BASE TABLE'",
                String.class, schema);
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String name : existing) {
            byLower.put(name.toLowerCase(Locale.ROOT), name);
        }
        for (String name : selected) {
            String hit = byLower.get(name.toLowerCase(Locale.ROOT));
            if (hit != null) {
                actual.put(name.toLowerCase(Locale.ROOT), hit);
            }
        }
        return actual;
    }

    private List<String> selectedTableNames(Long projectId, List<Long> tableIds) {
        List<GenTable> tables = tableMapper.selectList(new LambdaQueryWrapper<GenTable>()
                .eq(GenTable::getProjectId, projectId)
                .in(GenTable::getId, tableIds));
        List<String> names = new ArrayList<>();
        for (GenTable table : tables) {
            if (StringUtils.hasText(table.getTableName())) {
                names.add(table.getTableName().trim());
            }
        }
        return names;
    }

    /**
     * 按当前库结构导出 DDL：SHOW CREATE TABLE 的原文，保证「手工出口」与流水线交付物是同一套表。
     */
    private String renderDdl(String schema, List<String> tables) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- init_tables.sql 由代码生成器 V7.3 自动生成（源库：").append(schema).append("）\n");
        sql.append("SET NAMES utf8mb4;\n");
        sql.append("CREATE DATABASE IF NOT EXISTS `").append(schema)
                .append("` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n");
        sql.append("USE `").append(schema).append("`;\n\n");
        for (String table : tables) {
            try {
                String create = jdbcTemplate.queryForObject(
                        "SHOW CREATE TABLE `" + schema + "`.`" + table + "`",
                        (rs, rowNum) -> rs.getString(2));
                if (StringUtils.hasText(create)) {
                    sql.append(create).append(";\n\n");
                }
            } catch (Exception e) {
                log.warn("导出表 DDL 失败 {}.{}: {}", schema, table, e.getMessage());
            }
        }
        return sql.toString();
    }

    /**
     * 最新时间戳目录里的 menu.sql（目录名 yyyyMMddHHmmss，字典序即时间序）。
     */
    private Path latestMenuSql(Path artifactRoot) {
        if (!Files.isDirectory(artifactRoot)) {
            return null;
        }
        try (var dirs = Files.list(artifactRoot)) {
            return dirs.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .map(d -> d.resolve("menu.sql"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("menu.sql 检索失败 {}: {}", artifactRoot, e.getMessage());
            return null;
        }
    }

    // ==================== 落盘辅助 ====================

    /**
     * 覆盖前备份到 {@code <backendRoot>/.gen-backup/<batchNo>/}，与代码写盘同款路径语义。
     */
    private void backupIfExists(SysProject project, String batchNo, Path target, String fileName) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        Path root = Paths.get(project.getBackendRoot()).toAbsolutePath().normalize();
        Path backupDir = root.resolve(".gen-backup").resolve(batchNo)
                .resolve(project.getProjectCode() + "-server").resolve("db");
        Files.createDirectories(backupDir);
        Files.copy(target, backupDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
    }

    static String safeBatchNo(String batchNo) {
        // 只放行字母/数字/下划线/中划线：批次号会拼进备份路径，点号必须剔除（防 ".." 路径穿越）
        return StringUtils.hasText(batchNo)
                ? batchNo.replaceAll("[^A-Za-z0-9_-]", "_")
                : "SQL" + LocalDateTime.now().format(BATCH_TS);
    }

    private static GenSqlDeliverVO.Item item(String fileName, String status, String path, long bytes, String message) {
        GenSqlDeliverVO.Item item = new GenSqlDeliverVO.Item();
        item.setFileName(fileName);
        item.setStatus(status);
        item.setPath(path);
        item.setBytes(bytes);
        item.setMessage(message);
        return item;
    }
}
