package com.codegen.support;

import com.codegen.vo.GenAiRecommendVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * V7.0 AI 建议白名单校验（纯函数，可单测）。
 *
 * <p>AI 输出不可信：枚举越界、引用不存在的列/表/字典、主子成环，
 * 逐项丢弃并记录原因，合法项不受影响——比「整体拒绝」细，比「照单全收」稳。
 * 被丢弃的字段在 VO 里保持 null，应用时只写非 null 字段，
 * 这些列就自然回落到 V6.6 规则推断的现值。
 */
public final class AiRecommendSanitizer {

    private static final Set<String> HTML_TYPES = Set.of(
            "input", "textarea", "number", "select", "radio", "switch",
            "date", "datetime", "image", "file", "multiImage");

    private static final Set<String> QUERY_TYPES = Set.of("EQ", "LIKE", "GE", "LE");

    private static final Set<String> RELATION_TYPES = Set.of("middle");

    /** 模块 key：路径片段用，英文数字驼峰/下划线/中划线，1-64 字符 */
    private static final Pattern MODULE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    /** 字典编码风格（与 DictDetectServiceImpl.CODE_OK 一致） */
    private static final Pattern DICT_CODE = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private AiRecommendSanitizer() {
    }

    /**
     * 校验并清洗 AI 原始输出。
     *
     * @param raw           AI 返回的 JSON（{\"tables\":[...],\"menus\":[...]}）
     * @param tableColumns  系统实际表 → 列名集合（校验引用存在性的基准）
     * @param knownDictCodes 平台已知字典编码（sys_dict_type.dict_code）
     */
    public static GenAiRecommendVO sanitize(JsonNode raw,
                                            Map<String, Set<String>> tableColumns,
                                            Set<String> knownDictCodes) {
        GenAiRecommendVO vo = new GenAiRecommendVO();
        if (raw == null) {
            return vo;
        }
        for (JsonNode t : raw.path("tables")) {
            sanitizeTable(t, tableColumns, knownDictCodes, vo);
        }
        detectMasterCycle(vo);
        for (JsonNode m : raw.path("menus")) {
            sanitizeMenu(m, vo);
        }
        return vo;
    }

    // ==================== 表级 + 列级 ====================

    private static void sanitizeTable(JsonNode t, Map<String, Set<String>> tableColumns,
                                      Set<String> knownDictCodes, GenAiRecommendVO vo) {
        String tableName = text(t, "tableName", "table_name");
        if (!StringUtils.hasText(tableName) || !tableColumns.containsKey(tableName)) {
            if (StringUtils.hasText(tableName)) {
                vo.getDrops().add("表 " + tableName + "：表不存在，整表建议丢弃");
            }
            return;
        }
        Set<String> cols = tableColumns.get(tableName);
        GenAiRecommendVO.TableSuggest ts = new GenAiRecommendVO.TableSuggest();
        ts.setTableName(tableName);

        String moduleName = text(t, "moduleName", "module_name");
        if (StringUtils.hasText(moduleName)) {
            if (MODULE_KEY.matcher(moduleName).matches()) {
                ts.setModuleName(moduleName);
            } else {
                vo.getDrops().add("表 " + tableName + "：moduleName「" + moduleName + "」非法（须英文开头，仅字母数字_-）");
            }
        }
        String treeParent = text(t, "treeParentColumn", "tree_parent_column");
        if (StringUtils.hasText(treeParent)) {
            if (cols.contains(treeParent)) {
                ts.setTreeParentColumn(treeParent);
            } else {
                vo.getDrops().add("表 " + tableName + "：treeParentColumn「" + treeParent + "」列不存在");
            }
        }
        String treeLabel = text(t, "treeLabelColumn", "tree_label_column");
        if (StringUtils.hasText(treeLabel)) {
            if (cols.contains(treeLabel)) {
                ts.setTreeLabelColumn(treeLabel);
            } else {
                vo.getDrops().add("表 " + tableName + "：treeLabelColumn「" + treeLabel + "」列不存在");
            }
        }
        String master = text(t, "masterTableName", "master_table_name", "masterTable");
        if (StringUtils.hasText(master)) {
            if (!tableColumns.containsKey(master)) {
                vo.getDrops().add("表 " + tableName + "：masterTableName「" + master + "」表不存在");
            } else if (master.equals(tableName)) {
                vo.getDrops().add("表 " + tableName + "：masterTableName 自引用成环");
            } else {
                ts.setMasterTableName(master);
            }
        }
        String relationType = text(t, "relationType", "relation_type");
        if (StringUtils.hasText(relationType)) {
            if (RELATION_TYPES.contains(relationType)) {
                ts.setRelationType(relationType);
            } else {
                vo.getDrops().add("表 " + tableName + "：relationType「" + relationType + "」非法（仅支持 middle）");
            }
        }

        for (JsonNode c : t.path("columns")) {
            GenAiRecommendVO.ColumnSuggest cs = sanitizeColumn(tableName, c, cols, knownDictCodes, vo.getDrops());
            if (cs != null) {
                ts.getColumns().add(cs);
            }
        }
        vo.getTables().add(ts);
    }

