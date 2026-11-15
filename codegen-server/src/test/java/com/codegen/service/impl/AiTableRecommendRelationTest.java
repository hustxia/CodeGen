package com.codegen.service.impl;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.support.AiRecommendSanitizer;
import com.codegen.util.GenTemplateModel;
import com.codegen.util.GenUtils;
import com.codegen.vo.GenAiBuildPlanVO;
import com.codegen.vo.GenPipelineAiVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.5 T3-3：生成产物层 + 关系视图的单测。
 *
 * <p>覆盖：① computeRelations 的 single/master/detail/tree 判定（含典型自关联 parent_id）；
 * ② §4.7 富文本列推断与 AI 推荐白名单；③ §4.6 模型层 showIdColumn 默认隐藏；
 * ④ §4.7 模型层 hasRichText / 列级 isRichText 标志（含子表口径）。
 */
class AiTableRecommendRelationTest {

    private final AiTableRecommendServiceImpl service = new AiTableRecommendServiceImpl();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // ① 关系视图：单表 / 主从 / 树形
    // ------------------------------------------------------------------

    @Test
    void relations_masterDetailSingleTree_areAllClassified() {
        List<GenPipelineAiVO.TableModel> tables = List.of(
                table("bus_order", col("id"), col("order_no")),
                table("bus_order_item", col("id"), col("order_id")),   // → detail of bus_order
                table("bus_product", col("id"), col("product_name")),   // → single
                table("sys_category", col("id"), col("parent_id")),     // → tree（典型自关联）
                table("bus_tag", col("id"), col("ghost_id"))            // 悬空外键 → single
        );

        List<GenAiBuildPlanVO.TableRelation> rels = computeRelations(tables);

        assertEquals(5, rels.size());
        // out.sort by name，顺序固定
        assertEquals(List.of("bus_order", "bus_order_item", "bus_product", "bus_tag", "sys_category"),
                rels.stream().map(GenAiBuildPlanVO.TableRelation::getName).toList());

        assertEquals("master", role(rels, "bus_order"));
        assertEquals(List.of("bus_order_item"), rel(rels, "bus_order").getDetails());
        assertEquals("detail", role(rels, "bus_order_item"));
        assertEquals("bus_order", rel(rels, "bus_order_item").getMaster());
        assertEquals("single", role(rels, "bus_product"));
        assertEquals("single", role(rels, "bus_tag"));   // 悬空外键不产生关系
        assertEquals("tree", role(rels, "sys_category")); // parent_id → 树形
    }

    @Test
    void relations_multipleDetails_areAllCollectedUnderOneMaster() {
        List<GenPipelineAiVO.TableModel> tables = List.of(
                table("bus_order", col("id"), col("order_no")),
                table("bus_order_item", col("id"), col("order_id")),
                table("bus_order_log", col("id"), col("order_id"))
        );
        List<GenAiBuildPlanVO.TableRelation> rels = computeRelations(tables);

        assertEquals("master", role(rels, "bus_order"));
        assertEquals(List.of("bus_order_item", "bus_order_log"), rel(rels, "bus_order").getDetails());
    }

    @Test
    void relations_pidAndUpperParentId_alsoTree() {
        List<GenPipelineAiVO.TableModel> tables = List.of(
                table("sys_menu", col("id"), col("pid")),
                table("sys_dept", col("id"), col("PARENT_ID"))
        );
        List<GenAiBuildPlanVO.TableRelation> rels = computeRelations(tables);
        assertEquals("tree", role(rels, "sys_menu"));
        assertEquals("tree", role(rels, "sys_dept"));
    }

    @Test
    void relations_nullColumns_doesNotThrow() {
        GenPipelineAiVO.TableModel t = new GenPipelineAiVO.TableModel();
        t.setTableName("bus_empty");
        t.setColumns(null);
        List<GenAiBuildPlanVO.TableRelation> rels = computeRelations(List.of(t));
        assertEquals("single", role(rels, "bus_empty"));
    }

    @SuppressWarnings("unchecked")
    private List<GenAiBuildPlanVO.TableRelation> computeRelations(List<GenPipelineAiVO.TableModel> tables) {
        Object out = ReflectionTestUtils.invokeMethod(service, "computeRelations", tables);
        assertNotNull(out);
        return (List<GenAiBuildPlanVO.TableRelation>) out;
    }

    private static GenAiBuildPlanVO.TableRelation rel(List<GenAiBuildPlanVO.TableRelation> rels, String name) {
        return rels.stream().filter(r -> name.equals(r.getName())).findFirst().orElseThrow();
    }

    private static String role(List<GenAiBuildPlanVO.TableRelation> rels, String name) {
        return rel(rels, name).getRole();
    }

    private static GenPipelineAiVO.TableModel table(String name, GenPipelineAiVO.Column... cols) {
        GenPipelineAiVO.TableModel t = new GenPipelineAiVO.TableModel();
        t.setTableName(name);
        t.setColumns(new ArrayList<>(List.of(cols)));
        return t;
    }

