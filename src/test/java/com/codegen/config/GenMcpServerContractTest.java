package com.codegen.config;

import com.codegen.service.GenMcpService;
import com.codegen.vo.GenMcpToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 直接驱动真实 MCP Servlet，覆盖 initialize → initialized → tools/list → tools/call。
 */
class GenMcpServerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private GenMcpService mcpService;
    private HttpServletStreamableServerTransportProvider transport;
    private McpSyncServer server;

    @BeforeEach
    void setUp() {
        mcpService = mock(GenMcpService.class);
        when(mcpService.tools()).thenReturn(List.of(new GenMcpToolDefinition(
                "codegen_pipeline_status", "查询状态", """
                {"type":"object","properties":{"pipelineId":{"type":"integer"}},"required":["pipelineId"]}
                """, false)));
        when(mcpService.call(eq("codegen_pipeline_status"), anyMap()))
                .thenReturn(Map.of("status", "running"));

        McpServerConfig config = new McpServerConfig();
        McpJsonMapper jsonMapper = config.mcpJsonMapper(objectMapper);
        transport = config.mcpTransport(jsonMapper);
        server = config.mcpServer(transport, jsonMapper, mcpService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void initializeListAndCallToolsThroughRealStreamableServlet() throws Exception {
        MockHttpServletResponse initialize = exchange(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-11-25",
                  "capabilities":{},
                  "clientInfo":{"name":"contract-test","version":"1.0.0"}
                }}
                """);
        assertEquals(200, initialize.getStatus());
        String sessionId = initialize.getHeader("Mcp-Session-Id");
        assertNotNull(sessionId);
        JsonNode initializeJson = responseJson(initialize);
        assertEquals("codegen-server", initializeJson.at("/result/serverInfo/name").asText());
        assertFalse(initializeJson.at("/result/capabilities/tools").isMissingNode());

        MockHttpServletResponse initialized = exchange(sessionId,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertEquals(202, initialized.getStatus());

        MockHttpServletResponse list = exchange(sessionId,
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        assertEquals(200, list.getStatus());
        assertEquals("codegen_pipeline_status",
                responseJson(list).at("/result/tools/0/name").asText());

        MockHttpServletResponse call = exchange(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"codegen_pipeline_status","arguments":{"pipelineId":7}
                }}
                """);
        assertEquals(200, call.getStatus());
        JsonNode callJson = responseJson(call);
        assertFalse(callJson.at("/result/isError").asBoolean());
        assertTrue(callJson.at("/result/content/0/text").asText().contains("running"));
        verify(mcpService).call(eq("codegen_pipeline_status"), argThat(arguments ->
                ((Number) arguments.get("pipelineId")).longValue() == 7L));
    }

    private MockHttpServletResponse exchange(String sessionId, String json) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setAsyncSupported(true);
        request.setRequestURI("/mcp");
        request.setServletPath("/mcp");
        request.setContentType("application/json");
        request.addHeader("Accept", "application/json, text/event-stream");
        if (sessionId != null) {
            request.addHeader("Mcp-Session-Id", sessionId);
            request.addHeader("MCP-Protocol-Version", "2025-11-25");
        }
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        transport.service(request, response);
        return response;
    }

    private JsonNode responseJson(MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        if (body.startsWith("event:") || body.startsWith("id:")) {
            for (String line : body.split("\\R")) {
                if (line.startsWith("data:")) {
                    body = line.substring(5).trim();
                    break;
                }
            }
        }
        return objectMapper.readTree(body);
    }
}
