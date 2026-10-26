package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 执行 DDL 并自动导入表配置。
 */
@Data
@Schema(description = "建库建表一键导入入参")
public class GenDdlExecuteDTO {

    @Schema(description = "DDL 脚本内容（支持多语句，; 分隔）")
    @NotBlank(message = "DDL 不能为空")
    private String ddl;

    @Schema(description = "目标库名；留空则从 DDL 的 CREATE DATABASE / USE 语句推断，再回落当前连接库")
    private String schema;

    @Schema(description = "是否同时导入 gen_table 配置，默认 true")
    private Boolean autoImport = Boolean.TRUE;

    @Schema(description = "允许执行 DROP / TRUNCATE 等破坏性语句，默认 false")
    private Boolean allowDestructive = Boolean.FALSE;

    @Schema(description = "自动导入表配置时归属的系统ID；留空则不绑定系统，生成时回落全局配置")
    private Long projectId;

    @Schema(description = "dryRun=true 只解析不执行，返回将要执行的语句清单", example = "false")
    private Boolean dryRun = Boolean.FALSE;
}
