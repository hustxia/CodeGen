package com.codegen.support;

import com.codegen.support.GenChartDeriver.ChartSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V9.15 图表推导器契约：按列元数据推导通用图元，且零领域词（跨系统通用硬约束）。
 */
@DisplayName("GenChartDeriver 图表推导")
class GenChartDeriverTest {

    private static Map<String, Object> col(String javaField, String dbField, String label,
                                           boolean isEnum, boolean isDate, boolean isNumber) {
        Map<String, Object> c = new HashMap<>();
        c.put("javaField", javaField);
        c.put("dbField", dbField);
        c.put("commentLabel", label);
        c.put("hasEnum", isEnum);
        c.put("hasDict", false);
        c.put("isDate", isDate);
        c.put("isDatetime", false);
        c.put("isNumber", isNumber);
        return c;
    }

    @Test
    @DisplayName("chartOn=false → 空清单（零侵入，存量产物零 diff）")
    void chartOffReturnsEmpty() {
        List<Map<String, Object>> cols = List.of(
                col("status", "status", "状态", true, false, false),
                col("createTime", "create_time", "创建时间", false, true, false));
        assertTrue(GenChartDeriver.derive(cols, false).isEmpty());
        assertFalse(GenChartDeriver.hasCharts(cols, false));
    }

    @Test
    @DisplayName("枚举→pie 日期→line 数值→bar status枚举→gauge")
    void deriveAllTypes() {
        List<Map<String, Object>> cols = List.of(
                col("id", "id", "主键", false, false, true),
                col("status", "status", "状态", true, false, false),
                col("type", "type", "类型", true, false, false),
                col("createTime", "create_time", "创建时间", false, true, false),
                col("amount", "amount", "金额", false, false, true));
        List<ChartSpec> charts = GenChartDeriver.derive(cols, true);
        assertEquals(4, charts.size(), "每表最多 4 张图，id 不参与 ranking");
        Set<String> types = charts.stream().map(ChartSpec::type).collect(Collectors.toSet());
        assertEquals(Set.of("gauge", "pie", "line", "bar"), types);
        ChartSpec gauge = charts.stream().filter(c -> "gauge".equals(c.type())).findFirst().orElseThrow();
        assertEquals("status", gauge.field());
        assertEquals("count", gauge.metric());
        ChartSpec bar = charts.stream().filter(c -> "bar".equals(c.type())).findFirst().orElseThrow();
        assertEquals("amount", bar.field());
        assertEquals("sum", bar.metric());
        ChartSpec line = charts.stream().filter(c -> "line".equals(c.type())).findFirst().orElseThrow();
        assertEquals("30d", line.range());
    }

    @Test
    @DisplayName("status 优先 gauge 且同图型只出一张；超过 4 种类型按列截断")
    void gaugePriorityAndDedup() {
        List<Map<String, Object>> enumCols = List.of(
                col("status", "status", "状态", true, false, false),
                col("auditState", "audit_state", "审核状态", true, false, false),
                col("type", "type", "类型", true, false, false),
                col("category", "category", "分类", true, false, false),
                col("level", "level", "级别", true, false, false));
        List<ChartSpec> charts = GenChartDeriver.derive(enumCols, true);
        long gauges = charts.stream().filter(c -> "gauge".equals(c.type())).count();
        assertEquals(1, gauges, "多个 status/state 列只出一张 gauge");
        assertEquals(2, charts.size(), "pie/gauge 各只出一张，其余同图型列跳过（每表每图型最多一张）");

        // 4 种图型各一张 → 恰好 4 张上限；第 5 个同型列被截断
        List<Map<String, Object>> mixed = List.of(
                col("status", "status", "状态", true, false, false),
                col("createTime", "create_time", "创建时间", false, true, false),
                col("amount", "amount", "金额", false, false, true),
                col("type", "type", "类型", true, false, false),
                col("score", "score", "评分", false, false, true));
        assertEquals(GenChartDeriver.MAX_CHARTS_PER_TABLE,
                GenChartDeriver.derive(mixed, true).size(), "四种图型齐全，正好到上限");
    }

    @Test
    @DisplayName("无图表化列 → 空清单（调用方只出 KPI + 空态）")
    void noChartableColumns() {
        List<Map<String, Object>> cols = List.of(
                col("name", "name", "名称", false, false, false),
                col("remark", "remark", "备注", false, false, false));
        assertTrue(GenChartDeriver.derive(cols, true).isEmpty());
    }

    @Test
    @DisplayName("主键/外键不参与 ranking（避免拿 id 做柱图）")
    void idsExcludedFromBar() {
        List<Map<String, Object>> cols = List.of(
                col("id", "id", "主键", false, false, true),
                col("roleId", "role_id", "角色外键", false, false, true),
                col("paid", "paid", "已付金额", false, false, true));
        List<ChartSpec> charts = GenChartDeriver.derive(cols, true);
        assertEquals(1, charts.size());
        assertEquals("bar", charts.get(0).type());
        assertEquals("paid", charts.get(0).field(), "paid 不是外键形态，应作为数值列出柱图");
    }

    @Test
    @DisplayName("通用性契约：推导器源码零领域词（警/村/仓储/police/village/warehouse）")
    void noDomainWordsInSource() throws Exception {
        Path src = Path.of("src/main/java/com/codegen/support/GenChartDeriver.java");
        String code = Files.readString(src, StandardCharsets.UTF_8);
        // 注释里为说明「反面案例」提到了 GenMenuGroup/领域词举例，这里只断言「可执行代码」不含——
        // 去掉注释行后再匹配。领域词出现在注释是允许的（说明为何规避），出现在逻辑里才是耦合。
        String executable = java.util.Arrays.stream(code.split("\\R"))
                .filter(line -> !line.trim().startsWith("*") && !line.trim().startsWith("//") && !line.trim().startsWith("/*"))
                .collect(Collectors.joining("\n"));
        String lower = executable.toLowerCase();
        for (String word : List.of("警", "村", "仓储", "订单", "police", "village", "warehouse")) {
            assertFalse(lower.contains(word), "推导器可执行代码不该出现领域词: " + word);
        }
    }
}
