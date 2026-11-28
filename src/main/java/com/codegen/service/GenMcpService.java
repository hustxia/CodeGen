package com.codegen.service;

import com.codegen.vo.GenMcpToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * M1.3 MCP 工具目录与 tools/call 入口。
 */
public interface GenMcpService {

    List<GenMcpToolDefinition> tools();

    Object call(String toolName, Map<String, Object> arguments);
}
