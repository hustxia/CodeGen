package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** V9.25 澄清答复入参；answer 为空字符串表示跳过 */
@Data
@Schema(description = "澄清答复入参")
public class ClarifyAnswerDTO {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotBlank(message = "问题码不能为空")
    private String questionCode;

    private String answer;
}
