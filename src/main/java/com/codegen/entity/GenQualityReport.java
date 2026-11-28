package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生成代码质量门禁报告（M2.2）。
 *
 * <p>每次生成前扫描一遍渲染产物，报告头存汇总，明细见 {@link GenQualityIssue}。
 * 命中 BLOCKER 时 {@code status=FAIL}，生成写盘被中止。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_quality_report")
public class GenQualityReport extends BaseEntity {

    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_FAIL = "FAIL";

    private Long projectId;

    /**
     * 本次扫描的表 ID 列表（逗号分隔）
     */
    private String tableIds;

    /**
     * 引擎组合：DET / DET+LLM
     */
    private String engine;

    /**
     * LLM 状态：APPLIED / DEGRADED / DISABLED
     */
    private String llmState;

    private String status;

    private Boolean blocked;

    private Integer blockerCount;

    private Integer warnCount;

    private Integer issueCount;

    private Integer fileCount;

    private Integer ruleCount;

    private Boolean degraded;

    private String degradedReason;

    private String message;

    private Long elapsedMs;
}
