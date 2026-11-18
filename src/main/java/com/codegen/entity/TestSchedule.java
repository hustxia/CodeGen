package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V9.2 定时回归任务：按 cron 周期性触发指定套件执行（trigger_type=schedule）。
 *
 * <p>最近一次触发记录存在 {@code lastRunId}：正数=最近批次 ID；
 * -1=本实例已认领、正在跑（防多实例重复触发的哨兵值）；null=从未跑/已复位。
 * 到期判定由 {@code lastRunId} 反查批次的 started_at 得出，无需额外时间列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_schedule")
public class TestSchedule extends BaseEntity {

    private Long projectId;

    private String name;

    private Long envId;

    /** 逗号分隔的套件 ID 列表 */
    private String suiteIds;

    /** 6 段 cron 表达式（秒 分 时 日 月 周） */
    private String cron;

    /** 0 停用 / 1 启用 */
    private Integer enabled;

    /** 最近批次 ID；-1 表示认领中（哨兵） */
    private Long lastRunId;

    private String notifyType;

    private String notifyTarget;
}
