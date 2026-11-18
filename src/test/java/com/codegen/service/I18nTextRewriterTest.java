package com.codegen.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** V8.0 渲染后重写器：开关关闭零 diff、开关打开接线正确、模板源码零改动。 */
class I18nTextRewriterTest {

    @Test
    @DisplayName("开关关闭时原样返回（同一 Map 实例），存量产物逐字节零 diff")
    void 关闭零diff() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/views/demo/index.vue", "<el-button>查询</el-button>");
        assertSame(files, I18nTextRewriter.rewrite(files, false));
        assertEquals("<el-button>查询</el-button>", files.get("src/views/demo/index.vue"));
    }

    @Test
    @DisplayName(".vue 固定按钮/表头/统计文案接线为 $t")
    void Vue文件接线() {
        String src = "<el-button type=\"primary\" @click=\"handleSearch\">查询</el-button>"
                + "<el-button>重置</el-button>"
                + "<el-table-column label=\"操作\" width=\"150\">"
                + "共 {{ total }} 条";
        String out = I18nTextRewriter.rewriteOne("src/views/demo/index.vue", src);
        assertTrue(out.contains("$t('common.query')"));
        assertTrue(out.contains("$t('common.reset')"));
        assertTrue(out.contains(":label=\"$t('common.operation')\""));
        assertTrue(out.contains("$t('common.total', { count: total })"));
        assertFalse(out.contains("@@T@@"), "占位符未还原");
    }

    @Test
    @DisplayName("动态业务名按钮保留中文名，只把动词接线")
    void 正则规则保留业务名() {
        String out = I18nTextRewriter.rewriteOne("index.vue", ">新增采购订单</el-button>");
        assertEquals(">{{ $t('common.add') }}采购订单</el-button>", out);
    }

    @Test
    @DisplayName("独立 .js 用 window.$t（非 Vue 模块不能调 this.$t）")
    void Js文件接线() {
        String out = I18nTextRewriter.rewriteOne("src/utils/request.js",
                "Message({ message: res.msg || '请求失败' })");
        assertEquals("Message({ message: res.msg || window.$t('common.requestFailed') })", out);
    }

    @Test
    @DisplayName("script 段规则显式带 this.，不会被误写成裸 $t")
    void 脚本段提示() {
        String out = I18nTextRewriter.rewriteOne("index.vue",
                "this.$message.success('删除成功')");
        assertEquals("this.$message.success(this.$t('common.deleteSuccess'))", out);
    }

    @Test
    @DisplayName("vuePage.ftl 双轨制契约：$t( 只出现在 i18nEnabled 守卫内，源中文文案保留")
    void 模板源码零改动() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("gen-templates/vuePage.ftl")) {
            assertNotNull(in, "模板资源缺失");
            String tpl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // V8.0 起模板不再是「只放中文、运行时再改写」：ON 分支由 <#if i18nEnabled> 守卫
            // 直接产出 $t(…)，OFF 分支保留中文原文，二者拼成「剥掉 $t 后逐字节相等」的契约。
            // 因此断言从「模板不得出现 $t(」改为「出现时必须处于 i18nEnabled 守卫分支内」。
            assertFalse(tpl.contains("@@T@@"), "不应出现占位符改写标记");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<#if\\b[^>]*>|</#if>|<#elseif\\b[^>]*>|<#else>|\\$t\\(")
                    .matcher(tpl);
            java.util.Deque<Boolean> guards = new java.util.ArrayDeque<>();
            while (m.find()) {
                String tok = m.group();
                if (tok.startsWith("<#if")) {
                    guards.push(tok.contains("i18nEnabled"));
                } else if (tok.startsWith("</#if")) {
                    if (!guards.isEmpty()) guards.pop();
                } else if (tok.startsWith("<#else")) {
                    if (!guards.isEmpty()) guards.pop();
                    guards.push(tok.contains("i18nEnabled"));
                } else {
                    assertTrue(guards.contains(Boolean.TRUE),
                            "出现未被 i18nEnabled 守卫的 $t(，会破坏 OFF 零 diff 契约（偏移 " + m.start() + "）");
                }
            }
            assertTrue(tpl.contains(">查询</el-button>"), "源中文文案应留在模板里");
        }
    }

    @Test
    @DisplayName("批量重写只处理前端文件，后端 java/sql 不受影响")
    void 批量只动前端() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("entity/Demo.java", "// 查询");
        files.put("api/demo.js", "// 请求失败提示");
        files.put("views/demo/index.vue", ">查询</el-button>");
        Map<String, String> out = I18nTextRewriter.rewrite(files, true);
        assertEquals("// 查询", out.get("entity/Demo.java"));
        assertTrue(out.get("views/demo/index.vue").contains("$t('common.query')"));
    }
}
