package ${pkg}.controller;

<#-- V9.15 数据可视化聚合接口：每表一个 /${moduleName}/stats，列名与指标生成期固化，运行时只接收白名单枚举。 -->
import ${rootPkg}.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ${comment} 数据可视化聚合接口（V9.15 自动生成）。
 *
 * <p>安全约定：SQL 中的表名/列名全部在生成期固化，运行时 field/metric/range
 * 只与白名单枚举比较，绝不拼接用户输入（防注入）。非法参数返回 code=400。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "${comment}统计", description = "数据看板聚合接口")
@RestController
@RequestMapping("/${moduleName}")
public class ${className}StatsController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 生成期白名单：合法 field 参数（Java 属性名）。 */
    private static final Set<String> ALLOWED_FIELDS = Set.of(<#list chartFieldWhitelist as f>"${f}"<#sep>, </#list>);
    /** 生成期固化：field → 真实数据库列名。 */
    private static final Map<String, String> FIELD_TO_COLUMN = Map.of(<#list charts as c>"${c.field}", "${c.dbField}"<#sep>, </#list>);
    /** 生成期固化：field → 默认图型（count/sum 语义）。 */
    private static final Map<String, String> FIELD_TO_METRIC = Map.of(<#list charts as c>"${c.field}", "${c.metric}"<#sep>, </#list>);
    /** 生成期固化：field → 图型（line=按日期分组趋势，其余按列分组）。 */
    private static final Map<String, String> FIELD_TO_TYPE = Map.of(<#list chartFieldTypes?keys as k>"${k}", "${chartFieldTypes[k]}"<#sep>, </#list>);

    private static final Set<String> ALLOWED_METRICS = Set.of("count", "sum", "avg");
    private static final Set<String> ALLOWED_RANGES = Set.of("7d", "30d", "90d", "365d", "all");

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(defaultValue = "count") String metric,
            @RequestParam(required = false) String field,
            @RequestParam(defaultValue = "30d") String range) {
        String resolvedField = (field == null || field.isBlank()) ? ALLOWED_FIELDS.iterator().next() : field;
        if (!ALLOWED_FIELDS.contains(resolvedField)) {
            return Result.fail(400, "非法 field 参数");
        }
        if (!ALLOWED_METRICS.contains(metric)) {
            return Result.fail(400, "非法 metric 参数");
        }
        if (!ALLOWED_RANGES.contains(range)) {
            return Result.fail(400, "非法 range 参数");
        }
        String column = FIELD_TO_COLUMN.get(resolvedField);
        String chartType = FIELD_TO_TYPE.get(resolvedField);
        String sql;
        List<Object> args = new ArrayList<>();
        if ("line".equals(chartType)) {
            <#-- 时间趋势：range 只接受白名单枚举，天数常量生成期算好，无注入风险 -->
            int days = "all".equals(range) ? -1
                    : "7d".equals(range) ? 7 : "30d".equals(range) ? 30
                    : "90d".equals(range) ? 90 : 365;
            String timeWhere = days > 0
                    ? " AND " + column + " >= DATE_SUB(NOW(), INTERVAL " + days + " DAY)"
                    : "";
            sql = "SELECT DATE_FORMAT(" + column + ", '%Y-%m-%d') AS label, COUNT(*) AS cnt"
                    + " FROM ${tableName} WHERE deleted = 0" + timeWhere
                    + " GROUP BY DATE_FORMAT(" + column + ", '%Y-%m-%d')"
                    + " ORDER BY label ASC LIMIT 366";
        } else if ("sum".equals(metric) || "avg".equals(metric)) {
            String agg = "sum".equals(metric) ? "SUM" : "AVG";
            sql = "SELECT " + column + " AS label, " + agg + "(" + column + ") AS val FROM ${tableName} WHERE deleted = 0 GROUP BY " + column + " ORDER BY val DESC LIMIT 50";
        } else {
            <#-- 分类分布：按列分组计数。列名是生成期常量，无注入风险。 -->
            sql = "SELECT " + column + " AS label, COUNT(*) AS cnt FROM ${tableName} WHERE deleted = 0 GROUP BY " + column + " ORDER BY cnt DESC LIMIT 50";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            Object label = row.get("label");
            labels.add(label == null ? "未分类" : String.valueOf(label));
            Object val = row.containsKey("cnt") ? row.get("cnt") : row.get("val");
            // Long/BigDecimal 一律转字符串下发（V6.4 教训：Map 里的 Long 不受 POJO 序列化配置覆盖）
            String v = val == null ? "0" : String.valueOf(val);
            values.add(v);
            try { total += Long.parseLong(v); } catch (NumberFormatException ignored) { }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("values", values);
        data.put("total", String.valueOf(total));
        return Result.ok(data);
    }
}
