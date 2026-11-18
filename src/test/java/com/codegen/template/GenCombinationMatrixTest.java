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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.4 P0-3：生成物级开关组合矩阵。
 *
 * <p>背景：开关维度已到 10 个（CRUD 四开关 + 树形 + 审批流 + Excel 导入 + 唯一校验 +
 * 关联下拉 + 文件能力包），共 2^10 = 1024 种组合。第五轮走查靠人工枚举才发现
 * 「import 块缺逗号 → 生成坏 JS → 页面白屏」，只覆盖了当时想到的那些组合。
 * 本测试把枚举固化进仓库：每种组合渲染 vuePage / apiJs / controller，
 * 产物落 {@code target/gen-matrix/} 并写 manifest，再由
 * {@code scripts/check-generated-js.mjs} 对 JS 做语法校验。
 *
 * <p>为什么 JS 校验放 Node 侧：Java 里没有可靠的 ES 语法解析器（Nashorn 已移除），
 * 而项目工具链本来就有 Node（scripts/*.mjs、前端构建）。渲染不抛异常只是第一层，
 * 「生成出来是坏 JS」只有真正的解析器能抓到。
 *
 * <p>规模控制：1024 组合单机约 30-60s。开关继续增加时应改为 pairwise + 必测白名单。
 */
@DisplayName("生成物开关组合矩阵（1024 组合）")
class GenCombinationMatrixTest {

    /** 维度顺序即二进制位序（低位在前），改顺序会让历史坏组合清单不可比 */
    static final String[] DIMS = {
            "genAddApi", "genEditApi", "genDeleteApi", "genPageApi", "genTree",
            "hasFlow", "hasExcelImport", "hasSingleUnique", "hasRef", "fileEnabled"
    };
    static final int COMBINATIONS = 1 << DIMS.length;   // 1024

    private static Path tplDir;
    private static Path outDir;

    @BeforeAll
    static void setUp() throws IOException {
        tplDir = Path.of("src/main/resources/gen-templates");
        assertTrue(Files.isDirectory(tplDir), "找不到模板目录：" + tplDir.toAbsolutePath());
        GenTemplateModel.setRootPkg("com.codegen");
        outDir = Path.of("target", "gen-matrix");
        Files.createDirectories(outDir);
    }

    @Test
    @DisplayName("1024 组合全部渲染成功，产物与 manifest 落 target/gen-matrix")
    void renderAllCombinations() throws IOException {
        List<String> bad = new ArrayList<>();
        StringBuilder manifest = new StringBuilder("[\n");
        int first = 1;
        for (int i = 0; i < COMBINATIONS; i++) {
            boolean[] on = bits(i);
            String key = key(on);
            Map<String, Object> model;
            try {
                model = GenTemplateModel.build(table(on), columns(on), List.of(), project(on));
            } catch (Exception e) {
                bad.add(key + " 建模失败: " + e.getMessage());
                continue;
            }
            Path dir = outDir.resolve(key);
            Files.createDirectories(dir);
            List<String> files = new ArrayList<>();
            for (String[] tpl : new String[][]{{"vuePage", "page.vue"}, {"apiJs", "api.js"}, {"controller", "controller.java"}}) {
                String out;
                try {
                    out = render(tpl[0], model);
                } catch (Exception e) {
                    bad.add(key + " " + tpl[0] + " 渲染失败: " + e.getMessage());
                    continue;
                }
                Files.writeString(dir.resolve(tpl[1]), out, StandardCharsets.UTF_8);
                files.add(tpl[1]);
            }
            manifest.append(first++ == 1 ? "" : ",\n");
            manifest.append("  {\"key\":\"").append(key).append("\",\"index\":").append(i)
                    .append(",\"dir\":\"").append(dir.toAbsolutePath().toString().replace("\\", "/")).append("\"")
                    .append(",\"files\":[").append(files.stream().map(f -> "\"" + f + "\"")
                            .reduce((a, b) -> a + "," + b).orElse("")).append("]")
                    // 记录「模型算出来的真实门控」，不是测试输入的意图——
                    // hasExcelImport 依赖 genAddApi、genPrint 依赖 genDetailApi 等派生关系都在这里体现
                    .append(",\"gates\":{")
                    .append("\"hasFlow\":").append(model.get("hasFlow"))
                    .append(",\"hasExcelImport\":").append(model.get("hasExcelImport"))
                    .append(",\"hasSingleUnique\":").append(model.get("hasSingleUnique"))
                    .append(",\"hasRef\":").append(model.get("hasRef"))
                    .append(",\"genTree\":").append(model.get("genTree"))
                    .append(",\"fileEnabled\":").append(model.get("fileEnabled"))
                    .append(",\"genAddApi\":").append(model.get("genAddApi"))
                    .append(",\"genEditApi\":").append(model.get("genEditApi"))
                    .append(",\"genDeleteApi\":").append(model.get("genDeleteApi"))
                    .append(",\"genPageApi\":").append(model.get("genPageApi"))
                    .append("}}");
        }
        manifest.append("\n]\n");
        Files.writeString(outDir.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);

        assertTrue(bad.isEmpty(), "存在渲染失败的组合（共 " + bad.size() + " 个）：\n"
                + String.join("\n", bad.subList(0, Math.min(bad.size(), 20))));
    }

    // ------------------------------------------------------------------
    // 组合构造
    // ------------------------------------------------------------------

