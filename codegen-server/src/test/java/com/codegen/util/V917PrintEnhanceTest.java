package com.codegen.util;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V9.17 打印增强（V4.5 能力包三态开关不变）：
 * <ul>
 *   <li>print_title：打印页标题表级覆盖 → 表注释回落；</li>
 *   <li>print_landscape：A4 横版（NULL/0=纵向默认，1=横向）；</li>
 *   <li>金额列人民币大写行：isMoneyColumn 判定 + printPage.ftl 内联 rmbUpper 纯 JS。</li>
 * </ul>
 *
 * <p>设计红线：所有新增块在模板里都有条件守卫——默认配置（print_landscape 空、无金额列、
 * 标题未覆盖）下产物必须逐字节零 diff，否则 10 个基线场景会无辜变红。
 * 因此本测试同时断言「开了有什么」与「没开时没有」两个方向。
 *
 * <p>大写转换算法本体是生成物里的 JS（rmbUpper/rmbCore），Java 侧无法直接单测；
 * 这里以模板渲染断言守住「函数在不在、调用对不对」，算法正确性已用 Node 冒烟
 * 33 例核验（1000→壹仟元整 / 105→壹佰零伍元整 / 1005→壹仟零伍元整 /
 * 10005→壹万零伍元整 / 1.05→壹元零伍分 / 0.3→叁角 / 10.5→壹拾元伍角 /
 * 9999999999999.99→玖万亿…玖角玖分；超 Number 安全整数区显式返回空串），见收尾报告。
 */
@DisplayName("V9.17 打印增强：print_title / 横版 / 金额大写")
class V917PrintEnhanceTest {

