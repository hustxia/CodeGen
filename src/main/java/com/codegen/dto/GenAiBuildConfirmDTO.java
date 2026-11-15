package com.codegen.dto;

import com.codegen.vo.GenPipelineAiVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * V9.5 确认落库/建库入参。
 *
 * <p>草稿（draft）= 仅 {@code GenPipelineService.create}（产出 draft 任务，不建库不导表）；
 * 建库（build）= create + prepareDb（真实建库，进 await_confirm，run 由前端构建按钮触发）。
 * 微服务（micro）= 按 {@code moduleTableNames} 把表拆到各自服务，逐服务 create/prepareDb，
 * 每服务自有 schema（dbName 见 {@code AiTableRecommendServiceImpl} 的 sanitize）。
 */
@Data
@Schema(description = "V9.5 确认落库/建库入参")
public class GenAiBuildConfirmDTO {

    @Schema(description = "系统名称（中文）")
    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    @Schema(description = "一句话业务描述")
    private String systemDesc;

    @Schema(description = "目标数据库名（单体形态使用；微服务形态按模块派生）", example = "ams_new")
    @NotBlank(message = "数据库名不能为空")
    private String dbName;

    @Schema(description = "范围：new / supplement")
    private String scope = "new";

    @Schema(description = "补表时的目标系统项目 ID（scope=supplement 必填，用于剔除已有表）")
    private Long projectId;

    @Schema(description = "部署形态：mono / micro")
    private String deploy = "micro";

    @Schema(description = "执行模式：draft=落为草稿 build=确认建库", example = "build")
    private String mode = "build";

    @Schema(description = "人工确认/编辑后的表结构（ai 模式）")
    @NotEmpty(message = "请至少保留一张表")
    private List<GenPipelineAiVO.TableModel> tables;

    @Schema(description = "模块 → 其包含的表名清单（微服务形态拆分用；单体形态可空）")
    private Map<String, List<String>> moduleTableNames;
}
