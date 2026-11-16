package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.config.GenPipelineProperties;
import com.codegen.dto.GenAiBuildConfirmDTO;
import com.codegen.dto.GenAiBuildPlanDTO;
import com.codegen.dto.GenPipelineDdlPreviewDTO;
import com.codegen.service.AiBuildJobService;
import com.codegen.service.AiTableRecommendService;
import com.codegen.vo.GenAiBuildConfirmVO;
import com.codegen.vo.GenAiBuildJobVO;
import com.codegen.vo.GenAiBuildPlanVO;
import com.codegen.vo.GenPipelineDdlPreviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V9.5「AI 推荐建表 + 人工确认」独立入口。
 *
 * <p>与 V9.4 的 {@code GenPipelineController} 完全独立，新增文件、不改动 V9.4 任何类（AC-01）。
 * 内部复用 V9.4 的 {@code AiModelService} 与 {@code GenPipelineService}。
 */
@Tag(name = "V9.5 AI 建表推荐", description = "AI 推荐建表 + 人工确认（独立模块）")
@RestController
@RequestMapping("/gen/ai-build")
public class GenAiBuildController {

    @Autowired
    private AiTableRecommendService aiBuildService;
    @Autowired
    private AiBuildJobService jobService;
    @Autowired
    private GenPipelineProperties pipelineProps;

    @Operation(summary = "AI 推荐表设计：规模分批建模 + 关系视图（单表/主从/树形）；只读，零写库")
    @PostMapping("/plan")
    public Result<GenAiBuildPlanVO> plan(@RequestBody @Valid GenAiBuildPlanDTO dto) {
        return Result.ok(aiBuildService.plan(dto));
    }

    @Operation(summary = "同源 DDL 预览：委托 V9.4 previewDdl（与建库执行共用 buildDdl，不写库）")
    @PostMapping("/ddl-preview")
    public Result<GenPipelineDdlPreviewVO> ddlPreview(@RequestBody @Valid GenPipelineDdlPreviewDTO dto) {
        return Result.ok(aiBuildService.ddlPreview(dto));
    }

    @Operation(summary = "确认落库/建库：draft=落草稿 build=建库；micro=逐模块微服务拆分建库")
    @PostMapping("/confirm")
    public Result<GenAiBuildConfirmVO> confirm(@RequestBody @Valid GenAiBuildConfirmDTO dto) {
        return Result.ok(aiBuildService.confirm(dto));
    }

    @Operation(summary = "V9.6 提交分批建模任务（大型/超大型）：拆模块→每模块一批→多 worker 并发")
    @PostMapping("/plan/job")
    public Result<GenAiBuildJobVO> submitPlanJob(@RequestBody @Valid GenAiBuildPlanDTO dto) {
        return Result.ok(jobService.submitPlan(dto));
    }

    @Operation(summary = "V9.6 查询分批任务进度（前端轮询）")
    @GetMapping("/job/{id}")
    public Result<GenAiBuildJobVO> jobStatus(@PathVariable("id") Long id) {
        return Result.ok(jobService.status(id));
    }

    @Operation(summary = "V9.6 取分批任务结果（未完成返回业务错误）")
    @GetMapping("/job/{id}/result")
    public Result<GenAiBuildPlanVO> jobResult(@PathVariable("id") Long id) {
        return Result.ok(jobService.result(id));
    }

    @Operation(summary = "V9.6 取消分批任务")
    @PostMapping("/job/{id}/cancel")
    public Result<Void> cancelJob(@PathVariable("id") Long id) {
        jobService.cancel(id);
        return Result.ok(null);
    }

    @Operation(summary = "V9.7 行业模板清单（电商/ERP/CRM/CMS，秒级克隆表结构）")
    @GetMapping("/templates")
    public Result<java.util.List<com.codegen.vo.GenAiTemplateVO>> templates() {
        return Result.ok(aiBuildService.templates());
    }

    @Operation(summary = "V9.7 行业模板建表：克隆模板表结构（不调 LLM），走与 AI 相同的收口/编辑/落库链路")
    @PostMapping("/plan/template")
    public Result<GenAiBuildPlanVO> planFromTemplate(@RequestBody @Valid com.codegen.dto.GenAiTemplatePlanDTO dto) {
        return Result.ok(aiBuildService.planFromTemplate(dto));
    }

    @Operation(summary = "V9.5 配置：AI 建模是否可用（未配 LLM key 时前端禁用 AI 推荐）")
    @PostMapping("/config")
    public Result<java.util.Map<String, Object>> config() {
        return Result.ok(java.util.Map.of("aiEnabled", pipelineProps.getAi().isEnabled()));
    }
}
