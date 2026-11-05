package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenPipelineProperties;
import com.codegen.service.AiModelService;
import com.codegen.vo.GenPipelineAiModulesVO;
import com.codegen.vo.GenPipelineAiVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 建模实现：OpenAI 兼容 /chat/completions，响应要求纯 JSON。
 *
 * <p>为什么不引 SDK：只发一个 chat 请求，JDK 自带 HttpClient 就够，
 * 少一个依赖就少一个升级坑。超时按配置（默认 600s，本地 CPU 推理一次 3-5 分钟），
 * 超时即失败可重试。
 *
 * <p>Prompt 硬约束：snake_case 命名、只给业务列（审计列服务端补）、
 * 输出纯 JSON——任何多余的话都会让解析挂掉。
 *
 * <p>qwen3 适配（2026-10-25，见坑库 N+6）：/no_think 放 user 消息尾部才生效
 * （放 system 无效）；思考 token 在纯 CPU 上会把请求拖到超时。
 */
@Slf4j
@Service
public class AiModelServiceImpl implements AiModelService {

    private static final String SYSTEM_PROMPT = """
            你是资深的领域建模专家。根据用户给出的需求，设计 MySQL 表结构。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"tables":[{"tableName":"xxx","tableComment":"中文注释","columns":[{"name":"xxx","type":"varchar(64)","comment":"中文注释","pk":false}]}]}
            3. 表名/列名用小写 snake_case；每张表恰好一个主键列（pk=true，类型 bigint）。
            4. 只给业务列。不要输出 id 之外的通用列（create_time/update_time/create_by/update_by/deleted 由系统自动补）。
            5. 类型只用：bigint、int、varchar(N)、decimal(M,N)、text、datetime、date、tinyint。
            6. 所有 comment 用中文。
            7. 直接给出结果，不要逐步推理。/no_think
            """;

    private static final String MODULES_SYSTEM_PROMPT = """
            你是资深的系统架构师。根据用户给出的系统名称和描述，把系统拆分为业务模块。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"modules":[{"name":"用户管理","desc":"账号、角色、权限与登录审计"}]}
            3. 拆分 8-12 个业务模块；name 用中文短词，desc 用一句话中文说明该模块管什么。
            4. 模块要覆盖完整业务闭环：核心业务域 + 支撑域（如消息通知、操作日志、统计报表、系统配置）。
            5. 直接给出结果，不要逐步推理。/no_think
            """;

    @Autowired
    private GenPipelineProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenPipelineAiVO model(String systemName, String systemDesc, String moduleName) {
        String userContent;
        if (StringUtils.hasText(moduleName)) {
            // 完整系统模式：只设计指定模块的表，单次输出预算可控（~1000 token），本地 CPU 3-4 分钟
            userContent = "系统名称：" + systemName
                    + "\n系统描述：" + (systemDesc == null ? systemName : systemDesc)
                    + "\n本次只设计「" + moduleName + "」这个模块的表：设计 6-8 张该模块的核心业务表，不要跨模块。"
                    + "\n/no_think";
        } else {
            userContent = "系统名称：" + systemName
                    + "\n一句话描述：" + (systemDesc == null ? systemName : systemDesc)
                    + "\n设计 3-5 张核心表。"
                    + "\n/no_think";
        }
        String json = chatJson(SYSTEM_PROMPT, userContent);
        return parseTables(json);
    }

    @Override
    public GenPipelineAiModulesVO modules(String systemName, String systemDesc) {
        String userContent = "系统名称：" + systemName
                + "\n系统描述：" + (systemDesc == null ? systemName : systemDesc)
                + "\n/no_think";
        String json = chatJson(MODULES_SYSTEM_PROMPT, userContent);
        return parseModules(json);
    }

    // ==================== V7.1 种子数据 AI 增强 ====================

