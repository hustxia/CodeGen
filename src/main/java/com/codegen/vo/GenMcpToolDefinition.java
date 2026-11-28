package com.codegen.vo;

/**
 * MCP 工具目录项，inputSchema 为 JSON Schema 字符串，可直接交给 MCP Java SDK。
 */
public record GenMcpToolDefinition(
        String name,
        String description,
        String inputSchema,
        boolean mutating) {
}
