package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenTable;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.DictDetectService;
import com.codegen.util.GenUtils;
import com.codegen.vo.DictDetectItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字典自动识别实现。
 *
 * <p>注释解析刻意写成「宁可漏判，不可错判」：注释里出现冒号/括号+枚举符号才认，
 * 认出来之后还要过一道选项数量与长度校验，避免把一句普通说明（如「备注（选填）」）
 * 误当成字典写进 sys_dict_data。
 */
@Service
public class DictDetectServiceImpl implements DictDetectService {

    private static final Logger log = LoggerFactory.getLogger(DictDetectServiceImpl.class);

    /** 和 GenUtils 的 HTML 控件推断保持一致：这类控件才需要字典 */
    private static final Set<String> DICT_HTML_TYPES = Set.of("select", "radio", "checkbox");

    /** 选项数量上下限：1 个选项没意义，超过 30 大概率是误判 */
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 30;

    /** 字典编码：MySQL 标识符风格 */
    private static final Pattern CODE_OK = Pattern.compile("[A-Za-z0-9_]{1,64}");

    /** enum('a','b') —— 双引号也兼容，值里允许转义 '' */
    private static final Pattern ENUM_TYPE = Pattern.compile(
            "^enum\\s*\\((.*)\\)$", Pattern.CASE_INSENSITIVE);

    /** 单个 enum 值：'xxx' */
    private static final Pattern ENUM_VALUE = Pattern.compile("'((?:[^']|'')*)'");

