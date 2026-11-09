package com.codegen.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批动作入参
 */
@Data
public class FlowActionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程编码，对应 sys_flow_def.flow_code */
    private String flowCode;

    /** 业务单据主键 */
    private Long bizId;

    /** 审批意见，可空 */
    private String comment;
}
