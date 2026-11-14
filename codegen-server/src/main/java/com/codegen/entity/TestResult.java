package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * V9.2 用例执行结果：一条用例一次执行的完整快照。
 *
 * <p>{@code caseName} 存快照而非关联查询，用例改名后历史报告仍然可读。
 * 响应体超过 64KB 时截断并追加标记，防止单行撑爆表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_result")
public class TestResult extends BaseEntity {

    private Long runId;

    private Long caseId;

    private String caseName;

    /** pass / fail / skip */
    private String status;

    private Long durationMs;

    private String requestSnapshot;

    private String responseSnapshot;

    private String screenshotPath;

    private String failedAssertJson;

    private String errorMsg;
}