    /** CREATE TABLE [IF NOT EXISTS] `tbl` */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_$.]+)`?",
            Pattern.CASE_INSENSITIVE);

    /**
     * 注释里的选项段：取第一个冒号或全/半角括号之前当名称，之后当选项列表。
     * 举个能匹配的例子：{@code 订单状态:待付款,已付款,已取消}
     */
    private static final Pattern COMMENT_NAME_SPLIT = Pattern.compile("^([^:：（(]+)[:：（(](.+?)[)）]?$");

    /** 选项分隔符：逗号/顿号/斜杠/竖线/分号 */
    private static final String OPTION_SEP_REGEX = "[,，、/|;；]";

    /** 标签与值之间的连接符，如 男=1 / 男:1 / 男(1) */
    private static final Pattern LABEL_VALUE = Pattern.compile(
            "^(.+?)\\s*[=:：]\\s*(.+)$");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenTableMapper genTableMapper;

    // ------------------------------------------------------------------ 识别

    @Override
    public List<DictDetectItemVO> detect(Long tableId) {
        GenTable table = genTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "表配置不存在");
        }
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT column_name, column_type, IFNULL(column_comment,'') AS column_comment, "
                        + "IFNULL(html_type,'') AS html_type, IFNULL(dict_type,'') AS dict_type "
                        + "FROM gen_table_column WHERE table_id = ? AND deleted = 0 ORDER BY sort, id",
                tableId);

        List<DictDetectItemVO> result = new ArrayList<>();
        for (Map<String, Object> row : cols) {
            String columnName = str(row.get("column_name"));
            String columnType = str(row.get("column_type"));
            String comment = str(row.get("column_comment"));
            String htmlType = str(row.get("html_type"));
            String dictType = str(row.get("dict_type"));

            DictDetectItemVO item = detectColumn(table.getTableName(), columnName, columnType,
                    comment, htmlType, dictType);
            if (item != null) {
                result.add(item);
            }
        }
        fillExisting(result, tableId);
        return result;
    }

    @Override
    public List<DictDetectItemVO> detectFromDdl(String tableName, String ddl) {
        if (!StringUtils.hasText(ddl)) {
            return List.of();
        }
        String table = StringUtils.hasText(tableName) ? tableName.trim() : extractTableName(ddl);
        if (!StringUtils.hasText(table)) {
            table = "tmp_table";
        }
        List<DictDetectItemVO> result = new ArrayList<>();
        // 复用 GenUtils 的 DDL 解析，拿到与导入一致的列模型（已含 javaType/htmlType 推断）
        try {
            com.codegen.dto.GenTableSaveDTO parsed = GenUtils.parseDdl(ddl);
            if (parsed == null || parsed.getColumns() == null) {
                return result;
            }
            for (com.codegen.dto.GenColumnDTO c : parsed.getColumns()) {
                DictDetectItemVO item = detectColumn(table, c.getColumnName(), c.getColumnType(),
                        c.getColumnComment(), c.getHtmlType(), c.getDictType());
                if (item != null) {
                    result.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("DDL 枚举识别失败，已跳过：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 单列识别。返回 null 表示这一列不需要字典。
     */
    private DictDetectItemVO detectColumn(String tableName, String columnName, String columnType,
                                          String comment, String htmlType, String dictType) {
        if (!StringUtils.hasText(columnName)) {
            return null;
        }
        String lowerName = columnName.toLowerCase(Locale.ROOT);

        // 显式配了 dict_type 的列，说明开发者已经指定了字典，不要再自动改它
        if (StringUtils.hasText(dictType)) {
            return null;
        }

        List<DictDetectItemVO.Option> options = null;
        String source = null;

        // 1) enum 类型优先
        List<String> enumValues = parseEnumValues(columnType);
        if (enumValues != null) {
            options = optionsFromEnum(enumValues, comment);
            source = "enum";
        }

        // 2) enum 没识别出来，再试注释；只有下拉/单选控件才看注释
        if (options == null && DICT_HTML_TYPES.contains(htmlType == null ? "" : htmlType.toLowerCase(Locale.ROOT))) {
            options = optionsFromComment(comment);
            if (options != null) {
                source = "comment";
            }
        }

        if (options == null || options.size() < MIN_OPTIONS) {
            return null;
        }

        DictDetectItemVO vo = new DictDetectItemVO();
        vo.setDictCode(buildDictCode(tableName, columnName));
        vo.setDictName(buildDictName(comment, columnName));
        vo.setSource(source);
        vo.setTableName(tableName);
        vo.setColumnName(columnName);
        vo.setColumnComment(comment);
        vo.setOptions(options);
        vo.setCurrentDictType("");
        // 编码非法（表名/列名带奇怪字符）时直接不返回，避免后续写入失败
        return CODE_OK.matcher(vo.getDictCode()).matches() ? vo : null;
    }

    /**
     * 解析 enum('1','2') → [1, 2]；不是 enum 返回 null
     */
    private List<String> parseEnumValues(String columnType) {
        if (!StringUtils.hasText(columnType)) {
            return null;
        }
        Matcher m = ENUM_TYPE.matcher(columnType.trim());
        if (!m.matches()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        Matcher vm = ENUM_VALUE.matcher(m.group(1));
        while (vm.find()) {
            values.add(vm.group(1).replace("''", "'").trim());
        }
        return values.isEmpty() ? null : values;
    }

    /**
     * enum 值转选项。
     *
     * <p>标签取法：如果注释里也写了 {@code 男=1} 这种映射，优先用注释里的中文标签，
     * 否则退回用值本身（如 status enum('待付款','已付款') 直接当标签）。
     */
    private List<DictDetectItemVO.Option> optionsFromEnum(List<String> values, String comment) {
        Map<String, String> labelByValue = new LinkedHashMap<>();
        List<DictDetectItemVO.Option> fromComment = optionsFromComment(comment);
        if (fromComment != null) {
            for (DictDetectItemVO.Option o : fromComment) {
                labelByValue.put(o.getValue(), o.getLabel());
            }
        }
        List<DictDetectItemVO.Option> list = new ArrayList<>();
        int sort = 0;
        for (String v : values) {
            String label = labelByValue.getOrDefault(v, v);
            list.add(new DictDetectItemVO.Option(label, v, ++sort));
        }
        return list;
    }

    /**
     * 从注释里抽选项。抽不出返回 null。
     *
     * <p>支持三种写法：
     * <pre>
     *   订单状态:待付款,已付款,已取消
     *   订单状态（待付款、已付款、已取消）   ← 这种没有值，用标签同时当值
     *   性别:男=1,女=2
     * </pre>
     */
    private List<DictDetectItemVO.Option> optionsFromComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String text = comment.trim();
        Matcher m = COMMENT_NAME_SPLIT.matcher(text);
        String body;
        if (m.matches()) {
            body = m.group(2);
        } else {
            // 没有名称分隔符，整段当选项列表（如「待付款,已付款」）
            body = text;
        }
        if (!StringUtils.hasText(body)) {
            return null;
        }
        String[] parts = body.split(OPTION_SEP_REGEX);
        List<DictDetectItemVO.Option> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int sort = 0;
        for (String raw : parts) {
            String part = raw == null ? "" : raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            String label = part;
            String value = part;
            Matcher lv = LABEL_VALUE.matcher(part);
            if (lv.matches()) {
                label = lv.group(1).trim();
                value = lv.group(2).trim();
            }
            if (label.isEmpty() || value.isEmpty()) {
                continue;
            }
            // 单项过长基本是说明文字而非枚举
            if (label.length() > 32 || value.length() > 64) {
                return null;
            }
            if (!seen.add(value)) {
                continue;
            }
            list.add(new DictDetectItemVO.Option(label, value, ++sort));
            if (list.size() > MAX_OPTIONS) {
                return null;
            }
        }
        return list.size() >= MIN_OPTIONS ? list : null;
    }

    /**
     * 字典编码：{@code 表名_列名}，去掉常见的表前缀噪音后仍然保持可读。
     *
     * <p>例：{@code v2_order.status} → {@code v2_order_status}
     */
    private String buildDictCode(String tableName, String columnName) {
        String t = tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
        String c = columnName.trim().toLowerCase(Locale.ROOT);
        String code = t + "_" + c;
        return code.replaceAll("[^a-z0-9_]", "_");
    }

    /**
     * 字典名称：优先用注释里冒号前的主标题，否则用「表注释-列名」不好拿，就退化成列注释
     */
    private String buildDictName(String comment, String columnName) {
        if (StringUtils.hasText(comment)) {
            String text = comment.trim();
            Matcher m = COMMENT_NAME_SPLIT.matcher(text);
            if (m.matches() && StringUtils.hasText(m.group(1))) {
                String name = m.group(1).trim();
                // 名称里不应带引号，去掉以免前端显示怪异
                name = name.replace("\"", "").replace("'", "");
                if (!name.isEmpty() && name.length() <= 64) {
                    return name;
                }
            }
            if (text.length() <= 64) {
                return text;
            }
        }
        return columnName;
    }

    /**
     * 补「库里是否已存在」「目标列当前 dict_type」等状态位
     */
    private void fillExisting(List<DictDetectItemVO> items, Long tableId) {
        if (items.isEmpty()) {
            return;
        }
        for (DictDetectItemVO item : items) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT dict_label, dict_value, sort_no FROM sys_dict_data "
                            + "WHERE dict_code = ? ORDER BY sort_no, id", item.getDictCode());
            item.setExists(!jdbcTemplate.queryForList(
                    "SELECT 1 FROM sys_dict_type WHERE dict_code = ? AND deleted = 0",
                    Integer.class, item.getDictCode()).isEmpty());
            List<DictDetectItemVO.Option> existing = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                existing.add(new DictDetectItemVO.Option(
                        str(row.get("dict_label")), str(row.get("dict_value")),
                        row.get("sort_no") == null ? 0 : ((Number) row.get("sort_no")).intValue()));
            }
            item.setExistingOptions(existing);

            List<String> current = jdbcTemplate.queryForList(
                    "SELECT IFNULL(dict_type,'') FROM gen_table_column "
                            + "WHERE table_id = ? AND column_name = ? AND deleted = 0",
                    String.class, tableId, item.getColumnName());
            String cur = current.isEmpty() ? "" : current.get(0);
            item.setCurrentDictType(cur);
            item.setBound(item.getDictCode().equalsIgnoreCase(cur));
        }
    }

    // ------------------------------------------------------------------ 落库

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int apply(Long tableId, List<String> dictCodes) {
        GenTable table = genTableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException(404, "表配置不存在");
        }
        List<DictDetectItemVO> all = detect(tableId);
        if (all.isEmpty()) {
            return 0;
        }
        // 再识别一次拿全量，然后按入参过滤
        Set<String> wanted = new LinkedHashSet<>();
        if (dictCodes != null) {
            for (String c : dictCodes) {
                if (StringUtils.hasText(c)) {
                    wanted.add(c.trim());
                }
            }
        }
        int applied = 0;
        for (DictDetectItemVO item : all) {
            if (!wanted.isEmpty() && !wanted.contains(item.getDictCode())) {
                continue;
            }
            upsertDictType(item);
            for (DictDetectItemVO.Option opt : item.getOptions()) {
                // 按 value 判重：同一个字典里值唯一，标签允许不同（业务重命名过）
                List<Long> hit = jdbcTemplate.queryForList(
                        "SELECT id FROM sys_dict_data WHERE dict_code = ? AND dict_value = ?",
                        Long.class, item.getDictCode(), opt.getValue());
                if (hit.isEmpty()) {
                    jdbcTemplate.update(
                            "INSERT INTO sys_dict_data(dict_code, dict_label, dict_value, sort_no, status) "
                                    + "VALUES(?, ?, ?, ?, 1)",
                            item.getDictCode(), opt.getLabel(), opt.getValue(), opt.getSortNo());
                } else {
                    // 已存在：只补 sort_no，不动 label —— 用户可能已经手工改过文案
                    jdbcTemplate.update("UPDATE sys_dict_data SET sort_no = ? WHERE id = ?",
                            opt.getSortNo(), hit.get(0));
                }
            }
            // 回填目标列，让生成页直接用上这个字典
            jdbcTemplate.update("UPDATE gen_table_column SET dict_type = ? "
                            + "WHERE table_id = ? AND column_name = ? AND deleted = 0",
                    item.getDictCode(), tableId, item.getColumnName());
            applied++;
        }
        return applied;
    }

    /**
     * 建/更新字典类型。已逻辑删除的同名编码会复活，避免唯一键冲突。
     */
    private void upsertDictType(DictDetectItemVO item) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_dict_type WHERE dict_code = ?", Long.class, item.getDictCode());
        if (ids.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO sys_dict_type(dict_code, dict_name, status, deleted) VALUES(?, ?, 1, 0)",
                    item.getDictCode(), item.getDictName());
            return;
        }
        jdbcTemplate.update("UPDATE sys_dict_type SET dict_name = ?, deleted = 0 WHERE id = ?",
                item.getDictName(), ids.get(0));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * 从 CREATE TABLE 语句里取表名，取不到返回空串
     */
    private static String extractTableName(String ddl) {
        if (!StringUtils.hasText(ddl)) {
            return "";
        }
        Matcher m = CREATE_TABLE.matcher(ddl);
        return m.find() ? m.group(1).replace("`", "").trim() : "";
    }
}
