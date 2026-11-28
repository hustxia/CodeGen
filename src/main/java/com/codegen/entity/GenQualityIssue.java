package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 质量门禁行级问题明细（M2.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_quality_issue")
public class GenQualityIssue extends BaseEntity {

    private Long reportId;

    private String ruleId;

    private String category;

    /**
     * 引擎：DET / LLM
     */
    private String engine;

    /**
     * 级别：BLOCKER / WARN
     */
    private String severity;

    private String filePath;

    private Long lineNo;

    private String message;

    /**
     * ❌ 命中片段
     */
    private String badSnippet;

    /**
     * ✅ 修正示例
     */
    private String fixSnippet;

    private String sourceRef;
}
