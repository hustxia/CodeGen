package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenConvergeReport;
import com.codegen.entity.GenMcpCallLog;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.mapper.GenMcpCallLogMapper;
import com.codegen.service.GenConvergeService;
import com.codegen.service.GenPipelineService;
import com.codegen.service.GenSpecService;
import com.codegen.vo.GenMcpToolDefinition;
import com.codegen.vo.GenPipelineVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenMcpServiceTest {

    @Mock
    private GenPipelineService pipelineService;

    @Mock
    private GenSpecService specService;

    @Mock
    private GenConvergeService convergeService;

    @Mock
    private GenMcpCallLogMapper logMapper;

    private GenMcpServiceImpl service() {
        return new GenMcpServiceImpl(specService, convergeService, pipelineService, logMapper,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void pipelineRunRequiresExplicitConfirmation() {
        GenMcpServiceImpl service = service();

        assertThrows(BusinessException.class, () -> service.call(
                "codegen_pipeline_run", Map.of("pipelineId", 7L)));

        verifyNoInteractions(pipelineService);
    }

    @Test
    void confirmedPipelineRunDelegatesToExistingPipelineService() {
        GenMcpServiceImpl service = service();
        GenPipelineVO expected = new GenPipelineVO();
        when(pipelineService.run(7L)).thenReturn(expected);

        Object actual = service.call("codegen_pipeline_run",
                Map.of("pipelineId", 7L, "confirmed", true));

        assertSame(expected, actual);
        verify(pipelineService).run(7L);
    }

    @Test
    void remainingToolsDelegateToExistingDomainServices() {
        GenMcpServiceImpl service = service();
        when(specService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GenSpecArtifact confirmedArtifact = new GenSpecArtifact();
        when(specService.confirm(8L)).thenReturn(confirmedArtifact);
        GenConvergeReport report = new GenConvergeReport();
        when(convergeService.run(3L)).thenReturn(report);
        GenPipelineVO status = new GenPipelineVO();
        when(pipelineService.detail(7L)).thenReturn(status);

        Object saved = service.call("codegen_spec_save", Map.of(
                "projectId", 3,
                "artifactType", "SPEC",
                "title", "订单规格",
                "yamlMeta", "{}",
                "contentMd", "# SPEC",
                "structuredJson", "{}",
                "confirmed", true));
        Object confirmed = service.call("codegen_spec_confirm",
                Map.of("artifactId", 8, "confirmed", true));
        Object converged = service.call("codegen_converge_run",
                Map.of("projectId", 3, "confirmed", true));
        Object actualStatus = service.call("codegen_pipeline_status", Map.of("pipelineId", 7));

        ArgumentCaptor<GenSpecArtifact> artifact = ArgumentCaptor.forClass(GenSpecArtifact.class);
        verify(specService).save(artifact.capture());
        assertEquals(3L, artifact.getValue().getProjectId());
        assertEquals("SPEC", artifact.getValue().getArtifactType());
        assertEquals("订单规格", artifact.getValue().getTitle());
        assertSame(artifact.getValue(), saved);
        assertSame(confirmedArtifact, confirmed);
        assertSame(report, converged);
        assertSame(status, actualStatus);
        verify(specService).confirm(8L);
        verify(convergeService).run(3L);
        verify(pipelineService).detail(7L);
    }

    @Test
    void catalogExposesOnlyFiveToolsAndMarksWriteConfirmations() {
        List<GenMcpToolDefinition> tools = service().tools();

        assertEquals(List.of(
                "codegen_spec_save",
                "codegen_spec_confirm",
                "codegen_converge_run",
                "codegen_pipeline_run",
                "codegen_pipeline_status"), tools.stream().map(GenMcpToolDefinition::name).toList());
        assertEquals(4, tools.stream().filter(GenMcpToolDefinition::mutating).count());
        tools.stream().filter(GenMcpToolDefinition::mutating).forEach(tool ->
                assertTrue(tool.inputSchema().contains("\"confirmed\""), tool.name()));
    }

    @Test
    void successfulAndFailedToolCallsAreAudited() {
        GenPipelineVO status = new GenPipelineVO();
        status.setStatus("running");
        when(pipelineService.detail(7L)).thenReturn(status);

        service().call("codegen_pipeline_status", Map.of("pipelineId", 7));
        assertThrows(BusinessException.class, () -> service().call(
                "codegen_pipeline_run", Map.of("pipelineId", 7)));

        ArgumentCaptor<GenMcpCallLog> captor = ArgumentCaptor.forClass(GenMcpCallLog.class);
        verify(logMapper, times(2)).insert(captor.capture());
        List<GenMcpCallLog> logs = captor.getAllValues();
        assertEquals("codegen_pipeline_status", logs.get(0).getToolName());
        assertEquals("SUCCESS", logs.get(0).getStatus());
        assertTrue(logs.get(0).getArgumentsJson().contains("\"pipelineId\":7"));
        assertTrue(logs.get(0).getResultSummary().contains("running"));
        assertTrue(logs.get(0).getDurationMs() >= 0);
        assertEquals("codegen_pipeline_run", logs.get(1).getToolName());
        assertEquals("FAILED", logs.get(1).getStatus());
        assertTrue(logs.get(1).getErrorMessage().contains("confirmed=true"));
    }
}
