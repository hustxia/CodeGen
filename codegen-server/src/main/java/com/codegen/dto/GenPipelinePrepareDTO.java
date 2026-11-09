package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * prepare-db 入参：确认后的表结构（ai 模式）或 DDL 文本（manual 模式）。
 */
@Data
@Schema(description = "建库建表入参（表结构确认后的结果）")
public class GenPipelinePrepareDTO {

    @Schema(description = "ai 模式：确认/编辑过的表结构")
    private List<com.codegen.vo.GenPipelineAiVO.TableModel> tables;

    @Schema(description = "manual 模式：建表 DDL 文本")
    private String ddlText;
}
