package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 测试中心-运行记录。一次触发一行，是版本验收证据的顶层索引。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tc_run")
public class TcRun extends BaseEntity {

    /** 展示用编号：TC + 时间戳 + 当日序号 */
    private String runNo;

    /** MANUAL / CI / SCHEDULE / RERUN */
    private String triggerType;

    /** 逗号分隔的套件 id，按注册表顺序 */
    private String suiteIds;

    /** QUEUED/RUNNING/PASSED/FAILED/CANCELLED/TIMEOUT */
    private String status;

    private Integer total;
    private Integer passed;
    private Integer failed;
    private Integer skipped;
    private Long durationMs;
    private String gitCommit;

    /** codegen.testcenter/v1 摘要，与 CI 输出字段级一致 */
    private String summaryJson;

    /** 本次运行隔离目录（日志与产物的唯一根目录） */
    private String workDir;

    private String errorMsg;
}
