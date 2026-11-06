package com.codegen.service.impl;

import com.codegen.service.GenRefService;
import com.codegen.vo.RefDetectItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外键关联推断实现。
 *
 * <p>真实业务表的外键通常只有「列名」这一条线索（`category_id`），没有数据库级外键约束，
 * 所以这里按证据强弱打分，宁可漏判也不乱绑：</p>
 *
 * <ol>
 *   <li>列名去掉 _id 后与某张表名完全一致 —— 最强；</li>
 *   <li>与某张表名同前缀（wms_product_id → wms_product），避免跨模块误绑；</li>
 *   <li>表名以 _xxx 结尾（notice_id → wms_outbound_notice）；</li>
 *   <li>列注释（去掉尾部 ID/编号）出现在候选表注释里 —— 只作加分，不单独成立。</li>
 * </ol>
 *
 * <p>名称列的选择同样按约定：name → *_name → title → code，都没有就不绑，
 * 因为绑了也显示不出可读名称，反而会生成无意义的远程搜索下拉。</p>
 */
@Service
public class GenRefServiceImpl implements GenRefService {

    private static final Logger log = LoggerFactory.getLogger(GenRefServiceImpl.class);

    /** 低于该分数视为没把握，不落库 */
    private static final int MIN_SCORE = 60;

    /** 名称列候选，顺序即优先级 */
    private static final List<String> LABEL_CANDIDATES =
            List.of("name", "title", "real_name", "full_name", "code");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record TableMeta(Long id, String name, String comment, String prefix) {
    }

