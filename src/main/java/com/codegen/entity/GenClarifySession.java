package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 澄清会话（V9.25 M2.1）：问答、命中、显式忽略全部留痕。
 * 确认后写 SPEC 草案，仍需在规格工件页人工确认（人工卡点不被绕过）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_clarify_session")
public class GenClarifySession extends BaseEntity {

    public static final String STATUS_CLARIFYING = "CLARIFYING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_ABANDONED = "ABANDONED";

    private Long projectId;
    private String industry;
    private String systemName;
    private String systemDesc;
    private String moduleNames;
    private String answersJson;
    private String hitsJson;
    private String overriddenCodes;
    private Integer advancedMode;
    private Integer questionIndex;
    private String status;
    private Long specId;
}
