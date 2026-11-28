package com.codegen.service.impl;

import com.codegen.config.GenPipelineProperties;
import com.codegen.entity.GenConvergeReport;
import com.codegen.entity.GenPipeline;
import com.codegen.service.GenConvergeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2.1 生成后自动对账步骤：
 *
 * <ul>
 *   <li>偏差率超阈值 → 抛异常，由执行器把流水线置 failed（阻断）；</li>
 *   <li>未超阈值 → 返回可读摘要，流水线继续（告警）。</li>
 * </ul>
 */
class PipelineConvergeStepTest {

    private GenPipelineProperties props(double maxRatio) {
        GenPipelineProperties props = new GenPipelineProperties();
        props.setConvergeMaxDeviationRatio(maxRatio);
        return props;
    }

    private PipelineExecutor executor(double maxRatio, GenConvergeReport report) {
        GenConvergeService svc = new GenConvergeService() {
            @Override
            public GenConvergeReport run(Long projectId) {
                return report;
            }

            @Override
            public List<GenConvergeReport> reports(Long projectId) {
                throw new AssertionError("步骤执行不应调用 reports");
            }

            @Override
            public ReportDetail reportDetail(Long reportId) {
                throw new AssertionError("步骤执行不应调用 reportDetail");
            }
        };
        // 前 20 位协作者本用例不触达，第 21 位为 convergeService
        return new PipelineExecutor(null, null, null, null, null, null, null, props(maxRatio),
                null, null, null, null, null, null, null, null, null, null, null, null, svc);
    }

    private GenPipeline pipeline() {
        GenPipeline p = new GenPipeline();
        p.setId(1L);
        p.setProjectId(9L);
        p.setPipelineNo("PPL20261128001");
        return p;
    }

    private GenConvergeReport report(int ok, int missing, int extra) {
        GenConvergeReport r = new GenConvergeReport();
        r.setId(88L);
        r.setProjectId(9L);
        r.setOkCount(ok);
        r.setMissingCount(missing);
        r.setExtraCount(extra);
        r.setStatus(missing == 0 && extra == 0
                ? GenConvergeReport.STATUS_CONVERGED : GenConvergeReport.STATUS_UNCONVERGED);
        return r;
    }

    @Test
    @DisplayName("偏差率超阈值 → 阻断并给出偏差明细与处理入口")
    void blocksWhenDeviationAboveThreshold() {
        PipelineExecutor executor = executor(0.2d, report(6, 2, 2));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> executor.stepConverge(pipeline()));
        assertTrue(e.getMessage().contains("40%"), "要含实际偏差率：" + e.getMessage());
        assertTrue(e.getMessage().contains("缺失 2"), "要含缺失数：" + e.getMessage());
        assertTrue(e.getMessage().contains("多余 2"), "要含多余数：" + e.getMessage());
        assertTrue(e.getMessage().contains("Converge"), "要给出处理入口：" + e.getMessage());
    }

    @Test
    @DisplayName("偏差率未超阈值 → 返回摘要，流水线继续")
    void continuesWhenDeviationWithinThreshold() {
        PipelineExecutor executor = executor(0.2d, report(9, 1, 0));

        String message = executor.stepConverge(pipeline());
        assertTrue(message.contains("UNCONVERGED"), "摘要要标出对账状态：" + message);
        assertTrue(message.contains("缺失 1"), "摘要要含缺失数：" + message);
        assertTrue(message.contains("#88"), "摘要要含报告号，便于审计追踪：" + message);
    }

    @Test
    @DisplayName("完全一致 → 摘要为 CONVERGED")
    void convergedSummary() {
        PipelineExecutor executor = executor(0.2d, report(10, 0, 0));

        String message = executor.stepConverge(pipeline());
        assertTrue(message.contains("CONVERGED"), "摘要要标出已收敛：" + message);
    }
}
