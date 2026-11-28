package com.codegen.quality;

import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenQualityIssueVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 确定性质量扫描器（M2.2）。
 *
 * <p>不依赖任何外部服务：按 {@link QualityRuleCatalog} 的 DET 规则逐文件扫描，
 * 命中即给出行号与 ❌ 原文，✅ 取规则库的修正示例。同样的输入永远得到同样的结果，
 * 因此可以放心用于「落盘前阻断」。
 */
@Component
public class DeterministicQualityScanner {

    /** 单文件单规则最多报 20 条，避免一个坏模板刷爆报告 */
    private static final int MAX_PER_RULE_FILE = 20;

    private static final int SNIPPET_MAX = 300;

    public List<GenQualityIssueVO> scan(List<GenFileVO> files) {
        List<GenQualityIssueVO> issues = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return issues;
        }
        Set<String> seen = new HashSet<>();
        for (GenFileVO file : files) {
            String path = file.getPath();
            String content = file.getContent() == null ? "" : file.getContent();
            if (path == null || content.isEmpty()) {
                continue;
            }
            for (QualityRule rule : QualityRuleCatalog.deterministicRules()) {
                if (!rule.matchesFile(path)) {
                    continue;
                }
                List<GenQualityIssueVO> hits = switch (rule.detector()) {
                    case REGEX -> scanRegex(rule, path, content);
                    case SQL_MISSING_DELETED -> scanSqlMissingDeleted(rule, path, content);
                    case SQL_MISSING_AUDIT -> scanCreateTableMissingAudit(rule, path, content);
                    case MAP_OF_ARITY -> scanMapOfArity(rule, path, content);
                    case LONG_ID_JSON -> scanLongIdJson(rule, path, content);
                    case HARDCODED_SECRET -> scanSecret(rule, path, content);
                    case TODO_MARKER -> scanTodo(rule, path, content);
                    case NONE -> List.of();
                };
                for (GenQualityIssueVO issue : hits) {
                    String key = issue.getRuleId() + "|" + issue.getFile() + "|" + issue.getLineNo();
                    if (seen.add(key)) {
                        issues.add(issue);
                    }
                }
            }
        }
        return issues;
    }

    // ---------- 各类检测器 ----------

    private List<GenQualityIssueVO> scanRegex(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        if (rule.pattern() == null) {
            return out;
        }
        Matcher m = rule.pattern().matcher(content);
        while (m.find() && out.size() < MAX_PER_RULE_FILE) {
            String line = lineText(content, m.start());
            out.add(issue(rule, path, lineOf(content, m.start()), line));
        }
        return out;
    }

    /**
     * 手写 SQL 缺少 deleted = 0：按分号切语句，排除 CREATE/INSERT/元数据查询，
     * 命中 SELECT/UPDATE/DELETE 且全句没有 deleted 即报。
     */
    private List<GenQualityIssueVO> scanSqlMissingDeleted(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        for (SqlStatement stmt : splitStatements(content)) {
            if (out.size() >= MAX_PER_RULE_FILE) {
                break;
            }
            String lower = stmt.text().toLowerCase();
            // 只检查真正被执行的 SQL；字符串常量拼接的“半句 SQL”留给后续语句判断，
            // 否则 base + " WHERE deleted = 0" 这种写法会被误报
            boolean executable = lower.contains("jdbctemplate") || lower.contains("queryforlist")
                    || lower.contains("queryforobject") || lower.contains("queryforall")
                    || lower.contains("@select") || lower.contains("@update") || lower.contains("@delete")
                    || lower.contains("preparestatement") || lower.contains("createnativequery")
                    || lower.contains("createsqlquery");
            if (!executable) {
                continue;
            }
            if (!(lower.contains("select ") || lower.contains("update ") || lower.contains("delete "))) {
                continue;
            }
            if (!lower.contains(" from ") && !lower.startsWith("update ") && !lower.contains("update ")) {
                continue;
            }
            if (lower.contains("deleted") || lower.contains("information_schema")
                    || lower.contains("create table") || lower.contains("insert into")) {
                continue;
            }
            // 语句可能从上一行的类声明/字符串拼接处开始，行号定位到真正出现 SQL 关键字的那一行
            String[] lines = stmt.text().split("\\n", -1);
            int offset = 0;
            while (offset < lines.length - 1) {
                String l = lines[offset].toLowerCase();
                if (l.contains("select ") || l.contains("update ") || l.contains("delete ")) {
                    break;
                }
                offset++;
            }
            out.add(issue(rule, path, stmt.line() + offset, lines[offset].trim()));
        }
        return out;
    }

    /**
     * CREATE TABLE 缺审计列：create_time / update_time / deleted 任一缺失即报。
     */
    private List<GenQualityIssueVO> scanCreateTableMissingAudit(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        for (SqlStatement stmt : splitStatements(content)) {
            if (out.size() >= MAX_PER_RULE_FILE) {
                break;
            }
            String lower = stmt.text().toLowerCase();
            if (!lower.contains("create table")) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String col : new String[]{"create_time", "update_time", "deleted"}) {
                if (!lower.contains(col)) {
                    missing.add(col);
                }
            }
            if (!missing.isEmpty()) {
                out.add(issue(rule, path, stmt.line(), firstLine(stmt.text())));
            }
        }
        return out;
    }

    /**
     * Map.of 最多 10 组键值。按括号配平截取参数，统计顶层逗号。
     */
    private List<GenQualityIssueVO> scanMapOfArity(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        String marker = "Map.of(";
        int from = 0;
        while (out.size() < MAX_PER_RULE_FILE) {
            int idx = content.indexOf(marker, from);
            if (idx < 0) {
                break;
            }
            int open = idx + marker.length() - 1;
            int close = matchingParen(content, open);
            if (close < 0) {
                break;
            }
            String args = content.substring(open + 1, close);
            if (countTopLevelCommas(args) > 10) {
                out.add(issue(rule, path, lineOf(content, idx), lineText(content, idx)));
            }
            from = close + 1;
        }
        return out;
    }

    /**
     * 雪花 Long ID 直接放进响应 Map：put("id", x.getId()) / Map.of("id", x.getId())。
     */
    private List<GenQualityIssueVO> scanLongIdJson(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        Matcher m = java.util.regex.Pattern.compile(
                "(?:\\.put|Map\\.of)\\s*\\(\\s*\"[^\"]*[iI]d\"\\s*,\\s*[^)]*\\.get[A-Za-z]*Id\\s*\\(\\s*\\)")
                .matcher(content);
        while (m.find() && out.size() < MAX_PER_RULE_FILE) {
            String line = lineText(content, m.start());
            if (line.contains("String.valueOf") || line.contains(".toString()")) {
                continue;
            }
            out.add(issue(rule, path, lineOf(content, m.start()), line));
        }
        return out;
    }

    /**
     * 硬编码口令/密钥：password/secret/apiKey 等键直接跟 6 位以上字面量。
     */
    private List<GenQualityIssueVO> scanSecret(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        Matcher m = java.util.regex.Pattern.compile(
                "(?i)(password|passwd|pwd|secret|api[_-]?key|access[_-]?key|token)\\s*[=:]\\s*[\"']?([^\\s\"',;]{6,})")
                .matcher(content);
        while (m.find() && out.size() < MAX_PER_RULE_FILE) {
            String value = m.group(2);
            String lower = value.toLowerCase();
            if (value.contains("${") || value.contains("*") || lower.contains("your")
                    || lower.contains("example") || lower.contains("placeholder")
                    || lower.startsWith("${")) {
                continue;
            }
            out.add(issue(rule, path, lineOf(content, m.start()), lineText(content, m.start())));
        }
        return out;
    }

    private List<GenQualityIssueVO> scanTodo(QualityRule rule, String path, String content) {
        List<GenQualityIssueVO> out = new ArrayList<>();
        Matcher m = java.util.regex.Pattern.compile("(?i)\\b(TODO|FIXME)\\b").matcher(content);
        while (m.find() && out.size() < MAX_PER_RULE_FILE) {
            out.add(issue(rule, path, lineOf(content, m.start()), lineText(content, m.start())));
        }
        return out;
    }

    // ---------- 工具 ----------

    private GenQualityIssueVO issue(QualityRule rule, String path, long line, String badSnippet) {
        GenQualityIssueVO vo = new GenQualityIssueVO();
        vo.setRuleId(rule.id());
        vo.setTitle(rule.title());
        vo.setCategory(rule.category().name());
        vo.setEngine(rule.engine().name());
        vo.setSeverity(rule.severity().name());
        vo.setFile(path);
        vo.setLineNo(line);
        vo.setMessage(rule.message());
        vo.setBadSnippet(abbreviate(stripBad(badSnippet, rule), SNIPPET_MAX));
        vo.setFixSnippet(abbreviate(rule.goodSnippet(), SNIPPET_MAX));
        vo.setSourceRef(rule.sourceRef());
        return vo;
    }

    private String stripBad(String badSnippet, QualityRule rule) {
        String s = badSnippet == null ? "" : badSnippet.trim();
        return s.isEmpty() ? rule.badSnippet() : s;
    }

    private long lineOf(String content, int index) {
        long line = 1;
        int end = Math.min(index, content.length());
        for (int i = 0; i < end; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String lineText(String content, int index) {
        int start = content.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int end = content.indexOf('\n', index);
        if (end < 0) {
            end = content.length();
        }
        return content.substring(start, end).trim();
    }

    private String firstLine(String text) {
        int i = text.indexOf('\n');
        return (i < 0 ? text : text.substring(0, i)).trim();
    }

    private int matchingParen(String content, int open) {
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int countTopLevelCommas(String args) {
        int depth = 0;
        int commas = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                commas++;
            }
        }
        return commas;
    }

    /**
     * 按分号切 SQL 语句，同时记录语句起始行。分号在字符串字面量内时跳过（尽力而为）。
     */
    private List<SqlStatement> splitStatements(String content) {
        List<SqlStatement> out = new ArrayList<>();
        int start = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
            } else if (c == ';') {
                String text = content.substring(start, i).trim();
                if (!text.isEmpty()) {
                    out.add(new SqlStatement((long) lineOf(content, start), text));
                }
                start = i + 1;
            }
        }
        String tail = content.substring(start).trim();
        if (!tail.isEmpty()) {
            out.add(new SqlStatement((long) lineOf(content, start), tail));
        }
        return out;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private record SqlStatement(Long line, String text) {
    }
}
