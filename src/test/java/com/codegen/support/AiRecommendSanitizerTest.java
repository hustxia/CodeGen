package com.codegen.support;

import com.codegen.vo.GenAiRecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.0 AI 页面与菜单推荐：服务端白名单校验（spec AC-2）。
 *
 * <p>AI 输出不可信：枚举越界、引用不存在的列/表/字典、主子成环，
 * 必须逐项丢弃并记录原因，合法项不受影响——比「整体拒绝」细，
 * 比「照单全收」稳。
 */
class AiRecommendSanitizerTest {

    private final ObjectMapper json = new ObjectMapper();

    /** 两张表的元数据：stock（status/name）与 order（id/order_no） */
    private Map<String, Set<String>> meta() {
        return Map.of(
                "wms_stock", Set.of("id", "status", "name"),
                "wms_order", Set.of("id", "order_no"));
    }

    @Test
    @DisplayName("列级：非法枚举按字段丢弃（同行的合法字段保留），并记录原因")
    void invalidEnumFieldDropped() throws Exception {
        String raw = """
                {"tables":[{"tableName":"wms_stock","columns":[
                  {"columnName":"status","isQuery":1,"queryType":"EQ","isList":1,"isForm":1,"htmlType":"select","dictType":"sys_status","sort":5},
                  {"columnName":"name","isQuery":1,"queryType":"FUZZY","isList":1,"isForm":1,"htmlType":"richtext","sort":6}
                ]}]}
                """;
        GenAiRecommendVO vo = AiRecommendSanitizer.sanitize(
                json.readTree(raw), meta(), Set.of("sys_status"));

        assertEquals(1, vo.getTables().size());
        assertEquals(2, vo.getTables().get(0).getColumns().size(), "字段级丢弃不应株连整列");
        GenAiRecommendVO.ColumnSuggest name = vo.getTables().get(0).getColumns().stream()
                .filter(c -> "name".equals(c.getColumnName())).findFirst().orElseThrow();
        assertNull(name.getQueryType(), "FUZZY 非法，该字段丢弃");
        assertNull(name.getHtmlType(), "richtext 非法，该字段丢弃");
        assertEquals(1, name.getIsQuery(), "同行的合法字段保留");
        assertEquals(6, name.getSort(), "同行的合法字段保留");
        assertTrue(vo.getDrops().stream().anyMatch(d -> d.contains("name") && d.contains("queryType")),
                "丢弃原因要含列名与字段：" + vo.getDrops());
        assertTrue(vo.getDrops().stream().anyMatch(d -> d.contains("name") && d.contains("htmlType")),
                "htmlType 丢弃原因也要记录：" + vo.getDrops());
    }

    @Test
    @DisplayName("列级：引用不存在的列、未在已知集合的 dictType 被丢弃")
    void unknownColumnAndDictDropped() throws Exception {
        String raw = """
                {"tables":[
                  {"tableName":"wms_stock","columns":[
                    {"columnName":"ghost_col","isQuery":1,"htmlType":"input","sort":1},
                    {"columnName":"status","isQuery":1,"queryType":"EQ","isList":1,"isForm":1,"htmlType":"select","dictType":"invented_dict","sort":5}
                  ]},
                  {"tableName":"ghost_table","columns":[
                    {"columnName":"id","isQuery":0,"htmlType":"input","sort":1}
                  ]}
                ]}
                """;
        GenAiRecommendVO vo = AiRecommendSanitizer.sanitize(
                json.readTree(raw), meta(), Set.of("sys_status"));

        // ghost_table 整表丢弃；wms_stock 的 ghost_col 整列丢弃；
        // status 列只有 dictType 一个字段丢弃（未在平台字典中），其余合法字段保留
        assertEquals(1, vo.getTables().size());
        assertEquals("wms_stock", vo.getTables().get(0).getTableName());
        assertEquals(1, vo.getTables().get(0).getColumns().size());
        GenAiRecommendVO.ColumnSuggest status = vo.getTables().get(0).getColumns().get(0);
        assertEquals("status", status.getColumnName());
        assertNull(status.getDictType(), "未知字典该字段丢弃");
        assertEquals("select", status.getHtmlType(), "合法字段保留");
        assertEquals(3, vo.getDrops().size(), "表不存在、列不存在、字典未知各一条：" + vo.getDrops());
    }

