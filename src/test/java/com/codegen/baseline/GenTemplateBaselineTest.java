package com.codegen.baseline;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 产物黄金基线：改模板后，产物到底变了什么。
 *
 * <p>为什么需要它：{@link com.codegen.template.TplRenderTest} 断言的是「某段该出现的代码在不在」，
 * 属于抽样检查——模板里多渲染一个空行、字段顺序变了、循环多跑一次，它全都看不见。
 * 而这类变化会原样落到每一个被接管工程里，等到目标工程编译或人眼 review 才发现。
 *
 * <p>本测试把三个典型场景的**完整产物**固定成基线文件，逐字符比对：
 * <ul>
 *   <li>{@code simple}：单表 3 列，基础 CRUD；</li>
 *   <li>{@code relation}：字典列 + 外键列 + 多选查询，覆盖 dict/ref 分支；</li>
 *   <li>{@code master-detail}：主子表 + 三级明细，覆盖 child/grandChild 分支。</li>
 * </ul>
 *
 * <p>两条设计红线：
 * <ol>
 *   <li><b>默认只读</b>：不传 {@code -Dgen.baseline.update=true} 时绝不写基线。
 *       否则「跑挂了就更新基线」会把真实回归永久抹掉，这是基线机制最常见的死法。</li>
 *   <li><b>换行统一按 LF 比对</b>：仓库开了 autocrlf，Windows 检出后模板是 CRLF，
 *       Linux CI 上是 LF。不归一化的话，同一份模板在两个平台产出不同字节，
 *       测试会变成「换个机器就红」的噪声源。</li>
 * </ol>
 *
 * <p>更新基线：
 * <pre>mvn test -Dtest=GenTemplateBaselineTest -Dgen.baseline.update=true</pre>
 * 更新后请把 {@code src/test/resources/gen-baseline/} 的 diff 一起提交，
 * 让「模板变了什么」在 review 里可见。
 */
@DisplayName("产物黄金基线：13 个模板 × 6 个场景逐字符比对")
class GenTemplateBaselineTest {

    private static final String UPDATE_FLAG = "gen.baseline.update";

    /** 模板与产物一一对应，顺序固定，保证失败信息与 diff 稳定 */
    private static final List<String> TEMPLATES = List.of(
            "entity", "mapper", "service", "serviceImpl", "pageDto", "saveDto",
            "vo", "controller", "apiJs", "vuePage", "printPage", "routerSnippet", "readme");

