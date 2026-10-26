package com.codegen.service.impl;

import com.codegen.service.GenDriftService;
import com.codegen.util.GenDigestUtils;
import com.codegen.vo.GenDriftSummaryVO;
import com.codegen.vo.GenDriftVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 漂移检测实现（V4.2 P0-3）
 *
 * <p>「最新台账」取每个 {@code abs_path} 的 MAX(id)：雪花 ID 时间有序，
 * 且只挑 {@code md5 <> ''} 的记录——SKIPPED / ERROR 行没有内容指纹，
 * 若让它们参与「最新」，会把一个刚被跳过的文件误判成 UNKNOWN。
 */
@Service
public class GenDriftServiceImpl implements GenDriftService {

    private static final Logger log = LoggerFactory.getLogger(GenDriftServiceImpl.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, LedgerSnapshot> latestByPath() {
        // 子查询先按路径收敛到一条，再回表取其余字段：MySQL 8 下这比窗口函数更省，
        // 而且 gen_output 有 idx_batch_no/idx_table_id，行数在千级以内，一次扫完没有问题
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT o.id, o.table_id, o.batch_no, o.table_name, o.rel_path, o.abs_path, "
                        + "o.action, o.md5, o.backup_path, o.prev_md5 "
                        + "FROM gen_output o INNER JOIN ("
                        + "  SELECT abs_path, MAX(id) AS max_id FROM gen_output "
                        + "  WHERE deleted = 0 AND md5 <> '' AND abs_path <> '' GROUP BY abs_path"
                        + ") t ON o.id = t.max_id");
        Map<String, LedgerSnapshot> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String absPath = str(row.get("abs_path"));
            if (!StringUtils.hasText(absPath)) {
                continue;
            }
            result.put(absPath, new LedgerSnapshot(
                    asLong(row.get("id")),
                    asLong(row.get("table_id")),
                    str(row.get("batch_no")),
                    str(row.get("table_name")),
                    str(row.get("rel_path")),
                    str(row.get("action")),
                    str(row.get("md5")),
                    str(row.get("backup_path")),
                    str(row.get("prev_md5"))));
        }
        return result;
    }

    @Override
    public GenDriftSummaryVO scan(List<Long> tableIds) {
        Map<String, LedgerSnapshot> latest = latestByPath();
        GenDriftSummaryVO summary = new GenDriftSummaryVO();
        boolean filterByTable = tableIds != null && !tableIds.isEmpty();
        for (Map.Entry<String, LedgerSnapshot> entry : latest.entrySet()) {
            LedgerSnapshot snap = entry.getValue();
            if (filterByTable && (snap.tableId() == null || !tableIds.contains(snap.tableId()))) {
                continue;
            }
            GenDriftVO vo = new GenDriftVO();
            vo.setTableName(snap.tableName());
            vo.setRelPath(snap.relPath());
            vo.setAbsPath(entry.getKey());
            vo.setBatchNo(snap.batchNo());
            vo.setAction(snap.action());
            vo.setExpectedMd5(snap.md5());
            vo.setLastModified(lastModified(entry.getKey()));
            vo.setFileSize(sizeOf(entry.getKey()));
            String actual = null;
            try {
                actual = GenDigestUtils.md5Hex(Paths.get(entry.getKey()));
            } catch (Exception e) {
                vo.setStatus(UNKNOWN);
                vo.setMessage("读取失败：" + e.getMessage());
                summary.getItems().add(vo);
                summary.setUnknown(summary.getUnknown() + 1);
                continue;
            }
            vo.setActualMd5(actual);
            vo.setStatus(judge(snap.md5(), actual));
            if (DRIFTED.equals(vo.getStatus())) {
                vo.setMessage("磁盘内容与台账不一致，生成后被手改");
            } else if (MISSING.equals(vo.getStatus())) {
                vo.setMessage("台账有记录但文件已不在磁盘");
            }
            summary.getItems().add(vo);
        }
        // 有问题的排前面：漂移 > 缺失 > 未知 > 一致，让报告第一屏就是需要看的
        summary.getItems().sort(Comparator.comparingInt((GenDriftVO v) -> severity(v.getStatus()))
                .thenComparing(v -> v.getRelPath() == null ? "" : v.getRelPath()));
        summary.setTotal(summary.getItems().size());
        summary.setSame((int) summary.getItems().stream().filter(v -> SAME.equals(v.getStatus())).count());
        summary.setDrifted((int) summary.getItems().stream().filter(v -> DRIFTED.equals(v.getStatus())).count());
        summary.setMissing((int) summary.getItems().stream().filter(v -> MISSING.equals(v.getStatus())).count());
        summary.setUnknown((int) summary.getItems().stream().filter(v -> UNKNOWN.equals(v.getStatus())).count());
        log.info("drift scan: total={} same={} drifted={} missing={} unknown={}",
                summary.getTotal(), summary.getSame(), summary.getDrifted(),
                summary.getMissing(), summary.getUnknown());
        return summary;
    }

    @Override
    public String judge(String expectedMd5, String actualMd5) {
        if (!StringUtils.hasText(expectedMd5)) {
            return UNKNOWN;
        }
        if (actualMd5 == null) {
            return MISSING;
        }
        return expectedMd5.equalsIgnoreCase(actualMd5) ? SAME : DRIFTED;
    }

    private static int severity(String status) {
        if (DRIFTED.equals(status)) {
            return 0;
        }
        if (MISSING.equals(status)) {
            return 1;
        }
        if (UNKNOWN.equals(status)) {
            return 2;
        }
        return 3;
    }

    private static String lastModified(String absPath) {
        try {
            FileTime time = Files.getLastModifiedTime(Paths.get(absPath));
            return TIME_FORMAT.format(time.toInstant().atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            return "";
        }
    }

    private static Long sizeOf(String absPath) {
        try {
            Path path = Paths.get(absPath);
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
