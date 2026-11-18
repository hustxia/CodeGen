package com.codegen.service.impl;

import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.5 移动端支持基线：开关回落 / 动态页配置生成 / 工程物料（纯函数 + 反射，不连库、不起 Spring）。
 * 红线断言：mobile_enabled 关闭时渲染产物与 V6.4 逐文件一致（零 diff）。
 */
class GenV65MobileBaselineTest {

    // ---------- 开关回落 ----------

    @Test
    void resolveMobile_followsProjectTriState() {
        assertFalse(GenTemplateModel.resolveMobile(null), "NULL=未配置必须回落关");
        assertFalse(GenTemplateModel.resolveMobile(0), "0=关");
        assertTrue(GenTemplateModel.resolveMobile(1), "1=开");
    }

    // ---------- 动态页配置生成（表侧） ----------

    private static GenTable table(String name, String comment, String className, String module) {
        GenTable t = new GenTable();
        t.setTableName(name);
        t.setTableComment(comment);
        t.setClassName(className);
        t.setModuleName(module);
        t.setPackageName("com.demo");
        t.setBackendPath("demo-server/src/main/java/com/demo");
        t.setGatewayPrefix("demo");
        t.setGenFrontend(1);
        t.setGenVuePage(1);
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
        return c;
    }

    private static GenTemplateEngine.ResolvedTemplate vuePageTpl() {
        try {
            String content = Files.readString(Path.of("src/main/resources/gen-templates/vuePage.ftl"),
                    StandardCharsets.UTF_8);
            return new GenTemplateEngine.ResolvedTemplate(content, "src/views/${moduleName}/index.vue");
        } catch (Exception e) {
            throw new IllegalStateException("读取 vuePage.ftl 失败", e);
        }
    }

    private Map<String, String> render(GenTable t, GenTemplateModel.ProjectConfig project) throws Exception {
        List<GenTableColumn> cols = List.of(
                col("id", "主键", "bigint", "id", "Long", 1, 0, 0, "EQ", 1, 0, 1),
                col("fee_name", "费用名称", "varchar(50)", "feeName", "String", 0, 1, 1, "LIKE", 1, 1, 2),
                col("amount", "金额", "bigint", "amount", "Long", 0, 1, 0, "EQ", 1, 1, 3),
                col("pay_status", "支付状态:1已支付0未支付", "tinyint", "payStatus", "Integer", 0, 0, 1, "EQ", 1, 1, 4));
        return GenTemplateEngine.render(t, new java.util.ArrayList<>(cols),
                (List<GenTemplateModel.ChildInfo>) null,
                tplCode -> tplCode.equals("vuePage") ? vuePageTpl() : tplCode.equals("vo") ? voTpl() : null, project);
    }

    private static GenTemplateEngine.ResolvedTemplate voTpl() {
        try {
            String content = Files.readString(Path.of("src/main/resources/gen-templates/vo.ftl"),
                    StandardCharsets.UTF_8);
            return new GenTemplateEngine.ResolvedTemplate(content,
                    "demo-server/src/main/java/com/demo/vo/${className}VO.java");
        } catch (Exception e) {
            throw new IllegalStateException("读取 vo.ftl 失败", e);
        }
    }