    @Override
    public List<RefDetectItemVO> detect(Long projectId, boolean apply) {
        List<TableMeta> tables = loadTables(projectId);
        if (tables.isEmpty()) {
            return List.of();
        }
        Map<Long, TableMeta> byId = new LinkedHashMap<>();
        Map<String, TableMeta> byName = new HashMap<>();
        for (TableMeta t : tables) {
            byId.put(t.id(), t);
            byName.put(t.name().toLowerCase(Locale.ROOT), t);
        }
        Map<Long, List<Map<String, Object>>> columnsByTable = loadColumns(byId.keySet());

        List<RefDetectItemVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : columnsByTable.entrySet()) {
            TableMeta source = byId.get(entry.getKey());
            if (source == null) {
                continue;
            }
            for (Map<String, Object> column : entry.getValue()) {
                String columnName = str(column.get("column_name"));
                String refTable = str(column.get("ref_table"));
                if (!columnName.toLowerCase(Locale.ROOT).endsWith("_id")
                        || "id".equalsIgnoreCase(columnName)
                        || !refTable.isBlank()) {
                    continue;
                }
                // 表自己的主键列（detail_id / address_id）不是外键——按 is_pk 和「表名末段」两道判据拦掉
                Object isPk = column.get("is_pk");
                if (isPk instanceof Number n && n.intValue() == 1) {
                    continue;
                }
                String baseName = columnName.substring(0, columnName.length() - 3).toLowerCase(Locale.ROOT);
                if (source.name().toLowerCase(Locale.ROOT).endsWith("_" + baseName)) {
                    continue;
                }
                Match match = match(source, column, tables, byName, columnsByTable);
                if (match == null) {
                    continue;
                }
                result.add(new RefDetectItemVO(source.name(), columnName, match.table().name(),
                        match.label(), match.reason()));
                if (apply) {
                    jdbcTemplate.update("UPDATE gen_table_column SET ref_table = ?, ref_label = ? "
                                    + "WHERE id = ?",
                            match.table().name(), match.label(), column.get("id"));
                    // 表单控件顺手切成下拉：外键列让用户手输 ID 没有意义
                    String htmlType = str(column.get("html_type"));
                    if (htmlType.isBlank() || "input".equalsIgnoreCase(htmlType)
                            || "textarea".equalsIgnoreCase(htmlType)) {
                        jdbcTemplate.update("UPDATE gen_table_column SET html_type = 'select' "
                                + "WHERE id = ?", column.get("id"));
                    }
                }
            }
        }
        log.info("外键推断完成：{} 个候选，apply={}", result.size(), apply);
        return result;
    }

    private record Match(TableMeta table, String label, String reason, int score) {
    }

    private Match match(TableMeta source, Map<String, Object> column, List<TableMeta> tables,
                        Map<String, TableMeta> byName, Map<Long, List<Map<String, Object>>> columnsByTable) {
        String columnName = str(column.get("column_name")).toLowerCase(Locale.ROOT);
        String base = columnName.substring(0, columnName.length() - 3);
        String baseComment = stripIdSuffix(str(column.get("column_comment")));

        List<Match> matches = new ArrayList<>();
        for (TableMeta candidate : tables) {
            if (candidate.id().equals(source.id())) {
                continue;
            }
            String name = candidate.name().toLowerCase(Locale.ROOT);
            int score = 0;
            String reason = null;
            if (name.equals(base)) {
                score = 100;
                reason = "列名与表名完全一致";
            } else if (name.equals(source.prefix() + "_" + base)) {
                score = 95;
                reason = "同前缀表名匹配（" + source.prefix() + "_）";
            } else if (name.endsWith("_" + base)) {
                score = 70;
                reason = "表名以 _" + base + " 结尾";
            }
            if (score == 0) {
                continue;
            }
            if (!baseComment.isEmpty() && baseComment.length() >= 2
                    && str(candidate.comment()).contains(baseComment)) {
                score += 15;
                reason = reason + "，注释含「" + baseComment + "」";
            }
            String label = pickLabel(candidate, columnsByTable);
            if (label == null) {
                continue;
            }
            matches.add(new Match(candidate, label, reason, score));
        }
        return matches.stream()
                .max(Comparator.comparingInt(Match::score)
                        .thenComparing(m -> -m.table().name().length()))
                .filter(m -> m.score() >= MIN_SCORE)
                .orElse(null);
    }

    /** 名称列：先按「表名主体 + _name/_no」这类强约定，再退化到通用候选列 */
    private String pickLabel(TableMeta table, Map<Long, List<Map<String, Object>>> columnsByTable) {
        List<Map<String, Object>> columns = columnsByTable.getOrDefault(table.id(), List.of());
        List<String> names = columns.stream()
                .map(c -> str(c.get("column_name")).toLowerCase(Locale.ROOT))
                .toList();
        // 表名去掉前缀后的主体：wms_product → product、demo_order → order
        String name = table.name().toLowerCase(Locale.ROOT);
        int idx = name.indexOf('_');
        String base = idx > 0 ? name.substring(idx + 1) : name;
        if (base.startsWith(table.prefix() + "_")) {
            base = base.substring(table.prefix().length() + 1);
        }
        for (String suffix : List.of("_name", "_no", "_code", "_title")) {
            if (names.contains(base + suffix)) {
                return base + suffix;
            }
        }
        for (String candidate : LABEL_CANDIDATES) {
            if (names.contains(candidate)) {
                return candidate;
            }
        }
        for (String suffix : List.of("_name", "_no", "_code")) {
            for (String column : names) {
                if (column.endsWith(suffix) && !column.endsWith("_id")) {
                    return column;
                }
            }
        }
        for (int i = 0; i < columns.size(); i++) {
            String column = names.get(i);
            String type = str(columns.get(i).get("column_type")).toLowerCase(Locale.ROOT);
            boolean skip = column.equals("id") || column.endsWith("_id") || column.endsWith("_time")
                    || column.startsWith("create_") || column.startsWith("update_") || column.equals("deleted");
            if (!skip && (type.startsWith("varchar") || type.startsWith("char"))) {
                return column;
            }
        }
        return null;
    }

    private List<TableMeta> loadTables(Long projectId) {
        String sql = "SELECT id, table_name, table_comment, project_id FROM gen_table "
                + "WHERE deleted = 0" + (projectId == null ? "" : " AND project_id = ?")
                + " ORDER BY table_name";
        List<Map<String, Object>> rows = projectId == null
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, projectId);
        List<TableMeta> tables = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = str(row.get("table_name"));
            int idx = name.indexOf('_');
            String prefix = idx > 0 ? name.substring(0, idx) : name;
            tables.add(new TableMeta(((Number) row.get("id")).longValue(), name,
                    str(row.get("table_comment")), prefix.toLowerCase(Locale.ROOT)));
        }
        return tables;
    }

    private Map<Long, List<Map<String, Object>>> loadColumns(java.util.Collection<Long> tableIds) {
        Map<Long, List<Map<String, Object>>> map = new LinkedHashMap<>();
        if (tableIds.isEmpty()) {
            return map;
        }
        String in = String.join(",", tableIds.stream().map(String::valueOf).toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, table_id, column_name, column_comment, column_type, html_type, "
                        + "is_pk, ref_table, ref_label FROM gen_table_column WHERE deleted = 0 AND table_id IN (" + in + ") "
                        + "ORDER BY sort");
        for (Map<String, Object> row : rows) {
            Long tableId = ((Number) row.get("table_id")).longValue();
            map.computeIfAbsent(tableId, k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private static String stripIdSuffix(String comment) {
        String text = comment == null ? "" : comment.trim();
        if (text.endsWith("ID")) {
            return text.substring(0, text.length() - 2).trim();
        }
        if (text.endsWith("id")) {
            return text.substring(0, text.length() - 2).trim();
        }
        if (text.endsWith("编号")) {
            return text.substring(0, text.length() - 2).trim();
        }
        return text;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
