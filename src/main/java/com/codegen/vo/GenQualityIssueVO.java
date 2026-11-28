package com.codegen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 质量门禁单条问题（M2.2）。
 *
 * <p>行级对照：{@code badSnippet} 为命中行原文（❌），{@code fixSnippet} 为可执行的修正示例（✅）。
 */
@Data
public class GenQualityIssueVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String ruleId;

    private String title;

    private String category;

    /**
     * 引擎：DET / LLM
     */
    private String engine;

    /**
     * 级别：BLOCKER / WARN
     */
    private String severity;

    /**
     * 相对路径
     */
    private String file;

    /**
     * 行号，0 表示与具体行无关
     */
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

    /**
     * 坑库可追溯来源
     */
    private String sourceRef;
}
