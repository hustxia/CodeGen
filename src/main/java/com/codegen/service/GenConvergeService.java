package com.codegen.service;

import com.codegen.entity.GenConvergeItem;
import com.codegen.entity.GenConvergeReport;

import java.util.List;

/**
 * Converge 规格对账服务（V1.30 第一期）
 * <p>
 * 对账范围第一版收敛为三类：表 / 字段 / 枚举。先窄后宽，语义对账后续版本再扩。
 */
public interface GenConvergeService {

    /**
     * 执行对账：以项目最新生效 SPEC 工件的 structured_json 为期望清单，
     * 采集 gen_table / gen_table_column 实际产物逐项比对，落报告与明细。
     *
     * @param projectId 项目ID
     * @return 对账报告（含汇总数）
     * @throws IllegalStateException 项目无可用 SPEC 工件时
     */
    GenConvergeReport run(Long projectId);

    /**
     * 项目的对账报告列表（时间倒序）
     */
    List<GenConvergeReport> reports(Long projectId);

    /**
     * 报告详情（含明细，kind 顺序：MISSING → EXTRA → OK）
     */
    ReportDetail reportDetail(Long reportId);

    /**
     * 报告 + 明细聚合 VO
     */
    record ReportDetail(GenConvergeReport report, List<GenConvergeItem> items) {
    }
}
