package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP tools/call 审计日志；initialize 与 tools/list 不记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_mcp_call_log")
public class GenMcpCallLog extends BaseEntity {

    private String toolName;

    private String argumentsJson;

    /** SUCCESS / FAILED */
    private String status;

    private String resultSummary;

    private String errorMessage;

    private Long durationMs;
}
