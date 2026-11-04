package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenPipelineProperties;
import com.codegen.entity.GenTable;
import com.codegen.entity.GenTableColumn;
import com.codegen.mapper.GenTableColumnMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.service.AiRecommendService;
import com.codegen.service.DictService;
import com.codegen.support.AiRecommendSanitizer;
import com.codegen.vo.DictTypeVO;
import com.codegen.vo.GenAiRecommendVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * V7.0 AI 页面与菜单推荐实现：按模块分批调 AI → 白名单清洗 → 合并产物。
 *
 * <p>分批沿用 model/modules 两段式思想：列级/表级按模块分批（单批输出预算可控，
 * 40 张表也不会截断），菜单组织是跨表归纳，用一次只带表名/注释的轻量调用收尾。
 *
 * <p>HTTP 调用骨架与 {@link AiModelServiceImpl} 同款（OpenAI 兼容 /chat/completions，
 * response_format=json_object）。两处 prompt/解析完全不同，共享的只是 HTTP 骨架，
 * 为不动已验证的建模链路，这里各持一份；第三次出现时再抽公共 client。
 */
@Slf4j
@Service
public class AiRecommendServiceImpl implements AiRecommendService {

    /** 单批最多多少张表：列级建议是输出大户，10 张表 ≈ 100 列，本地小模型也能完整吐完 */
    static final int MAX_TABLES_PER_BATCH = 10;

    private static final String TABLE_SYSTEM_PROMPT = """
            你是资深的后台管理系统页面配置专家。根据表结构元数据，为每张表推荐页面配置。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"tables":[{"tableName":"原样照抄","moduleName":"模块key","treeParentColumn":"","treeLabelColumn":"","masterTableName":"","relationType":"","columns":[{"columnName":"原样照抄","isQuery":0,"queryType":"EQ","isList":1,"isForm":1,"htmlType":"input","dictType":"","sort":1}]}]}
            3. tableName / columnName 必须原样照抄输入，不允许发明新表、新列。
            4. htmlType 只能用：input,textarea,number,select,radio,switch,date,datetime,image,file,multiImage。queryType 只能用：EQ,LIKE,GE,LE。
            5. isQuery/isList/isForm 只能是 0 或 1。dictType 只能用我给你的字典编码，不确定就留空字符串。
            6. moduleName 用英文小写模块 key（如 stock、order、system），按业务相近程度归纳。
            7. treeParentColumn/treeLabelColumn 只对自关联树形表（如部门、分类）给值，其他留空字符串。
            8. masterTableName 只对明细/行项目类子表给值（值为主表的 tableName），relationType 只对多对多中间表给 middle，其他留空字符串。
            9. 主键列不参与查询/表单：isQuery=0、isForm=0、isList=0。
            10. 拿不准的字段不要输出该字段（只输出有把握的建议）。
            11. 直接给出结果，不要逐步推理。/no_think
            """;

    private static final String MENU_SYSTEM_PROMPT = """
            你是资深的后台管理系统信息架构师。根据系统的表清单，组织菜单目录。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"menus":[{"moduleName":"模块key","dirLabel":"中文目录名","icon":"el-icon-xxx","sort":1,"tables":["表名"]}]}
            3. 按业务相近程度把表组织成若干目录，每张表恰好属于一个目录。
            4. moduleName 用英文小写模块 key；dirLabel 用 2-6 个汉字；icon 用 Element UI 图标类名（如 el-icon-box、el-icon-tickets、el-icon-setting）。
            5. sort 从 1 开始按业务重要性递增；tables 里的表名必须来自输入清单。
            6. 直接给出结果，不要逐步推理。/no_think
            """;

    @Autowired
    private GenPipelineProperties properties;

    @Autowired
    private GenTableMapper tableMapper;

    @Autowired
    private GenTableColumnMapper columnMapper;

