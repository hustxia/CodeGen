package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * V9.5「AI 推荐建表 + 人工确认」建模入参。
 *
 * <p>本 DTO 只描述「想要什么系统」，AI 建模在 {@code AiTableRecommendService} 内
 * 复用 V9.4 的 {@code AiModelService}，不改动 V9.4 任何类（AC-01）。
 */
@Data
@Schema(description = "V9.5 AI 推荐建表入参")
public class GenAiBuildPlanDTO {

    @Schema(description = "系统名称（中文）", example = "设备资产管理系统")
    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    @Schema(description = "一句话业务描述", example = "管理设备台账、维修、盘点")
    private String systemDesc;

    @Schema(description = "范围：new=新建系统，supplement=为已有系统补表", example = "new")
    private String scope = "new";

    @Schema(description = "补表时的目标系统项目 ID（scope=supplement 必填）")
    private Long projectId;

    @Schema(description = "规模：small=精简(<50表) large=大型(<500表) huge=超大型(几千张)", example = "huge")
    private String scale = "huge";

    @Schema(description = "部署形态：mono=单体工程 micro=微服务拆分（超大型默认）", example = "micro")
    private String deploy = "micro";

    @Schema(description = "V9.7 AI 生成模式：skeleton=极速骨架（AI 只列字段名，类型规则补全，快 3-5 倍）full=完整设计（AI 定类型）",
            example = "skeleton")
    private String generationMode = "skeleton";
}
