package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成代码质量门禁结果（M2.2）。
 *
 * <p>{@code status=PASS} 才允许落盘；{@code blocked=true} 表示命中 BLOCKER，
 * 由 {@code GenApplyService} 在写盘前中止，并把行级 ❌→✅ 带回前端。
 */
@Data
public class GenQualityGateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_FAIL = "FAIL";

    public static final String LLM_APPLIED = "APPLIED";
    public static final String LLM_DEGRADED = "DEGRADED";
    public static final String LLM_DISABLED = "DISABLED";

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    private String status = STATUS_PASS;

    /**
     * 命中 BLOCKER，生成必须中止
     */
    private boolean blocked;

    /**
     * 实际使用的引擎组合：DET / DET+LLM
     */
    private String engine = "DET";

    /**
     * LLM 引擎状态：APPLIED / DEGRADED / DISABLED
     */
    private String llmState = LLM_DISABLED;

    /**
     * 降级原因（LLM 不可用/超时/解析失败）
     */
    private String degradedReason;

    private Integer blockerCount = 0;

    private Integer warnCount = 0;

    private Integer issueCount = 0;

    private Integer fileCount = 0;

    private Integer ruleCount = 0;

    private Integer llmRuleCount = 0;

    private Long elapsedMs = 0L;

    /**
     * 步骤摘要文案
     */
    private String message;

    private List<GenQualityIssueVO> issues = new ArrayList<>();
}
