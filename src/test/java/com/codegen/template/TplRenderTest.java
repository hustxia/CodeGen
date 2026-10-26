package com.codegen.template;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 模板渲染断言测试：用真实 .ftl 渲染，检查产物里该有的东西在不在。
 *
 * <p>为什么必须断言内容：模板里全是 {@code <#if>}，字段名写错、变量名改了、
 * 判断条件漏了分支，Freemarker 都不会报错，只会安静地少渲染一段。
 * 生成的代码少了 {@code @Autowired} 或少了 {@code import}，要等到编译才暴露，
 * 而那时候人已经在骂「代码生成器生成的代码跑不起来」。
 *
 * <p>本测试是原脚本 {@code TplRenderCheck.main()} 的 JUnit 版本（V3.1 P1-1 第 1 组）：
 * 不再需要 {@code javac + java -cp} 手工跑，纳入 {@code mvn test} 后每次构建都会执行，
 * 失败信息直接指出是哪个模板缺了哪一段。
 *
 * <p>不连数据库、不起 Spring——只读 classpath 里的模板文件 + 纯函数式构模型。
 */
@DisplayName("模板渲染内容断言")
class TplRenderTest {

    private static Path tplDir;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        // 等价于生产环境的 @PostConstruct，模板里 import 的基座类按这个包名拼
        GenTemplateModel.setRootPkg("com.codegen");
    }

    /**
     * 渲染指定模板。渲染异常直接 fail，不要吞掉——异常本身就是最严重的断言失败。
     */
    private static String render(String tplFile, Map<String, Object> model) {
        try {
            String tpl = Files.readString(tplDir.resolve(tplFile), StandardCharsets.UTF_8);
            return GenTemplateEngine.renderString(tpl, model);
        } catch (IOException e) {
            fail("读取模板失败：" + tplFile + "：" + e.getMessage());
            return "";
        } catch (Exception e) {
            fail("渲染模板异常：" + tplFile + "：" + e.getMessage());
            return "";
        }
    }

    private static void has(String out, String needle, String what) {
        assertTrue(out.contains(needle),
                () -> what + " —— 产物里未找到：" + needle);
    }

    private static void hasNot(String out, String needle, String what) {
        assertFalse(out.contains(needle),
                () -> what + " —— 产物里不应出现：" + needle);
    }

    /**
     * 基础场景表：金额 + 手机号 + 联动 + 脱敏，覆盖大多数 P1 特性
     */
    private static Map<String, Object> basicModel() {
        GenTable table = GenTemplateModel.sampleTable();
        table.setTableName("v2_order");
        table.setTableComment("订单");
        table.setClassName("V2Order");
        table.setModuleName("v2Order");
        table.setPackageName("com.demo.order");

        List<GenTableColumn> cols = new ArrayList<>(GenTemplateModel.sampleCols());

        GenTableColumn phone = new GenTableColumn();
        phone.setColumnName("phone");
        phone.setColumnComment("手机号");
        phone.setColumnType("varchar(20)");
        phone.setJavaField("phone");
        phone.setJavaType("String");
        phone.setHtmlType("input");
        phone.setIsList(1);
        phone.setIsForm(1);
        phone.setMaskType("phone");
        phone.setUniqueFlag(1);
        phone.setExcelImport(1);
        phone.setExcelExport(1);
        phone.setSort(90);
        cols.add(phone);

        GenTableColumn shipTime = new GenTableColumn();
        shipTime.setColumnName("ship_time");
        shipTime.setColumnComment("发货时间");
        shipTime.setColumnType("datetime");
        shipTime.setJavaField("shipTime");
        shipTime.setJavaType("LocalDateTime");
        shipTime.setHtmlType("datetime");
        shipTime.setIsList(1);
        shipTime.setIsForm(1);
        shipTime.setLinkRule("{\"when\":\"phone\",\"op\":\"notEmpty\",\"effect\":\"required\",\"else\":\"optional\"}");
        shipTime.setSort(91);
        cols.add(shipTime);

        GenTableColumn remark = new GenTableColumn();
        remark.setColumnName("remark");
        remark.setColumnComment("备注");
        remark.setColumnType("varchar(200)");
        remark.setJavaField("remark");
        remark.setJavaType("String");
        remark.setHtmlType("input");
        remark.setIsList(0);
        remark.setIsForm(1);
        remark.setLinkRule("{\"when\":\"phone\",\"op\":\"empty\",\"effect\":\"hide\"}");
        remark.setSort(92);
        cols.add(remark);

        return GenTemplateModel.build(table, cols);
    }

    @Nested
    @DisplayName("基础 CRUD + P1 增强")
    class Basic {

        @Test
        @DisplayName("vuePage：联动规则、脱敏、查重、ID 列宽")
        void vuePage() {
            String out = render("vuePage.ftl", basicModel());
            has(out, "linkRules: [", "联动规则数组");
            has(out, "linkState: {}", "联动状态");
            has(out, "v-if=\"link.visible('shipTime')\"", "发货时间按规则显隐");
            has(out, "v-if=\"link.visible('remark')\"", "备注按规则显隐");
            has(out, ":disabled=\"link.disabled('shipTime')\"", "禁用绑定");
            has(out, "applyLink() {", "applyLink 方法");
            has(out, "evalLink(rule, value) {", "evalLink 方法");
            has(out, "this.applyLink()", "弹窗打开时触发");
            has(out, "uniqueValidator(field) {", "前端查重");
            has(out, "/check-unique", "查重接口路径");
            has(out, "width=\"180\"", "ID 列宽");
            hasNot(out, "linkRules: [\n", "linkRules 必须单行内联（多行会撑坏模板）");
        }

        @Test
        @DisplayName("controller：Excel 导入导出、条件导出、查重兜底")
        void controller() {
            Map<String, Object> model = basicModel();
            String out = render("controller.ftl", model);

            if (Boolean.TRUE.equals(model.get("hasExcelImport"))) {
                has(out, "downloadImportFailed", "失败明细下载接口");
                has(out, "validateImportRow", "逐行校验");
                has(out, "isBlankRow", "空行跳过");
                has(out, "DuplicateKeyException", "重复行按跳过处理");
                has(out, "\"skipped\"", "返回跳过数");
                has(out, "import-template", "导入模板下载");
                has(out, "importTemplate()", "模板下载方法");
            }
            if (Boolean.TRUE.equals(model.get("hasExcelExport"))) {
                has(out, "@PostMapping(\"/export\")", "导出必须是 POST（带筛选条件）");
                has(out, "@RequestBody(required = false)", "导出接受可选条件");
                has(out, "wrapper.orderByDesc", "导出复用筛选口径");
                hasNot(out, "Service.list()", "导出不能全量 list()");
            }
            // import 缺失是生成代码编译不过的头号原因
            has(out, "import com.codegen.common.exception.BusinessException;", "BusinessException 导入");
            has(out, "import org.springframework.beans.factory.annotation.Autowired;", "Autowired 导入");
            has(out, "import org.springframework.web.bind.annotation.GetMapping;", "GetMapping 导入");
            has(out, "@Autowired\n    private", "Service 注入");
            has(out, "checkUniqueField", "查重接口");
            has(out, "checkUnique(dto, null);", "新增兜底查重");
            has(out, "checkUnique(dto, dto.getId());", "修改兜底查重");
        }

        @Test
        @DisplayName("vo：脱敏 getter")
        void vo() {
            String out = render("vo.ftl", basicModel());
            has(out, "MaskUtils.mask(this.phone, \"phone\")", "VO 脱敏 getter");
        }
    }

    @Nested
    @DisplayName("三级主子表")
    class ThreeLevel {

        private Map<String, Object> model() {
            GenTable order = GenTemplateModel.sampleTable();
            order.setTableName("v3_order");
            order.setTableComment("订单");
            order.setClassName("V3Order");
            order.setModuleName("v3Order");
            order.setPackageName("com.demo.order");

            GenTable item = new GenTable();
            item.setTableName("v3_order_item");
            item.setTableComment("订单明细");
            item.setClassName("V3OrderItem");
            item.setPackageName("com.demo.order");
            item.setMasterForeignKey("order_id");

            GenTable batch = new GenTable();
            batch.setTableName("v3_order_batch");
            batch.setTableComment("发货批次");
            batch.setClassName("V3OrderBatch");
            batch.setPackageName("com.demo.order");
            batch.setMasterForeignKey("item_id");

            GenTemplateModel.ChildInfo grand = new GenTemplateModel.ChildInfo(batch, List.of());
            GenTemplateModel.ChildInfo child = new GenTemplateModel.ChildInfo(item, List.of(), grand);
            return GenTemplateModel.build(order, new ArrayList<>(GenTemplateModel.sampleCols()), child);
        }

        /** 子表（明细）自身的渲染模型：孙表字段挂在子表 DTO/VO 上，不再挂主表 */
        private Map<String, Object> childModel() {
            GenTable item = new GenTable();
            item.setTableName("v3_order_item");
            item.setTableComment("订单明细");
            item.setClassName("V3OrderItem");
            item.setPackageName("com.demo.order");
            item.setMasterForeignKey("order_id");
            item.setMasterTableId(999L);

            GenTable batch = new GenTable();
            batch.setTableName("v3_order_batch");
            batch.setTableComment("发货批次");
            batch.setClassName("V3OrderBatch");
            batch.setPackageName("com.demo.order");
            batch.setMasterForeignKey("item_id");

            GenTemplateModel.ChildInfo grand = new GenTemplateModel.ChildInfo(batch, List.of());
            return GenTemplateModel.build(item, new ArrayList<>(GenTemplateModel.sampleCols()), grand);
        }

        @Test
        @DisplayName("serviceImpl / saveDto / vo / controller / vuePage 都要出现第三级")
        void threeLevelWiring() {
            Map<String, Object> m = model();

            // V4.4 起 serviceImpl 是逻辑名，物理模板按形态派发（判据与引擎同源，缺文件直接 fail）
            String serviceImpl = render(GenTemplateModel.serviceImplTplCode(m) + ".ftl", m);
            has(serviceImpl, "saveV3OrderBatchRows", "三级保存方法");
            has(serviceImpl, "dto.getV3OrderBatchs()", "由 DTO 取下级列表");
            has(serviceImpl, "V3OrderBatchMapper v3OrderBatchMapper;", "三级 Mapper 注入");

            // V4.4 语义修正：孙表列表只挂子表自己的 DTO/VO，主表的不再重复（原本是死字段）
            has(render("saveDto.ftl", childModel()), "List<V3OrderBatchSaveDTO> v3OrderBatchs", "DTO 下级列表字段");
            has(render("vo.ftl", childModel()), "List<V3OrderBatchVO> v3OrderBatchs", "VO 下级列表字段");
            hasNot(render("saveDto.ftl", m), "List<V3OrderBatchSaveDTO>", "主表 DTO 不带孙表列表");
            hasNot(render("vo.ftl", m), "List<V3OrderBatchVO>", "主表 VO 不带孙表列表");
            has(render("controller.ftl", m), "setV3OrderBatchs(", "详情接口装配三级");

            String vue = render("vuePage.ftl", m);
            has(vue, "openGrandRows('v3OrderItem', 'v3OrderBatch', row)", "前端下级编辑入口");
            has(vue, "defaultGrandMap", "下级默认行");
            has(vue, "this.grandRows = row[field]", "直接改引用");
        }
    }

    @Nested
    @DisplayName("中间表")
    class MiddleTable {

        @Test
        @DisplayName("多选回显接口存在，且不生成导出")
        void middleTable() {
            GenTable mid = GenTemplateModel.sampleTable();
            mid.setTableName("product_tag");
            mid.setTableComment("商品标签关联");
            mid.setClassName("ProductTag");
            mid.setModuleName("productTag");
            mid.setPackageName("com.demo.product");
            mid.setRelationType("middle");

            List<GenTableColumn> cols = new ArrayList<>(GenTemplateModel.sampleCols());
            cols.add(relationKey("product_id", "商品ID", "productId", 80));
            cols.add(relationKey("tag_id", "标签ID", "tagId", 81));

            String out = render("controller.ftl", GenTemplateModel.build(mid, cols));
            has(out, "/bind-values", "多选回显接口");
            has(out, "ProductTag::getProductId", "左键 lambda");
            has(out, "ProductTag::getTagId", "右键 lambda");
            hasNot(out, "@PostMapping(\"/export\")", "中间表不生成导出");
        }

        private GenTableColumn relationKey(String name, String comment, String field, int sort) {
            GenTableColumn col = new GenTableColumn();
            col.setColumnName(name);
            col.setColumnComment(comment);
            col.setColumnType("bigint");
            col.setJavaField(field);
            col.setJavaType("Long");
            col.setHtmlType("number");
            col.setIsList(1);
            col.setIsForm(1);
            col.setRelationKey(1);
            col.setSort(sort);
            return col;
        }
    }

    @Nested
    @DisplayName("审批流")
    class Flow {

        @Test
        @DisplayName("四个流程接口 + api.js + 页面按钮")
        void flowWiring() {
            GenTable table = GenTemplateModel.sampleTable();
            table.setFlowCode("leave_apply");

            List<GenTableColumn> cols = new ArrayList<>(GenTemplateModel.sampleCols());
            GenTableColumn status = new GenTableColumn();
            status.setColumnName("status");
            status.setColumnComment("单据状态");
            status.setColumnType("varchar(2)");
            status.setJavaField("status");
            status.setJavaType("String");
            status.setHtmlType("select");
            status.setIsList(1);
            status.setIsForm(0);
            status.setSort(90);
            cols.add(status);

            Map<String, Object> model = GenTemplateModel.build(table, cols);

            String controller = render("controller.ftl", model);
            has(controller, "/flow/submit", "提交接口");
            has(controller, "/flow/approve", "审批接口");
            has(controller, "/flow/reject", "驳回接口");
            has(controller, "/flow/records", "记录接口");
            has(controller, "private FlowService flowService;", "注入 FlowService");
            has(controller, "\"leave_apply\"", "流程编码");

            String api = render("apiJs.ftl", model);
            has(api, "flowSubmit", "api 提交方法");
            has(api, "flowCode: 'leave_apply'", "api 带流程编码");

            String vue = render("vuePage.ftl", model);
            has(vue, "handleFlow('submit', row)", "提交按钮");
            has(vue, "handleFlow('approve', row)", "审批按钮");
            has(vue, "handleFlow('reject', row)", "驳回按钮");
            has(vue, "openFlowRecords(row)", "记录入口");
            has(vue, "row.status === '0'", "按状态显示按钮");
            has(vue, "flowStatusText(r.toStatus)", "流水时间线");
        }
    }

    @Nested
    @DisplayName("多系统权限点前缀")
    class MultiProject {

        @Test
        @DisplayName("绑定系统后，页面里的权限码带系统前缀")
        void permsCarryProjectCode() {
            GenTable table = GenTemplateModel.sampleTable();
            table.setTableName("sys_flow_task");
            table.setClassName("SysFlowTask");
            table.setModuleName("flowTask");
            table.setPackageName("com.mkt.sys");

            GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                    "com.mkt", "mkt", "com.mkt", "mkt2");

            String out = render("vuePage.ftl",
                    GenTemplateModel.build(table, new ArrayList<>(GenTemplateModel.sampleCols()), null, project));
            has(out, "'mkt2_sys_flow_task:query'", "权限码带系统前缀");
        }

        @Test
        @DisplayName("未绑定系统时权限码不带前缀，存量页面不受影响")
        void permsWithoutProject() {
            GenTable table = GenTemplateModel.sampleTable();
            table.setTableName("sys_flow_task");
            table.setClassName("SysFlowTask");
            table.setModuleName("flowTask");
            table.setPackageName("com.demo.sys");

            String out = render("vuePage.ftl",
                    GenTemplateModel.build(table, new ArrayList<>(GenTemplateModel.sampleCols())));
            has(out, "'sys_flow_task:query'", "无前缀权限码");
        }
    }
}
