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
    @DisplayName("22 个 .ftl 模板源码零改动：不出现 $t / @@T@@（V7.4 三副本基线）")
    void 模板源码零改动() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("gen-templates/vuePage.ftl")) {
            assertNotNull(in, "模板资源缺失");
            String tpl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(tpl.contains("$t("), "模板被直接改写，基线快照会全量漂移");
            assertFalse(tpl.contains("@@T@@"));
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