    private static GenPipelineAiVO.Column col(String name) {
        GenPipelineAiVO.Column c = new GenPipelineAiVO.Column();
        c.setName(name);
        return c;
    }

    // ------------------------------------------------------------------
    // ② §4.7 富文本列推断
    // ------------------------------------------------------------------

    @Test
    void inferHtmlType_textColumnWithBodySemantics_isRichText() {
        assertEquals("richtext", GenUtils.inferHtmlType("text", "String", "content"));
        assertEquals("richtext", GenUtils.inferHtmlType("longtext", "String", "article_body"));
        assertEquals("richtext", GenUtils.inferHtmlType("mediumtext", "String", "product_description"));
        assertEquals("richtext", GenUtils.inferHtmlType("text", "String", "remark"));
    }

    @Test
    void inferHtmlType_shortVarcharOrUnrelatedColumn_isNotRichText() {
        // varchar 短文本即使叫 content 也不升级富文本（避免把备注/链接列变成编辑器）
        assertEquals("input", GenUtils.inferHtmlType("varchar(64)", "String", "content"));
        assertEquals("textarea", GenUtils.inferHtmlType("text", "String", "memo"));
        // 既有能力不受影响
        assertEquals("image", GenUtils.inferHtmlType("varchar(255)", "String", "cover"));
        assertEquals("multiImage", GenUtils.inferHtmlType("varchar(2000)", "String", "slider_images"));
    }

    @Test
    void sanitizer_acceptsRichTextHtmlType() throws Exception {
        // AI 推荐白名单：richtext 不再被 sanitize 打回 input
        JsonNode raw = MAPPER.readTree("""
                {"tables":[{"tableName":"bus_article","columns":[{"columnName":"content","htmlType":"richtext","sort":1}]}]}
                """);
        var vo = AiRecommendSanitizer.sanitize(raw,
                Map.of("bus_article", Set.of("content", "id")), Set.of());
        assertFalse(vo.getTables().isEmpty());
        assertEquals("richtext", vo.getTables().get(0).getColumns().get(0).getHtmlType());
    }

    // ------------------------------------------------------------------
    // ③ §4.6 主键 id 默认隐藏 + ④ §4.7 模型标志
    // ------------------------------------------------------------------

    @Test
    void model_showIdColumnDefaultsFalse_andRichTextFlagsAreDerived() {
        GenTable table = baseTable("bus_article", "文章", "BusArticle", "article");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(dbCol("id", "主键", "bigint", "id", "Long", 1, 0, 0, "input"));
        cols.add(dbCol("content", "正文", "text", "content", "String", 0, 1, 1, null)); // 走推断 → richtext
        cols.add(dbCol("cover", "封面", "varchar(255)", "cover", "String", 0, 1, 1, "image"));

        Map<String, Object> model = GenTemplateModel.build(table, cols);

        // §4.6：模板靠 showIdColumn 守卫，默认不渲染物理主键
        assertEquals(Boolean.FALSE, model.get("showIdColumn"));
        // §4.7：表级 hasRichText 为真（正文列触发）
        assertEquals(Boolean.TRUE, model.get("hasRichText"));

        List<Map<String, Object>> listCols = (List<Map<String, Object>>) model.get("listColumns");
        Map<String, Object> contentCol = listCols.stream()
                .filter(c -> "content".equals(c.get("javaField"))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, contentCol.get("isRichText"));
        Map<String, Object> coverCol = listCols.stream()
                .filter(c -> "cover".equals(c.get("javaField"))).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, contentCol.get("isUpload"));
        assertTrue(Boolean.TRUE.equals(coverCol.get("isImage")));
    }

    @Test
    void model_noRichTextColumn_hasRichTextIsFalse() {
        GenTable table = baseTable("demo_tag", "标签", "DemoTag", "tag");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(dbCol("id", "主键", "bigint", "id", "Long", 1, 0, 0, "input"));
        cols.add(dbCol("tag_name", "标签名称", "varchar(50)", "tagName", "String", 0, 1, 1, "input"));

        Map<String, Object> model = GenTemplateModel.build(table, cols);
        assertEquals(Boolean.FALSE, model.get("showIdColumn"));
        assertEquals(Boolean.FALSE, model.get("hasRichText"));
    }

    private static GenTable baseTable(String name, String comment, String className, String module) {
        GenTable t = new GenTable();
        t.setTableName(name);
        t.setTableComment(comment);
        t.setClassName(className);
        t.setModuleName(module);
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setGatewayPrefix("demo");
        t.setAuthor("codegen");
        t.setGenFrontend(1);
        return t;
    }

    private static GenTableColumn dbCol(String name, String comment, String dbType, String javaField,
                                        String javaType, int isPk, int isList, int isForm, String htmlType) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(isPk);
        c.setIsList(isList);
        c.setIsForm(isForm);
        c.setSort(1);
        if (htmlType != null) {
            c.setHtmlType(htmlType);
        }
        return c;
    }
}