    private static Path tplDir;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        GenTemplateModel.setRootPkg("com.codegen");
    }

    private static String renderPrintPage(Map<String, Object> model) {
        try {
            String tpl = Files.readString(tplDir.resolve("printPage.ftl"), StandardCharsets.UTF_8);
            return GenTemplateEngine.renderString(tpl, model);
        } catch (IOException e) {
            fail("读取模板失败：" + e.getMessage());
            return "";
        } catch (Exception e) {
            fail("渲染 printPage.ftl 异常：" + e.getMessage());
            return "";
        }
    }

    // ------------------------------------------------------------------
    // isMoneyColumn：金额列语义判定（唯一实现）
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("金额列判定：宁缺毋滥（漏判少一行大写，误判把汇率也翻成人民币）")
    class MoneyColumnDetect {

        @Test
        @DisplayName("BigDecimal + 列名命中词表 → 是金额列")
        void bigDecimalWithAmountName() {
            assertTrue(GenTemplateModel.isMoneyColumn("amount", "金额", "BigDecimal"));
            assertTrue(GenTemplateModel.isMoneyColumn("total_price", "总价", "BigDecimal"));
            assertTrue(GenTemplateModel.isMoneyColumn("order_amt", "单据金额", "BigDecimal"));
        }

        @Test
        @DisplayName("BigDecimal + 注释兜底命中 → 是金额列")
        void bigDecimalWithCommentKeyword() {
            assertTrue(GenTemplateModel.isMoneyColumn("mny_col", "缴费金额", "BigDecimal"));
            assertTrue(GenTemplateModel.isMoneyColumn("c1", "押金", "BigDecimal"));
        }

        @Test
        @DisplayName("非 BigDecimal 一律不算金额列（Long amount 也算数量语义风险区）")
        void nonBigDecimalNeverMoney() {
            assertFalse(GenTemplateModel.isMoneyColumn("amount", "金额", "Long"),
                    "Long 型 amount 是 pay 基线场景的既有列，算成金额会让该基线无辜变化");
            assertFalse(GenTemplateModel.isMoneyColumn("qty", "数量", "Integer"));
            assertFalse(GenTemplateModel.isMoneyColumn("amount", "金额", "Double"));
        }

        @Test
        @DisplayName("BigDecimal 但语义无关（比率/数量）→ 不是金额列")
        void bigDecimalWithoutMoneySemantics() {
            assertFalse(GenTemplateModel.isMoneyColumn("rate", "汇率", "BigDecimal"));
            assertFalse(GenTemplateModel.isMoneyColumn("weight", "重量", "BigDecimal"));
        }
    }

    // ------------------------------------------------------------------
    // 模型键：printTitle / printLandscape / printRmb / child.moneyField
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("模板模型键")
    class ModelKeys {

        @Test
        @DisplayName("printTitle：表级覆盖优先，未配置回落表注释")
        void printTitleFallbackAndOverride() {
            GenTable table = masterTable();
            Map<String, Object> model = GenTemplateModel.build(table, masterCols(), List.of(childInfo(childTable())), null);
            assertEquals("测试单据", model.get("printTitle"), "未配置时应回落表注释");

            table.setPrintTitle("  销售出库单  ");
            model = GenTemplateModel.build(table, masterCols(), List.of(childInfo(childTable())), null);
            assertEquals("销售出库单", model.get("printTitle"), "表级覆盖生效（trim 后）");
        }

        @Test
        @DisplayName("printLandscape：默认 0（纵向），显式 1 才横版")
        void printLandscapeDefaultOff() {
            GenTable table = masterTable();
            Map<String, Object> model = GenTemplateModel.build(table, masterCols(), List.of(childInfo(childTable())), null);
            assertEquals(0, model.get("printLandscape"), "默认必须落 0，否则基线全部变化");

            table.setPrintLandscape(1);
            model = GenTemplateModel.build(table, masterCols(), List.of(childInfo(childTable())), null);
            assertEquals(1, model.get("printLandscape"));
        }

        @Test
        @DisplayName("printRmb：无金额列=false；明细金额列存在=true 且 child.moneyField 命中")
        void printRmbAndChildMoneyField() {
            Map<String, Object> model = GenTemplateModel.build(masterTable(), masterCols(),
                    List.of(childInfo(childTable())), null);
            assertFalse(Boolean.TRUE.equals(model.get("printRmb")),
                    "夹具无金额列时不得内联大写逻辑");

            GenTable moneyChild = childTable();
            GenTableColumn fee = col("fee_amount", "金额", "decimal(12,2)", "feeAmount", "BigDecimal", 1);
            List<GenTableColumn> childCols = List.of(
                    col("goods_name", "商品名", "varchar(100)", "goodsName", "String", 2),
                    fee);
            model = GenTemplateModel.build(masterTable(), masterCols(),
                    List.of(new GenTemplateModel.ChildInfo(moneyChild, childCols)), null);
            assertTrue(Boolean.TRUE.equals(model.get("printRmb")));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) model.get("children");
            assertEquals("feeAmount", children.get(0).get("moneyField"),
                    "明细大写行取最后一个金额列（单据行合计惯例在末列）");
        }
    }

    // ------------------------------------------------------------------
    // printPage.ftl 渲染断言
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("printPage.ftl 渲染")
    class PrintPageRender {

        @Test
        @DisplayName("默认配置产物零 diff 三要素：portrait、标题=注释、无大写代码")
        void defaultOutputUnchanged() {
            Map<String, Object> model = GenTemplateModel.build(masterTable(), masterCols(),
                    List.of(childInfo(childTable())), null);
            String out = renderPrintPage(model);
            assertTrue(out.contains("size: A4 portrait"), "默认必须纵向（契约测试钉死了这句）");
            assertFalse(out.contains("landscape"), "默认产物不得出现横版字样");
            assertTrue(out.contains("<div class=\"doc-title\">测试单据</div>"), "标题默认回落表注释");
            assertFalse(out.contains("rmbUpper"), "无金额列不得内联 rmbUpper（基线零 diff 红线）");
            assertFalse(out.contains("rmb-line"), "无金额列不得输出大写行样式");
        }

        @Test
        @DisplayName("print_title 覆盖后 doc-title 用覆盖值")
        void printTitleOverrideRenders() {
            GenTable table = masterTable();
            table.setPrintTitle("收款收据");
            Map<String, Object> model = GenTemplateModel.build(table, masterCols(),
                    List.of(childInfo(childTable())), null);
            String out = renderPrintPage(model);
            assertTrue(out.contains("<div class=\"doc-title\">收款收据</div>"));
            assertFalse(out.contains("测试单据"), "覆盖生效后表注释不得再出现在抬头");
        }

        @Test
        @DisplayName("横版：@page landscape + 预览 1123px 宽；且不再有 portrait")
        void landscapeRender() {
            GenTable table = masterTable();
            table.setPrintLandscape(1);
            Map<String, Object> model = GenTemplateModel.build(table, masterCols(),
                    List.of(childInfo(childTable())), null);
            String out = renderPrintPage(model);
            assertTrue(out.contains("size: A4 landscape"), "横版开关必须落到 @page size");
            assertFalse(out.contains("portrait"), "横版产物不得残留 portrait");
            assertTrue(out.contains("width: 1123px"), "屏幕预览应按 A4 横向宽度模拟");
        }

        @Test
        @DisplayName("金额大写：主表键值区大写行 + 明细块大写行 + rmbUpper 函数三处齐全")
        void moneyUppercaseRender() {
            GenTable table = masterTable();
            GenTableColumn amount = col("total_amount", "金额", "decimal(12,2)", "totalAmount", "BigDecimal", 1);
            GenTable moneyChild = childTable();
            List<GenTableColumn> childCols = List.of(
                    col("goods_name", "商品名", "varchar(100)", "goodsName", "String", 2),
                    col("fee_amount", "金额", "decimal(12,2)", "feeAmount", "BigDecimal", 1));

            Map<String, Object> model = GenTemplateModel.build(table,
                    new ArrayList<>(List.of(masterCol(), amount)),
                    List.of(new GenTemplateModel.ChildInfo(moneyChild, childCols)), null);
            String out = renderPrintPage(model);

            assertTrue(out.contains("rmbUpper(v) {"), "金额列存在时必须内联大写转换函数");
            assertTrue(out.contains("rmbUpper(master.totalAmount)"), "主表键值区缺金额大写行");
            assertTrue(out.contains("rmbUpper(colSum('demoOrderItem', 'feeAmount'))"),
                    "明细块缺人民币大写行，或取列不是最后一个金额列");
            assertTrue(out.contains("class=\"rmb-line\"") && out.contains("class=\"rmb-row\""),
                    "大写行样式与结构缺失");
        }

        @Test
        @DisplayName("孙表块大写行与子表块同口径")
        void grandMoneyUppercaseRender() {
            GenTable grand = childTable();
            grand.setTableName("demo_order_item_attr");
            grand.setClassName("DemoOrderItemAttr");
            List<GenTableColumn> grandCols = List.of(
                    col("attr_name", "属性名", "varchar(50)", "attrName", "String", 2),
                    col("gc_fee", "金额", "decimal(10,2)", "gcFee", "BigDecimal", 1));
            GenTable child = childTable();
            GenTemplateModel.ChildInfo grandInfo = new GenTemplateModel.ChildInfo(grand, grandCols, (GenTemplateModel.ChildInfo) null);
            GenTemplateModel.ChildInfo childInfo = new GenTemplateModel.ChildInfo(child,
                    List.of(col("goods_name", "商品名", "varchar(100)", "goodsName", "String", 2)), grandInfo);

            Map<String, Object> model = GenTemplateModel.build(masterTable(), masterCols(),
                    List.of(childInfo), null);
            String out = renderPrintPage(model);
            assertTrue(out.contains("rmbUpper(colSum('demoOrderItem.demoOrderItemAttr', 'gcFee'))"),
                    "孙表块缺大写行或键拼错（孙表 rowsOf 键是 child.gc 两段式）");
        }
    }

    // ------------------------------------------------------------------
    // 夹具：主表 + 子表（与 GenPipelineContractTest 打印契约同构，键名对齐基线）
    // ------------------------------------------------------------------

    private static GenTable masterTable() {
        GenTable t = new GenTable();
        t.setTableName("demo_order");
        t.setTableComment("测试单据");
        t.setClassName("DemoOrder");
        t.setModuleName("order");
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setGatewayPrefix("demo");
        t.setGenFrontend(1);
        t.setGenApiJs(1);
        t.setGenPrint(1);
        t.setGenDetailApi(1);
        return t;
    }

    private static List<GenTableColumn> masterCols() {
        return List.of(masterCol());
    }

    private static GenTableColumn masterCol() {
        return col("order_no", "订单号", "varchar(32)", "orderNo", "String", 2);
    }

    private static GenTable childTable() {
        GenTable t = new GenTable();
        t.setTableName("demo_order_item");
        t.setTableComment("订单明细");
        t.setClassName("DemoOrderItem");
        t.setModuleName("orderItem");
        t.setPackageName("com.demo");
        t.setMasterTableId(1L);
        t.setMasterForeignKey("order_id");
        t.setMasterDepth(1);
        return t;
    }

    private static GenTemplateModel.ChildInfo childInfo(GenTable child) {
        return new GenTemplateModel.ChildInfo(child,
                List.of(col("goods_name", "商品名", "varchar(100)", "goodsName", "String", 2)));
    }

    /** 非主键业务列速造：isList=1，isForm 按 form 标记 */
    private static GenTableColumn col(String name, String comment, String dbType,
                                      String javaField, String javaType, int isForm) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsList(1);
        c.setIsForm(isForm);
        c.setSort(2);
        if (isForm == 1) {
            c.setHtmlType("input");
        }
        return c;
    }
}
