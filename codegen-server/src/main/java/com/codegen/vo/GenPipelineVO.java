package com.codegen.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流水线任务详情：任务头 + 全部步骤（按 stepOrder 排序）。
 * Long 一律字符串化，与平台约定一致。
 */
@Data
@Schema(description = "流水线任务详情")
public class GenPipelineVO {

    @Schema(description = "任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "流水线号")
    private String pipelineNo;

    @Schema(description = "关联系统 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    @Schema(description = "系统名称")
    private String systemName;

    @Schema(description = "一句话业务描述")
    private String systemDesc;

    @Schema(description = "目标数据库名")
    private String dbName;

    @Schema(description = "后端端口")
    private Integer backendPort;

    @Schema(description = "前端 dev 端口")
    private Integer frontendPort;

    @Schema(description = "ai / manual")
    private String mode;

    @Schema(description = "V5.2 数据初始化：rule=规则生成 off=跳过（NULL=off）")
    private String seedMode;

    @Schema(description = "draft/await_confirm/running/success/failed/stopped")
    private String status;

    @Schema(description = "当前执行到的步骤 key")
    private String currentStep;

    @Schema(description = "失败原因摘要")
    private String failReason;

    @Schema(description = "前端访问地址（部署完成后）")
    private String frontendUrl;

    @Schema(description = "后端子进程 PID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long backendPid;

    @Schema(description = "前端子进程 PID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long frontendPid;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "步骤明细（按 stepOrder 升序）")
    private List<Step> steps = new ArrayList<>();

    @Data
    @Schema(description = "流水线步骤")
    public static class Step {

        @Schema(description = "步骤 ID")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @Schema(description = "步骤 key")
        private String stepKey;

        @Schema(description = "步骤中文名")
        private String stepName;

        @Schema(description = "执行顺序")
        private Integer stepOrder;

        @Schema(description = "pipeline / backend / frontend")
        private String side;

        @Schema(description = "pending/running/success/failed/skipped")
        private String status;

        @Schema(description = "结果/错误摘要")
        private String message;

        @Schema(description = "日志摘要")
        private String logExcerpt;

        @Schema(description = "开始时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startedAt;

        @Schema(description = "结束时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime finishedAt;
    }
}
