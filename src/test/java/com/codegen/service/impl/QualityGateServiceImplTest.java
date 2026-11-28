package com.codegen.service.impl;

import com.codegen.mapper.GenQualityIssueMapper;
import com.codegen.mapper.GenQualityReportMapper;
import com.codegen.mapper.GenTableMapper;
import com.codegen.quality.DeterministicQualityScanner;
import com.codegen.service.GenCodeService;
import com.codegen.service.QualityLlmClient;
import com.codegen.vo.GenFileVO;
import com.codegen.vo.GenQualityGateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 双引擎编排：LLM 失败必须回落确定性引擎并标记降级；LLM 不能把 BLOCKER 降级。
 */
class QualityGateServiceImplTest {

    private QualityGateServiceImpl service;
    private QualityLlmClient llmClient;

    @BeforeEach
    void setUp() {
        service = new QualityGateServiceImpl();
        GenCodeService codeService = mock(GenCodeService.class);
        when(codeService.preview(any())).thenReturn(List.of(new GenFileVO("demo",
                "src/DemoController.java",
                "class DemoController {\n    public Object detail(@PathVariable Long id) { return null; }\n}")));
        llmClient = mock(QualityLlmClient.class);
        GenTableMapper tableMapper = mock(GenTableMapper.class);
        when(tableMapper.selectBatchIds(any())).thenReturn(List.of());
        GenQualityReportMapper reportMapper = mock(GenQualityReportMapper.class);
        GenQualityIssueMapper issueMapper = mock(GenQualityIssueMapper.class);

        ReflectionTestUtils.setField(service, "genCodeService", codeService);
        ReflectionTestUtils.setField(service, "llmClient", llmClient);
        ReflectionTestUtils.setField(service, "genTableMapper", tableMapper);
        ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "issueMapper", issueMapper);
        ReflectionTestUtils.setField(service, "deterministicScanner", new DeterministicQualityScanner());
    }

    @Test
    void llmTimeoutFallsBackToDeterministicEngine() throws Exception {
        when(llmClient.available()).thenReturn(true);
        when(llmClient.analyze(any(), any())).thenThrow(new RuntimeException("timeout"));

        GenQualityGateVO vo = service.run(List.of(1L));

        assertEquals("DET", vo.getEngine());
        assertEquals(GenQualityGateVO.LLM_DEGRADED, vo.getLlmState());
        assertTrue(vo.isBlocked());
        assertEquals(1, vo.getBlockerCount());
        assertEquals("Q001", vo.getIssues().get(0).getRuleId());
        assertEquals(2L, vo.getIssues().get(0).getLineNo());
        assertNotNull(vo.getIssues().get(0).getBadSnippet());
        assertTrue(vo.getIssues().get(0).getFixSnippet().contains("@PathVariable(\"id\")"));
    }

    @Test
    void llmCannotDowngradeRuleSeverity() throws Exception {
        when(llmClient.available()).thenReturn(true);
        when(llmClient.analyze(any(), any())).thenReturn(List.of(
                new QualityLlmClient.LlmIssue("Q015", "src/DemoController.java", 2L, "WARN",
                        "实体列不一致", "@PathVariable Long id", "@PathVariable(\"id\") Long id")));

        GenQualityGateVO vo = service.run(List.of(1L));

        assertEquals("DET+LLM", vo.getEngine());
        assertEquals(GenQualityGateVO.LLM_APPLIED, vo.getLlmState());
        assertTrue(vo.getIssues().stream()
                .anyMatch(i -> "Q015".equals(i.getRuleId()) && "BLOCKER".equals(i.getSeverity())));
    }
}
