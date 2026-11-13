package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * V9.2 执行批次：一次「哪些套件在哪个环境跑」的记录。
 *
 * <p>计数列（total/passed/failed/skipped）在批次结束时一次性回写，
 * 执行中由内存聚合，避免每条用例结果都 UPDATE 一次批次行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_run")
public class TestRun extends BaseEntity {

    private Long projectId;

    private Long envId;

    /** manual / schedule / ci */
    private String triggerType;

    /** 逗号分隔的套件 ID */
    private String suiteIds;

    /** running / success / failed / aborted */
    private String status;

    private Integer total;

    private Integer passed;

    private Integer failed;

    private Integer skipped;

    private Long durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorMsg;
}