    private static Path tplDir;
    private static Path baselineRoot;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        baselineRoot = Path.of("src/test/resources/gen-baseline");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        // 模板里 import 的基座类按这个包名拼，等价于生产环境的 @PostConstruct
        GenTemplateModel.setRootPkg("com.codegen");
        if (!isUpdateMode()) {
            assertTrue(Files.isDirectory(baselineRoot),
                    "基线目录不存在：" + baselineRoot.toAbsolutePath()
                            + "\n首次生成请执行：mvn test -Dtest=GenTemplateBaselineTest -D"
                            + UPDATE_FLAG + "=true");
        }
    }

    @Test
    @DisplayName("simple：单表基础 CRUD 的 12 个产物")
    void simple() {
        compare("simple", modelSimple());
    }

    @Test
    @DisplayName("relation：字典 + 外键 + 多选查询的 12 个产物")
    void relation() {
        compare("relation", modelRelation());
    }

    @Test
    @DisplayName("master-detail：主子表 + 三级明细的 12 个产物")
    void masterDetail() {
        compare("master-detail", modelMasterDetail());
    }

    @Test
    @DisplayName("master-multichild：一主两子（其一含三级）的 12 个产物")
    void masterMultichild() {
        compare("master-multichild", modelMasterMultichild());
    }

    @Test
    @DisplayName("multi：多模块结构（simple 同表，projectStructure='multi'）的 13 个产物")
    void multi() {
        compare("multi", modelMulti());
    }

    @Test
    @DisplayName("micro：微服务设施（simple 同表，projectStructure='micro'）的 13 个产物")
    void micro() {
        compare("micro", modelMicro());
    }

    @Test
    @DisplayName("flow：流程绑定（simple 同表 + flowCode）的 13 个产物")
    void flow() {
        compare("flow", modelFlow());
    }

    @Test
    @DisplayName("pay：支付接入约定（表含 pay_status/amount 列）的 13 个产物")
    void pay() {
        compare("pay", modelPay());
    }

    @Test
    @DisplayName("file：文件能力包（表含 image/file htmlType 列 + 系统 fileEnabled=1）的 13 个产物")
    void file() {
        compare("file", modelFile());
    }

    @Test
    @DisplayName("test：测试骨架（simple 同表 + testEnabled=1）的 15 个产物（13 + 集成测试/H2建表）")
    void test() {
        compareWithTestExtras("test", modelTest());
    }

    /**
     * V7.4 P0-3（第四轮建议 1）：5 个共享模板以「空模型」渲染的产物快照。
     *
     * <p>这 5 个模板是能力包注入用的（{@code GenTemplateEngine.renderFlowShared /
     * renderPayShared} 内部就是 {@code Map.of()} 空模型渲染），一旦有人往模板里加
     * 需要模型提供的变量，渲染会抛异常并被 catch 成「注入失败」——整个能力包静默丢失。
     * 单有「能渲染」的断言不够：产物长什么样没人看得见，所以这里锁快照。
     */
    @Test
    @DisplayName("shared-empty：5 个共享模板（flow/pay 能力包）空模型产物快照")
    void sharedEmpty() {
        Map<String, String> actual = new LinkedHashMap<>();
        for (String name : List.of("flowApi", "flowTodo", "payApi", "payCashier", "payOrderPage")) {
            String content = read(tplDir.resolve(name + ".ftl"));
            try {
                actual.put(name, normalize(GenTemplateEngine.renderString(content, Map.of())));
            } catch (Exception ex) {
                fail("共享模板 " + name + " 空模型渲染失败：" + ex.getMessage());
            }
        }
        compareRendered("shared-empty", actual);
    }

    // ------------------------------------------------------------------
    // 比对与更新
    // ------------------------------------------------------------------

    private void compare(String caseName, Map<String, Object> model) {
        compareRendered(caseName, renderAll(model));
    }

    /** V6.7 test 场景用：在 13 个固定模板之外追加测试骨架两模板后比对 */
    private void compareWithTestExtras(String caseName, Map<String, Object> model) {
        Map<String, String> actual = renderAll(model);
        for (String tpl : List.of("crudControllerTest", "testSchemaTable")) {
            String content = read(tplDir.resolve(tpl + ".ftl"));
            try {
                actual.put(tpl, normalize(GenTemplateEngine.renderString(content, model)));
            } catch (Exception ex) {
                fail("渲染模板 " + tpl + " 失败：" + ex.getMessage());
            }
        }
        compareRendered(caseName, actual);
    }

    private void compareRendered(String caseName, Map<String, String> actual) {
        List<String> updated = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> e : actual.entrySet()) {
            String tpl = e.getKey();
            String text = e.getValue();
            Path file = baselineRoot.resolve(caseName).resolve(tpl + ".txt");

            if (isUpdateMode()) {
                writeIfChanged(file, text);
                updated.add(caseName + "/" + tpl + ".txt");
                continue;
            }
            if (!Files.exists(file)) {
                problems.add("缺少基线文件：" + file
                        + "\n  基线不全会让这个模板彻底失去保护，请用 -D" + UPDATE_FLAG + "=true 生成");
                continue;
            }
            String expected = read(file);
            if (!expected.equals(text)) {
                problems.add(describeDiff(caseName, tpl, expected, text));
            }
        }

        if (isUpdateMode()) {
            System.out.println("[baseline] 已更新 " + updated.size() + " 个文件：" + updated);
            return;
        }
        if (!problems.isEmpty()) {
            fail("产物与基线不一致（" + problems.size() + " 处）：\n\n"
                    + String.join("\n\n", problems)
                    + "\n\n如果这次改动是有意的，请执行：mvn test -Dtest=GenTemplateBaselineTest -D"
                    + UPDATE_FLAG + "=true，并提交 gen-baseline 的 diff");
        }
    }

    /** 渲染 12 个模板，key 为模板编码，value 为 LF 归一化后的产物 */
    private Map<String, String> renderAll(Map<String, Object> model) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String tpl : TEMPLATES) {
            // V4.4 分道：serviceImpl 是逻辑名，物理模板按形态派发（判据与引擎同源）
            String physical = "serviceImpl".equals(tpl)
                    ? GenTemplateModel.serviceImplTplCode(model) : tpl;
            String content = read(tplDir.resolve(physical + ".ftl"));
            try {
                out.put(tpl, normalize(GenTemplateEngine.renderString(content, model)));
            } catch (Exception ex) {
                fail("渲染模板 " + physical + " 失败：" + ex.getMessage());
            }
        }
        return out;
    }

    /** 首个差异位置 + 上下文，比 assert failed 有用得多 */
    private String describeDiff(String caseName, String tpl, String expected, String actual) {
        String[] a = expected.split("\n", -1);
        String[] b = actual.split("\n", -1);
        int firstDiff = 0;
        int max = Math.min(a.length, b.length);
        while (firstDiff < max && a[firstDiff].equals(b[firstDiff])) {
            firstDiff++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("产物不一致：").append(caseName).append('/').append(tpl).append(".txt")
                .append("（基线 ").append(a.length).append(" 行 / 当前 ").append(b.length)
                .append(" 行，首个差异在第 ").append(firstDiff + 1).append(" 行）\n");
        int from = Math.max(0, firstDiff - 3);
        int to = Math.min(Math.max(a.length, b.length), firstDiff + 12);
        for (int i = from; i < to; i++) {
            String left = i < a.length ? a[i] : "<无此行>";
            String right = i < b.length ? b[i] : "<无此行>";
            if (left.equals(right)) {
                sb.append(String.format("  %4d   %s%n", i + 1, left));
            } else {
                sb.append(String.format("- %4d   %s%n", i + 1, left));
                sb.append(String.format("+ %4d   %s%n", i + 1, right));
            }
        }
        return sb.toString();
    }

    private static void writeIfChanged(Path file, String text) {
        try {
            if (Files.exists(file) && read(file).equals(text)) {
                return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return normalize(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String normalize(String s) {
        String t = s.replace("\r\n", "\n").replace("\r", "\n");
        // 末尾空行差异不影响产物语义，但会让 diff 噪声变多，统一去掉
        while (t.endsWith("\n\n")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static boolean isUpdateMode() {
        return "true".equalsIgnoreCase(System.getProperty(UPDATE_FLAG, "false"));
    }

    // ------------------------------------------------------------------
    // 三个场景的模型
    // ------------------------------------------------------------------

    private static Map<String, Object> modelSimple() {
        GenTable table = baseTable("demo_tag", "标签", "DemoTag", "tag");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("tag_name", "标签名称", "varchar(50)", "tagName", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        cols.add(col("sort_no", "排序", "int", "sortNo", "Integer", 0, 0, 0, "EQ", 1, 1, 3));
        return GenTemplateModel.build(table, cols);
    }

    /** V5.5 多模块场景：与 simple 同表同列，仅 projectStructure='multi'，钉住模块前缀差异 */
    private static Map<String, Object> modelMulti() {
        return modelWithStructure("multi");
    }

    /** V6.0 微服务场景：与 multi 同表同列，仅 projectStructure='micro'，钉住 apiBase 网关化差异 */
    private static Map<String, Object> modelMicro() {
        return modelWithStructure("micro");
    }

    /** V6.1 流程绑定场景：simple 同表 + flowCode，钉住 hasFlow 的 controller/apiJs 块 */
    private static Map<String, Object> modelFlow() {
        GenTable table = baseTable("demo_leave", "请假单", "DemoLeave", "leave");
        table.setFlowCode("hr_leave_flow");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("reason", "请假事由", "varchar(200)", "reason", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        cols.add(col("status", "状态", "char(1)", "status", "String", 0, 0, 0, "EQ", 1, 1, 3));
        return GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null, null);
    }

    /** V6.3 支付接入场景：表含 pay_status + amount（分）列 + 系统 payEnabled=1，钉住 hasPayStatus 的 vuePage「去支付」块 */
    private static Map<String, Object> modelPay() {
        GenTable table = baseTable("demo_fee", "缴费单", "DemoFee", "fee");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("fee_item", "缴费项目", "varchar(100)", "feeItem", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        cols.add(col("amount", "金额", "bigint", "amount", "Long", 0, 1, 0, "EQ", 1, 1, 3));
        cols.add(col("pay_status", "支付状态", "tinyint", "payStatus", "Integer", 0, 0, 0, "EQ", 1, 1, 4));
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, 1);
        return GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null, project);
    }

    /** V6.6 文件能力包场景：商品图（image）+ 说明书（file）列 + 系统 fileEnabled=1，钉住 hasUpload 的上传控件/缩略图块 */
    private static Map<String, Object> modelFile() {
        GenTable table = baseTable("demo_goods_file", "商品资料", "DemoGoodsFile", "goodsFile");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("goods_name", "商品名称", "varchar(100)", "goodsName", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        GenTableColumn image = col("cover_image", "商品图", "varchar(255)", "coverImage", "String", 0, 1, 0, "EQ", 1, 1, 3);
        image.setHtmlType("image");
        cols.add(image);
        GenTableColumn manual = col("manual_file", "说明书", "varchar(255)", "manualFile", "String", 0, 1, 0, "EQ", 1, 1, 4);
        manual.setHtmlType("file");
        cols.add(manual);
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, null, null, 1);
        return GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null, project);
    }

    @Test
    @DisplayName("file：上传列只出现在子表时 hasUpload 也必须为 true（否则组件未注册）")
    void hasUploadIncludesChildColumns() {
        GenTable main = baseTable("demo_order", "订单", "DemoOrder", "order");
        List<GenTableColumn> mainCols = new ArrayList<>();
        mainCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        mainCols.add(col("order_no", "订单号", "varchar(32)", "orderNo", "String", 0, 1, 1, "LIKE", 1, 1, 2));

        GenTable child = baseTable("demo_order_item", "订单明细", "DemoOrderItem", "orderItem");
        child.setMasterForeignKey("order_id");
        List<GenTableColumn> childCols = new ArrayList<>();
        childCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        GenTableColumn itemImage = col("item_image", "明细图", "varchar(255)", "itemImage", "String",
                0, 1, 0, "EQ", 1, 1, 2);
        itemImage.setHtmlType("image");
        childCols.add(itemImage);

        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, null, null, 1);
        Map<String, Object> model = GenTemplateModel.build(main, mainCols,
                List.of(new GenTemplateModel.ChildInfo(child, childCols, (GenTemplateModel.ChildInfo) null)),
                project);
        assertTrue(Boolean.TRUE.equals(model.get("hasUpload")),
                "主表无上传列、子表有 image 列时 hasUpload 必须为 true");
    }

    private static Map<String, Object> modelWithStructure(String structure) {
        GenTable table = baseTable("demo_tag", "标签", "DemoTag", "tag");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("tag_name", "标签名称", "varchar(50)", "tagName", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        cols.add(col("sort_no", "排序", "int", "sortNo", "Integer", 0, 0, 0, "EQ", 1, 1, 3));
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, structure);
        return GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null, project);
    }

    private static Map<String, Object> modelRelation() {
        GenTable table = baseTable("demo_goods", "商品", "DemoGoods", "goods");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("goods_name", "商品名称", "varchar(100)", "goodsName", "String", 0, 1, 1, "LIKE", 1, 1, 2));

        GenTableColumn status = col("status", "状态", "tinyint", "status", "Integer", 0, 1, 1, "EQ", 1, 1, 3);
        status.setHtmlType("radio");
        status.setDictType("sys_status");
        cols.add(status);

        // 外键列：refTable/refLabel 决定是否走远程下拉与多选回显
        GenTableColumn dept = col("dept_id", "所属部门", "bigint", "deptId", "Long", 0, 0, 1, "EQ", 1, 1, 4);
        dept.setHtmlType("select");
        dept.setRefTable("sys_dept");
        dept.setRefLabel("dept_name");
        cols.add(dept);

        GenTableColumn price = col("price", "价格", "decimal(10,2)", "price", "BigDecimal", 0, 0, 1, "GE", 1, 1, 5);
        price.setHtmlType("number");
        cols.add(price);

        return GenTemplateModel.build(table, cols);
    }

    private static Map<String, Object> modelMasterDetail() {
        GenTable main = baseTable("demo_order", "订单", "DemoOrder", "order");
        List<GenTableColumn> mainCols = new ArrayList<>();
        mainCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        mainCols.add(col("order_no", "订单号", "varchar(32)", "orderNo", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        mainCols.add(col("amount", "金额", "decimal(12,2)", "amount", "BigDecimal", 0, 0, 0, "EQ", 1, 1, 3));

        GenTable child = baseTable("demo_order_item", "订单明细", "DemoOrderItem", "orderItem");
        child.setMasterForeignKey("order_id");
        child.setMasterDepth(1);
        List<GenTableColumn> childCols = new ArrayList<>();
        childCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        childCols.add(col("goods_name", "商品名称", "varchar(100)", "goodsName", "String", 0, 1, 0, "EQ", 1, 1, 2));
        childCols.add(col("qty", "数量", "int", "qty", "Integer", 0, 1, 0, "EQ", 1, 1, 3));

        GenTable grand = baseTable("demo_order_item_attr", "明细属性", "DemoOrderItemAttr", "orderItemAttr");
        grand.setMasterForeignKey("item_id");
        grand.setMasterDepth(2);
        List<GenTableColumn> grandCols = new ArrayList<>();
        grandCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        grandCols.add(col("attr_name", "属性名", "varchar(50)", "attrName", "String", 0, 1, 0, "EQ", 1, 1, 2));
        grandCols.add(col("attr_value", "属性值", "varchar(100)", "attrValue", "String", 0, 0, 0, "EQ", 1, 1, 3));

        // V4.4 后 ChildInfo 有了 List 重载，裸 null 会产生二义性，必须显式转型
        GenTemplateModel.ChildInfo grandInfo = new GenTemplateModel.ChildInfo(grand, grandCols, (GenTemplateModel.ChildInfo) null);
        GenTemplateModel.ChildInfo childInfo = new GenTemplateModel.ChildInfo(child, childCols, grandInfo);
        return GenTemplateModel.build(main, mainCols, childInfo);
    }

    /**
     * V4.4 一主多子：订单 → 订单明细（含三级属性） + 操作日志（无下级）。
     * 覆盖 children[] 循环、多子各自 CRUD、孙层只出现在带它的那张子表。
     */
    private static Map<String, Object> modelMasterMultichild() {
        GenTable main = baseTable("demo_order", "订单", "DemoOrder", "order");
        List<GenTableColumn> mainCols = new ArrayList<>();
        mainCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        mainCols.add(col("order_no", "订单号", "varchar(32)", "orderNo", "String", 0, 1, 1, "LIKE", 1, 1, 2));

        GenTable child = baseTable("demo_order_item", "订单明细", "DemoOrderItem", "orderItem");
        child.setMasterForeignKey("order_id");
        List<GenTableColumn> childCols = new ArrayList<>();
        childCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        childCols.add(col("goods_name", "商品名称", "varchar(100)", "goodsName", "String", 0, 1, 0, "EQ", 1, 1, 2));
        childCols.add(col("qty", "数量", "int", "qty", "Integer", 0, 1, 0, "EQ", 1, 1, 3));

        GenTable grand = baseTable("demo_order_item_attr", "明细属性", "DemoOrderItemAttr", "orderItemAttr");
        grand.setMasterForeignKey("item_id");
        List<GenTableColumn> grandCols = new ArrayList<>();
        grandCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        grandCols.add(col("attr_name", "属性名", "varchar(50)", "attrName", "String", 0, 1, 0, "EQ", 1, 1, 2));

        GenTable log = baseTable("demo_order_log", "操作日志", "DemoOrderLog", "orderLog");
        log.setMasterForeignKey("order_id");
        List<GenTableColumn> logCols = new ArrayList<>();
        logCols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        logCols.add(col("log_text", "日志内容", "varchar(200)", "logText", "String", 0, 1, 0, "EQ", 1, 1, 2));

        GenTemplateModel.ChildInfo grandInfo = new GenTemplateModel.ChildInfo(grand, grandCols, (GenTemplateModel.ChildInfo) null);
        GenTemplateModel.ChildInfo itemInfo = new GenTemplateModel.ChildInfo(child, childCols, grandInfo);
        GenTemplateModel.ChildInfo logInfo = new GenTemplateModel.ChildInfo(log, logCols, (GenTemplateModel.ChildInfo) null);
        return GenTemplateModel.build(main, mainCols, List.of(itemInfo, logInfo), null);
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
        // V4.5 打印单据：基线场景全部开着渲染（4 场景 × 13 模板 = 52 份），
        // 单表场景打印 = 详情键值页，正好覆盖 printPage 的无子表分支
        t.setGenPrint(1);
        return t;
    }

    private static GenTableColumn col(String name, String comment, String dbType, String javaField,
                                      String javaType, int isPk, int isRequired, int isQuery,
                                      String queryType, int isList, int isForm, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(name);
        c.setColumnComment(comment);
        c.setColumnType(dbType);
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(isPk);
        c.setIsRequired(isRequired);
        c.setIsQuery(isQuery);
        c.setQueryType(queryType);
        c.setIsList(isList);
        c.setIsForm(isForm);
        c.setSort(sort);
        if (isPk == 0 && isForm == 1) {
            c.setHtmlType("input");
        }
        return c;
    }

    /** V6.7 测试骨架场景：simple 同表 + testEnabled=1（缺省开），钉住集成测试与 H2 建表脚本 */
    private static Map<String, Object> modelTest() {
        GenTable table = baseTable("demo_tag", "标签", "DemoTag", "tag");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("tag_name", "标签名称", "varchar(50)", "tagName", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        cols.add(col("sort_no", "排序", "int", "sortNo", "Integer", 0, 0, 0, "EQ", 1, 1, 3));
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, null, null, null, 1);
        return GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null, project);
    }

    @Test
    void hasControllerTestGatedBySwitch() {
        GenTable table = baseTable("demo_tag", "标签", "DemoTag", "tag");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 0, 0, 1));
        cols.add(col("tag_name", "标签名称", "varchar(50)", "tagName", "String", 0, 1, 1, "LIKE", 1, 1, 2));
        // 缺省（null）= 开
        Map<String, Object> on = GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null,
                new GenTemplateModel.ProjectConfig(null, null, "com.codegen", "demo",
                        null, null, null, null, null, null, null));
        assertTrue(Boolean.TRUE.equals(on.get("hasControllerTest")), "test_enabled 未配置（NULL）必须默认开");
        // 显式关闭
        Map<String, Object> off = GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null,
                new GenTemplateModel.ProjectConfig(null, null, "com.codegen", "demo",
                        null, null, null, null, null, null, 0));
        assertTrue(Boolean.FALSE.equals(off.get("hasControllerTest")), "test_enabled=0 必须关闭测试骨架");
        // multi/micro 不出测试（启动类在 bootstrap，跨模块 @SpringBootTest 落点 P1）
        Map<String, Object> multi = GenTemplateModel.build(table, cols, (java.util.List<GenTemplateModel.ChildInfo>) null,
                new GenTemplateModel.ProjectConfig(null, null, "com.codegen", "demo",
                        null, null, "multi", null, null, null, 1));
        assertTrue(Boolean.FALSE.equals(multi.get("hasControllerTest")), "multi 工程暂不出测试骨架（P1）");
    }

    /** 兜底：基线目录里出现多余文件时提示（改名/删模板后遗留） */
    @Test
    @DisplayName("基线目录没有孤儿文件")
    void noOrphanBaseline() throws IOException {
        if (!Files.isDirectory(baselineRoot) || isUpdateMode()) {
            return;
        }
        List<String> known = new ArrayList<>();
        for (String caseName : List.of("simple", "relation", "master-detail", "master-multichild", "multi", "micro", "flow", "pay", "file", "test")) {
            for (String tpl : TEMPLATES) {
                known.add(caseName + "/" + tpl + ".txt");
            }
            // V6.7 test 场景在 13 个固定模板外追加测试骨架两模板（compareWithTestExtras 同源）
            if ("test".equals(caseName)) {
                known.add("test/crudControllerTest.txt");
                known.add("test/testSchemaTable.txt");
            }
        }
        // V7.4：共享模板（能力包注入用）走空模型快照，不在 TEMPLATES 13 个之列
        for (String name : List.of("flowApi", "flowTodo", "payApi", "payCashier", "payOrderPage")) {
            known.add("shared-empty/" + name + ".txt");
        }
        try (Stream<Path> walk = Files.walk(baselineRoot)) {
            List<String> orphans = walk.filter(Files::isRegularFile)
                    .map(p -> baselineRoot.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !known.contains(p))
                    .sorted(Comparator.naturalOrder())
                    .toList();
            assertTrue(orphans.isEmpty(),
                    () -> "基线目录存在多余文件（模板删改后未清理，会掩盖真实回归）：" + orphans);
        }
    }
}