    private static GenAiRecommendVO.ColumnSuggest sanitizeColumn(
            String tableName, JsonNode c, Set<String> cols,
            Set<String> knownDictCodes, List<String> drops) {
        String columnName = text(c, "columnName", "column_name", "name");
        if (!StringUtils.hasText(columnName)) {
            return null;
        }
        String prefix = "表 " + tableName + " 列 " + columnName + "：";
        if (!cols.contains(columnName)) {
            drops.add(prefix + "列不存在，整列建议丢弃");
            return null;
        }
        GenAiRecommendVO.ColumnSuggest cs = new GenAiRecommendVO.ColumnSuggest();
        cs.setColumnName(columnName);

        cs.setIsQuery(flag(c, "isQuery", "is_query"));
        cs.setIsList(flag(c, "isList", "is_list"));
        cs.setIsForm(flag(c, "isForm", "is_form"));

        String queryType = text(c, "queryType", "query_type");
        if (StringUtils.hasText(queryType)) {
            if (QUERY_TYPES.contains(queryType.toUpperCase())) {
                cs.setQueryType(queryType.toUpperCase());
            } else {
                drops.add(prefix + "queryType「" + queryType + "」非法（EQ/LIKE/GE/LE）");
            }
        }
        String htmlType = text(c, "htmlType", "html_type");
        if (StringUtils.hasText(htmlType)) {
            if (HTML_TYPES.contains(htmlType)) {
                cs.setHtmlType(htmlType);
            } else {
                drops.add(prefix + "htmlType「" + htmlType + "」非法");
            }
        }
        String dictType = text(c, "dictType", "dict_type");
        if (StringUtils.hasText(dictType)) {
            if (!DICT_CODE.matcher(dictType).matches()) {
                drops.add(prefix + "dictType「" + dictType + "」编码非法");
            } else if (!knownDictCodes.contains(dictType)) {
                drops.add(prefix + "dictType「" + dictType + "」不在平台字典中");
            } else {
                cs.setDictType(dictType);
            }
        }
        JsonNode sort = c.path("sort");
        if (sort.isInt()) {
            cs.setSort(sort.asInt());
        }
        // 整行为空（所有字段都被丢弃或 AI 没给）就不占位
        boolean empty = cs.getIsQuery() == null && cs.getQueryType() == null
                && cs.getIsList() == null && cs.getIsForm() == null
                && cs.getHtmlType() == null && cs.getDictType() == null && cs.getSort() == null;
        return empty ? null : cs;
    }

    // ==================== 菜单层 ====================

    private static void sanitizeMenu(JsonNode m, GenAiRecommendVO vo) {
        String moduleName = text(m, "moduleName", "module_name");
        if (!StringUtils.hasText(moduleName) || !MODULE_KEY.matcher(moduleName).matches()) {
            if (StringUtils.hasText(moduleName)) {
                vo.getDrops().add("菜单：moduleName「" + moduleName + "」非法，菜单建议丢弃");
            }
            return;
        }
        GenAiRecommendVO.MenuSuggest ms = new GenAiRecommendVO.MenuSuggest();
        ms.setModuleName(moduleName);
        ms.setDirLabel(text(m, "dirLabel", "dir_label", "label"));
        ms.setIcon(text(m, "icon"));
        JsonNode sort = m.path("sort");
        if (sort.isInt()) {
            ms.setSort(sort.asInt());
        }
        for (JsonNode t : m.path("tables")) {
            if (t.isTextual()) {
                ms.getTables().add(t.asText());
            }
        }
        vo.getMenus().add(ms);
    }

    // ==================== 主子成环检测 ====================

    /**
     * A→B 且 B→A 的互指成环，两侧都丢弃。单边建议（只 A→B）不受影响——
     * 多级主子是合法形态（master_depth 支持 2 级），成环才是病态。
     */
    private static void detectMasterCycle(GenAiRecommendVO vo) {
        Map<String, String> masterOf = new HashMap<>();
        for (GenAiRecommendVO.TableSuggest ts : vo.getTables()) {
            if (StringUtils.hasText(ts.getMasterTableName())) {
                masterOf.put(ts.getTableName(), ts.getMasterTableName());
            }
        }
        List<String> cyclic = new ArrayList<>();
        for (Map.Entry<String, String> e : masterOf.entrySet()) {
            if (e.getKey().equals(masterOf.get(e.getValue()))) {
                cyclic.add(e.getKey());
            }
        }
        if (cyclic.isEmpty()) {
            return;
        }
        for (GenAiRecommendVO.TableSuggest ts : vo.getTables()) {
            if (cyclic.contains(ts.getTableName())) {
                vo.getDrops().add("表 " + ts.getTableName() + "：与 " + ts.getMasterTableName() + " 主子互指成环");
                ts.setMasterTableName(null);
            }
        }
    }

    // ==================== 工具 ====================

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /** 0/1 开关：只接受 0/1/true/false，其他视为未建议（null） */
    private static Integer flag(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode v = node.path(key);
            if (v.isInt()) {
                int n = v.asInt();
                return (n == 0 || n == 1) ? n : null;
            }
            if (v.isBoolean()) {
                return v.asBoolean() ? 1 : 0;
            }
        }
        return null;
    }
}