    private static final String SEED_SYSTEM_PROMPT = """
            你是业务数据专家。根据系统名称、描述和表结构，为示例种子数据生成业务贴合的候选值。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"persons":["..."],"companies":["..."],"cities":["..."],"streets":["..."],"depts":["..."],"products":["..."],"titles":["..."],"remarks":["..."],"specs":["..."],"columns":{"表名.列名":["值",...]}}
            3. products 是关键：填该系统业务域内真实可信的商品/物料/业务对象名称（商城出数码家电、医院出药品耗材、餐饮出菜品），绝不能用通用的"不锈钢板材"之类。
            4. companies/depts/titles/remarks/specs 也按该系统的行业习惯拟。
            5. columns 只给核心业务列（品名/标题/名称类），键必须用给出的"表名.列名"原文，每列 8-16 个值。
            6. 每个列表 8-16 项，简短中文；不要输出编号/金额/时间/手机号——这些由系统按格式规则生成。
            7. 直接给出结果，不要逐步推理。/no_think
            """;

    /** 单次调用的表数上限：控制输出预算，本地小模型也不截断 */
    private static final int SEED_BATCH_TABLES = 15;
    /** 提示词里每表最多列出的列数（列太多会稀释重点） */
    private static final int SEED_COLUMNS_PER_TABLE = 12;

    @Override
    public com.codegen.support.SeedValuePools seedPools(String systemName, String systemDesc,
                                                        List<com.codegen.support.GenSeedGenerator.SeedTableMeta> tables) {
        if (tables == null || tables.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "无表元数据，跳过 AI 值池");
        }
        // 分批调用并合并：各类别拼接去重，列级覆盖直接合并
        List<String> persons = new ArrayList<>(), companies = new ArrayList<>(), cities = new ArrayList<>(),
                streets = new ArrayList<>(), depts = new ArrayList<>(), products = new ArrayList<>(),
                titles = new ArrayList<>(), remarks = new ArrayList<>(), specs = new ArrayList<>();
        Map<String, List<String>> columns = new LinkedHashMap<>();

