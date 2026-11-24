package com.codegen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * V9.18 定时任务视图：在实体字段之外补「间隔分钟 / 最近执行 / 下次执行」，
 * 让页面不用自己解析 cron，也能显示「隔 30 分钟、下次 10:30 执行」。
 */
@Data
public class TestScheduleVO {

    private Long id;
    private Long projectId;
    private String name;
    private Long envId;
    private String suiteIds;
    /** 原始存储规则：cron 或 @every Nm */
    private String cron;
    /** 间隔模式下的分钟数；cron 模式为 null */
    private Integer intervalMinutes;
    private Integer enabled;
    /** 最近批次 ID；-1 表示正在认领执行 */
    private Long lastRunId;
    private LocalDateTime lastRunAt;
    private String lastRunStatus;
    /** 下次执行时间；停用任务为 null 以外的真实计划点，启用任务已到期则显示当前时间 */
    private LocalDateTime nextRunAt;
    private String notifyType;
    private String notifyTarget;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