    @Test
    @DisplayName("表级：树形列必须存在；主子引用必须存在；非法 moduleName 丢弃")
    void tableLevelWhitelist() throws Exception {
        String raw = """
                {"tables":[
                  {"tableName":"wms_stock","moduleName":"库存管理!!","treeParentColumn":"ghost_parent","treeLabelColumn":"name"},
                  {"tableName":"wms_order","masterTableName":"ghost_master"},
                  {"tableName":"wms_stock2","masterTableName":"wms_stock2"}
                ]}
                """;
        GenAiRecommendVO vo = AiRecommendSanitizer.sanitize(
                json.readTree(raw), meta(), Set.of());

        // wms_stock：moduleName 非法 + treeParentColumn 不存在 → 两个字段丢弃，treeLabelColumn 保留
        GenAiRecommendVO.TableSuggest stock = vo.getTables().stream()
                .filter(t -> "wms_stock".equals(t.getTableName())).findFirst().orElseThrow();
        assertNull(stock.getModuleName(), "中文+符号的模块 key 不允许");
        assertNull(stock.getTreeParentColumn(), "不存在的树形父列要丢弃");
        assertEquals("name", stock.getTreeLabelColumn(), "合法字段不受影响");
        // wms_order：主子引用不存在的表 → 丢弃
        GenAiRecommendVO.TableSuggest order = vo.getTables().stream()
                .filter(t -> "wms_order".equals(t.getTableName())).findFirst().orElseThrow();
        assertNull(order.getMasterTableName(), "引用不存在的表要丢弃");
        // wms_stock2 不在元数据里，整表建议丢弃
        assertTrue(vo.getTables().stream().noneMatch(t -> "wms_stock2".equals(t.getTableName())));
    }

    @Test
    @DisplayName("表级：主子互指成环（A→B 且 B→A）与自指，两侧都丢弃并记录")
    void masterCycleDropped() throws Exception {
        String raw = """
                {"tables":[
                  {"tableName":"wms_stock","masterTableName":"wms_order"},
                  {"tableName":"wms_order","masterTableName":"wms_stock"}
                ]}
                """;
        GenAiRecommendVO vo = AiRecommendSanitizer.sanitize(
                json.readTree(raw), meta(), Set.of());
        assertTrue(vo.getTables().stream().allMatch(t -> t.getMasterTableName() == null),
                "成环的主子引用两侧都要丢弃：" + vo.getTables());

        String self = """
                {"tables":[{"tableName":"wms_stock","masterTableName":"wms_stock"}]}
                """;
        GenAiRecommendVO vo2 = AiRecommendSanitizer.sanitize(
                json.readTree(self), meta(), Set.of());
        assertNull(vo2.getTables().get(0).getMasterTableName(), "自引用要丢弃");
        assertTrue(vo2.getDrops().stream().anyMatch(d -> d.contains("wms_stock")));
    }

    @Test
    @DisplayName("合法建议全保留：列级七字段 + 表级 + 菜单层原样通过，drops 为空")
    void validSuggestionsPassThrough() throws Exception {
        String raw = """
                {"tables":[
                  {"tableName":"wms_stock","moduleName":"stock","treeParentColumn":"status","treeLabelColumn":"name",
                   "columns":[
                     {"columnName":"status","isQuery":1,"queryType":"EQ","isList":1,"isForm":1,"htmlType":"select","dictType":"sys_status","sort":5},
                     {"columnName":"name","isQuery":1,"queryType":"LIKE","isList":1,"isForm":1,"htmlType":"input","sort":6}
                   ]}
                ],
                 "menus":[
                   {"moduleName":"stock","dirLabel":"库存管理","icon":"el-icon-box","sort":1,"tables":["wms_stock"]}
                 ]}
                """;
        GenAiRecommendVO vo = AiRecommendSanitizer.sanitize(
                json.readTree(raw), meta(), Set.of("sys_status"));

        assertEquals(List.of(), vo.getDrops(), "合法建议不应有丢弃：" + vo.getDrops());
        assertEquals(2, vo.getTables().get(0).getColumns().size());
        assertEquals("stock", vo.getTables().get(0).getModuleName());
        assertEquals(1, vo.getMenus().size());
        assertEquals("库存管理", vo.getMenus().get(0).getDirLabel());
    }
}
