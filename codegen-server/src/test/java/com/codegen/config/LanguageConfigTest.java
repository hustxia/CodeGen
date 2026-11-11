package com.codegen.config;

import com.codegen.vo.LanguageVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V7.5 项目语言配置：清单完整性 + 回落链 + 边界。
 *
 * <p>刻意不依赖 Spring 上下文 / 数据库——LanguageConfig 从 classpath JSON 加载，
 * 这也是「迁移脚本漏列不会让接口 500」的验证。
 */
class LanguageConfigTest {

    @Test
    void 清单覆盖100种以上且无重复码() {
        List<LanguageVO> all = LanguageConfig.all();
        assertTrue(all.size() >= 100, "世界语言清单应覆盖 100+ 种，实际 " + all.size());
        long distinct = all.stream().map(LanguageVO::getCode).distinct().count();
        assertEquals(all.size(), distinct, "语言码不能重复");
        assertTrue(all.stream().allMatch(v -> v.getLocale() != null && !v.getLocale().isBlank()));
        assertTrue(all.stream().allMatch(v -> v.getDir() != null));
    }

    @Test
    void 排序严格递增() {
        List<LanguageVO> all = LanguageConfig.all();
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).getSortNo() < all.get(i).getSortNo(),
                    "sortNo 必须严格递增，位置 " + i);
        }
    }

    @Test
    void 中文与英文默认语言可查() {
        LanguageVO zh = LanguageConfig.find("zh");
        LanguageVO en = LanguageConfig.find("EN");
        assertNotNull(zh);
        assertNotNull(en, "语言码比对应大小写不敏感");
        assertEquals("zh-CN", zh.getLocale());
        assertEquals("en-US", en.getLocale());
        assertEquals("中文", zh.getNameZh());
    }

    @Test
    void 下划线与大小写写法等价() {
        assertEquals("zh-CN", LanguageConfig.resolveLocale("ZH_CN"),
                "zh_CN 应归一为 zh-CN");
        assertEquals("zh-CN", LanguageConfig.resolveLocale("zh-cn"));
    }

    @Test
    void 未配置回落zh_CN保持存量零diff() {
        assertEquals("zh-CN", LanguageConfig.resolveLocale(null));
        assertEquals("zh-CN", LanguageConfig.resolveLocale(""));
        assertEquals("zh-CN", LanguageConfig.resolveLocale("   "));
    }

    @Test
    void 两字母码展开为默认地区标签() {
        assertEquals("ar-SA", LanguageConfig.resolveLocale("ar"));
        assertEquals("ja-JP", LanguageConfig.resolveLocale("ja"));
        assertEquals("pt-BR", LanguageConfig.resolveLocale("pt-BR"));
    }

    @Test
    void 非法标签被拒绝避免打坏生成物HTML() {
        assertEquals("zh-CN", LanguageConfig.resolveLocale("zh-CN\"><script>"),
                "含引号的标签必须回落，不能直接拼进 <html lang>");
        assertEquals("zh-CN", LanguageConfig.resolveLocale("x"));
        assertNotNull(LanguageConfig.validate("xx\""), "校验应报非法字符");
    }

    @Test
    void rtlLanguageDirectionResolvedByPrefix() {
        assertEquals("rtl", LanguageConfig.resolveDir("ar-SA"));
        assertEquals("rtl", LanguageConfig.resolveDir("fa"));
        assertEquals("rtl", LanguageConfig.resolveDir("he-IL"));
        assertEquals("ltr", LanguageConfig.resolveDir("zh-CN"));
        assertEquals("ltr", LanguageConfig.resolveDir("en-US"));
        assertEquals("ltr", LanguageConfig.resolveDir("不存在的语言"));
        List<LanguageVO> rtl = LanguageConfig.byDir("rtl");
        assertTrue(rtl.size() >= 10, "RTL 语言至少 10 种，实际 " + rtl.size());
        assertTrue(rtl.stream().allMatch(v -> "rtl".equals(v.getDir())));
    }

    @Test
    void 清单外的合法标签原样保留() {
        // 用户自定义语言不应因清单未收录就被强制改成中文
        assertEquals("xx-YY", LanguageConfig.resolveLocale("xx-YY"));
    }

    @Test
    void 校验与查询边界() {
        assertNull(LanguageConfig.validate(null), "空值表示未配置，合法");
        assertNull(LanguageConfig.validate("zh-CN"));
        assertNotNull(LanguageConfig.validate("not-a-real-lang"));
        assertNull(LanguageConfig.find(null));
        assertFalse(LanguageConfig.isSupported("zz"));
        assertTrue(LanguageConfig.isSupported("zh"));
    }

    @Test
    void 下拉选项含中文名本地自称与方向() {
        var options = LanguageConfig.options();
        assertTrue(options.size() >= 100, "下拉至少要覆盖 100+ 种可选项，实际 " + options.size());
        assertTrue(options.stream().anyMatch(o -> o.getLabel().contains("中文")));
        assertTrue(options.stream().anyMatch(o -> "rtl".equals(o.getDir())));
        assertTrue(options.stream().allMatch(o -> o.getValue() != null && o.getCode() != null));
    }

    @Test
    void 下拉选项BCP47标签唯一避免Vue重复键() {
        var options = LanguageConfig.options();
        long distinctValues = options.stream().map(o -> o.getValue().toLowerCase()).distinct().count();
        assertEquals(options.size(), distinctValues,
                "ms/nb 的 BCP47 变体与主条目 locale 相同，下拉必须按标签去重");
        long distinctCodes = options.stream().map(o -> o.getCode()).distinct().count();
        assertEquals(options.size(), distinctCodes, "语言码也必须唯一");
    }

    @Test
    void 已关联README译本() {
        long withReadme = LanguageConfig.all().stream()
                .filter(v -> v.getReadme() != null && !v.getReadme().isBlank()).count();
        assertTrue(withReadme >= 40, "应与仓库已有的 48 个 README 译本对齐，实际 " + withReadme);
    }
}
