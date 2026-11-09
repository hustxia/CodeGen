package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 领域建模入参：名称 + 一句话描述。
 */
@Data
@Schema(description = "AI 领域建模入参")
public class GenPipelineAiModelDTO {

    @Schema(description = "系统名称", example = "仓库管理系统")
    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    @Schema(description = "一句话业务描述", example = "管理入库、出库、库存")
    private String systemDesc;

    @Schema(description = "限定模块名（完整系统模式逐模块生成时传，空则整体建模）", example = "住院管理")
    private String moduleName;
}
