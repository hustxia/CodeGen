package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 测试中心-套件结果。一个 run 下每个选中的套件恰好一行（uk_run_suite）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tc_case_result")
public class TcCaseResult extends BaseEntity {

    private Long runId;
    private String suiteId;

    /** PASSED/FAILED/SKIPPED/TIMEOUT/CANCELLED */
    private String status;

    private Long durationMs;
    private Integer exitCode;

    /** 结构化基线值，如 {"tests":312,"failures":0} */
    private String metricsJson;

    private String logPath;

    /** 失败堆栈尾部，列表页直接显示，不必先下载日志 */
    private String errorExcerpt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
