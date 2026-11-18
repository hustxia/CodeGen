package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * V9.6 批次：一条记录 = 一个可独立执行的单元（拆模块 / 一个模块的建模 / 一批建表）。
 *
 * <p>认领靠「带条件的 UPDATE」而不是先查后改：多 worker 同时抢同一批时，
 * 只有 {@code where id=? and status='pending'} 命中 1 行的那个才执行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_ai_build_batch")
public class GenAiBuildJobBatch extends BaseEntity {

    private Long jobId;

    /** 顺序：0 固定是拆模块批次，其后按模块顺序编号 */
    private Integer seq;

    /** split / module / confirm */
    private String kind;

    private String moduleName;

    /** pending / running / success / failed / skipped */
    private String status;

    private Integer retryCount;

    /** 本批产出的表结构 JSON 数组（GenPipelineAiVO.TableModel[]） */
    private String tables;

    private Integer tableCount;

    private Long pipelineId;

    private String error;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
