package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.config.GenPipelineProperties;
import com.codegen.service.AiModelService;
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
 * 少一个依赖就少一个升级坑。超时按配置（默认 30s），超时即失败可重试。
 *
 * <p>Prompt 三条硬约束：snake_case 命名、只给业务列（审计列服务端补）、
 * 输出纯 JSON——任何多余的话都会让解析挂掉。
 */
@Slf4j
@Service
public class AiModelServiceImpl implements AiModelService {

    private static final String SYSTEM_PROMPT = """
            你是资深的领域建模专家。根据用户给出的系统名称和一句话描述，设计 MySQL 表结构。
            硬性要求：
            1. 输出纯 JSON，不要 markdown 代码块，不要任何解释文字。
            2. JSON 格式：{"tables":[{"tableName":"xxx","tableComment":"中文注释","columns":[{"name":"xxx","type":"varchar(64)","comment":"中文注释","pk":false}]}]}
            3. 至少 3 张表；表名/列名用小写 snake_case；每张表恰好一个主键列（pk=true，类型 bigint）。
            4. 只给业务列。不要输出 id 之外的通用列（create_time/update_time/create_by/update_by/deleted 由系统自动补）。
            5. 类型只用：bigint、int、varchar(N)、decimal(M,N)、text、datetime、date、tinyint。
            6. 所有 comment 用中文。
            """;

    @Autowired
    private GenPipelineProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenPipelineAiVO model(String systemName, String systemDesc) {
        GenPipelineProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || !StringUtils.hasText(ai.getBaseUrl()) || !StringUtils.hasText(ai.getApiKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建模未启用（gen.pipeline.ai.enabled=false 或未配置 key），请改用「手动粘贴 DDL」");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ai.getModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content",
                        "系统名称：" + systemName + "\n一句话描述：" + (systemDesc == null ? systemName : systemDesc))
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
                throw new BusinessException(ResultCode.FAIL.getCode(),
                        "AI 建模失败：HTTP " + response.statusCode() + "，可重试或改用「手动粘贴 DDL」");
            }
            resp = response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 建模请求失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.FAIL.getCode(),
                    "AI 建模请求失败（超时或网络异常），可重试或改用「手动粘贴 DDL」");
        }

        return parse(resp);
    }

    /**
     * 解析模型回复：剥掉可能的 markdown 围栏，提取第一个 JSON 对象。
     */
    private GenPipelineAiVO parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("回复为空");
            }
            String json = extractJson(content);
            JsonNode tables = objectMapper.readTree(json).path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new IllegalStateException("tables 为空");
            }
            GenPipelineAiVO vo = new GenPipelineAiVO();
            for (JsonNode t : tables) {
                GenPipelineAiVO.TableModel tm = new GenPipelineAiVO.TableModel();
                tm.setTableName(t.path("tableName").asText("").trim());
                tm.setTableComment(t.path("tableComment").asText("").trim());
                for (JsonNode c : t.path("columns")) {
                    GenPipelineAiVO.Column col = new GenPipelineAiVO.Column();
                    col.setName(c.path("name").asText("").trim());
                    col.setType(c.path("type").asText("varchar(255)").trim());
                    col.setComment(c.path("comment").asText("").trim());
                    col.setPk(c.path("pk").asBoolean(false));
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
