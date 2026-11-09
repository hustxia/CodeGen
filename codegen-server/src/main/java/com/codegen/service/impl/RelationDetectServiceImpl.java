package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.RelationDetectService;
import com.codegen.vo.RelationDetectVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 中间表识别实现。
 *
 * <p>实现要点：先读 information_schema 拿到外键，再用「列数 + 唯一键 + 命名」三重判断。
 * 单看外键数量会把「订单明细」这种一对多误判成中间表，
 * 所以额外要求「非外键业务列很少」，这是区分一对多与多对多的关键。
 */
@Service
public class RelationDetectServiceImpl implements RelationDetectService {

    private static final Logger log = LoggerFactory.getLogger(RelationDetectServiceImpl.class);

    /** 除主键/审计字段外，中间表最多容许这些数量的额外列 */
    private static final int MAX_EXTRA_COLUMNS = 2;

    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "id", "deleted", "create_by", "create_time", "update_by", "update_time",
            "created_at", "updated_at", "created_by", "updated_by", "version");

    private static final Pattern NAME_OK = Pattern.compile("^\\w+$");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private GenTableColumnMapper genTableColumnMapper;

    @Override
    public List<RelationDetectVO> detect(String schema) {
        String db = StringUtils.hasText(schema) ? schema.trim() : "shop";
        if (!NAME_OK.matcher(db).matches()) {
            return List.of();
        }
        // 一次把全库外键捞出来，按表分组，避免逐表查 information_schema
        List<Map<String, Object>> fkRows = jdbcTemplate.queryForList(
                "SELECT k.TABLE_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME "
                        + "FROM information_schema.KEY_COLUMN_USAGE k "
                        + "WHERE k.TABLE_SCHEMA = ? AND k.REFERENCED_TABLE_NAME IS NOT NULL "
                        + "ORDER BY k.TABLE_NAME, k.ORDINAL_POSITION", db);

        // table -> [ [column, refTable, refColumn], ... ]
        Map<String, List<String[]>> byTable = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : fkRows) {
            String t = str(r.get("TABLE_NAME"));
            byTable.computeIfAbsent(t, k -> new ArrayList<>())
                    .add(new String[]{str(r.get("COLUMN_NAME")), str(r.get("REFERENCED_TABLE_NAME")),
                            str(r.get("REFERENCED_COLUMN_NAME"))});
        }

        Set<String> configuredTables = new LinkedHashSet<>();
        for (GenTable t : genTableMapper.selectList(new LambdaQueryWrapper<>())) {
            configuredTables.add(t.getTableName());
        }

        List<RelationDetectVO> result = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> e : byTable.entrySet()) {
            String table = e.getKey();
            List<String[]> fks = e.getValue();
            if (fks.size() != 2) {
                continue;
            }
            String[] a = fks.get(0);
            String[] b = fks.get(1);
            // 两个外键必须指向不同的表，否则是同表自关联（如父子分类），不是多对多
            if (a[1].equalsIgnoreCase(b[1])) {
                continue;
            }
            List<String> extra = extraBizColumns(db, table);
            if (extra.size() > MAX_EXTRA_COLUMNS) {
                continue;
            }
            boolean ukOnBoth = hasUniqueOnBoth(db, table, a[0], b[0]);
            boolean namingMatch = namingMatch(table, a[1], b[1]);
            // 唯一键 + 命名 都命中才算高置信；只有命名但业务列也为 0，也放过
            if (!ukOnBoth && !(namingMatch && extra.isEmpty())) {
                continue;
            }

            RelationDetectVO vo = new RelationDetectVO();
            vo.setMiddleTable(table);
            vo.setMiddleComment(tableComment(db, table));
            vo.setLeftTable(a[1]);
            vo.setLeftComment(tableComment(db, a[1]));
            vo.setLeftKey(a[0]);
            vo.setRightTable(b[1]);
            vo.setRightComment(tableComment(db, b[1]));
            vo.setRightKey(b[0]);
            vo.setExtraColumns(extra);
            vo.setConfigured(configuredTables.contains(table));
            vo.setReason((ukOnBoth ? "两个外键构成联合唯一键" : "整表只有两个外键列")
                    + (namingMatch ? "；表名同时包含两侧表名词根" : ""));
            result.add(vo);
        }
        return result;
    }

    /**
     * 除主键/审计/两个外键之外的业务列
     */
    private List<String> extraBizColumns(String schema, String table) {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                String.class, schema, table);
        List<String> extra = new ArrayList<>();
        for (String c : cols) {
            if (SYSTEM_COLUMNS.contains(c.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (c.toLowerCase(Locale.ROOT).endsWith("_id")) {
                continue;
            }
            extra.add(c);
        }
        return extra;
    }

    /**
     * 两个外键是否被某个唯一索引同时覆盖
     */
    private boolean hasUniqueOnBoth(String schema, String table, String k1, String k2) {
        List<Map<String, Object>> idx = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, "
                        + "MIN(NON_UNIQUE) AS nu, COUNT(*) AS n "
                        + "FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND INDEX_NAME <> 'PRIMARY' "
                        + "GROUP BY INDEX_NAME", schema, table);
        for (Map<String, Object> r : idx) {
            int nu = r.get("nu") == null ? 1 : ((Number) r.get("nu")).intValue();
            int n = r.get("n") == null ? 0 : ((Number) r.get("n")).intValue();
            if (nu != 0 || n != 2) {
                continue;
            }
            String cols = str(r.get("cols")).toLowerCase(Locale.ROOT);
            if (cols.contains(k1.toLowerCase(Locale.ROOT)) && cols.contains(k2.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 表名是否由两侧词根拼成（product_tag / tag_product 都算）
     */
    private boolean namingMatch(String table, String left, String right) {
        String t = table.toLowerCase(Locale.ROOT).replace("_", "");
        String l = root(left);
        String r = root(right);
        if (l.isEmpty() || r.isEmpty() || l.equals(r)) {
            return false;
        }
        String alt = stripPlural(t);
        return (alt.contains(l) && alt.contains(r)) || alt.equals(l + r) || alt.equals(r + l);
    }

    /**
     * 表名词根：去掉常见前缀与复数 s
     */
    private String root(String tableName) {
        String s = tableName.toLowerCase(Locale.ROOT);
        s = s.replaceAll("^(t_|tb_|sys_|biz_)", "");
        s = stripPlural(s);
        return s.replace("_", "");
    }

    private String stripPlural(String s) {
        if (s.length() > 3 && s.endsWith("s") && !s.endsWith("ss")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String tableComment(String schema, String table) {
        try {
            String c = jdbcTemplate.queryForObject(
                    "SELECT IFNULL(table_comment,'') FROM information_schema.tables "
                            + "WHERE table_schema = ? AND table_name = ?", String.class, schema, table);
            return StringUtils.hasText(c) ? c : table;
        } catch (Exception e) {
            return table;
        }
    }

    // ------------------------------------------------------------------ 落库

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int apply(String schema, List<String> middleTables) {
        if (middleTables == null || middleTables.isEmpty()) {
            return 0;
        }
        List<RelationDetectVO> detected = detect(schema);
        Map<String, RelationDetectVO> byName = new java.util.LinkedHashMap<>();
        for (RelationDetectVO r : detected) {
            byName.put(r.getMiddleTable(), r);
        }
        int applied = 0;
        for (String name : middleTables) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            RelationDetectVO r = byName.get(name.trim());
            if (r == null) {
                continue;
            }
            GenTable t = genTableMapper.selectOne(new LambdaQueryWrapper<GenTable>()
                    .eq(GenTable::getTableName, r.getMiddleTable()));
            if (t == null) {
                // 中间表还没导入到 gen_table，先跳过（不自动建配置，避免意外生成一套 CRUD 页面）
                log.info("中间表 {} 尚未导入 gen_table，跳过标记", name);
                continue;
            }
            t.setRelationType("middle");
            genTableMapper.updateById(t);

            // 两个外键列打标，供生成侧识别
            List<GenTableColumn> cols = genTableColumnMapper.selectList(
                    new LambdaQueryWrapper<GenTableColumn>()
                            .eq(GenTableColumn::getTableId, t.getId()));
            for (GenTableColumn c : cols) {
                boolean isLeft = c.getColumnName() != null
                        && c.getColumnName().equalsIgnoreCase(r.getLeftKey());
                boolean isRight = c.getColumnName() != null
                        && c.getColumnName().equalsIgnoreCase(r.getRightKey());
                c.setRelationKey(isLeft || isRight ? 1 : 0);
                if (isLeft || isRight) {
                    // 关键：外键在页面上应该是「选一个实体」而不是「填一个数字」。
                    // 复用 existing 的 ref_table/ref_label 能力，直接变成远程搜索下拉。
                    c.setRefTable(isLeft ? r.getLeftTable() : r.getRightTable());
                    c.setRefLabel("name");
                    c.setHtmlType("select");
                    // 中间表行数可能很多，但这两个键是主要筛选维度，默认开查询
                    c.setIsQuery(1);
                    c.setQueryType("EQ");
                }
                genTableColumnMapper.updateById(c);
            }
            applied++;
        }
        return applied;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
