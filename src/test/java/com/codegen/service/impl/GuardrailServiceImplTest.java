package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.dto.ClarifyConfirmDTO;
import com.codegen.entity.GenClarifySession;
import com.codegen.entity.GenGuardrailRule;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.mapper.GenClarifyQuestionMapper;
import com.codegen.mapper.GenClarifySessionMapper;
import com.codegen.mapper.GenGuardrailRuleMapper;
import com.codegen.service.GenSpecService;
import com.codegen.support.GuardrailRuleEngine;
import com.codegen.vo.ClarifyConfirmVO;
import com.codegen.vo.GuardrailEvalVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M2.1 确认门禁契约：未处理 BLOCK 拒绝；高级模式留痕；重复确认幂等 */
@ExtendWith(MockitoExtension.class)
class GuardrailServiceImplTest {

    @Mock private GenGuardrailRuleMapper ruleMapper;
    @Mock private GenClarifyQuestionMapper questionMapper;
    @Mock private GenClarifySessionMapper sessionMapper;
    @Mock private GuardrailRuleEngine engine;
    @Mock private GenSpecService specService;
    @InjectMocks private GuardrailServiceImpl service;

    private GenClarifySession session;

    @BeforeEach
    void setUp() {
        session = new GenClarifySession();
        session.setId(31L);
        session.setProjectId(1L);
        session.setIndustry("agr_crop");
        session.setSystemName("智慧种植 + 水肥一体化");
        session.setSystemDesc("地块/种植季 + 水肥处方下发");
        session.setModuleNames("[\"地块与种植季\",\"水肥一体化\"]");
        session.setAnswersJson("{\"Q_DEVICE\":\"含直连设备\"}");
        session.setStatus(GenClarifySession.STATUS_READY);
        session.setQuestionIndex(5);
        org.mockito.Mockito.lenient().when(sessionMapper.selectById(31L)).thenReturn(session);
        org.mockito.Mockito.lenient().when(ruleMapper.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(specService.save(any())).thenAnswer(inv -> {
            GenSpecArtifact a = inv.getArgument(0);
            a.setId(777L);
            a.setVersion(3);
            return a;
        });
    }

    private GuardrailEvalVO evalWithBlock() {
        GuardrailEvalVO vo = new GuardrailEvalVO();
        vo.setBlocked(true);
        vo.setBlockCount(1);
        GuardrailEvalVO.Hit hit = new GuardrailEvalVO.Hit();
        hit.setRuleCode("AGR-C01");
        hit.setCategory("CONFLICT");
        hit.setSeverity("BLOCK");
        hit.setTitle("水肥控制不得直驱");
        hit.setReason("直驱阀门无人工兜底");
        hit.setSuggestion("处方下发→人工审核→回读对账");
        vo.setHits(new ArrayList<>(List.of(hit)));
        return vo;
    }

    @Test
    @DisplayName("未处理 BLOCK → 拒绝确认，报错含规则码")
    void blocksUnhandledConfirm() {
        when(engine.evaluate(any(), any(), any())).thenReturn(evalWithBlock());
        ClarifyConfirmDTO dto = new ClarifyConfirmDTO();
        BusinessException e = assertThrows(BusinessException.class, () -> service.confirm(31L, dto));
        assertTrue(e.getMessage().contains("AGR-C01"), e.getMessage());
        verify(specService, never()).save(any());
    }

    @Test
    @DisplayName("高级模式显式忽略 BLOCK → 放行并写 SPEC，标记 overridden")
    void advancedModeOverridesAndWritesSpec() {
        when(engine.evaluate(any(), any(), any())).thenReturn(evalWithBlock());
        ClarifyConfirmDTO dto = new ClarifyConfirmDTO();
        dto.setAdvancedMode(true);
        dto.setOverriddenCodes(List.of("AGR-C01"));

        ClarifyConfirmVO vo = service.confirm(31L, dto);
        assertEquals("777", vo.getSpecId());
        assertTrue(vo.isOverridden());
        assertEquals(GenClarifySession.STATUS_CONFIRMED, session.getStatus());
        assertEquals(777L, session.getSpecId());
        assertTrue(session.getOverriddenCodes().contains("AGR-C01"));
        verify(sessionMapper, times(1)).updateById(session);
    }

    @Test
    @DisplayName("重复确认幂等：不新建 SPEC 版本")
    void repeatConfirmIsIdempotent() {
        session.setStatus(GenClarifySession.STATUS_CONFIRMED);
        session.setSpecId(888L);
        when(specService.detail(888L)).thenReturn(new GenSpecArtifact());

        ClarifyConfirmVO vo = service.confirm(31L, new ClarifyConfirmDTO());
        assertEquals("888", vo.getSpecId());
        verify(specService, never()).save(any());
    }

    @Test
    @DisplayName("未知行业 → 400；不查库")
    void rejectsUnknownIndustry() {
        com.codegen.dto.GuardrailEvaluateDTO dto = new com.codegen.dto.GuardrailEvaluateDTO();
        dto.setIndustry("unknown");
        dto.setSystemName("测试系统");
        BusinessException e = assertThrows(BusinessException.class, () -> service.evaluate(dto));
        assertTrue(e.getMessage().contains("暂不支持"), e.getMessage());
        verify(ruleMapper, never()).selectList(any());
    }
}
