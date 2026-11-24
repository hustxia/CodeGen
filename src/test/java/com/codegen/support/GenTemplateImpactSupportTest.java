package com.codegen.support;

import com.codegen.entity.GenTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板影响面预览的纯函数测试：路径归一化、编码提取、表形态提示。
 *
 * <p>这些函数决定「页面选的模板路径」能否匹配上「渲染出的产物路径」，
 * 差一个斜杠就会静默显示零影响，所以单独钉死。
 */
@DisplayName("GenTemplateImpactSupport 纯函数")
class GenTemplateImpactSupportTest {

    @Test
    @DisplayName("模板路径归一化：反斜杠/重复斜杠/前缀斜杠")
    void normalizeTemplatePath() {
        assertEquals("gen-templates/controller.ftl",
                GenTemplateImpactSupport.normalizeTemplatePath(".\\gen-templates//controller.ftl"));
        assertEquals("gen-templates/entity.ftl",
                GenTemplateImpactSupport.normalizeTemplatePath("/gen-templates/entity.ftl"));
        assertNull(GenTemplateImpactSupport.normalizeTemplatePath("   "));
        assertNull(GenTemplateImpactSupport.normalizeTemplatePath(null));
    }

    @Test
    @DisplayName("模板编码：文件名去 .ftl，非 ftl 返回 null")
    void templateCode() {
        assertEquals("controller", GenTemplateImpactSupport.templateCode("gen-templates/controller.ftl"));
        // 编码大小写必须原样保留，否则匹配不上 gen_template.tpl_code
        assertEquals("payBizApi", GenTemplateImpactSupport.templateCode("/gen-templates/payBizApi.ftl"));
        assertNull(GenTemplateImpactSupport.templateCode("gen-templates/controller.txt"));
        assertNull(GenTemplateImpactSupport.templateCode("gen-templates/.ftl"));
    }

    @Test
    @DisplayName("从 classpath 资源地址截出模板相对路径")
    void templatePathFromResource() {
        assertEquals("gen-templates/entity.ftl",
                GenTemplateImpactSupport.templatePathFromResource(
                        "file:/D:/repo/target/classes/gen-templates/entity.ftl"));
        assertEquals("scaffold-mobile/src/pages/list.ftl",
                GenTemplateImpactSupport.templatePathFromResource(
                        "jar:file:/app.jar!/scaffold-mobile/src/pages/list.ftl"));
        // 仓库路径更早的位置出现 scaffold-* 目录时，以最后一个模板目录段为准
        assertEquals("gen-templates/entity.ftl",
                GenTemplateImpactSupport.templatePathFromResource(
                        "file:/D:/repo/scaffold-tools/gen-templates/entity.ftl"));
        assertNull(GenTemplateImpactSupport.templatePathFromResource("file:/D:/repo/other/x.ftl"));
    }

    @Test
    @DisplayName("产物路径归一化与模板路径同规则")
    void normalizeOutputPath() {
        assertEquals("src/api/product.js",
                GenTemplateImpactSupport.normalizeOutputPath("src\\api//product.js"));
    }

    @Test
    @DisplayName("表形态提示：单表 / 子表 / 主表 / 树形 / 多对多")
    void modeHints() {
        GenTable plain = new GenTable();
        plain.setTableName("demo_goods");
        assertEquals(List.of("单表"), GenTemplateImpactSupport.modeHints(plain, false));

        GenTable child = new GenTable();
        child.setMasterTableId(1L);
        assertEquals(List.of("主子表（子表）"), GenTemplateImpactSupport.modeHints(child, false));

        assertEquals(List.of("主子表（主表）"), GenTemplateImpactSupport.modeHints(plain, true));

        GenTable tree = new GenTable();
        tree.setTreeParentColumn("parent_id");
        assertEquals(List.of("树形"), GenTemplateImpactSupport.modeHints(tree, false));

        GenTable middle = new GenTable();
        middle.setRelationType("middle");
        assertEquals(List.of("多对多"), GenTemplateImpactSupport.modeHints(middle, false));

        // 组合形态按固定顺序去重输出
        GenTable combined = new GenTable();
        combined.setTreeParentColumn("parent_id");
        List<String> hints = GenTemplateImpactSupport.modeHints(combined, true);
        assertEquals(List.of("主子表（主表）", "树形"), hints);
        assertTrue(hints.size() == 2);
    }
}