        for (int from = 0; from < tables.size(); from += SEED_BATCH_TABLES) {
            List<com.codegen.support.GenSeedGenerator.SeedTableMeta> batch =
                    tables.subList(from, Math.min(from + SEED_BATCH_TABLES, tables.size()));
            StringBuilder user = new StringBuilder();
            user.append("系统名称：").append(systemName == null ? "" : systemName)
                    .append("\n系统描述：").append(systemDesc == null || systemDesc.isBlank() ? systemName : systemDesc)
                    .append("\n表结构（表名(注释): 列名(注释), ...）：\n");
            for (var t : batch) {
                user.append("- ").append(t.table()).append("(")
                        .append(abbrev(t.tableComment(), 40)).append("): ");
                StringBuilder cols = new StringBuilder();
                for (var c : t.columns()) {
                    if (cols.length() > 0) {
                        cols.append(", ");
                    }
                    cols.append(c.name()).append("(").append(abbrev(c.comment(), 20)).append(")");
                    if (cols.length() > 300) {
                        break; // 列过多的表只取前若干列，保输出预算
                    }
                }
                user.append(cols).append("\n");
            }
            user.append("/no_think");
            String json = chatJson(SEED_SYSTEM_PROMPT, user.toString());
            JsonNode root;
            try {
                root = objectMapper.readTree(json);
            } catch (Exception e) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "AI 值池 JSON 解析失败：" + e.getMessage());
            }
            mergeAll(root, persons, companies, cities, streets, depts, products, titles, remarks, specs);
            JsonNode colsNode = root.path("columns");
            if (colsNode.isObject()) {
                colsNode.fields().forEachRemaining(e -> {
                    List<String> vals = new ArrayList<>();
                    e.getValue().forEach(v -> vals.add(v.asText("")));
                    columns.merge(e.getKey(), vals, (a, b) -> {
                        a.addAll(b);
                        return a;
                    });
                });
            }
        }
        return com.codegen.support.SeedValuePools.of(persons, companies, cities, streets, depts,
                products, titles, remarks, specs, columns);
    }

    /** 合并一批 AI 输出的九个类别池（逐字段拼接，去重交给 SeedValuePools.clean） */
    private void mergeAll(JsonNode root, List<String>... lists) {
        String[] keys = {"persons", "companies", "cities", "streets", "depts",
                "products", "titles", "remarks", "specs"};
        for (int i = 0; i < keys.length && i < lists.length; i++) {
            JsonNode arr = root.path(keys[i]);
            if (arr.isArray()) {
                List<String> target = lists[i];
                arr.forEach(v -> target.add(v.asText("")));
            }
        }
    }

    private static String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 发一次 chat 请求并提取回复中的纯 JSON 串。
     */
    private String chatJson(String systemPrompt, String userContent) {
        GenPipelineProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || !StringUtils.hasText(ai.getBaseUrl()) || !StringUtils.hasText(ai.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建模未启用（gen.pipeline.ai.enabled=false 或未配置 key），请改用「手动粘贴 DDL」");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ai.getModel());
        // 0.2 会在 JSON 语法约束下陷入重复循环（Ollama 报 token repeat limit aborted），0.6 实测稳定
        body.put("temperature", 0.6);
        // OpenAI 兼容层的 JSON 模式：Ollama 走 llama.cpp 语法约束，从根上避免小模型输出非法 JSON
        // （qwen2.5:7b 实测不加时数组里会混裸字符，Jackson 解析必挂）；云端 OpenAI 也接受该字段
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
                if (detail.length() > 200) {
                    detail = detail.substring(0, 200);
                }
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "AI 请求失败：HTTP " + response.statusCode() + " " + detail);
            }
            resp = response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // ConnectException 等的 message 可能为 null，必须带上异常类名才有排查价值
            log.warn("AI 请求失败: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 请求失败（超时或网络异常），可重试或改用「手动粘贴 DDL」");
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
            log.warn("AI 回复提取失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 回复提取失败，可重试");
        }
    }

    private GenPipelineAiVO parseTables(String json) {
        try {
            JsonNode tables = objectMapper.readTree(json).path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new IllegalStateException("tables 为空");
            }
            GenPipelineAiVO vo = new GenPipelineAiVO();
            for (JsonNode t : tables) {
                GenPipelineAiVO.TableModel tm = new GenPipelineAiVO.TableModel();
                // 小模型不守 prompt 里的 camelCase 键名（qwen2.5:7b 实测输出 table_name），两种风格都收
                tm.setTableName(firstText(t, "tableName", "table_name"));
                tm.setTableComment(firstText(t, "tableComment", "table_comment", "comment"));
                for (JsonNode c : t.path("columns")) {
                    GenPipelineAiVO.Column col = new GenPipelineAiVO.Column();
                    col.setName(firstText(c, "name"));
                    col.setType(firstText(c, "type", "dataType"));
                    col.setComment(firstText(c, "comment", "columnComment"));
                    col.setPk(c.path("pk").asBoolean(false) || c.path("isPk").asBoolean(false));
                    tm.getColumns().add(col);
                }
                if (StringUtils.hasText(tm.getTableName()) && !tm.getColumns().isEmpty()) {
                    vo.getTables().add(tm);
                }
            }
            if (vo.getTables().isEmpty()) {
                throw new IllegalStateException("未解析出有效表");
            }
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 建模结果解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 建模结果解析失败，可重试或改用「手动粘贴 DDL」");
        }
    }

    private GenPipelineAiModulesVO parseModules(String json) {
        try {
            JsonNode modules = objectMapper.readTree(json).path("modules");
            if (!modules.isArray() || modules.isEmpty()) {
                throw new IllegalStateException("modules 为空");
            }
            GenPipelineAiModulesVO vo = new GenPipelineAiModulesVO();
            for (JsonNode m : modules) {
                String name = firstText(m, "name", "moduleName", "module_name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                GenPipelineAiModulesVO.Module mod = new GenPipelineAiModulesVO.Module();
                mod.setName(name);
                mod.setDesc(firstText(m, "desc", "description", "moduleDesc"));
                vo.getModules().add(mod);
            }
            if (vo.getModules().isEmpty()) {
                throw new IllegalStateException("未解析出有效模块");
            }
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 模块拆分解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(), "AI 模块拆分解析失败，可重试");
        }
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 解析模型回复：剥掉可能的 markdown 围栏，提取第一个 JSON 对象。
     */
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
}
