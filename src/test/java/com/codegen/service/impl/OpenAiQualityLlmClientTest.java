package com.codegen.service.impl;

import com.codegen.service.QualityLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LLM 引擎输出解析：严格 JSON，允许代码围栏，坏 JSON 必须抛出以触发降级。
 */
class OpenAiQualityLlmClientTest {

    @Test
    void parsesPlainJsonIssues() throws Exception {
        String json = "{\"issues\":[{\"ruleId\":\"Q014\",\"file\":\"src/A.java\","
                + "\"line\":12,\"message\":\"事务自调用失效\","
                + "\"badSnippet\":\"this.b();\",\"fixSnippet\":\"aopProxy.b();\"}]}";
        List<QualityLlmClient.LlmIssue> issues = OpenAiQualityLlmClient.parse(json);
        assertEquals(1, issues.size());
        assertEquals("Q014", issues.get(0).ruleId());
        assertEquals(12L, issues.get(0).line());
    }

    @Test
    void parsesFencedJson() throws Exception {
        String content = "```json\n{\"issues\":[{\"ruleId\":\"Q022\",\"file\":\"a.vue\","
                + "\"message\":\"翻页丢选中\",\"badSnippet\":\"clear()\"}]}\n```";
        List<QualityLlmClient.LlmIssue> issues = OpenAiQualityLlmClient.parse(content);
        assertEquals(1, issues.size());
        assertEquals("Q022", issues.get(0).ruleId());
    }

    @Test
    void emptyIssuesIsValid() throws Exception {
        assertEquals(0, OpenAiQualityLlmClient.parse("{\"issues\":[]}").size());
    }

    @Test
    void malformedJsonThrowsForFallback() {
        assertThrows(Exception.class, () -> OpenAiQualityLlmClient.parse("this is not json"));
    }
}
