package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V9.5 确认落库/建库结果。
 */
@Data
@Schema(description = "V9.5 确认结果")
public class GenAiBuildConfirmVO {

    @Schema(description = "执行模式：draft / build")
    private String mode;

    @Schema(description = "部署形态：mono / micro")
    private String deploy;

    @Schema(description = "表总数")
    private int tableCount;

    @Schema(description = "生成的服务/工程数（mono=1，micro=模块数）")
    private int serviceCount;

    @Schema(description = "单体形态下的流水线任务 ID")
    private Long pipelineId;

    @Schema(description = "微服务形态：逐服务的建库结果")
    private List<ServiceResult> services = new ArrayList<>();

    @Data
    @Schema(description = "微服务逐服务结果")
    public static class ServiceResult {

        @Schema(description = "模块名")
        private String moduleName;

        @Schema(description = "该服务的数据库 schema")
        private String dbName;

        @Schema(description = "流水线任务 ID")
        private Long pipelineId;

        @Schema(description = "状态：ok / failed")
        private String status = "ok";

        @Schema(description = "失败原因（status=failed 时）")
        private String failReason;
    }
}
