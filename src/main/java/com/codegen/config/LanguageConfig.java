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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 项目语言配置：平台级只读清单 + 唯一的回落实现。
 *
 * <p><b>单一真源</b>：{@code classpath:i18n/languages.json}（编译进 jar，随版本走）。
 * 数据库里的 {@code sys_language} 只是可选镜像，运行时不依赖它——表不存在、
 * 没跑迁移脚本，接口照样返回全部语言。这与「迁移脚本漏列导致列表接口 500」
 * 的历史事故（坑库 mysql.md V6.5）做了隔离。
 *
 * <p><b>单一实现</b>：语言码归一、默认语言回落、BCP47 标签解析、dir 判定、
 * HTML 属性合法性校验全部只在这里实现。模板渲染侧（静态上下文，无法注入 Bean）
 * 直接调用这里的静态方法，不允许在 .ftl 或 GenScaffoldServiceImpl 里再写一遍。
 *
 * <p><b>降级</b>：JSON 缺失或解析失败时回落内置最小清单（zh-CN / en-US），
 * 只打 warn 不抛异常，保证服务启动不被一份静态配置阻断。
 */
@Component
public class LanguageConfig {

    private static final Logger log = LoggerFactory.getLogger(LanguageConfig.class);

    /** 未配置时的默认语言：保持与改造前硬编码一致，存量系统零 diff */
    public static final String DEFAULT_LANGUAGE = "zh-CN";

    /** 拼进 HTML 属性前必须过这道校验，防止配置值里带引号打坏生成物 */
    private static final String SAFE_LANG_TAG = "^[A-Za-z0-9-]{2,32}$";

    private static final String RESOURCE = "i18n/languages.json";

    private static final List<LanguageVO> ALL = loadAll();
    private static final Map<String, LanguageVO> BY_CODE = indexByCode(ALL);
    private static final Map<String, LanguageVO> BY_LOCALE = indexByLocale(ALL);

    @PostConstruct
    public void reportLoaded() {
        long rtl = ALL.stream().filter(v -> "rtl".equals(v.getDir())).count();
        log.info("[LanguageConfig] loaded {} languages ({} rtl), default={}, source={}",
                ALL.size(), rtl, DEFAULT_LANGUAGE, RESOURCE);
    }

    // ==================== 查询 ====================

    /** 全量清单（不可变，按 sortNo 已排好序） */
    public static List<LanguageVO> all() {
        return ALL;
    }

    /**
     * 语言下拉选项。{@code value} 是 BCP47 标签，必须唯一：
     * 清单里存在「ISO 639-1 主条目」与「BCP47 变体」指向同一默认标签的情况
     * （如 {@code ms} / {@code ms-MY} 都是 ms-MY，{@code nb} / {@code nb-NO} 都是 nb-NO）。
     * 若原样输出，前端 {@code :key} 与 {@code v-model} 会出现两个同值选项，
     * Vue 报 Duplicate keys 且选中项可能错位。这里按标签去重，保留 sortNo 更小的主条目；
     * 全量清单仍由 {@link #all()} 完整返回（206 条），只合并下拉里语义完全等价的两项。
     */
    public static List<LanguageOptionVO> options() {
        List<LanguageOptionVO> list = new ArrayList<>(ALL.size());
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (LanguageVO v : ALL) {
            if (!seen.add(normalize(v.getLocale()))) {
                continue;
            }
            String label = v.getNameZh();
            if (hasText(v.getNameNative()) && !v.getNameNative().equals(v.getNameZh())) {
                label = v.getNameZh() + "（" + v.getNameNative() + "）";
            }
            list.add(new LanguageOptionVO(label, v.getLocale(),
                    v.getDir(), v.getScript(), v.getCode()));
        }
        return list;
    }

    public static List<LanguageVO> byDir(String dir) {
        if (!hasText(dir)) {
            return ALL;
        }
        List<LanguageVO> list = new ArrayList<>();
        for (LanguageVO v : ALL) {
            if (dir.equalsIgnoreCase(v.getDir())) {
                list.add(v);
            }
        }
        return list;
    }

    /**
     * 按码查询：大小写不敏感，允许 {@code zh_CN} / {@code zh-cn} 这类写法，
     * 也允许用完整 BCP47 标签（{@code ar-SA}）反查。
     */
    public static LanguageVO find(String codeOrLocale) {
        if (!hasText(codeOrLocale)) {
            return null;
        }
        String key = normalize(codeOrLocale);
        LanguageVO hit = BY_CODE.get(key);
        return hit != null ? hit : BY_LOCALE.get(key);
    }

    public static boolean isSupported(String codeOrLocale) {
        return find(codeOrLocale) != null;
    }

