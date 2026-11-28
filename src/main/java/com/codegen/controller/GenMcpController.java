package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.result.Result;
import com.codegen.dto.GenMcpLogPageDTO;
import com.codegen.entity.GenMcpCallLog;
import com.codegen.mapper.GenMcpCallLogMapper;
import com.codegen.service.GenMcpService;
import com.codegen.vo.GenMcpToolDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 管理接口；协议端点 /mcp 由 Streamable HTTP transport 单独注册。
 */
@Tag(name = "MCP Server", description = "M1.3 连接配置、工具目录与调用审计")
@RestController
@RequestMapping("/gen/mcp")
public class GenMcpController {

    private final GenMcpService mcpService;
    private final GenMcpCallLogMapper logMapper;

    public GenMcpController(GenMcpService mcpService, GenMcpCallLogMapper logMapper) {
        this.mcpService = mcpService;
        this.logMapper = logMapper;
    }

    @Operation(summary = "MCP 连接配置")
    @GetMapping("/config")
    public Result<Map<String, String>> config() {
        return Result.ok(Map.of(
                "serverName", "codegen-server",
                "serverVersion", "M1.3",
                "endpoint", "/mcp",
                "protocol", "Streamable HTTP",
                "authentication", "Authorization: Bearer <token>"));
    }

    @Operation(summary = "MCP 工具目录")
    @GetMapping("/tools")
    public Result<List<GenMcpToolDefinition>> tools() {
        return Result.ok(mcpService.tools());
    }

    @Operation(summary = "MCP 工具调用审计分页")
    @PostMapping("/logs")
    public Result<Page<GenMcpCallLog>> logs(@RequestBody GenMcpLogPageDTO dto) {
        LambdaQueryWrapper<GenMcpCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(dto.getToolName()), GenMcpCallLog::getToolName, dto.getToolName());
        wrapper.eq(StringUtils.hasText(dto.getStatus()), GenMcpCallLog::getStatus, dto.getStatus());
        wrapper.orderByDesc(GenMcpCallLog::getId);
        return Result.ok(logMapper.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper));
    }
}
