package com.codegen.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * V9.15 数据可视化图表推导器：列元数据 → 图清单。
 *
 * <p><b>纯函数、零领域词</b>：输入只有列的结构元数据（javaType/htmlType/注释推导的
 * hasEnum/hasDict/isDate/isDatetime/isNumber 等），输出只有通用图元（pie/line/bar/gauge
 * + count/sum/avg）。这里不出现任何业务系统名、表名或领域关键词——
 * 判断「这列出什么图」靠类型语义，不靠「这列是不是叫警情/订单」。
 * 这样任意行业模板克隆出的表，只要列类型正确就自动出图（通用性硬约束）。
 *
 * <p>反面案例：{@code GenMenuGroup.RULES} 把领域关键词硬编码在 Java 常量里，
 * 新增领域只能改代码重编译。图表推导刻意不走那条路。
 *
 * <p>回落语义：无匹配列时返回空清单（调用方只出 KPI COUNT + 空态文案），
 * 每表最多 {@link #MAX_CHARTS_PER_TABLE} 张图，防看板爆炸。
 */
public final class GenChartDeriver {

    private GenChartDeriver() {
    }

    /** 每表最多 4 张图（pie/line/bar/gauge 各一），超过按列顺序截断。 */
    public static final int MAX_CHARTS_PER_TABLE = 4;

    /**
     * 图规格：一个可生成的图卡。
     *
     * @param type      图型：pie / line / bar / gauge
     * @param title     默认标题（来自 commentLabel + 图型后缀，不含领域词）
     * @param field     Java 属性名（作为 /stats 的 field 参数，生成期白名单）
     * @param dbField   真实数据库列名（生成期固化进 SQL，防注入）
     * @param metric    聚合指标：count / sum / avg
     * @param range     时间范围（仅 line 有意义）：7d/30d/90d/365d/all；其它图为 null
     */
    public record ChartSpec(String type, String title, String field, String dbField,
                            String metric, String range) {
    }

    /**
     * 从列模型推导图清单。
     *
     * @param columns   列模型（GenTemplateModel.toModel 产物），元素为 Map，含
     *                  javaField/dbField/commentLabel/isNumber/isDate/isDatetime/hasEnum/hasDict 等键
     * @param chartOn   系统 chart_enabled 回落后的布尔值；false 直接返回空（零侵入）
     * @return 图清单，永不为 null；无匹配列时为空列表
     */
    public static List<ChartSpec> derive(List<Map<String, Object>> columns, boolean chartOn) {
        if (!chartOn || columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<ChartSpec> charts = new ArrayList<>();
        Set<String> usedTypes = new LinkedHashSet<>();
        for (Map<String, Object> c : columns) {
            if (charts.size() >= MAX_CHARTS_PER_TABLE) {
                break;
            }
            String field = str(c.get("javaField"));
            String dbField = str(c.get("dbField"));
            String label = str(c.get("commentLabel"));
            if (field.isEmpty() || dbField.isEmpty()) {
                continue;
            }
            boolean isId = isIdField(field, dbField);
            boolean hasEnum = bool(c.get("hasEnum")) || bool(c.get("hasDict"));
            boolean isDate = bool(c.get("isDate")) || bool(c.get("isDatetime"));
            boolean isNumber = bool(c.get("isNumber"));

            // status 枚举列 → gauge（优先于通用 pie，状态完成度更直观）
            if (hasEnum && isStatusField(field, dbField) && !usedTypes.contains("gauge")) {
                charts.add(new ChartSpec("gauge", label + "分布", field, dbField, "count", null));
                usedTypes.add("gauge");
                continue;
            }
            // 枚举/字典列 → pie（分类分布）
            if (hasEnum && !usedTypes.contains("pie")) {
                charts.add(new ChartSpec("pie", label + "分布", field, dbField, "count", null));
                usedTypes.add("pie");
                continue;
            }
            // 日期列 → line（趋势，按日/月分组）
            if (isDate && !usedTypes.contains("line")) {
                charts.add(new ChartSpec("line", label + "趋势", field, dbField, "count", "30d"));
                usedTypes.add("line");
                continue;
            }
            // 数值列（非主键）→ bar（排名，SUM）
            if (isNumber && !isId && !usedTypes.contains("bar")) {
                charts.add(new ChartSpec("bar", label + "排行", field, dbField, "sum", null));
                usedTypes.add("bar");
            }
        }
        return List.copyOf(charts);
    }

    /** 该表是否至少有一张图（调用方决定是否生成 stats 接口）。 */
    public static boolean hasCharts(List<Map<String, Object>> columns, boolean chartOn) {
        return !derive(columns, chartOn).isEmpty();
    }

    /**
     * 状态列判断：Java 属性名或数据库列名恰好是 status/state（或下划线/驼峰变体）。
     * 用「整词等于」而非子串包含——避免把 {@code task_status_detail} 这类普通明细列误判为状态列。
     */
    private static boolean isStatusField(String field, String dbField) {
        return matchesWord(field, "status", "state") || matchesWord(dbField, "status", "state");
    }

    /** 主键判断：属性名/db 名为 id，或标准外键形态（驼峰 *Id / 下划线 *_id）。避免拿自增 id 做排名柱图。 */
    private static boolean isIdField(String field, String dbField) {
        return "id".equals(lower(field)) || "id".equals(lower(dbField))
                || field.endsWith("Id") || field.endsWith("ID")
                || lower(dbField).endsWith("_id");
    }

    /** 整词匹配：把驼峰/下划线/连字符都当分隔符，任一分段等于候选词即为 true。 */
    private static boolean matchesWord(String value, String... words) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String[] parts = lower(value).split("[^a-z0-9]+");
        for (String part : parts) {
            for (String w : words) {
                if (w.equals(part)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static boolean bool(Object o) {
        return Boolean.TRUE.equals(o);
    }
}
