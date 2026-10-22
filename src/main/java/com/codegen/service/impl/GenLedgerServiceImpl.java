package com.codegen.service.impl;

import com.codegen.config.GenOutputProperties;
import com.codegen.service.GenDriftService;
import com.codegen.service.GenLedgerService;
import com.codegen.vo.GenLedgerHealthVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 台账巡检实现（V4.2 P1）
 */
@Service
public class GenLedgerServiceImpl implements GenLedgerService {

    private static final Logger log = LoggerFactory.getLogger(GenLedgerServiceImpl.class);

    private static final String BACKUP_DIR = ".gen-backup";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenDriftService genDriftService;

    @Autowired
    private GenOutputProperties outputProperties;

    @Override
    public GenLedgerHealthVO health() {
        GenLedgerHealthVO vo = new GenLedgerHealthVO();
        Map<String, GenDriftService.LedgerSnapshot> latest = genDriftService.latestByPath();
        vo.setUniquePaths(latest.size());

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gen_output WHERE deleted = 0", Integer.class);
        vo.setTotalRecords(total == null ? 0 : total);

        // 1. 孤儿记录：台账有、磁盘无（复用漂移判定的同一份「最新快照」，
        //    否则巡检和漂移页会对同一个文件给出两种结论）
        for (Map.Entry<String, GenDriftService.LedgerSnapshot> entry : latest.entrySet()) {
            if (Files.exists(Paths.get(entry.getKey()))) {
                continue;
            }
            GenDriftService.LedgerSnapshot snap = entry.getValue();
            vo.getOrphan().add(issue(snap.batchNo(), snap.tableName(), snap.relPath(),
                    entry.getKey(), "台账有记录，磁盘上已不存在"));
        }
        vo.setOrphanCount(vo.getOrphan().size());

        // 2. 备份缺失：覆盖类记录承诺了备份，备份不在 = 这条记录实际不可回滚
        List<Map<String, Object>> backups = jdbcTemplate.queryForList(
                "SELECT batch_no, table_name, rel_path, abs_path, backup_path FROM gen_output "
                        + "WHERE deleted = 0 AND backup_path IS NOT NULL AND backup_path <> ''");
        List<GenLedgerHealthVO.Issue> backupMissing = new ArrayList<>();
        for (Map<String, Object> row : backups) {
            String backupPath = str(row.get("backup_path"));
            if (!StringUtils.hasText(backupPath) || Files.exists(Paths.get(backupPath))) {
                continue;
            }
            backupMissing.add(issue(str(row.get("batch_no")), str(row.get("table_name")),
                    str(row.get("rel_path")), backupPath, "记录的备份文件已不存在，该文件不可回滚"));
        }
        vo.setBackupMissing(backupMissing);
        vo.setBackupMissingCount(backupMissing.size());

        // 3. 重复记录：同批次 + 同路径多行（历史双写残留会让「回滚哪个」变得含糊）
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                "SELECT batch_no, abs_path, COUNT(*) AS cnt FROM gen_output "
                        + "WHERE deleted = 0 AND abs_path <> '' "
                        + "GROUP BY batch_no, abs_path HAVING COUNT(*) > 1");
        for (Map<String, Object> row : duplicates) {
            vo.getDuplicate().add(issue(str(row.get("batch_no")), "", str(row.get("abs_path")),
                    str(row.get("abs_path")), "同批次同路径 " + row.get("cnt") + " 行"));
        }
        vo.setDuplicateCount(vo.getDuplicate().size());

        vo.setDriftedCount(genDriftService.scan(null).getDrifted());
        log.info("ledger health: records={} paths={} orphan={} backupMissing={} duplicate={} drifted={}",
                vo.getTotalRecords(), vo.getUniquePaths(), vo.getOrphanCount(),
                vo.getBackupMissingCount(), vo.getDuplicateCount(), vo.getDriftedCount());
        return vo;
    }

    @Override
    public Map<String, Object> cleanupBackups() {
        int keepBatches = outputProperties.getBackupKeepBatches();
        int keepDays = outputProperties.getBackupKeepDays();
        List<Path> backupRoots = backupRoots();
        List<String> removed = new ArrayList<>();
        long freed = 0;
        for (Path backupRoot : backupRoots) {
            if (!Files.isDirectory(backupRoot)) {
                continue;
            }
            List<Path> batches = new ArrayList<>();
            try (Stream<Path> stream = Files.list(backupRoot)) {
                stream.filter(Files::isDirectory).forEach(batches::add);
            } catch (IOException e) {
                log.warn("读取备份目录失败：{} 原因={}", backupRoot, e.getMessage());
                continue;
            }
            // 批次号形如 GEN20261022153000，按目录名倒序即「从新到旧」
            batches.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
            for (int i = 0; i < batches.size(); i++) {
                Path batchDir = batches.get(i);
                boolean withinCount = keepBatches > 0 && i < keepBatches;
                boolean withinDays = keepDays > 0 && lastModifiedAfter(batchDir, keepDays);
                if (withinCount || withinDays) {
                    continue;
                }
                try {
                    freed += sizeOf(batchDir);
                    deleteRecursively(batchDir);
                    removed.add(batchDir.toString());
                } catch (Exception e) {
                    log.warn("清理备份目录失败：{} 原因={}", batchDir, e.getMessage());
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keepBatches", keepBatches);
        result.put("keepDays", keepDays);
        result.put("removedCount", removed.size());
        result.put("removed", removed);
        result.put("freedBytes", freed);
        // 清理动作只写日志不写台账：它不是「生成产物」，写进台账会污染回滚视图
        log.info("backup cleanup: removed={} freed={}B", removed.size(), freed);
        return result;
    }

    /**
     * 所有可能的备份根目录（不同系统/前后端的 root 各自有一份 .gen-backup）
     */
    private List<Path> backupRoots() {
        Set<String> roots = new LinkedHashSet<>();
        if (StringUtils.hasText(outputProperties.getBackendRoot())) {
            roots.add(outputProperties.getBackendRoot());
        }
        if (StringUtils.hasText(outputProperties.getFrontendRoot())) {
            roots.add(outputProperties.getFrontendRoot());
        }
        outputProperties.getModules().values().forEach(m -> {
            if (StringUtils.hasText(m.getBackendRoot())) {
                roots.add(m.getBackendRoot());
            }
        });
        List<Path> result = new ArrayList<>();
        for (String root : roots) {
            try {
                result.add(Paths.get(root).toAbsolutePath().normalize().resolve(BACKUP_DIR));
            } catch (Exception e) {
                log.warn("忽略非法的备份根目录：{}", root);
            }
        }
        return result;
    }

    private static boolean lastModifiedAfter(Path path, int days) {
        try {
            Instant time = Files.getLastModifiedTime(path).toInstant();
            return time.isAfter(Instant.now().minus(days, ChronoUnit.DAYS));
        } catch (IOException e) {
            // 读不到时间就当作「很旧」还是「很新」？选后者：宁可少删，不可误删
            return true;
        }
    }

    private static long sizeOf(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static GenLedgerHealthVO.Issue issue(String batchNo, String tableName,
                                                 String relPath, String path, String detail) {
        GenLedgerHealthVO.Issue issue = new GenLedgerHealthVO.Issue();
        issue.setBatchNo(batchNo);
        issue.setTableName(tableName);
        issue.setRelPath(relPath);
        issue.setPath(path);
        issue.setDetail(detail);
        return issue;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
