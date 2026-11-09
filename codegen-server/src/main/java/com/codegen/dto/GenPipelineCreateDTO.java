package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建「一句话建系统」流水线任务。
 *
 * <p>建模（AI 或手动 DDL）在前端完成并暂存，创建任务时把确认结果一并带上：
 * ai 模式传 tables（可编辑过的表结构），manual 模式传 ddlText。
 */
@Data
@Schema(description = "创建建系统流水线任务入参")
public class GenPipelineCreateDTO {

    @Schema(description = "系统名称（中文）", example = "仓库管理系统")
    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    @Schema(description = "一句话业务描述", example = "管理入库、出库、库存")
    private String systemDesc;

    @Schema(description = "目标数据库名（小写字母/数字/下划线）", example = "wms_new")
    @NotBlank(message = "数据库名不能为空")
    private String dbName;

    @Schema(description = "ai=AI 建模 manual=手动粘贴 DDL", example = "ai")
    private String mode = "ai";

    @Schema(description = "V5.2 数据初始化：rule=规则生成（默认） ai=AI 值池增强（失败回落 rule） off=跳过", example = "rule")
    private String seedMode = "rule";

    @Schema(description = "manual 模式的建表 DDL")
    private String ddlText;

    @Schema(description = "ai 模式下确认过的表结构（可编辑后回传）")
    private java.util.List<com.codegen.vo.GenPipelineAiVO.TableModel> tables;
}
