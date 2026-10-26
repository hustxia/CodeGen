package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流水线任务分页查询。
 */
@Data
@Schema(description = "流水线任务分页查询")
public class GenPipelinePageDTO {

    @Schema(description = "页码，从 1 开始")
    private long pageNum = 1;

    @Schema(description = "每页条数")
    private long pageSize = 10;

    @Schema(description = "按状态过滤：draft/await_confirm/running/success/failed/stopped")
    private String status;
}
