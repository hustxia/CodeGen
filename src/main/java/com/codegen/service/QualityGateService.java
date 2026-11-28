package com.codegen.service;

import com.codegen.vo.GenQualityGateVO;

import java.util.List;

/**
 * 生成代码质量门禁服务（M2.2）。
 *
 * <p>在执行写盘前对渲染产物做确定性 + LLM 双引擎扫描；命中 BLOCKER 时
 * 由调用方中止落盘。报告与行级明细落 {@code gen_quality_report / gen_quality_issue}。
 */
public interface QualityGateService {

    /**
     * 扫描指定表的渲染产物并落报告。
     *
     * @param ids 表 ID
     * @return 门禁结果（含行级 ❌→✅）
     */
    GenQualityGateVO run(List<Long> ids);

    /**
     * 报告详情（含行级明细）
     */
    GenQualityGateVO reportDetail(Long reportId);
}
