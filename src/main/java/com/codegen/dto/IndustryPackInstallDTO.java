package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * V9.22 范式包安装入参：复用 V9.7 GenAiTemplatePlanDTO 的口径，
 * 增加 packKey（范式口径）与 moduleNames 必填校验。
 */
@Data
@Schema(description = "范式包安装入参")
public class IndustryPackInstallDTO {

    @Schema(description = "范式包key", example = "cm_city")
    @NotBlank(message = "范式包key不能为空")
    private String packKey;

    @Schema(description = "目标项目ID")
    private Long projectId;

    @Schema(description = "安装模块名（为空=全量安装）")
    @NotEmpty(message = "请至少选择一个模块")
    private List<String> moduleNames;

    @Schema(description = "new新建系统 / supplement补表", example = "new")
    private String scope = "new";

    @Schema(description = "系统名称（中文，new 时必填）", example = "某某城管平台")
    private String systemName;

    @Schema(description = "一句话业务描述")
    private String systemDesc;

    @Schema(description = "规模", example = "small")
    private String scale = "small";

    @Schema(description = "部署形态 mono/micro", example = "mono")
    private String deploy = "mono";
}
