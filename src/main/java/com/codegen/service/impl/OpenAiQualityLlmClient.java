package com.codegen.service.impl;

import com.codegen.config.GenPipelineProperties;
import com.codegen.quality.QualityRule;
import com.codegen.service.QualityLlmClient;
import com.codegen.vo.GenFileVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容的 LLM 质量审查实现（M2.2）。
 *
 * <p>与 AI 建模共用配置，但不引 SDK：一次 /chat/completions 请求即可。
 * 输入按文件大小截断，避免本地小模型上下文被打爆；输出严格 JSON，
 * 解析失败直接抛出，由 {@code QualityGateServiceImpl} 回落确定性引擎。
 */
@Slf4j
@Service
public class OpenAiQualityLlmClient implements QualityLlmClient {

    private static final int MAX_FILES = 16;
    private static final int MAX_CHARS_PER_FILE = 5000;
    private static final int MAX_TOTAL_CHARS = 60000;

    /** 门禁在写盘路径上，LLM 最多等 180s；超时即回落确定性引擎，避免卡住生成 */
    private static final int MAX_QUALITY_TIMEOUT_SECONDS = 180;

    private static final String SYSTEM_PROMPT = """
            你是资深代码质量审查器。根据给定的规则清单审查生成代码，只输出纯 JSON，不要 markdown 代码块或解释。
            输出格式：{"issues":[{"ruleId":"Q014","file":"相对路径","line":12,"message":"具体问题","badSnippet":"命中原文一行","fixSnippet":"修正后的一行示例"}]}
            要求：
            1. ruleId 必须来自规则清单，只能使用 engine=LLM 的规则；
            2. file 必须是输入中出现的文件路径；
            3. line 为大致行号，badSnippet 必须取自文件原文（便于服务端二次定位）；
            4. 没有问题时返回 {"issues":[]}；
            5. 不要重复报告同一文件同一行同一规则。
            """;

    @Autowired
    private GenPipelineProperties props;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean available() {
        GenPipelineProperties.Ai ai = props.getAi();
        return ai.isEnabled() && StringUtils.hasText(ai.getBaseUrl())
                && StringUtils.hasText(ai.getApiKey()) && StringUtils.hasText(ai.getModel());
    }

    @Override
    public List<LlmIssue> analyze(List<GenFileVO> files, List<QualityRule> rules) throws Exception {
        if (!available() || files == null || files.isEmpty() || rules == null || rules.isEmpty()) {
            return List.of();
        }
        String body = buildRequest(files, rules);
        GenPipelineProperties.Ai ai = props.getAi();
        int timeoutSeconds = Math.max(30, Math.min(MAX_QUALITY_TIMEOUT_SECONDS, ai.getTimeoutSeconds()));
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Math.min(10, timeoutSeconds)))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(ai.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ai.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM 质量审查 HTTP " + response.statusCode()
                    + "：" + abbreviate(response.body(), 200));
        }
        String content = objectMapper.readTree(response.body())
                .path("choices").path(0).path("message").path("content").asText("");
        return parse(content);
    }

    private String buildRequest(List<GenFileVO> files, List<QualityRule> rules) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", props.getAi().getModel());
        root.put("temperature", 0);
        root.put("stream", false);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);

        StringBuilder user = new StringBuilder("规则清单：\n");
        for (QualityRule rule : rules) {
            user.append(rule.id()).append('|').append(rule.title())
                    .append('|').append(rule.severity())
                    .append('|').append(rule.message())
                    .append("|❌").append(rule.badSnippet())
                    .append("|✅").append(rule.goodSnippet())
                    .append('\n');
        }
        user.append("\n待审查文件（按扩展名筛选，超长截断）：\n");
        int total = 0;
        int count = 0;
        for (GenFileVO file : files) {
            if (count >= MAX_FILES || total >= MAX_TOTAL_CHARS) {
                break;
            }
            String content = file.getContent();
            if (content == null || content.isEmpty() || !isReviewable(file.getPath())) {
                continue;
            }
            String clipped = content.length() <= MAX_CHARS_PER_FILE
                    ? content : content.substring(0, MAX_CHARS_PER_FILE) + "\n/*...截断...*/";
            user.append("\n===== FILE: ").append(file.getPath()).append(" =====\n").append(clipped).append('\n');
            total += clipped.length();
            count++;
        }
        messages.addObject().put("role", "user").put("content", user.toString());
        return objectMapper.writeValueAsString(root);
    }

    private boolean isReviewable(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".vue") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".sql")
                || lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    /**
     * 严格解析 LLM 输出；允许模型误加 ```json 围栏。
     */
    static List<LlmIssue> parse(String content) throws Exception {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String json = stripFences(content.trim());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode issues = root.path("issues");
        List<LlmIssue> out = new ArrayList<>();
        if (!issues.isArray()) {
            return out;
        }
        for (JsonNode node : issues) {
            String ruleId = node.path("ruleId").asText("").trim();
            String file = node.path("file").asText("").trim();
            if (ruleId.isEmpty() || file.isEmpty()) {
                continue;
            }
            out.add(new LlmIssue(ruleId, file,
                    node.path("line").asLong(0L),
                    node.path("severity").asText("").trim(),
                    node.path("message").asText("").trim(),
                    node.path("badSnippet").asText("").trim(),
                    node.path("fixSnippet").asText("").trim()));
        }
        return out;
    }

    private static String stripFences(String text) {
        String s = text;
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            s = firstNewline >= 0 ? s.substring(firstNewline + 1) : s;
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
