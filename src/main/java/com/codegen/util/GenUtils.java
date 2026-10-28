package com.codegen.util;

import com.codegen.dto.GenColumnDTO;
import com.codegen.dto.GenTableSaveDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码生成工具：DDL 解析、命名与类型推断
 */
public final class GenUtils {

    private GenUtils() {
    }

    /**
     * BaseEntity 已统一提供的列，解析时自动跳过（主键除外，用于识别）
     */
    private static final List<String> BASE_COLUMNS = List.of(
            "id", "deleted", "create_by", "create_time", "update_by", "update_time");

    /**
     * MySQL 类型 -> Java 类型
     */
    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            Map.entry("bigint", "Long"),
            Map.entry("int", "Integer"),
            Map.entry("integer", "Integer"),
            Map.entry("tinyint", "Integer"),
            Map.entry("smallint", "Integer"),
            Map.entry("mediumint", "Integer"),
            Map.entry("varchar", "String"),
            Map.entry("char", "String"),
            Map.entry("tinytext", "String"),
            Map.entry("text", "String"),
            Map.entry("mediumtext", "String"),
            Map.entry("longtext", "String"),
            Map.entry("json", "String"),
            Map.entry("enum", "String"),
            Map.entry("decimal", "BigDecimal"),
            Map.entry("numeric", "BigDecimal"),
            Map.entry("double", "Double"),
            Map.entry("float", "Double"),
            Map.entry("datetime", "LocalDateTime"),
            Map.entry("timestamp", "LocalDateTime"),
            Map.entry("date", "LocalDate"),
            Map.entry("time", "LocalTime"),
            Map.entry("year", "Integer"),
            Map.entry("bit", "Boolean"));

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?");
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "^`?(\\w+)`?\\s+([a-zA-Z]+)\\s*(\\([^)]*\\))?(.*)$", Pattern.DOTALL);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("COMMENT\\s+'([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIMARY_KEY_PATTERN = Pattern.compile(
            "(?i)^PRIMARY\\s+KEY\\s*\\(`?(\\w+)`?\\)");
    private static final Pattern TABLE_COMMENT_PATTERN = Pattern.compile(
            "(?i)COMMENT\\s*=?\\s*'([^']*)'");

    /**
     * 解析单条 CREATE TABLE 语句，推断表配置与字段配置（不落库）
     *
     * @param ddl 建表语句
     * @return 表配置（含字段推断结果）
     * @throws IllegalArgumentException 语句非法或无主键
     */
    public static GenTableSaveDTO parseDdl(String ddl) {
        if (ddl == null || ddl.isBlank()) {
            throw new IllegalArgumentException("DDL 不能为空");
        }
        // 去掉行注释，避免干扰解析
        String sql = ddl.replaceAll("(?m)^\\s*--.*$", "").trim();

        Matcher tableMatcher = TABLE_NAME_PATTERN.matcher(sql);
        if (!tableMatcher.find()) {
            throw new IllegalArgumentException("DDL 解析失败：无法识别 CREATE TABLE 语句");
        }
        String tableName = tableMatcher.group(2);

        int bodyStart = sql.indexOf('(');
        int bodyEnd = sql.lastIndexOf(')');
        if (bodyStart < 0 || bodyEnd <= bodyStart) {
            throw new IllegalArgumentException("DDL 解析失败：未找到表定义体");
        }
        String body = sql.substring(bodyStart + 1, bodyEnd);

        String tableComment = "";
        // 表注释只在表定义体之后的表选项里匹配，避免误取列注释
        String tail = sql.substring(bodyEnd + 1);
        Matcher tableCommentMatcher = TABLE_COMMENT_PATTERN.matcher(tail);
        if (tableCommentMatcher.find()) {
            tableComment = tableCommentMatcher.group(1);
        }

        String pkColumn = null;
        List<GenColumnDTO> columns = new ArrayList<>();
        int order = 0;
        for (String part : splitBody(body)) {
            String line = part.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher pkMatcher = PRIMARY_KEY_PATTERN.matcher(line);
            if (pkMatcher.find()) {
                pkColumn = pkMatcher.group(1);
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("KEY ") || upper.startsWith("UNIQUE ")
                    || upper.startsWith("INDEX ") || upper.startsWith("CONSTRAINT ")
                    || upper.startsWith("FULLTEXT ") || upper.startsWith("SPATIAL ")) {
                continue;
            }
            GenColumnDTO col = parseColumn(line);
            if (col == null) {
                continue;
            }
            if (isInlinePk(line)) {
                pkColumn = col.getColumnName();
            }
            if (BASE_COLUMNS.contains(col.getColumnName())) {
                continue;
            }
            col.setSort(++order);
            columns.add(col);
        }
        if (pkColumn == null) {
            throw new IllegalArgumentException("该表没有主键，无法导入");
        }

        GenTableSaveDTO dto = new GenTableSaveDTO();
        dto.setTableName(tableName);
        dto.setTableComment(tableComment.isBlank() ? tableName : tableComment);
        dto.setClassName(toPascal(tableName));
        dto.setModuleName(inferModuleName(tableName));
        dto.setPackageName("com.codegen");
        dto.setBackendPath("shop-service-admin/src/main/java/com/shop/admin");
        dto.setGatewayPrefix("admin");
        dto.setColumns(columns);
        return dto;
    }

    /**
     * 解析列定义行
     */
    private static GenColumnDTO parseColumn(String line) {
        Matcher matcher = COLUMN_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String columnName = matcher.group(1);
        String dbType = matcher.group(2).toLowerCase(Locale.ROOT);
        String rest = matcher.group(4) == null ? "" : matcher.group(4);

        GenColumnDTO col = new GenColumnDTO();
        col.setColumnName(columnName);
        col.setColumnType(matcher.group(2) + (matcher.group(3) == null ? "" : matcher.group(3)));
        String comment = "";
        Matcher commentMatcher = COMMENT_PATTERN.matcher(line);
        if (commentMatcher.find()) {
            comment = commentMatcher.group(1);
        }
        col.setColumnComment(comment.isBlank() ? columnName : comment);
        col.setJavaField(toCamel(columnName));
        col.setJavaType(TYPE_MAP.getOrDefault(dbType, "String"));
        col.setIsPk(0);
        col.setIsRequired(rest.toUpperCase(Locale.ROOT).contains("NOT NULL") ? 1 : 0);
        col.setIsQuery(0);
        col.setQueryType("EQ");
        col.setIsList(1);
        col.setIsForm(1);
        String htmlType = inferHtmlType(col.getColumnType(), col.getJavaType(), columnName);
        col.setHtmlType(htmlType);
        col.setDictType(inferDictType(htmlType, columnName));
        col.setValidateRule(inferValidateRule(col.getColumnType(), col.getJavaType(),
                col.getIsRequired() != null && col.getIsRequired() == 1));
        return col;
    }

    /**
     * 行内主键：AUTO_INCREMENT 或 PRIMARY KEY 内联
     */
    private static boolean isInlinePk(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("AUTO_INCREMENT") || upper.contains("PRIMARY KEY");
    }

    /**
     * 按括号深度 0 的逗号切分表定义体（兼容 decimal(10,2)、enum('a','b')）
     */
    private static List<String> splitBody(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inStr = false;
        for (char c : body.toCharArray()) {
            if (inStr) {
                cur.append(c);
                if (c == '\'') {
                    inStr = false;
                }
                continue;
            }
            if (c == '\'') {
                inStr = true;
                cur.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        return parts;
    }

    /**
     * 推断前端控件类型：类型优先，其次看列名语义（状态/类型/级别 → 下拉）
     */
    public static String inferHtmlType(String columnType, String javaType, String columnName) {
        String ct = columnType == null ? "" : columnType.toLowerCase(Locale.ROOT);
        String jt = javaType == null ? "" : javaType;
        if (ct.startsWith("enum") || ct.startsWith("set")) {
            return "select";
        }
        // V6.6 文件能力包：varchar 列名带图片/文件语义 → 上传控件
        // （仅 String 列参与；用户在平台显式改回其他控件时 htmlType 已落库，不再走到推断）
        if ("String".equals(jt) && looksLikeMultiImageColumn(columnName)) {
            return "multiImage";
        }
        if ("String".equals(jt) && looksLikeFileColumn(columnName)) {
            return looksLikeImageColumn(columnName) ? "image" : "file";
        }
        return switch (jt) {
            case "Boolean" -> "switch";
            case "LocalDate" -> "date";
            case "LocalDateTime", "LocalTime" -> "datetime";
            case "Integer", "Long", "BigDecimal", "Double" -> looksLikeEnumColumn(columnName) ? "select" : "number";
            case "String" -> ct.contains("text") ? "textarea" : "input";
            default -> "input";
        };
    }

    /**
     * V6.6 列名语义：image/img/pic/photo/logo/avatar/icon/cover 结尾或等于之 → 图片上传；
     * file/attachment/annex 结尾或等于之 → 文件上传。
     * 刻意不收裸 "url"（太泛，链接列不该变上传框）。
     */
    private static boolean looksLikeImageColumn(String columnName) {
        String n = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        return n.equals("image") || n.equals("img") || n.equals("pic") || n.equals("photo")
                || n.equals("logo") || n.equals("avatar") || n.equals("icon") || n.equals("cover")
                || n.endsWith("_image") || n.endsWith("_img") || n.endsWith("_pic")
                || n.endsWith("_photo") || n.endsWith("_logo") || n.endsWith("_avatar")
                || n.endsWith("_icon") || n.endsWith("_cover");
    }

    private static boolean looksLikeFileColumn(String columnName) {
        if (looksLikeImageColumn(columnName)) {
            return true;
        }
        String n = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        return n.endsWith("_file") || n.equals("file") || n.endsWith("_attachment")
                || n.equals("attachment") || n.endsWith("_annex") || n.equals("annex");
    }

    /**
     * V6.8 多图列语义：列名以 <b>复数</b> 图片语义结尾 → JSON 数组字符串（如 {@code slider_images}）。
     *
     * <p>必须与 {@link #looksLikeImageColumn} 区分开：单数结尾是单图 URL（varchar 256），
     * 复数结尾是 URL 数组的 JSON 串。判断顺序上 multiImage 先于单图，
     * 否则 {@code *_images} 会被 {@code *_image} 之外的单图逻辑漏掉、按普通文本框渲染。
     */
    public static boolean looksLikeMultiImageColumn(String columnName) {
        String n = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        return n.equals("images") || n.equals("pics") || n.equals("photos") || n.equals("gallery")
                || n.equals("slider_images") || n.endsWith("_images") || n.endsWith("_pics")
                || n.endsWith("_photos") || n.endsWith("_gallery");
    }

    /**
     * 列名语义：*_status / *_type / *_level / *_flag 这类离散值优先给下拉而非数字框
     */
    private static boolean looksLikeEnumColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        String n = columnName.toLowerCase(Locale.ROOT);
        return n.endsWith("_status") || n.endsWith("_type") || n.endsWith("_level")
                || n.endsWith("_flag") || n.endsWith("_state") || n.equals("status") || n.equals("type");
    }

    /**
     * 推断字典编码：状态类列默认挂 sys_status（sys_dict_data 已有 0=停用 / 1=启用）
     */
    public static String inferDictType(String htmlType, String columnName) {
        if (!"select".equals(htmlType) || columnName == null) {
            return null;
        }
        String n = columnName.toLowerCase(Locale.ROOT);
        if (n.equals("status") || n.endsWith("_status") || n.endsWith("_state")) {
            return "sys_status";
        }
        return null;
    }

    /**
     * 推断校验规则：非空 → required；varchar(n)/char(n) → maxlen=n
     */
    public static String inferValidateRule(String columnType, String javaType, boolean required) {
        List<String> rules = new ArrayList<>();
        if (required) {
            rules.add("required");
        }
        if ("String".equals(javaType) && columnType != null) {
            Matcher m = Pattern.compile("(?i)(?:var)?char\\s*\\((\\d+)\\)").matcher(columnType);
            if (m.find()) {
                int len = Integer.parseInt(m.group(1));
                if (len > 0 && len <= 2000) {
                    rules.add("maxlen=" + len);
                }
            }
        }
        return rules.isEmpty() ? null : String.join(",", rules);
    }

    /**
     * 按数据库列类型解析 Java 类型（忽略长度精度，如 varchar(50) -> String）
     */
    public static String resolveJavaType(String columnType) {
        if (columnType == null || columnType.isBlank()) {
            return "String";
        }
        String base = columnType.trim().toLowerCase(Locale.ROOT);
        int paren = base.indexOf('(');
        if (paren > 0) {
            base = base.substring(0, paren);
        }
        return TYPE_MAP.getOrDefault(base, "String");
    }

    /**
     * snake_case -> 小驼峰
     */
    public static String toCamel(String name) {
        String pascal = toPascal(name);
        if (pascal.isEmpty()) {
            return pascal;
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /**
     * snake_case -> 大驼峰
     */
    public static String toPascal(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : name.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /**
     * 推断前端模块名：去掉常见前缀（sys_/gen_），否则用全名
     */
    public static String inferModuleName(String tableName) {
        String lower = tableName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sys_")) {
            return lower.substring(4);
        }
        if (lower.startsWith("gen_")) {
            return lower.substring(4);
        }
        return lower;
    }
}
