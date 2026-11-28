package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenMcpCallLog;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.mapper.GenMcpCallLogMapper;
import com.codegen.service.GenConvergeService;
import com.codegen.service.GenMcpService;
import com.codegen.service.GenPipelineService;
import com.codegen.service.GenSpecService;
import com.codegen.vo.GenMcpToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP 工具调用服务。
 */
@Service
public class GenMcpServiceImpl implements GenMcpService {

    private static final List<GenMcpToolDefinition> TOOLS = List.of(
            new GenMcpToolDefinition("codegen_spec_save", "保存 SDD 规格工件新版本", """
                    {
                      "type":"object",
                      "properties":{
                        "projectId":{"type":"integer","description":"项目ID"},
                        "artifactType":{"type":"string","enum":["CONSTITUTION","SPEC","PLAN","TABLES","TASKS"]},
                        "title":{"type":"string"},
                        "yamlMeta":{"type":"string","description":"结构化元数据JSON字符串"},
                        "contentMd":{"type":"string","description":"Markdown正文"},
                        "structuredJson":{"type":"string","description":"期望清单JSON字符串"},
                        "confirmed":{"type":"boolean","const":true,"description":"必须显式确认为true"}
                      },
                      "required":["projectId","artifactType","title","confirmed"],
                      "additionalProperties":false
                    }
                    """, true),
            new GenMcpToolDefinition("codegen_spec_confirm", "确认 SDD 规格工件", """
                    {
                      "type":"object",
                      "properties":{
                        "artifactId":{"type":"integer","description":"工件ID"},
                        "confirmed":{"type":"boolean","const":true,"description":"必须显式确认为true"}
                      },
                      "required":["artifactId","confirmed"],
                      "additionalProperties":false
                    }
                    """, true),
            new GenMcpToolDefinition("codegen_converge_run", "执行 Converge 规格对账", """
                    {
                      "type":"object",
                      "properties":{
                        "projectId":{"type":"integer","description":"项目ID"},
                        "confirmed":{"type":"boolean","const":true,"description":"必须显式确认为true"}
                      },
                      "required":["projectId","confirmed"],
                      "additionalProperties":false
                    }
                    """, true),
            new GenMcpToolDefinition("codegen_pipeline_run", "启动已确认的生成流水线", """
                    {
                      "type":"object",
                      "properties":{
                        "pipelineId":{"type":"integer","description":"流水线任务ID"},
                        "confirmed":{"type":"boolean","const":true,"description":"必须显式确认为true"}
                      },
                      "required":["pipelineId","confirmed"],
                      "additionalProperties":false
                    }
                    """, true),
            new GenMcpToolDefinition("codegen_pipeline_status", "查询生成流水线任务状态与步骤", """
                    {
                      "type":"object",
                      "properties":{
                        "pipelineId":{"type":"integer","description":"流水线任务ID"}
                      },
                      "required":["pipelineId"],
                      "additionalProperties":false
                    }
                    """, false));

    private final GenSpecService specService;
    private final GenConvergeService convergeService;
    private final GenPipelineService pipelineService;
    private final GenMcpCallLogMapper logMapper;
    private final ObjectMapper objectMapper;

    public GenMcpServiceImpl(GenSpecService specService, GenConvergeService convergeService,
                             GenPipelineService pipelineService, GenMcpCallLogMapper logMapper,
                             ObjectMapper objectMapper) {
        this.specService = specService;
        this.convergeService = convergeService;
        this.pipelineService = pipelineService;
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GenMcpToolDefinition> tools() {
        return TOOLS;
    }

    @Override
    public Object call(String toolName, Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            Object result = route(toolName, arguments);
            audit(toolName, arguments, "SUCCESS", truncate(toJson(result), 2000), null, started);
            return result;
        } catch (RuntimeException e) {
            audit(toolName, arguments, "FAILED", null, truncate(e.getMessage(), 2000), started);
            throw e;
        }
    }

    private Object route(String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "codegen_spec_save" -> saveSpec(arguments);
            case "codegen_spec_confirm" -> {
                requireConfirmation(arguments);
                yield specService.confirm(longArg(arguments, "artifactId"));
            }
            case "codegen_converge_run" -> {
                requireConfirmation(arguments);
                yield convergeService.run(longArg(arguments, "projectId"));
            }
            case "codegen_pipeline_run" -> {
                requireConfirmation(arguments);
                yield pipelineService.run(longArg(arguments, "pipelineId"));
            }
            case "codegen_pipeline_status" -> pipelineService.detail(longArg(arguments, "pipelineId"));
            default -> throw new UnsupportedOperationException("MCP 工具尚未实现: " + toolName);
        };
    }

    private GenSpecArtifact saveSpec(Map<String, Object> arguments) {
        requireConfirmation(arguments);
        GenSpecArtifact artifact = new GenSpecArtifact();
        artifact.setProjectId(longArg(arguments, "projectId"));
        artifact.setArtifactType((String) arguments.get("artifactType"));
        artifact.setTitle((String) arguments.get("title"));
        artifact.setYamlMeta((String) arguments.get("yamlMeta"));
        artifact.setContentMd((String) arguments.get("contentMd"));
        artifact.setStructuredJson((String) arguments.get("structuredJson"));
        return specService.save(artifact);
    }

    private void audit(String toolName, Map<String, Object> arguments, String status,
                       String resultSummary, String errorMessage, long started) {
        GenMcpCallLog log = new GenMcpCallLog();
        log.setToolName(toolName);
        log.setArgumentsJson(toJson(arguments));
        log.setStatus(status);
        log.setResultSummary(resultSummary);
        log.setErrorMessage(errorMessage);
        log.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        logMapper.insert(log);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void requireConfirmation(Map<String, Object> arguments) {
        if (!Boolean.TRUE.equals(arguments.get("confirmed"))) {
            throw new BusinessException(400, "写操作必须显式传入 confirmed=true");
        }
    }

    private Long longArg(Map<String, Object> arguments, String name) {
        return ((Number) arguments.get(name)).longValue();
    }
}
