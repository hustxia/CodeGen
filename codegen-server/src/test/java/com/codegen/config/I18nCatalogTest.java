package com.codegen.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** V8.0 词库契约：键齐全、精译完整、占位回落、语言清单白名单。 */
class I18nCatalogTest {

    @Test
    @DisplayName("词库 60 键 / 8 精译 / 51 重写规则，且加载未降级")
    void 词库规模与完整性() {
        assertNotEquals("degraded", I18nCatalog.version());
        assertEquals(60, I18nCatalog.totalKeys());
        assertEquals(8, I18nCatalog.builtinLocales().size());
        assertEquals(51, I18nCatalog.rules().size());
        assertTrue(I18nCatalog.keys().stream().allMatch(k -> k.key().startsWith("common.")));
        assertTrue(I18nCatalog.rules().stream().allMatch(r -> I18nCatalog.isKnownKey(r.key())));
    }

    @Test
    @DisplayName("8 种精译每种都覆盖全部 60 键且值非空")
    void 精译键齐全() {
        for (String loc : I18nCatalog.builtinLocales()) {
            Map<String, String> data = I18nCatalog.builtin(loc);
            assertEquals(60, data.size(), loc + " 键数不足");
            assertTrue(data.values().stream().allMatch(v -> v != null && !v.isBlank()),
                    loc + " 存在空译文");
        }
        assertEquals("Add", I18nCatalog.builtin("en-US").get("common.add"));
        assertEquals("بحث", I18nCatalog.builtin("ar-SA").get("common.query"));
    }

    @Test
    @DisplayName("语言包按 用户译文 > 内置精译 > 占位 组装，键恒定 60")
    void 语言包优先级() {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("common.add", "Create");
        String pkg = I18nCatalog.languagePackage("en-US", overrides, "zh-CN");
        assertTrue(pkg.contains("\"common.add\" : \"Create\""), "用户译文未覆盖内置值");
        assertTrue(pkg.contains("\"common.query\" : \"Search\""), "内置精译丢失");

        String placeholder = I18nCatalog.languagePackage("ru-RU", null, "zh-CN");
        assertTrue(placeholder.contains("\"common.add\" : \"\""), "占位包应写空串交给运行时回落");
        assertEquals(60, placeholder.lines().filter(l -> l.contains("\"common.")).count());

        String fallback = I18nCatalog.languagePackage("ru-RU", null, "ru-RU");
        assertTrue(fallback.contains("\"common.add\" : \"新增\""),
                "默认语言无译文时应用中文原文兜底，界面不能空白");
    }

    @Test
    @DisplayName("未配语言=默认+8 精译；显式配置按清单；非法/清单外标签被丢弃")
    void 语言清单解析() {
        List<String> defaults = I18nCatalog.resolveLocales(null, "zh-CN");
        assertEquals("zh-CN", defaults.get(0));
        for (String loc : I18nCatalog.builtinLocales()) {
            assertTrue(defaults.contains(loc));
        }
        assertEquals(8, defaults.size());

        List<String> picked = I18nCatalog.resolveLocales("zh-CN,en-US,ar-SA", "zh-CN");
        assertEquals(List.of("zh-CN", "en-US", "ar-SA"), picked);

        List<String> bad = I18nCatalog.resolveLocales("../../etc,zh-CN,not-a-lang", "ar-SA");
        assertEquals(List.of("ar-SA", "zh-CN"), bad);
    }

    @Test
    @DisplayName("RTL 语言元数据沿用 V7.5 清单，不另建一份")
    void RTL元数据() {
        I18nCatalog.LocaleMeta ar = I18nCatalog.localeMeta("ar-SA");
        assertEquals("rtl", ar.dir());
        assertEquals("ltr", I18nCatalog.localeMeta("en-US").dir());
        assertNotNull(ar.nameNative());
    }
}
