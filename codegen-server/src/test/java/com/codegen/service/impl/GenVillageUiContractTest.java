package com.codegen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智慧村务界面改造（菜单美观 / 表单中文名）的防回归契约：
 * 1) village_affairs 模板所有列注释必须是中文，不能出现 householdID 这类英文 token，
 *    否则生成页面的表单/详情/列表 label 会裸露技术字段名；
 * 2) 菜单脚本必须为 12 个业务域都配 Element Plus 图标，且图标名有效；
 * 3) layout 模板必须把 meta.icon 带进菜单树并渲染，否则二级面板无图标。
 *
 * <p>锁定脚本与模板源码文本，防止后续分支回退导致界面退化。
 */
@DisplayName("村务界面改造契约")
class GenVillageUiContractTest {

    private String src(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("村务模板：所有列注释中文化（无英文 token）")
    void villageTemplateCommentsAreChinese() throws Exception {
        String json = src("src/main/resources/industry-templates/village_affairs.json");
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json);
        int total = 0, bad = 0;
        StringBuilder samples = new StringBuilder();
        JsonNode modules = root.get("modules");
        for (JsonNode module : modules) {
            for (JsonNode table : module.get("tables")) {
                for (JsonNode col : table.get("columns")) {
                    total++;
                    String comment = col.path("comment").asText("");
                    // IP地址这类含 ASCII 但语义是中文的注释放行；纯英文 token 不放行
                    if (comment.matches("[A-Za-z][A-Za-z0-9]*")) {
                        bad++;
                        if (samples.length() < 120) {
                            samples.append(col.path("name").asText()).append("=").append(comment).append(" ");
                        }
                    }
                }
            }
        }
        assertTrue(total > 1000, "村务模板字段数应 >1000，实际：" + total);
        assertEquals(0, bad, "不得有英文 token 列注释，实际：" + bad + " 例：" + samples);
    }

    @Test
    @DisplayName("菜单脚本：12 个业务域图标齐全且非占位")
    void menuScriptHasAllDomainIcons() throws Exception {
        String py = src("scripts/village_menu_replan.py");
        Matcher m = Pattern.compile("DOMAIN_ICONS\\s*=\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(py);
        assertTrue(m.find(), "必须定义 DOMAIN_ICONS 映射");
        String body = m.group(1);
        int count = body.split(":", -1).length - 1;
        assertEquals(12, count, "必须为 12 个业务域配图标，实际：" + count);
        assertFalse(body.contains("'Map'"), "Map 不是有效的 Element Plus 图标，应为 MapLocation");
        assertTrue(py.contains("icon: '%s'"), "domain_block 的 meta 必须输出 icon 字段");
    }

    @Test
    @DisplayName("layout 模板：仓储风格侧边栏渲染业务域图标与页面树")
    void layoutRendersWarehouseStyleMenu() throws Exception {
        String vue = src("src/main/resources/scaffold/web/layout.vue");
        assertTrue(vue.contains("icon: (route.meta && route.meta.icon)"),
                "buildMenu 必须把 meta.icon 带进菜单树");
        assertTrue(vue.contains("<component :is=\"grp.icon || iconOf(grp)\" />"),
                "一级业务域必须用 grp.icon（无图标时兜底）渲染");
        assertTrue(vue.contains("iconOf(grp)"),
                "必须提供无图标时的兜底图标方法");
        assertTrue(vue.contains(".group-icon {"),
                "必须定义 .group-icon 样式");
        assertTrue(vue.contains("class=\"sidebar\"") && vue.contains("class=\"group-children\""),
                "必须是左侧常驻树形菜单：sidebar + group-children");
        assertTrue(vue.contains("el-collapse-transition"),
                "业务域必须可展开/收起");
    }

    @Test
    @DisplayName("脚手架：免登录打开即用（无登录路由 / 自动签发会话 / demo-login 白名单）")
    void scaffoldIsLoginFree() throws Exception {
        String writer = src("src/main/java/com/codegen/service/impl/ScaffoldFrontendWriter.java");
        assertFalse(writer.contains("path: '/login'"), "路由表不得再注册登录页");
        assertFalse(writer.contains("window.location.href = '/login'"), "401 不得跳登录页");
        assertTrue(writer.contains("/auth/demo-login"), "前端启动必须自动签发演示会话");

        String authWriter = src("src/main/java/com/codegen/service/impl/ScaffoldAuthWriter.java");
        assertTrue(authWriter.contains("/auth/demo-login"), "后端必须放行 demo-login");
        assertTrue(authWriter.contains("demo-login"), "后端必须提供 demo-login 接口");
    }
}
