package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成前置 lint：按 BaseEntity 约定检查目标库每张表的审计列。
 */
@Data
@Schema(description = "流水线 lint 预检入参")
public class GenPipelineLintDTO {

    @Schema(description = "目标库名", example = "wms_new")
    @NotBlank(message = "数据库名不能为空")
    private String dbName;
}
