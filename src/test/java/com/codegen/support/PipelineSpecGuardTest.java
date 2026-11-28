package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenConvergeReport;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.service.GenSpecService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * M2.1 规格一致性护栏契约：
 *
 * <ul>
 *   <li>启动前：只有 CONFIRMED 的 SPEC 才算数，DRAFT / 无 SPEC 一律拒绝；</li>
 *   <li>生成后：偏差率 =（缺失 + 多余）/ 总比对项，超过阈值必须拒绝。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PipelineSpecGuardTest {

    @Mock
    private GenSpecService specService;

    @InjectMocks
    private PipelineSpecGuard guard;

    private GenSpecArtifact spec(String status) {
        GenSpecArtifact a = new GenSpecArtifact();
        a.setId(1L);
        a.setProjectId(7L);
        a.setArtifactType(GenSpecArtifact.TYPE_SPEC);
        a.setVersion(2);
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("无 CONFIRMED SPEC（只有 DRAFT）→ 拒绝启动，报错可读")
    void rejectsDraftOnly() {
        when(specService.history(7L, GenSpecArtifact.TYPE_SPEC))
                .thenReturn(List.of(spec(GenSpecArtifact.STATUS_DRAFT)));

        BusinessException e = assertThrows(BusinessException.class, () -> guard.requireConfirmedSpec(7L));
        assertTrue(e.getMessage().contains("未确认"), "报错要指明规格未确认：" + e.getMessage());
        assertTrue(e.getMessage().contains("规格工件"), "报错要给出处理入口：" + e.getMessage());
    }

    @Test
    @DisplayName("无任何 SPEC → 拒绝启动")
    void rejectsMissingSpec() {
        when(specService.history(7L, GenSpecArtifact.TYPE_SPEC)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> guard.requireConfirmedSpec(7L));
    }

    @Test
    @DisplayName("存在 CONFIRMED SPEC → 放行并返回该工件")
    void acceptsConfirmed() {
        GenSpecArtifact confirmed = spec(GenSpecArtifact.STATUS_CONFIRMED);
        when(specService.history(7L, GenSpecArtifact.TYPE_SPEC))
                .thenReturn(List.of(spec(GenSpecArtifact.STATUS_DRAFT), confirmed));

        assertSame(confirmed, guard.requireConfirmedSpec(7L));
    }

    @Test
    @DisplayName("偏差率 =（缺失 + 多余）/ 总比对项；空报告视为 0")
    void deviationRatio() {
        GenConvergeReport report = new GenConvergeReport();
        report.setOkCount(8);
        report.setMissingCount(1);
        report.setExtraCount(1);
        assertEquals(0.2d, PipelineSpecGuard.deviationRatio(report), 0.0001d);

        GenConvergeReport empty = new GenConvergeReport();
        empty.setOkCount(0);
        empty.setMissingCount(0);
        empty.setExtraCount(0);
        assertEquals(0d, PipelineSpecGuard.deviationRatio(empty), 0.0001d);
    }

    @Test
    @DisplayName("偏差率超过阈值 → 阻断；不超过则放行")
    void thresholdBlocksOnlyAboveLimit() {
        GenConvergeReport over = new GenConvergeReport();
        over.setId(88L);
        over.setOkCount(6);
        over.setMissingCount(2);
        over.setExtraCount(2);
        over.setStatus(GenConvergeReport.STATUS_UNCONVERGED);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> PipelineSpecGuard.assertWithinThreshold(over, 0.2d));
        assertTrue(e.getMessage().contains("超过阈值"), "阻断原因要明确：" + e.getMessage());
        assertTrue(e.getMessage().contains("40%"), "阻断信息要含实际偏差率：" + e.getMessage());

        GenConvergeReport within = new GenConvergeReport();
        within.setOkCount(9);
        within.setMissingCount(1);
        within.setExtraCount(0);
        within.setStatus(GenConvergeReport.STATUS_UNCONVERGED);
        PipelineSpecGuard.assertWithinThreshold(within, 0.2d);
    }
}
