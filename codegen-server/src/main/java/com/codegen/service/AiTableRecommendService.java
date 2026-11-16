package com.codegen.service;

import com.codegen.dto.GenAiBuildConfirmDTO;
import com.codegen.dto.GenAiBuildPlanDTO;
import com.codegen.dto.GenPipelineDdlPreviewDTO;
import com.codegen.vo.GenAiBuildConfirmVO;
import com.codegen.vo.GenAiBuildPlanVO;
import com.codegen.vo.GenPipelineDdlPreviewVO;

/**
 * V9.5「AI 推荐建表 + 人工确认」编排服务。
 *
 * <p>纯新增模块：内部注入并调用 V9.4 的 {@link AiModelService} 与 {@link GenPipelineService}，
 * 不继承、不改它们（AC-01）。新增能力：按规模分批建模、关系视图（单表/主从/树形判定）、
 * 微服务拆分落库。
 */
public interface AiTableRecommendService {

    /** AI 推荐表设计：返回扁平表结构 + 模块映射 + 关系视图 */
    GenAiBuildPlanVO plan(GenAiBuildPlanDTO dto);

    /** 同源 DDL 预览：委托 V9.4 的 previewDdl（不写库） */
    GenPipelineDdlPreviewVO ddlPreview(GenPipelineDdlPreviewDTO dto);

    /** 确认落库/建库：draft=create；build=create+prepareDb；micro=逐模块 create/prepareDb */
    GenAiBuildConfirmVO confirm(GenAiBuildConfirmDTO dto);

    /** V9.7 行业模板清单（key/name/desc/模块数/表数，不含表结构明细） */
    java.util.List<com.codegen.vo.GenAiTemplateVO> templates();

    /** V9.7 行业模板建表：克隆模板表结构 → 走与 AI 推荐相同的收口（去重/差集/关系视图） */
    GenAiBuildPlanVO planFromTemplate(com.codegen.dto.GenAiTemplatePlanDTO dto);
}