    private static boolean[] bits(int i) {
        boolean[] on = new boolean[DIMS.length];
        for (int b = 0; b < DIMS.length; b++) {
            on[b] = ((i >> b) & 1) == 1;
        }
        return on;
    }

    /** 可读 key：a1e0d1p1t0f1x0u0r0fi1（与 DIMS 一一对应，便于从坏组合清单反查开关） */
    private static String key(boolean[] on) {
        String[] tag = {"a", "e", "d", "p", "t", "f", "x", "u", "r", "fi"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < on.length; i++) {
            sb.append(tag[i]).append(on[i] ? '1' : '0');
        }
        return sb.toString();
    }

    private static GenTemplateModel.ProjectConfig project(boolean[] on) {
        // packageName, gatewayPrefix, rootPackage, projectCode, cascade, genPrint,
        // structure, pay, mobile, file, test
        return new GenTemplateModel.ProjectConfig("com.demo", "demo", "com.demo", "demo",
                1, 1, "single", 0, 0, on[9] ? 1 : 0, 0);
    }

    private static GenTable table(boolean[] on) {
        GenTable t = new GenTable();
        t.setTableName("demo_order");
        t.setTableComment("演示订单");
        t.setClassName("DemoOrder");
        t.setModuleName("order");
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setFrontendRoot("/tmp/demo-web");
        t.setGatewayPrefix("demo");
        t.setAuthor("matrix");
        t.setGenEntity(1);
        t.setGenMapper(1);
        t.setGenService(1);
        t.setGenController(1);
        t.setGenPageApi(on[3] ? 1 : 0);
        t.setGenDetailApi(1);
        t.setGenAddApi(on[0] ? 1 : 0);
        t.setGenEditApi(on[1] ? 1 : 0);
        t.setGenDeleteApi(on[2] ? 1 : 0);
        t.setGenFrontend(1);
        t.setGenApiJs(1);
        t.setGenVuePage(1);
        t.setGenQueryForm(1);
        t.setGenAddBtn(on[0] ? 1 : 0);
        t.setGenEditBtn(on[1] ? 1 : 0);
        t.setGenDeleteBtn(on[2] ? 1 : 0);
        t.setGenLog(1);
        t.setGenPrint(1);
        t.setTreeParentColumn(on[4] ? "parent_id" : null);
        t.setTreeLabelColumn(on[4] ? "name" : null);
        t.setFlowCode(on[5] ? "demo_order_flow" : null);
        return t;
    }

    private static List<GenTableColumn> columns(boolean[] on) {
        List<GenTableColumn> cols = new ArrayList<>();
        int sort = 1;
        cols.add(col("id", "id", "Long", sort++, 1));

        GenTableColumn name = col("name", "name", "String", sort++, 0);
        name.setColumnComment("名称");
        name.setIsQuery(1);
        name.setQueryType("LIKE");
        cols.add(name);

        // 审批流要求 status 列（flowStatusInList 口径）
        GenTableColumn status = col("status", "status", "Integer", sort++, 0);
        status.setColumnComment("状态");
        cols.add(status);

        if (on[8]) {                       // hasRef
            GenTableColumn ref = col("dict_value", "dictValue", "String", sort++, 0);
            ref.setColumnComment("字典值");
            ref.setRefTable("sys_dict_data");
            ref.setRefLabel("dict_label");
            ref.setHtmlType("select");
            cols.add(ref);
        }
        if (on[7]) {                       // hasSingleUnique
            GenTableColumn code = col("code", "code", "String", sort++, 0);
            code.setColumnComment("编码");
            code.setUniqueFlag(1);
            cols.add(code);
        }
        if (on[6]) {                       // hasExcelImport：必须落在 formCols
            GenTableColumn remark = col("remark", "remark", "String", sort++, 0);
            remark.setColumnComment("备注");
            remark.setExcelImport(1);
            remark.setHtmlType("input");
            cols.add(remark);
        }
        if (on[4]) {                       // genTree：parent_id 必须在 biz 列里
            GenTableColumn parent = col("parent_id", "parentId", "Long", sort++, 0);
            parent.setColumnComment("父级ID");
            parent.setIsForm(0);
            cols.add(parent);
        }

        GenTableColumn createTime = col("create_time", "createTime", "java.time.LocalDateTime", sort++, 0);
        createTime.setColumnComment("创建时间");
        createTime.setIsForm(0);
        cols.add(createTime);
        return cols;
    }

    private static GenTableColumn col(String columnName, String javaField, String javaType, int sort, int pk) {
        GenTableColumn c = new GenTableColumn();
        c.setColumnName(columnName);
        c.setColumnComment(columnName);
        c.setColumnType(javaType.equals("Long") ? "bigint" : "varchar(64)");
        c.setJavaField(javaField);
        c.setJavaType(javaType);
        c.setIsPk(pk);
        c.setIsRequired(0);
        c.setIsQuery(0);
        c.setQueryType("EQ");
        c.setIsList(1);
        c.setIsForm(1);
        c.setSort(sort);
        c.setHtmlType("input");
        c.setExcelExport(1);
        return c;
    }

    private static String render(String tplCode, Map<String, Object> model) {
        String content;
        try {
            content = Files.readString(tplDir.resolve(tplCode + ".ftl"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return GenTemplateEngine.renderString(content, model);
    }
}
