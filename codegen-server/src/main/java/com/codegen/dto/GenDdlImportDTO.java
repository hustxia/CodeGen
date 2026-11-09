package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DDL 文本一键导入入参。
 *
 * <p>与 {@link GenDdlExecuteDTO} 的区别：本接口<b>不执行 DDL</b>，
 * 直接从建表语句文本解析表/列/注释并注册生成配置，适合「DDL 还没落到目标库，
 * 先把表配置和字典准备好」的场景。
 */
@Data
@Schema(description = "DDL 文本一键导入入参")
public class GenDdlImportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "建表 DDL（支持多张表，; 分隔；非 CREATE TABLE 语句自动忽略）")
    @NotBlank(message = "DDL 不能为空")
    private String ddl;

    @Schema(description = "导入的表归属的系统ID；留空则不绑定系统，生成时回落全局配置")
    private Long projectId;

    @Schema(description = "是否自动识别并落库字典，默认 true")
    private Boolean applyDict = Boolean.TRUE;
}
