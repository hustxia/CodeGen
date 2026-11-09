package com.codegen.common.flow;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流水（跨模块共用）。
 *
 * <p>放在 common 而不是各业务模块里：生成出来的 Controller 只依赖这一个类型，
 * 不用为了拿审批记录去依赖整个 Flow 模块的 VO。
 */
@Data
public class FlowRecord {

    private Long id;

    private String flowCode;

    private String bizTable;

    private Long bizId;

    private String fromStatus;

    private String toStatus;

    /** SUBMIT / APPROVE / REJECT */
    private String action;

    private String comment;

    private Long operUserId;

    private String operName;

    private LocalDateTime operTime;
}
