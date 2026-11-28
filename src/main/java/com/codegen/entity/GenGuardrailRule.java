package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行业护栏规则（V9.25 M2.1）：4 行业 × 7 条 = 28。
 * 规则是数据，可启停、可改解释文案；判定逻辑在 GuardrailRuleEngine。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_guardrail_rule")
public class GenGuardrailRule extends BaseEntity {

    public static final String CAT_BLACKLIST = "BLACKLIST";
    public static final String CAT_WHITELIST = "WHITELIST";
    public static final String CAT_CONFLICT = "CONFLICT";
    public static final String CAT_BOUNDARY = "BOUNDARY";
    public static final String CAT_CLARIFY = "CLARIFY";

    public static final String SEVERITY_BLOCK = "BLOCK";
    public static final String SEVERITY_WARN = "WARN";
    public static final String SEVERITY_INFO = "INFO";

    private String ruleCode;
    private String industry;
    private String category;
    private String severity;
    private String triggerType;
    private String triggerExpr;
    private String title;
    private String reason;
    private String suggestion;
    private Integer sortNo;
    private Integer enabled;
}
