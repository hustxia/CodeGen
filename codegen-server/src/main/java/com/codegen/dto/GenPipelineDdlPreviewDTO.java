package com.codegen.dto;

import com.codegen.vo.GenPipelineAiVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * AI 模式 DDL 预览入参，只读计算，不执行任何 SQL。
 */
@Data
@Schema(description = "AI 模式 DDL 预览入参")
public class GenPipelineDdlPreviewDTO {

    @Schema(description = "目标数据库名", example = "asset_demo")
    @NotBlank(message = "数据库名不能为空")
    private String dbName;

    @Schema(description = "确认中的候选表结构")
    @NotEmpty(message = "请至少保留一张表再预览 SQL")
    private List<GenPipelineAiVO.TableModel> tables;
}
