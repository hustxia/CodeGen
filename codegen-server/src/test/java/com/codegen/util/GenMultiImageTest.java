package com.codegen.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.8 生成物可维护性：多图列识别、品牌回落、图片尺寸常量的单测。
 *
 * <p>三条都是「一处实现、多处消费」的规则，写死在这里防止后续改动把语义打回去：
 * 多图必须优先于单图识别（{@code slider_images} 不能被当成普通文本），
 * 主色非法值必须回落而不是拼进 CSS。
 */
class GenMultiImageTest {

    @Test
    @DisplayName("多图列名（复数图片语义）识别为 multiImage，单图仍是 image")
    void multiImageWinsOverSingleImage() {
        assertEquals("multiImage", GenUtils.inferHtmlType("varchar(1024)", "String", "slider_images"));
        assertEquals("multiImage", GenUtils.inferHtmlType("varchar(1024)", "String", "detail_pics"));
        assertEquals("multiImage", GenUtils.inferHtmlType("varchar(1024)", "String", "evidence_photos"));
        assertEquals("multiImage", GenUtils.inferHtmlType("varchar(1024)", "String", "gallery"));
        // 单数结尾仍是单图上传，不能被多图规则吃掉
        assertEquals("image", GenUtils.inferHtmlType("varchar(255)", "String", "cover_image"));
        assertEquals("image", GenUtils.inferHtmlType("varchar(255)", "String", "avatar"));
        // 非字符串列不参与（JSON 数组列一定是 varchar/text）
        assertFalse("multiImage".equals(GenUtils.inferHtmlType("int", "Integer", "slider_images")));
    }

    @Test
    @DisplayName("主题主色：合法值原样保留，非法值/空回落默认色")
    void themeColorFallback() {
        assertEquals("#e1251b", GenTemplateModel.resolveThemeColor(null));
        assertEquals("#e1251b", GenTemplateModel.resolveThemeColor("   "));
        assertEquals("#e1251b", GenTemplateModel.resolveThemeColor("red;"));
        assertEquals("#e1251b", GenTemplateModel.resolveThemeColor("#12345"));
        assertEquals("#e1251b", GenTemplateModel.resolveThemeColor("javascript:alert(1)"));
        assertEquals("#0a84ff", GenTemplateModel.resolveThemeColor("#0a84ff"));
        assertEquals("#0A84FF", GenTemplateModel.resolveThemeColor(" #0A84FF "));
        assertEquals("#fff", GenTemplateModel.resolveThemeColor("#fff"));
    }

    @Test
    @DisplayName("应用显示名：配置 → 项目名 → 编码 → 兜底，逐级回落")
    void appNameFallback() {
        assertEquals("悦集商城", GenTemplateModel.resolveAppName("悦集商城", "mall", "mall"));
        assertEquals("mall", GenTemplateModel.resolveAppName(null, "mall", "mall"));
        assertEquals("mall", GenTemplateModel.resolveAppName("  ", "mall", "mall"));
        assertEquals("mall", GenTemplateModel.resolveAppName(null, null, "mall"));
        assertEquals("应用", GenTemplateModel.resolveAppName(null, null, null));
    }

    @Test
    @DisplayName("图片展示尺寸是常量单源：列表 80 / 详情 120（模板只消费，不再写死像素）")
    void imageSizeConstants() {
        assertEquals(80, GenTemplateModel.IMAGE_LIST_SIZE);
        assertEquals(120, GenTemplateModel.IMAGE_DETAIL_SIZE);
        assertTrue(GenTemplateModel.DEFAULT_THEME_COLOR.matches("^#[0-9a-fA-F]{6}$"));
    }
}
