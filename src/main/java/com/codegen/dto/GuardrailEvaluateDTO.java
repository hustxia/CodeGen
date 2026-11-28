package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** V9.25 独立护栏检测入参 */
@Data
@Schema(description = "护栏检测入参")
public class GuardrailEvaluateDTO {

    @NotBlank(message = "行业不能为空")
    private String industry;

    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    private String systemDesc;
    private List<String> moduleNames;
    private Map<String, String> answers;
}
