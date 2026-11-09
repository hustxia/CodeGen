package com.codegen.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codegen.common.result.Result;
import com.codegen.dto.GenPipelineAiModelDTO;
import com.codegen.dto.GenPipelineCreateDTO;
import com.codegen.dto.GenPipelinePageDTO;
import com.codegen.dto.GenPipelinePrepareDTO;
import com.codegen.service.AiModelService;
import com.codegen.service.GenPipelineService;
import com.codegen.vo.GenPipelineAiVO;
import com.codegen.vo.GenPipelineLintVO;
import com.codegen.vo.GenPipelinePrepareVO;
import com.codegen.vo.GenPipelineVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * V5.0「一句话建系统」流水线接口（9 个）。
 */
@Tag(name = "建系统流水线", description = "V5.0 一句话建系统")
@RestController
@RequestMapping("/gen/pipeline")
public class GenPipelineController {

    @Autowired
    private GenPipelineService pipelineService;
    @Autowired
    private AiModelService aiModelService;
    @Autowired
    private com.codegen.config.GenPipelineProperties pipelineProps;

    @Operation(summary = "向导配置：AI 建模是否可用（未配 LLM key 时前端直接禁用 AI 选项）")
    @GetMapping("/config")
    public Result<java.util.Map<String, Object>> config() {
        return Result.ok(java.util.Map.of("aiEnabled", pipelineProps.getAi().isEnabled()));
    }

    @Operation(summary = "AI 领域建模：名称+一句话描述 → 候选表结构（moduleName 非空时只设计该模块的表）")
    @PostMapping("/ai-model")
    public Result<GenPipelineAiVO> aiModel(@RequestBody @Valid GenPipelineAiModelDTO dto) {
        return Result.ok(aiModelService.model(dto.getSystemName(), dto.getSystemDesc(), dto.getModuleName()));
    }

    @Operation(summary = "AI 模块拆分：系统 → 业务模块清单（完整系统模式第一步）")
    @PostMapping("/ai-modules")
    public Result<com.codegen.vo.GenPipelineAiModulesVO> aiModules(@RequestBody @Valid GenPipelineAiModelDTO dto) {
        return Result.ok(aiModelService.modules(dto.getSystemName(), dto.getSystemDesc()));
    }

    @Operation(summary = "创建流水线任务（建模结果一并落库）")
    @PostMapping
    public Result<GenPipelineVO> create(@RequestBody @Valid GenPipelineCreateDTO dto) {
        return Result.ok(pipelineService.create(dto));
    }

    @Operation(summary = "建库建表 + 建系统台账 + 导入表配置")
    @PostMapping("/{id}/prepare-db")
    public Result<GenPipelinePrepareVO> prepareDb(@PathVariable("id") Long id,
                                                  @RequestBody @Valid GenPipelinePrepareDTO dto) {
        return Result.ok(pipelineService.prepareDb(id, dto));
    }

    @Operation(summary = "启动流水线（异步，进度走 SSE）")
    @PostMapping("/{id}/run")
    public Result<GenPipelineVO> run(@PathVariable("id") Long id) {
        return Result.ok(pipelineService.run(id));
    }

    @Operation(summary = "任务详情（头 + 全部步骤）")
    @GetMapping("/{id}")
    public Result<GenPipelineVO> detail(@PathVariable("id") Long id) {
        return Result.ok(pipelineService.detail(id));
    }

    @Operation(summary = "任务分页")
    @PostMapping("/page")
    public Result<IPage<GenPipelineVO>> page(@RequestBody GenPipelinePageDTO dto) {
        return Result.ok(pipelineService.page(dto));
    }

    @Operation(summary = "进度推送（SSE，text/event-stream）")
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") Long id) {
        return pipelineService.stream(id);
    }

    @Operation(summary = "停止：杀双进程并置 stopped")
    @PostMapping("/{id}/stop")
    public Result<GenPipelineVO> stop(@PathVariable("id") Long id) {
        return Result.ok(pipelineService.stop(id));
    }

    @Operation(summary = "生成前置 lint：目标库审计列检查，缺列给补齐 SQL（只读 dry-run）")
    @PostMapping("/lint-db")
    public Result<GenPipelineLintVO> lint(@RequestBody com.codegen.dto.GenPipelineLintDTO dto) {
        return Result.ok(pipelineService.lint(dto.getDbName()));
    }

    @Operation(summary = "lint 一键补齐（V5.1）：逐表 ALTER 补缺审计列，幂等，执行后复检")
    @PostMapping("/{id}/lint/fix")
    public Result<com.codegen.vo.GenPipelineLintFixVO> fix(@PathVariable("id") Long id) {
        return Result.ok(pipelineService.fix(id));
    }

    @Operation(summary = "V5.2 数据初始化：存量系统补跑 seed（只补台账中 0 行的表，幂等）")
    @PostMapping("/{id}/seed")
    public Result<com.codegen.vo.GenSeedVO> seed(@PathVariable("id") Long id) {
        return Result.ok(pipelineService.seedExisting(id));
    }

    @Autowired
    private com.codegen.service.AiRecommendService aiRecommendService;

    @Operation(summary = "V7.0 AI 建议预览：与现值 diff（只读，零写库）")
    @GetMapping("/{id}/ai-recommend/preview")
    public Result<com.codegen.vo.GenAiRecommendPreviewVO> aiRecommendPreview(@PathVariable("id") Long id) {
        return Result.ok(aiRecommendService.preview(id));
    }

    @Operation(summary = "V7.0 AI 建议应用：勾选项写回 gen_table/gen_table_column，建议值以服务端产物为准")
    @PostMapping("/{id}/ai-recommend/apply")
    public Result<Integer> aiRecommendApply(@PathVariable("id") Long id,
                                            @RequestBody com.codegen.dto.GenAiRecommendApplyDTO dto) {
        return Result.ok(aiRecommendService.apply(id, dto));
    }
}
