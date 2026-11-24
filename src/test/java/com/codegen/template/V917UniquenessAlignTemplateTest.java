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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.17 唯一性校验对齐：用真实 .ftl 渲染断言「后端 400 结构 + 前端字段定位」同源。
 *
 * <p>为什么用模板渲染而不是只看字符串：字段名、文案、接口路径都来自同一个列模型
 * （{@code uniqueColumns[0].javaField}），只有真渲染才能证明前后端拿到的是同一个值。
 * 不起 Spring、不连数据库，只读 classpath 模板 + 纯函数构模型。
 */
@DisplayName("V9.17 唯一性校验前后端对齐：模板渲染断言")
class V917UniquenessAlignTemplateTest {

    private static final String FIELD = "code";
    private static final String MSG = "编码已存在";
    private static Path tplDir;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        GenTemplateModel.setRootPkg("com.codegen");
    }

    /** 单列唯一模型：code / 编码 / unique_flag=1，hasSingleUnique=true。 */
    private static Map<String, Object> uniqueModel() {
        GenTable table = GenTemplateModel.sampleTable();
        table.setTableName("v917_goods");
        table.setTableComment("商品");
        table.setClassName("V917Goods");
        table.setModuleName("v917Goods");
        table.setPackageName("com.demo.goods");

        List<GenTableColumn> cols = new ArrayList<>(GenTemplateModel.sampleCols());
        GenTableColumn code = new GenTableColumn();
        code.setColumnName("code");
        code.setColumnComment("编码");
        code.setColumnType("varchar(32)");
        code.setJavaField(FIELD);
        code.setJavaType("String");
        code.setHtmlType("input");
        code.setIsList(1);
        code.setIsForm(1);
        code.setUniqueFlag(1);
        code.setSort(88);
        cols.add(code);

        Map<String, Object> m = GenTemplateModel.build(table, cols);
        assertTrue(Boolean.TRUE.equals(m.get("hasUnique")), "模型必须识别为有唯一列");
        assertTrue(Boolean.TRUE.equals(m.get("hasSingleUnique")), "单列唯一场景");
        return m;
    }

    private static String render(String tpl, Map<String, Object> m) throws Exception {
        String src = Files.readString(tplDir.resolve(tpl), StandardCharsets.UTF_8);
        return GenTemplateEngine.renderString(src, m);
    }

    private static void has(String out, String needle, String what) {
        assertTrue(out.contains(needle), () -> what + " —— 产物未找到：" + needle);
    }

    private static void hasNot(String out, String needle, String what) {
        assertFalse(out.contains(needle), () -> what + " —— 产物不应出现：" + needle);
    }

    @Test
    @DisplayName("controller：前置查重与 DB 唯一键兜底同走 400 工厂，文案/字段名同源")
    void controllerContract() throws Exception {
        Map<String, Object> m = uniqueModel();
        String out = render("controller.ftl", m);
        String var = (String) m.get("varName");

        has(out, "private static final String UNIQUE_CONFLICT_MSG = \"" + MSG + "\";", "400 冲突文案常量");
        has(out, "private static final String UNIQUE_CONFLICT_FIELD = \"" + FIELD + "\";", "冲突字段名常量");
        has(out, "private void writeWithUniqueGuard(Runnable write)", "DB 唯一键兜底方法");
        has(out, "catch (org.springframework.dao.DuplicateKeyException ex)", "捕获数据库唯一键冲突");
        has(out, "throw BusinessException.unique(UNIQUE_CONFLICT_MSG, UNIQUE_CONFLICT_FIELD);", "统一 400 工厂");
        has(out, "checkUnique(dto, null);", "新增前置查重");
        has(out, "checkUnique(dto, dto.getId());", "修改前置查重");
        has(out, "writeWithUniqueGuard(() -> " + var + "Service.save(entity));", "新增落库兜底");
        has(out, "writeWithUniqueGuard(() -> " + var + "Service.updateById(entity));", "修改落库兜底");
        has(out, "@GetMapping(\"/check-unique\")", "前端失焦查重接口");
        has(out, "if (\"" + FIELD + "\".equals(field))", "查重接口字段名与保存接口同源");
    }

    @Test
    @DisplayName("saveDto：唯一列声明 400 错误体与 field，字段名渲染为 code")
    void saveDtoContract() throws Exception {
        String out = render("saveDto.ftl", uniqueModel());
        has(out, "data:{field:\"" + FIELD + "\"}", "DTO 注释声明 data.field");
        has(out, "private String " + FIELD + ";", "字段本身仍为原类型");
    }

    @Test
    @DisplayName("vuePage：400 错误体解析 + el-form-item 红字定位，前后端字段名一致")
    void vuePageContract() throws Exception {
        Map<String, Object> m = uniqueModel();
        String out = render("vuePage.ftl", m);
        has(out, "applyUniqueFieldError(e) {", "错误处理方法");
        has(out, "if (!body || body.code !== 400) return", "只处理 400，不误伤其它错误");
        has(out, "(body.data && body.data.field) || body.field", "兼容嵌套/扁平两种错误体");
        has(out, "body.msg || body.message || ''", "兼容 msg/message");
        has(out, "this.$refs.form.fields.find(f => f.prop === field)", "按字段名定位表单项");
        has(out, "item.validateState = 'error'", "红字落到 el-form-item");
        has(out, "this.applyUniqueFieldError(e)", "保存失败时调用");
        has(out, "this._uniqueErrorField = undefined", "成功后清理残留红字");
        has(out, "uniqueValidator(field) {", "失焦异步查重保留");
        has(out, "url: '" + m.get("apiBase") + "/check-unique'", "查重接口路径");
    }

    @Test
    @DisplayName("无唯一列：不生成任何查重/兜底代码，既有合法成功路径零改动")
    void noUniqueColumnKeepsOutputClean() throws Exception {
        GenTable table = GenTemplateModel.sampleTable();
        Map<String, Object> m = GenTemplateModel.build(table, new ArrayList<>(GenTemplateModel.sampleCols()));
        String controller = render("controller.ftl", m);
        String vue = render("vuePage.ftl", m);
        hasNot(controller, "writeWithUniqueGuard", "无唯一列不得生成兜底方法");
        hasNot(controller, "BusinessException.unique", "无唯一列不得生成 400 工厂调用");
        hasNot(vue, "applyUniqueFieldError", "无唯一列不得生成字段定位逻辑");
        hasNot(vue, "/check-unique", "无唯一列不得生成查重请求");
    }
}