    @Test
    void mobileConfig_emittedWhenSwitchOn() throws Exception {
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, null, 1);
        Map<String, String> files = render(table("demo_fee", "费用", "DemoFee", "fee"), project);
        String relPath = "mobile/src/config/tables/fee/demo_fee.js";
        assertTrue(files.containsKey(relPath), "开启 mobile 后必须产出表配置文件");
        String js = files.get(relPath);
        assertTrue(js.contains("table: 'demo_fee'"), "配置必须带表名");
        assertTrue(js.contains("title: '费用'"), "标题必须是剥过枚举说明的 commentLabel 语义");
        assertTrue(js.contains("api: '/api/demo/fee'"), "接口前缀必须与 PC apiBase 同源");
        // 枚举列 → picker + enum 映射（与 PC 下拉同源）
        assertTrue(js.contains("ctrl: 'picker'"), "枚举列必须映射成 picker");
        assertTrue(js.contains("enum: [{value: 1, label: '已支付'}"), "枚举映射必须可执行");
        // 必填列标记
        assertTrue(js.contains("required: true"), "必填列必须标记");
        // 动态页三件引用的分组键
        assertTrue(js.contains("search:"), "缺 search 分组");
        assertTrue(js.contains("list:"), "缺 list 分组");
        assertTrue(js.contains("form:"), "缺 form 分组");
    }

    @Test
    void mobileConfig_absentWhenSwitchOff_zeroDiffRedLine() throws Exception {
        GenTemplateModel.ProjectConfig off = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, null, null);
        GenTemplateModel.ProjectConfig v64Compat = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, 1);
        for (GenTemplateModel.ProjectConfig project : List.of(off, v64Compat)) {
            Map<String, String> files = render(table("demo_fee", "费用", "DemoFee", "fee"), project);
            assertTrue(files.keySet().stream().noneMatch(k -> k.startsWith("mobile/")),
                    "开关关闭时不得产出任何 mobile/ 文件（V6.4 黄金基线零 diff 红线）");
        }
    }

    @Test
    void mobileConfig_skippedForChildAndTreeTables() throws Exception {
        GenTemplateModel.ProjectConfig project = new GenTemplateModel.ProjectConfig(
                null, null, "com.codegen", "demo", null, null, null, null, 1);
        // 从表：masterTableId 非空
        GenTable child = table("demo_fee_item", "费用明细", "DemoFeeItem", "fee");
        child.setMasterTableId(99L);
        assertTrue(render(child, project).keySet().stream().noneMatch(k -> k.startsWith("mobile/")),
                "从表不上移动端（规划口径）");
        // 树表：treeParentColumn 非空
        GenTable tree = table("demo_org", "机构", "DemoOrg", "org");
        tree.setTreeParentColumn("parent_id");
        assertTrue(render(tree, project).keySet().stream().noneMatch(k -> k.startsWith("mobile/")),
                "树表缺省不上移动端（规划口径）");
    }

    // ---------- 工程物料（scaffold-mobile） ----------

    @SuppressWarnings("unchecked")
    private Map<String, String> mobileResources() throws Exception {
        Object svc = Class.forName("com.codegen.service.impl.GenScaffoldServiceImpl")
                .getDeclaredConstructor().newInstance();
        Method m = svc.getClass().getDeclaredMethod("mobileResources", String.class, int.class, String.class);
        m.setAccessible(true);
        return (Map<String, String>) m.invoke(svc, "hr", 8421, "demo");
    }

    @Test
    void mobileResources_coversSkeletonContract() throws Exception {
        Map<String, String> files = mobileResources();
        List.of("package.json", "vite.config.js", "index.html", "src/main.js", "src/App.vue",
                "src/manifest.json", "src/pages.json", "src/utils/request.js", "src/utils/tables.js",
                "src/store/user.js", "src/pages/login/login.vue", "src/pages/index/index.vue",
                "src/pages/dynamic/list.vue", "src/pages/dynamic/detail.vue", "src/pages/dynamic/form.vue",
                "src/pages/todo/todo.vue", "src/pages/mine/mine.vue", "README.md")
                .forEach(f -> assertNotNull(files.get(f), "缺工程物料：" + f));
    }

    @Test
    void mobileResources_placeholdersResolved() throws Exception {
        Map<String, String> files = mobileResources();
        files.forEach((path, content) -> assertFalse(content.contains("__PROJECT__") || content.contains("__API_PORT__"),
                path + " 存在未替换占位符"));
        String pkg = files.get("package.json");
        assertTrue(pkg.contains("\"name\": \"hr-mobile\""), "工程名必须替换");
        assertTrue(pkg.contains("@dcloudio/uni-app"), "必须锁定 uni-app 依赖");
        assertTrue(pkg.contains("build:mp-weixin"), "必须带小程序出包脚本");
        String vite = files.get("vite.config.js");
        assertTrue(vite.contains("http://127.0.0.1:8421"), "H5 代理端口必须指向后端");
        assertFalse(vite.contains("/api/__GW__"), "网关段占位必须替换");
        assertTrue(vite.contains("(/api/demo)?") || vite.contains("/api/demo"), "H5 代理必须剥网关段（与 PC 同口径）");
        assertFalse(vite.contains("__API_PORT__"), "端口占位符必须替换");
        String pages = files.get("src/pages.json");
        assertTrue(pages.contains("pages/dynamic/list"), "动态页三件必须静态注册");
        assertTrue(pages.contains("pages/dynamic/form"), "缺表单动态页");
        assertTrue(pages.contains("tabBar"), "缺 tabBar（首页/待办/我的）");
        String request = files.get("src/utils/request.js");
        assertTrue(request.contains("Bearer "), "必须注入 Bearer token");
        assertTrue(request.contains("statusCode === 401"), "401 必须跳登录");
        String readme = files.get("README.md");
        assertTrue(readme.contains("不校验合法域名"), "小程序联调开关必须写进 README（诚实边界）");
    }
}
