package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流水线步骤明细：一步一行，按 stepOrder 排序。
 *
 * <p>side 字段给双进度条分组用：backend / frontend 两轨并行段，
 * 其余步骤归入 pipeline 全局轨。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_pipeline_step")
public class GenPipelineStep extends BaseEntity {

    /** gen_pipeline.id */
    private Long pipelineId;

    /** 步骤 key：model/db/scaffold/gen/package_backend/deploy_backend/package_frontend/deploy_frontend/health */
    private String stepKey;

    /** 步骤中文名 */
    private String stepName;

    /** 执行顺序，从 1 开始 */
    private Integer stepOrder;

    /** pipeline / backend / frontend */
    private String side;

    /** pending/running/success/failed/skipped */
    private String status;

    /** 结果/错误摘要 */
    private String message;

    /** 日志摘要（尾部 N 行） */
    private String logExcerpt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
