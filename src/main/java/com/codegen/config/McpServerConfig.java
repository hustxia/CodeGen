package com.codegen.config;

import com.codegen.service.GenMcpService;
import com.codegen.vo.GenMcpToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * M1.3 MCP Streamable HTTP 协议适配。业务与审计统一走 GenMcpService。
 */
@Configuration
public class McpServerConfig {

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransport(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transport, "/mcp");
        registration.setName("mcp");
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport,
                                   McpJsonMapper jsonMapper,
                                   GenMcpService mcpService,
                                   ObjectMapper objectMapper) {
        List<SyncToolSpecification> tools = mcpService.tools().stream()
                .map(definition -> toolSpecification(definition, jsonMapper, mcpService, objectMapper))
                .toList();
        return McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .serverInfo("codegen-server", "M1.3")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                // 在 Servlet 请求线程执行，保留 AuthContextFilter 写入的 UserContext。
                .immediateExecution(true)
                .tools(tools)
                .build();
    }

    private SyncToolSpecification toolSpecification(GenMcpToolDefinition definition,
                                                     McpJsonMapper jsonMapper,
                                                     GenMcpService mcpService,
                                                     ObjectMapper objectMapper) {
        Tool tool = Tool.builder(definition.name())
                .description(definition.description())
                .inputSchema(jsonMapper, definition.inputSchema())
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> arguments = request.arguments() == null
                            ? Map.of() : request.arguments();
                    try {
                        Object result = mcpService.call(definition.name(), arguments);
                        return CallToolResult.builder()
                                .addTextContent(objectMapper.writeValueAsString(result))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        String message = e.getMessage() == null ? "工具调用失败" : e.getMessage();
                        return CallToolResult.builder()
                                .addTextContent(message)
                                .isError(true)
                                .build();
                    }
                })
                .build();
    }
}
