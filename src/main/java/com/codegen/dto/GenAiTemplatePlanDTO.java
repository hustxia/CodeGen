package com.codegen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * V9.7 行业模板建表入参：选模板 → 克隆表结构 → 走与 AI 推荐完全相同的收口
 * （去重/补表差集/关系视图），后续编辑与落库链路零差异。
 */
@Data
@Schema(description = "V9.7 行业模板建表入参")
public class GenAiTemplatePlanDTO {

    @Schema(description = "模板 key（见 GET /gen/ai-build/templates）", example = "ecommerce")
    @NotBlank(message = "模板 key 不能为空")
    private String templateKey;

    @Schema(description = "系统名称（中文）", example = "某某商城")
    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    @Schema(description = "一句话业务描述（可选，仅记录用）")
    private String systemDesc;

    @Schema(description = "只克隆这些模块；为空则全量克隆", example = "[\"商品管理\",\"订单管理\"]")
    private List<String> moduleNames;

    @Schema(description = "范围：new=新建系统，supplement=为已有系统补表", example = "new")
    private String scope = "new";

    @Schema(description = "补表时的目标系统项目 ID（scope=supplement 必填）")
    private Long projectId;

    @Schema(description = "规模（仅透传给前端展示与落库链路）", example = "small")
    private String scale = "small";

    @Schema(description = "部署形态：mono=单体工程 micro=微服务拆分", example = "mono")
    private String deploy = "mono";
}
