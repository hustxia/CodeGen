package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** V9.25 开始澄清入参 */
@Data
@Schema(description = "开始澄清入参")
public class ClarifyStartDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "行业不能为空")
    private String industry;

    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    private String systemDesc;
    private List<String> moduleNames;
}
