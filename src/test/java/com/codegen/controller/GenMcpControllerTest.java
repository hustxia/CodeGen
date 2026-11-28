package com.codegen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.entity.GenMcpCallLog;
import com.codegen.mapper.GenMcpCallLogMapper;
import com.codegen.service.GenMcpService;
import com.codegen.vo.GenMcpToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GenMcpControllerTest {

    @Mock
    private GenMcpService mcpService;

    @Mock
    private GenMcpCallLogMapper logMapper;

    @InjectMocks
    private GenMcpController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void managementEndpointsExposeConfigToolsAndPagedAuditLogs() throws Exception {
        when(mcpService.tools()).thenReturn(List.of(
                new GenMcpToolDefinition("codegen_pipeline_status", "查询状态", "{}", false)));
        GenMcpCallLog log = new GenMcpCallLog();
        log.setToolName("codegen_pipeline_status");
        log.setStatus("SUCCESS");
        when(logMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<GenMcpCallLog> page = invocation.getArgument(0);
            page.setRecords(List.of(log));
            page.setTotal(1);
            return page;
        });

        mvc.perform(get("/gen/mcp/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endpoint").value("/mcp"))
                .andExpect(jsonPath("$.data.protocol").value("Streamable HTTP"))
                .andExpect(jsonPath("$.data.authentication").value("Authorization: Bearer <token>"));
        mvc.perform(get("/gen/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("codegen_pipeline_status"));
        mvc.perform(post("/gen/mcp/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].toolName").value("codegen_pipeline_status"));
    }
}