    @Autowired
    private DictService dictService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenAiRecommendVO recommend(Long projectId) {
        List<GenTable> tables = tableMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenTable>()
                        .eq(GenTable::getProjectId, projectId)
                        .orderByAsc(GenTable::getId));
        if (tables.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "系统下没有表配置，无法推荐");
        }
        List<Long> tableIds = tables.stream().map(GenTable::getId).toList();
        List<GenTableColumn> columns = columnMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenTableColumn>()
                        .in(GenTableColumn::getTableId, tableIds)
                        .orderByAsc(GenTableColumn::getSort));
        Map<Long, List<GenTableColumn>> columnsOf = columns.stream()
                .collect(Collectors.groupingBy(GenTableColumn::getTableId, LinkedHashMap::new, Collectors.toList()));

        // 白名单校验基准：表 → 列名集合、平台已知字典编码
        Map<String, Set<String>> tableColumns = new LinkedHashMap<>();
        for (GenTable t : tables) {
            tableColumns.put(t.getTableName(), columnsOf.getOrDefault(t.getId(), List.of())
                    .stream().map(GenTableColumn::getColumnName).collect(Collectors.toSet()));
        }
        Set<String> knownDictCodes = new HashSet<>();
        for (DictTypeVO t : dictService.listTypes(null)) {
            if (StringUtils.hasText(t.getDictCode())) {
                knownDictCodes.add(t.getDictCode());
            }
        }
        String dictHint = knownDictCodes.isEmpty()
                ? "（平台暂无字典，所有 dictType 留空字符串）"
                : knownDictCodes.stream().sorted().collect(Collectors.joining(","));

        GenAiRecommendVO merged = new GenAiRecommendVO();
        List<List<GenTable>> batches = batches(tables, MAX_TABLES_PER_BATCH);
        log.info("AI 推荐：{} 张表分 {} 批", tables.size(), batches.size());
        for (List<GenTable> batch : batches) {
            String userContent = buildTableUserContent(batch, columnsOf, dictHint);
            String json = chatJson(TABLE_SYSTEM_PROMPT, userContent);
            GenAiRecommendVO part = AiRecommendSanitizer.sanitize(
                    parseJson(json), tableColumns, knownDictCodes);
            merged.getTables().addAll(part.getTables());
            merged.getDrops().addAll(part.getDrops());
        }
        // 菜单组织：跨表归纳，一次轻量调用（只带表名/注释，输出预算小）
        try {
            String menuJson = chatJson(MENU_SYSTEM_PROMPT, buildMenuUserContent(tables));
            GenAiRecommendVO menuPart = AiRecommendSanitizer.sanitize(
                    parseJson(menuJson), tableColumns, knownDictCodes);
            merged.getMenus().addAll(menuPart.getMenus());
            merged.getDrops().addAll(menuPart.getDrops());
        } catch (BusinessException e) {
            // 菜单层失败不株连列/表建议：记一条丢弃原因，产物主体仍可用
            log.warn("AI 菜单组织建议失败（列/表建议保留）: {}", e.getMessage());
            merged.getDrops().add("菜单组织建议生成失败：" + e.getMessage());
        }
        return merged;
    }

    /**
     * 分批：按现有 moduleName 分组（未分组的归一桶），组内再按 maxPerBatch 切。
     * 静态纯函数，AC-6（40 表分批不截断）的可测核心。
     */
    static List<List<GenTable>> batches(List<GenTable> tables, int maxPerBatch) {
        Map<String, List<GenTable>> byModule = new LinkedHashMap<>();
        for (GenTable t : tables) {
            String key = StringUtils.hasText(t.getModuleName()) ? t.getModuleName() : "";
            byModule.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<List<GenTable>> result = new ArrayList<>();
        for (List<GenTable> group : byModule.values()) {
            for (int i = 0; i < group.size(); i += maxPerBatch) {
                result.add(group.subList(i, Math.min(i + maxPerBatch, group.size())));
            }
        }
        return result;
    }

    // ==================== prompt 组装 ====================

    private String buildTableUserContent(List<GenTable> batch,
                                         Map<Long, List<GenTableColumn>> columnsOf,
                                         String dictHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("平台字典编码：").append(dictHint).append('\n');
        sb.append("表结构：\n[");
        for (int i = 0; i < batch.size(); i++) {
            GenTable t = batch.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"tableName\":\"").append(t.getTableName()).append('"');
            sb.append(",\"comment\":\"").append(escape(t.getTableComment())).append('"');
            sb.append(",\"columns\":[");
            List<GenTableColumn> cols = columnsOf.getOrDefault(t.getId(), List.of());
            for (int j = 0; j < cols.size(); j++) {
                GenTableColumn c = cols.get(j);
                if (j > 0) {
                    sb.append(',');
                }
                sb.append("{\"name\":\"").append(c.getColumnName()).append('"');
                sb.append(",\"comment\":\"").append(escape(c.getColumnComment())).append('"');
                sb.append(",\"type\":\"").append(c.getColumnType()).append('"');
                sb.append(",\"pk\":").append(c.getIsPk() != null && c.getIsPk() == 1);
                sb.append('}');
            }
            sb.append("]}");
        }
        sb.append("]\n为这些表推荐页面配置。/no_think");
        return sb.toString();
    }

    private String buildMenuUserContent(List<GenTable> tables) {
        StringBuilder sb = new StringBuilder("系统表清单：\n");
        for (GenTable t : tables) {
            sb.append("- ").append(t.getTableName());
            if (StringUtils.hasText(t.getTableComment())) {
                sb.append("（").append(t.getTableComment()).append(')');
            }
            sb.append('\n');
        }
        sb.append("组织菜单目录。/no_think");
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    // ==================== AI 调用（与 AiModelServiceImpl 同款骨架） ====================

    private String chatJson(String systemPrompt, String userContent) {
        GenPipelineProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || !StringUtils.hasText(ai.getBaseUrl()) || !StringUtils.hasText(ai.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 推荐未启用（gen.pipeline.ai.enabled=false 或未配置 key）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ai.getModel());
        body.put("temperature", 0.6);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        String resp;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(ai.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(5, ai.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String detail = response.body() == null ? "" : response.body();
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "AI 请求失败：HTTP " + response.statusCode() + " " + abbreviate(detail, 200));
            }
            resp = response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 推荐请求失败: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 请求失败（超时或网络异常）");
        }
        try {
            JsonNode root = objectMapper.readTree(resp);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("回复为空");
            }
            return extractJson(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 推荐回复提取失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 回复提取失败，可重试");
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("AI 建议 JSON 解析失败（可能截断）: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 建议解析失败（输出截断或非法 JSON）");
        }
    }

    private String extractJson(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("回复中找不到 JSON");
        }
        return s.substring(start, end + 1);
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
