package com.codegen.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vue3 兼容性防回归（V9.18）：
 * 生成器模板曾输出 this.$set（Vue2 API），在产物 Vue3 项目里直接抛
 * "TypeError: this.$set is not a function"，而且 loadDicts 用 try/catch 吞掉异常，
 * 表现为「字典全空 / 点新增弹服务异常」——82 个页面共 206 处。
 *
 * <p>本测试锁定模板与脚手架组件里不能再出现 this.$set。
 */
class GenVillageVue3CompatTest {

    private static final String RES = "src/main/resources/";

    @Test
    @DisplayName("Vue3 兼容：生成模板与脚手架组件不得使用 this.$set")
    void noVue2SetApiInTemplates() throws Exception {
        String[] files = {
                RES + "gen-templates/vuePage.ftl",
                RES + "scaffold-file/FileUpload.vue",
                RES + "scaffold-file/MultiImageUpload.vue",
        };
        for (String f : files) {
            String content = Files.readString(Path.of(f), StandardCharsets.UTF_8);
            assertFalse(content.contains("this.$set("),
                    f + " 仍在用 Vue2 的 this.$set，Vue3 产物会运行时报错：" + firstLine(content, "this.$set("));
        }
    }

    @Test
    @DisplayName("Vue3 兼容：vuePage 模板枚举内置不依赖空字典表")
    void enumMapRenderedInline() throws Exception {
        String tpl = Files.readString(Path.of(RES + "gen-templates/vuePage.ftl"), StandardCharsets.UTF_8);
        assertTrue(tpl.contains("enumMap: {"), "模板必须内置 enumMap，避免依赖可能为空的 sys_dict 表");
        assertTrue(tpl.contains("hasEnum"), "模板必须识别注释里的枚举并渲染下拉");
    }

    private static String firstLine(String text, String token) {
        int i = text.indexOf(token);
        if (i < 0) return "";
        int line = 1;
        for (int j = 0; j < i; j++) if (text.charAt(j) == '\n') line++;
        return "line " + line;
    }
}
