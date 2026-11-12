package com.codegen.service;

import com.codegen.config.I18nCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V8.0 渲染后文案重写器：把产物前端里写死的中文 UI 文案换成 {@code $t('common.*')}。
 *
 * <p><b>为什么不在 .ftl 里改</b>：V7.4 建立了「22 个模板源码 / jar / DB 三副本 MD5 一致」
 * 门禁与 13 类基线快照。直接在模板里替换会让默认（未开 i18n）场景的产物也变，
 * 全部基线都要刷，风险远大于收益。这里在渲染结果上做精确串替换：
 * <ul>
 *   <li>开关关闭 → 原样返回，<b>存量产物逐字节零 diff</b>；</li>
 *   <li>规则唯一真源是 {@code i18n/keys.json}，同一句中文只写一次；</li>
 *   <li>.vue 用 {@code $t}（模板里可直接用、script 里规则已写 {@code this.@@T@@}），
 *       独立 .js 用 {@code window.$t}（由产物 main.js 挂载，无需 import）。</li>
 * </ul>
 *
 * <p>{@code @@T@@} 占位是刻意的：Java {@code Matcher.replaceAll} 会把 {@code $t} 里的
 * {@code $t} 当成非法组引用直接抛异常，而 regex 规则又需要 {@code $1} 回引。
 * 全部规则替换完成后统一还原成 {@code $t} / {@code window.$t}。
 */
public final class I18nTextRewriter {

    private static final Logger log = LoggerFactory.getLogger(I18nTextRewriter.class);

    private static final String PLACEHOLDER = "@@T@@";

    private I18nTextRewriter() {
    }

    public static boolean isFrontendFile(String relPath) {
        if (relPath == null) {
            return false;
        }
        String p = relPath.replace('\\', '/');
        return p.endsWith(".vue") || p.endsWith(".js");
    }

    /** 批量重写（生成入口统一调用）；开关关闭或空词库时不改动任何字节 */
    public static Map<String, String> rewrite(Map<String, String> files, boolean enabled) {
        if (!enabled || files == null || files.isEmpty() || I18nCatalog.rules().isEmpty()) {
            return files;
        }
        Map<String, String> out = new LinkedHashMap<>(files.size());
        int hits = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String content = e.getValue();
            String rewritten = isFrontendFile(e.getKey()) ? rewriteOne(e.getKey(), content) : content;
            if (!rewritten.equals(content)) {
                hits++;
            }
            out.put(e.getKey(), rewritten);
        }
        log.info("[I18nTextRewriter] {} 个文件中 {} 个发生文案接线", files.size(), hits);
        return out;
    }

    /** 单文件重写（脚手架 write() 逐文件调用） */
    public static String rewriteOne(String relPath, String content) {
        if (content == null || content.isEmpty() || I18nCatalog.rules().isEmpty()) {
            return content;
        }
        String out = content;
        for (I18nCatalog.Rule rule : I18nCatalog.rules()) {
            try {
                if (rule.regex()) {
                    out = Pattern.compile(rule.from()).matcher(out).replaceAll(rule.to());
                } else {
                    out = out.replace(rule.from(), rule.to());
                }
            } catch (RuntimeException ex) {
                // 单条规则写错不能让整次生成失败；记下键名便于修词库
                log.warn("[I18nTextRewriter] 规则 {} 执行失败：{}", rule.key(), ex.getMessage());
            }
        }
        if (!out.contains(PLACEHOLDER)) {
            return out;
        }
        String marker = relPath != null && relPath.endsWith(".js") ? "window.$t" : "$t";
        return out.replace(PLACEHOLDER, marker);
    }

    /** 统计某段文本里还残留多少条可接线文案（自检/单测用，不修改内容） */
    public static int countRemaining(String content) {
        if (content == null) {
            return 0;
        }
        int n = 0;
        for (I18nCatalog.Rule rule : I18nCatalog.rules()) {
            if (rule.regex()) {
                n += (int) Pattern.compile(rule.from()).matcher(content).results().count();
            } else if (content.contains(rule.from())) {
                n++;
            }
        }
        return n;
    }
}
