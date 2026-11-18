package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V9.6 AI 建表分批任务（父）：大型/超大型系统拆成多个批次，由调度器多 worker 并发执行。
 *
 * <p>为什么落库而不是放内存：一次「超大型」可能跑几十分钟，进程重启/发布都会丢内存任务；
 * 落库后 {@code pending} 批次在重启后仍会被调度器认领，失败批次按 {@code retryCount} 重试。
 *
 * <p>{@code payload} 存建模上下文（DTO + 已有表清单 + 追加了约束的 desc）：
 * 批次执行时不必再回表拼参数，也保证重试时输入完全一致（幂等）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_ai_build_job")
public class GenAiBuildJob extends BaseEntity {

    /** 可读任务号，如 AIJ20261115163000001 */
    private String jobNo;

    /** plan=建模；confirm=落库建表（预留） */
    private String jobType;

    /** pending / running / success / partial / failed / canceled */
    private String status;

    private String systemName;

    private String systemDesc;

    /** new / supplement */
    private String scope;

    private Long projectId;

    private String scale;

    private String deploy;

    private Integer totalBatch;

    private Integer doneBatch;

    private Integer failedBatch;

    private Integer totalTable;

    /** 任务上下文 JSON（含 dto / existing / descForAi） */
    private String payload;

    /** 最终结果 JSON（GenAiBuildPlanVO） */
    private String result;

    private String error;
}
