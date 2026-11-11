package com.codegen.config;

import com.codegen.vo.LanguageOptionVO;
import com.codegen.vo.LanguageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V8.0 产物国际化词库：平台级静态配置的唯一真源。
 *
 * <p><b>三条单一实现</b>（坑库「测试与基线.md」：同一用户可见文案必须同源同一个变量）：
 * <ol>
 *   <li>语言清单沿用 V7.5 {@link LanguageConfig}，本类不另建清单；</li>
 *   <li>文案键与重写规则同在 {@code classpath:i18n/keys.json}，平台翻译页、
 *       内置词库、渲染后重写器读的是同一份；</li>
 *   <li>译文回落只在产物 {@code src/utils/i18n.js} 的 {@code t()} 实现，
 *       平台生成占位包时只写空串，不复制默认值。</li>
 * </ol>
 *
 * <p>classpath 资源缺失/损坏时降级为空词库并 WARN，不阻断服务启动（V7.5 同款）。
 */
@Component
public class I18nCatalog {

    private static final Logger log = LoggerFactory.getLogger(I18nCatalog.class);

    public static final String SOURCE_LOCALE = "zh-CN";

    /** 语言标签拼进文件名前必须过这道校验，防路径穿越 */
    private static final String SAFE_TAG = "^[A-Za-z0-9-]{2,32}$";

    private static final String KEYS_RESOURCE = "i18n/keys.json";
    private static final String MESSAGES_DIR = "i18n/messages/";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Catalog CATALOG = load();

    /** 文案键定义（平台翻译页表格行） */
    public record KeyDef(String key, String category, String sourceText) {
    }

    /** 渲染后重写规则；{@code to} 里的 {@code @@T@@} 由重写器还原成 $t / window.$t */
    public record Rule(String key, String from, String to, boolean regex) {
    }

    private record Catalog(List<KeyDef> keys, List<Rule> rules, List<String> builtinLocales,
                           Map<String, Map<String, String>> messages, String version) {
    }

    @PostConstruct
    public void reportLoaded() {
        log.info("[I18nCatalog] v{} loaded {} keys / {} rewrite rules / {} builtin locales",
                CATALOG.version(), CATALOG.keys().size(), CATALOG.rules().size(),
                CATALOG.builtinLocales().size());
    }

    public static String version() {
        return CATALOG.version();
    }

    public static List<KeyDef> keys() {
        return CATALOG.keys();
    }

    public static List<Rule> rules() {
        return CATALOG.rules();
    }

    public static List<String> builtinLocales() {
        return CATALOG.builtinLocales();
    }

    public static int totalKeys() {
        return CATALOG.keys().size();
    }

    /** 某个内置精译语言的值；非精译语言返回空 Map */
    public static Map<String, String> builtin(String locale) {
        return CATALOG.messages().getOrDefault(locale, Map.of());
    }

    public static boolean isBuiltin(String locale) {
        return CATALOG.builtinLocales().contains(locale);
    }