    // ==================== 回落（唯一实现） ====================

    /**
     * 解析生成物应使用的 {@code <html lang>}。
     *
     * <p>回落链：系统配置 → 清单里的默认 BCP47 标签 → {@code zh-CN}。
     * 传入两字母码（{@code ar}）时返回其默认地区标签（{@code ar-SA}）；
     * 传入清单外的合法标签时原样保留（不因为清单没收录就拒绝用户的自定义语言）；
     * 非法值（含引号、空串）一律回落 {@code zh-CN}，避免生成物 HTML 被打坏。
     */
    public static String resolveLocale(String configured) {
        if (!hasText(configured)) {
            return DEFAULT_LANGUAGE;
        }
        String v = configured.trim();
        if (!v.matches(SAFE_LANG_TAG)) {
            log.warn("[LanguageConfig] illegal language tag ignored: {}", v);
            return DEFAULT_LANGUAGE;
        }
        LanguageVO hit = find(v);
        if (hit != null && hasText(hit.getLocale())) {
            return hit.getLocale();
        }
        return v;
    }

    /**
     * 判定书写方向。按语言码前缀匹配（{@code ar-SA} → {@code ar} → rtl），
     * 查不到时返回 {@code ltr}，绝不抛异常中断生成。
     */
    public static String resolveDir(String configured) {
        if (!hasText(configured)) {
            return "ltr";
        }
        String v = configured.trim();
        LanguageVO hit = find(v);
        if (hit == null) {
            String base = v.split("-", 2)[0].toLowerCase(Locale.ROOT);
            hit = BY_CODE.get(base);
        }
        return hit != null && "rtl".equals(hit.getDir()) ? "rtl" : "ltr";
    }

    /** 校验系统配置里填的语言码，供保存接口使用；返回 null 表示合法 */
    public static String validate(String configured) {
        if (!hasText(configured)) {
            return null;
        }
        String v = configured.trim();
        if (!v.matches(SAFE_LANG_TAG)) {
            return "语言标签只能包含字母、数字和连字符：" + v;
        }
        if (!isSupported(v)) {
            return "不在支持的语言清单中：" + v;
        }
        return null;
    }

    // ==================== 加载 ====================

    private static List<LanguageVO> loadAll() {
        try (InputStream in = LanguageConfig.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[LanguageConfig] {} not found, fallback to minimal list", RESOURCE);
                return minimal();
            }
            JsonNode root = new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            JsonNode arr = root.get("languages");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                log.warn("[LanguageConfig] {} has no languages, fallback to minimal list", RESOURCE);
                return minimal();
            }
            ObjectMapper mapper = new ObjectMapper();
            List<LanguageVO> list = new ArrayList<>(arr.size());
            for (JsonNode n : arr) {
                LanguageVO v = mapper.treeToValue(n, LanguageVO.class);
                if (!hasText(v.getCode()) || !hasText(v.getLocale())) {
                    continue;
                }
                if (!hasText(v.getDir())) {
                    v.setDir("ltr");
                }
                if (v.getEnabled() == null) {
                    v.setEnabled(true);
                }
                list.add(v);
            }
            if (list.isEmpty()) {
                return minimal();
            }
            list.sort((a, b) -> Integer.compare(
                    a.getSortNo() == null ? 0 : a.getSortNo(),
                    b.getSortNo() == null ? 0 : b.getSortNo()));
            return Collections.unmodifiableList(list);
        } catch (Exception e) {
            log.warn("[LanguageConfig] failed to load {}, fallback to minimal list: {}",
                    RESOURCE, e.toString());
            return minimal();
        }
    }

    private static List<LanguageVO> minimal() {
        List<LanguageVO> list = List.of(
                new LanguageVO("zh", "zh-CN", "Chinese", "中文", "中文", "Hans", "ltr"),
                new LanguageVO("en", "en-US", "English", "英语", "English", "Latn", "ltr"));
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    private static Map<String, LanguageVO> indexByCode(List<LanguageVO> list) {
        Map<String, LanguageVO> map = new LinkedHashMap<>();
        for (LanguageVO v : list) {
            map.putIfAbsent(normalize(v.getCode()), v);
        }
        return map;
    }

    private static Map<String, LanguageVO> indexByLocale(List<LanguageVO> list) {
        Map<String, LanguageVO> map = new LinkedHashMap<>();
        for (LanguageVO v : list) {
            if (hasText(v.getLocale())) {
                map.putIfAbsent(normalize(v.getLocale()), v);
            }
        }
        return map;
    }

    /** 归一：{@code _} → {@code -}，全小写；{@code ZH_CN} 与 {@code zh-cn} 等价 */
    private static String normalize(String s) {
        return s.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
