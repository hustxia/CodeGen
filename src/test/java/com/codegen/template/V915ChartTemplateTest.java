package com.codegen.template;

import com.codegen.support.GenDashboardWriter;
import com.codegen.util.GenTemplateEngine;
import com.codegen.util.GenTemplateModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V9.15 图表能力包模板渲染契约：不连库、不起 Spring，直接渲染 .ftl 验产物内容。
 */
@DisplayName("V9.15 图表模板渲染")
class V915ChartTemplateTest {

    private static Path tplDir;

    @BeforeAll
    static void setUp() {
        tplDir = Path.of("src/main/resources/gen-templates");
        GenTemplateModel.setRootPkg("com.codegen");
    }

    private static String render(String tpl, Map<String, Object> model) {
        try {
            return GenTemplateEngine.renderString(Files.readString(tplDir.resolve(tpl), StandardCharsets.UTF_8), model);
        } catch (Exception e) {
            fail("渲染失败：" + tpl + "：" + e.getMessage());
            return "";
        }
    }

    private static Map<String, Object> statsModel() {
        return Map.of(
                "pkg", "com.demo.controller", "rootPkg", "com.codegen",
                "moduleName", "alarm", "className", "Alarm", "comment", "警情",
                "tableName", "v94_alarm_main",
                "chartFieldWhitelist", List.of("status", "createTime", "amount"),
                "chartFieldTypes", Map.of("status", "gauge", "createTime", "line", "amount", "bar"),
                "charts", List.of(
                        Map.of("field", "status", "dbField", "status", "type", "gauge", "metric", "count"),
                        Map.of("field", "createTime", "dbField", "create_time", "type", "line", "metric", "count"),
                        Map.of("field", "amount", "dbField", "amount", "type", "bar", "metric", "sum")));
    }

    @Test
    @DisplayName("chartStats.ftl 渲染出白名单/列名映射/日期分支，且无未解析标记")
    void chartStatsRenders() {
        String out = render("chartStats.ftl", statsModel());
        assertFalse(out.contains("<#"), "存在未解析的 FreeMarker 标记");
        assertTrue(out.contains("ALLOWED_FIELDS = Set.of(\"status\", \"createTime\", \"amount\")"), "白名单未内联");
        assertTrue(out.contains("FIELD_TO_COLUMN = Map.of("), "列名映射缺失");
        assertTrue(out.contains("\"createTime\", \"create_time\""), "createTime→create_time 映射缺失");
        assertTrue(out.contains("DATE_FORMAT(") && out.contains("INTERVAL \" + days + \" DAY"), "日期趋势分支未渲染");
        assertTrue(out.contains("String agg = \"sum\".equals(metric) ? \"SUM\" : \"AVG\""), "数值 SUM/AVG 分支未渲染");
        assertTrue(out.contains("Result.fail(400"), "非法参数 400 缺失");
        assertTrue(out.contains("String.valueOf(label)"), "Long→字符串下发缺失");
    }

    @Test
    @DisplayName("看板页含图卡配置与取数，空清单显示空态")
    void dashboardRenders() {
        List<GenDashboardWriter.Card> cards = List.of(
                new GenDashboardWriter.Card("v94_alarm_main", "alarm", "status", "gauge", "状态分布", "count", "30d", "/api/gw/alarm"),
                new GenDashboardWriter.Card("v94_alarm_main", "alarm", "createTime", "line", "创建时间趋势", "count", "30d", "/api/gw/alarm"));
        String out = GenDashboardWriter.dashboardVue("智慧警务", cards, "");
        assertTrue(out.contains("field: 'status'"), "图卡 field 缺失");
        assertTrue(out.contains("type: 'line'"), "line 图卡缺失");
        assertTrue(out.contains("/stats"), "取数路径缺失");
        assertTrue(out.contains("apiBase: '/api/gw/alarm'"), "apiBase 未固化进图卡（多系统/网关前缀会 404）");
        assertTrue(out.contains("ElMessage.error"), "错误提示缺失（禁止空 catch）");
        assertTrue(out.contains("ChartCard"), "图卡组件缺失");

        String empty = GenDashboardWriter.dashboardVue("空系统", List.of(), "");
        assertTrue(empty.contains("暂无可图表化"), "空态文案缺失");
    }

    @Test
    @DisplayName("chart-off：resolveChart 默认关，推导器零图")
    void chartOffZeroCharts() {
        assertFalse(GenTemplateModel.resolveChart(null));
        assertFalse(GenTemplateModel.resolveChart(0));
        assertTrue(GenTemplateModel.resolveChart(1));
    }
}
