package com.codegen.quality;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 一条可执行的质量规则（M2.2）。
 *
 * <p>单一事实源：规则目录 {@link QualityRuleCatalog} 声明 52 条，确定性引擎按
 * {@link Detector} 执行，LLM 引擎按规则清单做语义审查。每条规则都带
 * ❌ 反例 / ✅ 正例，用于报告里做行级对照。
 */
public record QualityRule(
        String id,
        String title,
        Category category,
        Engine engine,
        Severity severity,
        List<String> fileExtensions,
        Detector detector,
        Pattern pattern,
        String message,
        String badSnippet,
        String goodSnippet,
        String sourceRef) {

    public enum Category {
        JAVA, VUE, JS, SQL, CONFIG
    }

    public enum Engine {
        /** 确定性引擎：正则/结构化扫描，逐行定位 */
        DET,
        /** LLM 语义引擎：批量送审，严格 JSON 回填 */
        LLM
    }

    public enum Severity {
        BLOCKER, WARN
    }

    public enum Detector {
        REGEX,
        /** 手写 SQL 缺少 deleted = 0 */
        SQL_MISSING_DELETED,
        /** CREATE TABLE 缺少审计列 */
        SQL_MISSING_AUDIT,
        /** Map.of 键值对超过 10 组 */
        MAP_OF_ARITY,
        /** 雪花 Long ID 直接放进响应 Map */
        LONG_ID_JSON,
        /** 硬编码口令/密钥 */
        HARDCODED_SECRET,
        /** TODO/FIXME 残留 */
        TODO_MARKER,
        /** 仅 LLM 使用 */
        NONE
    }

    public boolean matchesFile(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return fileExtensions.stream().anyMatch(lower::endsWith);
    }

    static QualityRule det(String id, String title, Category category, Severity severity,
                           List<String> extensions, Detector detector, String regex,
                           String message, String badSnippet, String goodSnippet, String sourceRef) {
        return new QualityRule(id, title, category, Engine.DET, severity, extensions, detector,
                regex == null ? null : Pattern.compile(regex), message, badSnippet, goodSnippet, sourceRef);
    }

    static QualityRule llm(String id, String title, Category category, Severity severity,
                           List<String> extensions, String message,
                           String badSnippet, String goodSnippet, String sourceRef) {
        return new QualityRule(id, title, category, Engine.LLM, severity, extensions,
                Detector.NONE, null, message, badSnippet, goodSnippet, sourceRef);
    }
}