    /**
     * 按系统配置算出要生成的语言标签列表。
     *
     * <p>留空 = 默认语言 + 8 种内置精译（避免一次生成 206 个空包）；
     * 显式配置 = 按清单顺序，非白名单标签丢弃并 WARN；默认语言恒在第一位。
     */
    public static List<String> resolveLocales(String supportedLanguages, String defaultLanguage) {
        String def = LanguageConfig.resolveLocale(defaultLanguage);
        Set<String> valid = new LinkedHashSet<>();
        for (LanguageOptionVO option : LanguageConfig.options()) {
            valid.add(option.getValue());
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (def.matches(SAFE_TAG)) {
            out.add(def);
        }
        if (supportedLanguages == null || supportedLanguages.isBlank()) {
            for (String loc : CATALOG.builtinLocales()) {
                if (valid.contains(loc)) {
                    out.add(loc);
                }
            }
            return new ArrayList<>(out);
        }
        for (String raw : supportedLanguages.split(",")) {
            String tag = raw.trim();
            if (tag.isEmpty() || !tag.matches(SAFE_TAG)) {
                log.warn("[I18nCatalog] 跳过非法语言标签 {}", tag);
                continue;
            }
            if (valid.contains(tag)) {
                out.add(tag);
                continue;
            }
            LanguageVO hit = LanguageConfig.find(tag);
            if (hit != null && valid.contains(hit.getLocale())) {
                out.add(hit.getLocale());
            } else {
                log.warn("[I18nCatalog] 跳过清单外语言标签 {}", tag);
            }
        }
        return new ArrayList<>(out);
    }

    /** 语言展示名与书写方向，给产物切换器用；找不到时回落为标签本身 + ltr */
    public static LocaleMeta localeMeta(String locale) {
        for (LanguageVO v : LanguageConfig.all()) {
            if (v.getLocale().equals(locale) || v.getCode().equals(locale)) {
                return new LocaleMeta(locale, v.getNameZh(), v.getNameNative(), v.getDir());
            }
        }
        return new LocaleMeta(locale, locale, locale, LanguageConfig.resolveDir(locale));
    }

    public record LocaleMeta(String locale, String label, String nameNative, String dir) {
    }

    /**
     * 组装一个语言包的 JSON 文本：所有键齐全，值优先级 用户译文 &gt; 内置精译 &gt; 占位。
     *
     * <p>占位写空串，由产物运行时回落到默认语言；默认语言自身没有译文时
     * 用中文原文兜底，保证界面不会出现空白。
     *
     * @param locale        目标语言
     * @param overrides     用户译文（可为 null）
     * @param defaultLocale 该系统默认语言
     */
    public static String languagePackage(String locale, Map<String, String> overrides,
                                         String defaultLocale) {
        Map<String, String> builtin = builtin(locale);
        boolean isDefault = locale.equals(defaultLocale);
        Map<String, String> data = new LinkedHashMap<>();
        for (KeyDef k : CATALOG.keys()) {
            String value = null;
            if (overrides != null) {
                value = overrides.get(k.key());
            }
            if (value == null) {
                value = builtin.get(k.key());
            }
            if ((value == null || value.isBlank()) && isDefault) {
                value = k.sourceText();
            }
            data.put(k.key(), value == null ? "" : value);
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException("语言包序列化失败: " + locale, e);
        }
    }

    /** 文案键的中文原文，供平台翻译页展示 */
    public static String sourceText(String key) {
        for (KeyDef k : CATALOG.keys()) {
            if (k.key().equals(key)) {
                return k.sourceText();
            }
        }
        return null;
    }

    public static boolean isKnownKey(String key) {
        return sourceText(key) != null;
    }

    // ==================== 加载 ====================

    private static Catalog load() {
        try (InputStream in = I18nCatalog.class.getClassLoader().getResourceAsStream(KEYS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 缺失 " + KEYS_RESOURCE);
            }
            JsonNode root = MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<KeyDef> keys = new ArrayList<>();
            for (JsonNode n : root.path("keys")) {
                keys.add(new KeyDef(n.path("key").asText(), n.path("category").asText(),
                        n.path("sourceText").asText()));
            }
            List<Rule> rules = new ArrayList<>();
            for (JsonNode n : root.path("rewrite")) {
                rules.add(new Rule(n.path("key").asText(), n.path("from").asText(),
                        n.path("to").asText(), n.path("regex").asBoolean(false)));
            }
            List<String> locales = new ArrayList<>();
            for (JsonNode n : root.path("builtinLocales")) {
                locales.add(n.asText());
            }
            Map<String, Map<String, String>> messages = new LinkedHashMap<>();
            for (String loc : locales) {
                messages.put(loc, loadMessages(loc));
            }
            return new Catalog(Collections.unmodifiableList(keys), Collections.unmodifiableList(rules),
                    Collections.unmodifiableList(locales), Collections.unmodifiableMap(messages),
                    root.path("version").asText("8.0"));
        } catch (Exception e) {
            log.warn("[I18nCatalog] 词库加载失败，降级为空词库（i18n 开关打开时不产出语言包）：{}",
                    e.getMessage());
            return new Catalog(List.of(), List.of(), List.of(), Map.of(), "degraded");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadMessages(String locale) {
        String path = MESSAGES_DIR + locale + ".json";
        try (InputStream in = I18nCatalog.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                log.warn("[I18nCatalog] 缺少精译文案 {}", path);
                return Map.of();
            }
            Map<String, Object> raw = MAPPER.readValue(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
            return Collections.unmodifiableMap(out);
        } catch (Exception e) {
            log.warn("[I18nCatalog] 精译文案解析失败 {}：{}", path, e.getMessage());
            return Map.of();
        }
    }
}
