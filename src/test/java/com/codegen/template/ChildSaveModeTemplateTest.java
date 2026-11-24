package com.codegen.template;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.18 子表保存模式：用真实 serviceImplMasterdetail.ftl 渲染断言两条分支。
 *
 * <p>红线约束：childSaveMode=0 的完整产物字节级不变由
 * {@code GenTemplateBaselineTest}（gen-baseline 快照）负责；
 * 本测试只补「两个分支互不串味」：0 分支不得出现任何增量代码，1 分支不得再 delete-all。
 */
@DisplayName("V9.18 子表保存模式：全量替换 / 增量合并两条模板分支")
class ChildSaveModeTemplateTest {

    private static Path tplDir;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        GenTemplateModel.setRootPkg("com.codegen");
    }

    /** 一主三子：一对多（含三级）、一对一、多对多中间表 */
    private static Map<String, Object> model(int childSaveMode) {
        GenTable main = baseTable("v918_order", "订单", "V918Order", "order");
        main.setChildSaveMode(childSaveMode);
        List<GenTableColumn> mainCols = new ArrayList<>();
        mainCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, 1, 0, 1));
        mainCols.add(col("order_no", "订单号", "varchar(32)", "orderNo", "String", 0, 1, 1, 1, 1, 2));

        // 1) 一对多 + 三级
        GenTable item = baseTable("v918_order_item", "订单明细", "V918OrderItem", "orderItem");
        item.setMasterForeignKey("order_id");
        List<GenTableColumn> itemCols = new ArrayList<>();
        itemCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, 0, 0, 1));
        itemCols.add(col("goods_name", "商品名称", "varchar(100)", "goodsName", "String", 0, 1, 1, 1, 1, 2));
        GenTable itemAttr = baseTable("v918_order_item_attr", "明细属性", "V918OrderItemAttr", "orderItemAttr");
        itemAttr.setMasterForeignKey("item_id");
        List<GenTableColumn> attrCols = new ArrayList<>();
        attrCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, 0, 0, 1));
        attrCols.add(col("attr_name", "属性名", "varchar(50)", "attrName", "String", 0, 1, 1, 1, 1, 2));
        GenTemplateModel.ChildInfo attrInfo = new GenTemplateModel.ChildInfo(itemAttr, attrCols);
        GenTemplateModel.ChildInfo itemInfo = new GenTemplateModel.ChildInfo(item, itemCols, attrInfo);

        // 2) 一对一
        GenTable profile = baseTable("v918_order_profile", "订单档案", "V918OrderProfile", "orderProfile");
        profile.setMasterForeignKey("order_id");
        profile.setRelationType("ONE_TO_ONE");
        List<GenTableColumn> profileCols = new ArrayList<>();
        profileCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, 0, 0, 1));
        profileCols.add(col("remark", "备注", "varchar(200)", "remark", "String", 0, 0, 0, 1, 1, 2));
        GenTemplateModel.ChildInfo profileInfo = new GenTemplateModel.ChildInfo(profile, profileCols);

        // 3) 多对多中间表：order_id 是主表外键，tag_id 是关联键（relationKey=1 且进表单）
        GenTable tagRel = baseTable("v918_order_tag", "订单标签", "V918OrderTag", "orderTag");
        tagRel.setMasterForeignKey("order_id");
        tagRel.setRelationType("middle");
        List<GenTableColumn> tagCols = new ArrayList<>();
        tagCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, 0, 0, 1));
        tagCols.add(col("order_id", "订单ID", "bigint", "orderId", "Long", 0, 0, 0, 0, 0, 2));
        GenTableColumn tagId = col("tag_id", "标签ID", "bigint", "tagId", "Long", 0, 0, 0, 1, 1, 3);
        tagId.setRelationKey(1);
        tagCols.add(tagId);
        GenTemplateModel.ChildInfo tagInfo = new GenTemplateModel.ChildInfo(tagRel, tagCols);

        return GenTemplateModel.build(main, mainCols, List.of(itemInfo, profileInfo, tagInfo), null);
    }

    private static String render(String tpl, Map<String, Object> m) throws Exception {
        String src = Files.readString(tplDir.resolve(tpl), StandardCharsets.UTF_8);
        return GenTemplateEngine.renderString(src, m);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            n++;
            idx += needle.length();
        }
        return n;
    }

    @Test
    @DisplayName("mode=0：产物仍是全量替换，不含任何增量合并代码")
    void fullReplaceUnchanged() throws Exception {
        String out = render("serviceImplMasterdetail.ftl", model(0));
        assertEquals(3, count(out, "childMapper.delete(new LambdaQueryWrapper"),
                "三张子表都走「先按外键删」");
        assertFalse(out.contains("keepIds"), "全量替换不得出现三路合并的保留集合");
        assertFalse(out.contains("deleteBatchIds"), "全量替换不得出现差集删除");
        assertFalse(out.contains("三路合并"), "全量替换不得出现增量合并注释");
        assertTrue(out.contains("全量替换"), "保留原有 Javadoc");
    }

    @Test
    @DisplayName("mode=1 一对多：按主键三路合并，无 delete-all，保留集合参与差集删除")
    void incrementalOneToMany() throws Exception {
        String out = render("serviceImplMasterdetail.ftl", model(1));
        assertTrue(out.contains("三路合并·新增"), "新增分支");
        assertTrue(out.contains("三路合并·更新"), "更新分支");
        assertTrue(out.contains("三路合并·删除"), "删除分支");
        assertTrue(out.contains("keepIds"), "保留集合");
        assertTrue(out.contains("childMapper.updateById(matched)"), "变更走 update 而非重建");
        assertTrue(out.contains("childMapper.deleteBatchIds(removeIds)"), "缺失行按主键集合删除");
        assertTrue(out.contains("List<Long> removeIds = new ArrayList<>()"), "差集删除集合");
        // 三级同样走三路合并
        assertTrue(out.contains("增量保存：按主键三路合并，避免三层明细整体重建"), "孙层也增量");
    }

    @Test
    @DisplayName("mode=1 一对一：按外键 upsert，存在则更新既有行，不存在才插入")
    void incrementalOneToOne() throws Exception {
        String out = render("serviceImplMasterdetail.ftl", model(1));
        assertTrue(out.contains("一对一）：外键唯一，已存在则更新、不存在则插入"), "一对一 Javadoc");
        assertTrue(out.contains("V918OrderProfileSaveDTO dto = rows.get(0);"), "一对一取首行");
        assertTrue(out.contains("V918OrderProfile child = existing.get(0);"), "命中既有行");
        assertTrue(out.contains("Long existingId = child.getId();"), "锁定既有主键");
        assertTrue(out.contains("childMapper.updateById(child);"), "更新而非插入第二行");
    }

    @Test
    @DisplayName("mode=1 多对多：按关联键差集增删，已有关联不再插入（防重复关联行）")
    void incrementalManyToMany() throws Exception {
        String out = render("serviceImplMasterdetail.ftl", model(1));
        assertTrue(out.contains("多对多中间表）：按关联键做差集"), "多对多 Javadoc");
        assertTrue(out.contains("Object key = dto.getTagId();"), "关联键取自 DTO");
        assertTrue(out.contains("existingKeys.contains(key)"), "已有关联跳过插入");
        assertTrue(out.contains("!incomingKeys.contains(old.getTagId())"), "缺失关联进入删除");
        assertTrue(out.contains("List<Object> incomingKeys = new ArrayList<>()"), "关联键去重集合");
    }

    @Test
    @DisplayName("mode=1 引入 ArrayList 导入；mode=0 且非树形时不多引入（零 diff 前提）")
    void arrayListImportGated() throws Exception {
        assertTrue(render("serviceImplMasterdetail.ftl", model(1)).contains("import java.util.ArrayList;"));
        assertTrue(render("serviceImplMasterdetail.ftl", model(0)).contains("import java.util.List;"));
        assertFalse(render("serviceImplMasterdetail.ftl", model(0)).contains("import java.util.ArrayList;"),
                "全量替换分支不引入 ArrayList（与 V9.18 前产物一致）");
    }

    private static GenTable baseTable(String name, String comment, String className, String module) {
        GenTable t = new GenTable();
        t.setTableName(name);
        t.setTableComment(comment);
        t.setClassName(className);
        t.setModuleName(module);
        t.setPackageName("com.demo");
        t.setAuthor("codegen");
        t.setGenEntity(1);
        t.setGenMapper(1);
        t.setGenService(1);
        t.setGenController(1);
        t.setGenPageApi(1);
        t.setGenDetailApi(1);
        t.setGenAddApi(1);
        t.setGenEditApi(1);
        t.setGenDeleteApi(1);
        t.setGenFrontend(1);
        t.setGenApiJs(1);
        t.setGenVuePage(1);
        t.setGenQueryForm(1);
        t.setGenAddBtn(1);
        t.setGenEditBtn(1);
        t.setGenDeleteBtn(1);
        t.setGenLog(1);
        return t;
    }

    private static GenTableColumn col(String name, String comment, String dbType, String javaField,
                                      String javaType, int isPk, int isRequired, int isQuery,
                                      int isList, int isForm, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(isPk);
        c.setIsRequired(isRequired);
        c.setIsQuery(isQuery);
        c.setQueryType(isQuery == 1 ? "EQ" : null);
        c.setIsList(isList);
        c.setIsForm(isForm);
        c.setSort(sort);
        return c;
    }
}
