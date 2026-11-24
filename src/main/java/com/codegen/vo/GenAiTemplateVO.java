package com.codegen.vo;

import com.codegen.vo.GenPipelineAiVO.TableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V9.7 行业模板：预置行业表结构包（电商/ERP/CRM/CMS）。
 *
 * <p>模板是「固定的东西」的显式资产化：选模块直接克隆表结构，秒级出表、不经过 LLM。
 * 表结构结构与 AI 建模产物同形（{@link TableModel}），走同一条收口/编辑/落库链路。
 */
@Data
@Schema(description = "V9.7 行业模板")
public class GenAiTemplateVO {

    @Schema(description = "模板 key（plan/template 接口入参）", example = "ecommerce")
    private String key;

    @Schema(description = "模板名称", example = "电商商城")
    private String name;

    @Schema(description = "一句话说明", example = "商品、订单、购物车、售后等电商核心域")
    private String desc;

    @Schema(description = "行业分类（前端按此分组/筛选）", example = "电商零售")
    private String category;

    @Schema(description = "模块数（列表接口用）")
    private int moduleCount;

    @Schema(description = "表总数（列表接口用）")
    private int tableCount;

    @Schema(description = "来源：builtin=预置包 / user=用户自定义包", example = "builtin")
    private String source;

    @Schema(description = "模板明细：模块清单（仅 detail 接口返回）")
    private List<Module> modules = new ArrayList<>();

    @Data
    @Schema(description = "行业模板模块")
    public static class Module {

        @Schema(description = "模块名（中文）", example = "商品管理")
        private String name;

        @Schema(description = "该模块的表结构（与 AI 建模产物同形，含完整列定义）")
        private List<TableModel> tables = new ArrayList<>();
    }
}
