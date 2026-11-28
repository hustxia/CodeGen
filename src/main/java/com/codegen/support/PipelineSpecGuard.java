package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import com.codegen.entity.GenConvergeReport;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.service.GenSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * M2.1 规格一致性护栏。
 *
 * <p>两道闸门嵌入生成流水线：
 * <ol>
 *   <li>启动前：项目必须存在 CONFIRMED 的 SPEC 工件，DRAFT / 无规格一律拒绝；</li>
 *   <li>生成后：按 Converge 报告计算偏差率，超阈值即阻断。</li>
 * </ol>
 *
 * <p>对账报告本身落 {@code gen_converge_report / gen_converge_item}，即为护栏审计留痕。
 */
@Component
public class PipelineSpecGuard {

    @Autowired
    private GenSpecService specService;

    /**
     * 启动前校验：取项目最新 CONFIRMED 的 SPEC，不存在则拒绝启动。
     *
     * <p>不用 {@link GenSpecService#latestEffectiveSpec(Long)}——它会在无 CONFIRMED 时
     * 回退 DRAFT，而护栏只认已确认规格。
     */
    public GenSpecArtifact requireConfirmedSpec(Long projectId) {
        GenSpecArtifact confirmed = specService.history(projectId, GenSpecArtifact.TYPE_SPEC).stream()
                .filter(a -> GenSpecArtifact.STATUS_CONFIRMED.equals(a.getStatus()))
                .findFirst()
                .orElse(null);
        if (confirmed == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "规格一致性护栏：系统尚未确认 SPEC 规格，请先在「规格工件」中创建并确认后再启动流水线");
        }
        return confirmed;
    }

    /** 偏差率 =（缺失 + 多余）/ 总比对项；无任何比对项视为 0。 */
    public static double deviationRatio(GenConvergeReport report) {
        int missing = n(report.getMissingCount());
        int extra = n(report.getExtraCount());
        int total = n(report.getOkCount()) + missing + extra;
        return total == 0 ? 0d : (double) (missing + extra) / total;
    }

    /** 偏差率超过阈值即抛出，由执行器落成步骤失败与流水线 failReason。 */
    public static void assertWithinThreshold(GenConvergeReport report, double maxRatio) {
        double ratio = deviationRatio(report);
        if (ratio > maxRatio) {
            throw new IllegalStateException("规格一致性护栏：偏差率 "
                    + percent(ratio) + " 超过阈值 " + percent(maxRatio)
                    + "，缺失 " + n(report.getMissingCount())
                    + " / 多余 " + n(report.getExtraCount())
                    + "（报告 #" + report.getId() + "）；请在 Converge 页面处理后重跑");
        }
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }
}
