package com.codegen.template;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 第四轮走查契约：共享模板空模型渲染 + 生成物「前端 URL / 后端 import / 响应体」同源。
 *
 * <p>为什么需要它：{@code GenTemplateEngine.renderFlowShared/renderPayShared} 用
 * {@code Map.of()} 空模型渲染共享模板，且异常被 catch 成空 Map —— 模板里只要有一处
 * JS 模板字符串写成裸 {@code ${var}}，FreeMarker 就会求值失败，整个能力包（flow.js、
 * 待办页、路由片段）被静默丢弃，编译与基线测试都发现不了。
 *
 * <p>另一类失配是「模板各自改了一半」：前端拼 {@code /{tableName}/xxx} 而后端挂在
 * {@code /{moduleName}/xxx}；或 {@code hasFlow} 分支用了 7 个类型，import 门控却只看
 * CRUD 开关。这类问题只有把两端放在同一个断言里才会暴露。
 *
 * <p>不连数据库、不起 Spring：只读 .ftl + 纯函数式构模型。
 */
@DisplayName("模板共享渲染与生成物同源契约")
class GenTemplateContractTest {

    private static Path tplDir;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        GenTemplateModel.setRootPkg("com.codegen");
    }

    /** 五个共享模板都必须能被空模型渲染；flowApi 的 JS 模板字符串要保留字面 ${taskId} */
    @Test
    @DisplayName("共享模板空模型渲染不抛，flow 详情 URL 保留字面插值")
    void sharedTemplatesRenderWithEmptyModel() {
        for (String name : List.of("flowApi", "flowTodo", "payApi", "payCashier", "payOrderPage")) {
            String out = render(name, Map.of());
            assertTrue(out.length() > 20, name + " 用空模型渲染产物不应为空");
        }
        assertTrue(render("flowApi", Map.of()).contains("/flow/task/${taskId}"),
                "flowApi 必须保留 JS 字面 ${taskId}；写成裸 ${taskId} 会被 FreeMarker 当变量求值，"
                        + "导致 renderFlowShared 抛异常后静默丢弃整个 V6.1 能力包");
    }

    /** 查重与导入失败明细是模块级端点，前端必须拼 apiBase 而不是 tableName */
    @Test
    @DisplayName("vuePage 查重/失败明细 URL 与 controller 的 /{moduleName} 对齐")
    void vuePageNonModuleEndpointsUseApiBase() {
        GenTable table = table("demo_tag", "tag", "DemoTag");
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "id", "Long", 1));
        GenTableColumn name = col("tag_name", "tagName", "String", 2);
        name.setUniqueFlag(1);      // hasSingleUnique=true → 生成 uniqueValidator
        name.setExcelImport(1);     // hasExcelImport=true → 生成 downloadFailed
        cols.add(name);

        String out = render("vuePage", GenTemplateModel.build(table, cols));
        assertTrue(out.contains("'/api/demo/tag/check-unique'"),
                "查重 URL 必须挂在 apiBase（=/api/demo/tag），后端是 @RequestMapping(\"/tag\") + /check-unique");
        assertTrue(out.contains("'/api/demo/tag/import/failed'"),
                "导入失败明细 URL 必须挂在 apiBase，后端是 @RequestMapping(\"/tag\") + /import/failed");
        assertFalse(out.contains("/demo_tag/check-unique"), "不得再用 tableName 拼请求 URL（必 404）");
        assertFalse(out.contains("/demo_tag/import/failed"), "不得再用 tableName 拼请求 URL（必 404）");
    }

    /** hasFlow 单独开启时，审批流分支用到的 7 类 import 必须齐全 */
    @Test
    @DisplayName("仅开 hasFlow 时 controller 的 import 门控不能漏")
    void controllerImportsCoverFlowOnly() {
        GenTable table = table("demo_leave", "leave", "DemoLeave");
        table.setFlowCode("hr_leave_flow");
        table.setGenPageApi(0);
        table.setGenDetailApi(0);
        table.setGenAddApi(0);
        table.setGenEditApi(0);
        table.setGenDeleteApi(0);
        Map<String, Object> model = GenTemplateModel.build(table, List.of(col("id", "id", "Long", 1)));

        // 前提：这个模型确实只靠 hasFlow 打开了分支，否则断言等于没测
        assertTrue(Boolean.TRUE.equals(model.get("hasFlow")), "构造模型必须开启 hasFlow");
        String out = render("controller", model);
        for (String imp : List.of(
                "import org.springframework.web.bind.annotation.GetMapping;",
                "import org.springframework.web.bind.annotation.PostMapping;",
                "import org.springframework.web.bind.annotation.RequestParam;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import java.util.LinkedHashMap;",
                "import java.util.Map;")) {
            assertTrue(out.contains(imp), "仅开 hasFlow 时 flowRecords/flowSubmit 分支仍需 " + imp);
        }
    }

    /** hasExcelImport 单独开启时，/import-template 与 /import/failed 的 @GetMapping 需要 import */
    @Test
    @DisplayName("仅开 hasExcelImport 时 controller 必须 import GetMapping")
    void controllerImportsCoverExcelImportOnly() {
        GenTable table = table("demo_tag", "tag", "DemoTag");
        table.setGenPageApi(0);
        table.setGenDetailApi(0);
        table.setGenEditApi(0);
        table.setGenDeleteApi(0);
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "id", "Long", 1));
        GenTableColumn name = col("tag_name", "tagName", "String", 2);
        name.setExcelImport(1);
        cols.add(name);
        Map<String, Object> model = GenTemplateModel.build(table, cols);

        assertTrue(Boolean.TRUE.equals(model.get("hasExcelImport")), "构造模型必须开启 hasExcelImport");
        String out = render("controller", model);
        assertTrue(out.contains("import org.springframework.web.bind.annotation.GetMapping;"),
                "Excel 导入分支的 @GetMapping(\"/import-template\") 依赖该 import");
    }

    /** 收银台 import 的函数必须真被 payApi 导出，且按 Result 取 data */
    @Test
    @DisplayName("支付渠道接口已导出，收银台按 res.data 取渠道字符串")
    void payChannelContract() {
        String api = render("payApi", Map.of());
        assertTrue(api.contains("export function getPayChannel"),
                "payCashier.ftl import 了 getPayChannel，payApi.ftl 必须真的导出，否则 mounted() 抛 TypeError 被吞");
        assertTrue(api.contains("url: '/pay/channel'"), "渠道接口必须与后端 GET /pay/channel 对齐");

        String cashier = render("payCashier", Map.of());
        assertTrue(cashier.contains("res.data || 'mock'"),
                "拦截器 return res（整个 body），Result<String> 的渠道值在 res.data，不能取 res.channel");
    }

    /** F-01：hasFlow 与 CRUD 开关任意组合时，import 块相邻两项之间必须有逗号 */
    @Test
    @DisplayName("关闭删除、开启审批流时 vuePage 的 import 块不能缺逗号")
    void vuePageImportCommaWithFlowWithoutDelete() {
        GenTable table = table("demo_leave", "leave", "DemoLeave");
        table.setFlowCode("hr_leave_flow");
        table.setGenDeleteApi(0);
        String out = render("vuePage", GenTemplateModel.build(table, List.of(col("id", "id", "Long", 1))));
        assertCommaBetween(out, "updateDemoLeave", "flowSubmitDemoLeave",
                "genEditApi + hasFlow 且 genDeleteApi=0：update 后缺逗号会让整个 script 语法错误");

        // 只开新增、不开编辑/删除时，衔接 flow 函数的同样变成 create 这一行
        table.setGenEditApi(0);
        String out2 = render("vuePage", GenTemplateModel.build(table, List.of(col("id", "id", "Long", 1))));
        assertCommaBetween(out2, "createDemoLeave", "flowSubmitDemoLeave",
                "仅 genAddApi + hasFlow：create 后缺逗号会让整个 script 语法错误");
    }

    /** F-02：genPageApi=0 时 methods 落 <#else>，但筛选区/分页条仍绑定三个方法 */
    @Test
    @DisplayName("未开列表接口时筛选区按钮绑定的方法仍要有定义")
    void vuePageElseBranchDefinesSearchHandlers() {
        GenTable table = table("demo_tag", "tag", "DemoTag");
        table.setGenPageApi(0);
        table.setGenDetailApi(0);
        table.setGenAddApi(0);
        table.setGenEditApi(0);
        table.setGenDeleteApi(0);
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "id", "Long", 1));
        GenTableColumn q = col("tag_name", "tagName", "String", 2);
        q.setIsQuery(1);
        cols.add(q);

        String out = render("vuePage", GenTemplateModel.build(table, cols));
        assertTrue(out.contains("@click=\"handleSearch\""), "前提：筛选区确实渲染了查询按钮");
        assertTrue(out.contains("async fetchList() {}"), "前提：methods 必须落在 <#else> 分支");
        assertTrue(out.contains("handleSearch() {}"), "<#else> 分支缺 handleSearch，点击即 TypeError");
        assertTrue(out.contains("handleReset() {}"), "<#else> 分支缺 handleReset，点击即 TypeError");
        assertTrue(out.contains("handleSizeChange() {}"), "<#else> 分支缺 handleSizeChange，翻页即 TypeError");
    }

    /** F-03：hasMultiImage 必须按列级 isMultiImage 统计，并覆盖子表/孙表 */
    @Test
    @DisplayName("多图门控按列级 isMultiImage 且覆盖子表，不能只按主表列名猜")
    void multiImageGateUsesColumnModel() {
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                "com.demo", "demo", "com.demo", null, null, null, null, null, null, 1, null);

        // 显式 htmlType=multiImage 但列名不含 images/pics 语义：旧实现按列名猜会漏
        List<GenTableColumn> cols = new ArrayList<>();
        cols.add(col("id", "id", "Long", 1));
        GenTableColumn attach = col("attachment", "attachment", "String", 2);
        attach.setHtmlType("multiImage");
        cols.add(attach);
        Map<String, Object> model = GenTemplateModel.build(table("demo_goods", "goods", "DemoGoods"),
                cols, List.<GenTemplateModel.ChildInfo>of(), project);
        assertTrue(Boolean.TRUE.equals(model.get("hasMultiImage")),
                "显式 htmlType=multiImage 的列必须打开多图门控");
        String out = render("vuePage", model);
        assertTrue(out.contains("import MultiImageUpload"), "门控打开时必须注册多图组件");
        assertTrue(out.contains("imageListOf(raw)"), "门控打开时必须生成图片解析方法");

        // 多图列只在子表：主表 biz 一列多图都没有
        GenTableColumn childPhoto = col("sku_photo", "skuPhoto", "String", 2);
        childPhoto.setHtmlType("multiImage");
        GenTemplateModel.ChildInfo child = new GenTemplateModel.ChildInfo(
                table("demo_goods_item", "goods", "DemoGoodsItem"),
                List.of(col("id", "id", "Long", 1), childPhoto), List.of());
        Map<String, Object> model2 = GenTemplateModel.build(table("demo_goods", "goods", "DemoGoods"),
                List.of(col("id", "id", "Long", 1)), List.of(child), project);
        assertTrue(Boolean.TRUE.equals(model2.get("hasMultiImage")),
                "子表多图列同样会渲染 <multi-image-upload>，门控必须覆盖子表");
    }

    private static void assertCommaBetween(String out, String from, String to, String msg) {
        int a = out.indexOf(from);
        int b = out.indexOf(to);
        assertTrue(a >= 0 && b > a, msg + "（前提：两项都要出现在 import 块里）");
        assertTrue(out.substring(a, b).contains(","), msg);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String render(String tplCode, Map<String, Object> model) {
        String content;
        try {
            content = Files.readString(tplDir.resolve(tplCode + ".ftl"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return GenTemplateEngine.renderString(content, model);
        } catch (Exception e) {
            fail("渲染模板 " + tplCode + " 失败：" + e.getMessage());
            return "";
        }
    }

    private static GenTable table(String name, String module, String className) {
        GenTable t = new GenTable();
        t.setTableName(name);
        t.setTableComment("演示表");
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
        t.setGenPrint(1);
        return t;
    }

    private static GenTableColumn col(String columnName, String javaField, String javaType, int sort) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(columnName);
        c.setColumnComment(columnName);
        c.setColumnType("varchar(50)");
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(sort == 1 ? 1 : 0);
        c.setIsRequired(0);
        c.setIsQuery(0);
        c.setQueryType("EQ");
        c.setIsList(1);
        c.setIsForm(1);
        c.setSort(sort);
        c.setHtmlType("input");
        return c;
    }
}
