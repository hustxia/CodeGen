package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.dto.ClarifyAnswerDTO;
import com.codegen.dto.ClarifyConfirmDTO;
import com.codegen.dto.ClarifyStartDTO;
import com.codegen.dto.GuardrailEvaluateDTO;
import com.codegen.entity.GenClarifySession;
import com.codegen.entity.GenGuardrailRule;
import com.codegen.service.GuardrailService;
import com.codegen.vo.ClarifyConfirmVO;
import com.codegen.vo.ClarifyStepVO;
import com.codegen.vo.GuardrailEvalVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * V9.25 护栏澄清向导：规则库 + 一次一问 + 命中解释 + 确认写 SPEC。
 */
@Tag(name = "领域护栏与澄清向导", description = "M2.1：28 条行业规则 + 一次一问 + 非目标写入 SPEC")
@RestController
@RequestMapping("/gen/guardrail")
public class GuardrailController {

    @Autowired
    private GuardrailService guardrailService;

    @Operation(summary = "规则列表（行业/类别筛选）")
    @GetMapping("/rules")
    public Result<List<GenGuardrailRule>> rules(
            @RequestParam(value = "industry", required = false) String industry,
            @RequestParam(value = "category", required = false) String category) {
        return Result.ok(guardrailService.rules(industry, category));
    }

    @Operation(summary = "规则计数（总数/按行业/按类别）")
    @GetMapping("/rules/count")
    public Result<Map<String, Object>> ruleCount() {
        return Result.ok(guardrailService.ruleCount());
    }

    @Operation(summary = "独立护栏检测（不建会话）")
    @PostMapping("/evaluate")
    public Result<GuardrailEvalVO> evaluate(@RequestBody @Valid GuardrailEvaluateDTO dto) {
        return Result.ok(guardrailService.evaluate(dto));
    }

    @Operation(summary = "开始澄清：建会话并返回第 1 问")
    @PostMapping("/clarify/start")
    public Result<ClarifyStepVO> start(@RequestBody @Valid ClarifyStartDTO dto) {
        return Result.ok(guardrailService.start(dto));
    }

    @Operation(summary = "答复当前问题：返回下一问或结束")
    @PostMapping("/clarify/answer")
    public Result<ClarifyStepVO> answer(@RequestBody @Valid ClarifyAnswerDTO dto) {
        return Result.ok(guardrailService.answer(dto));
    }

    @Operation(summary = "按会话上下文重跑护栏检测")
    @PostMapping("/clarify/evaluate/{id}")
    public Result<GuardrailEvalVO> evaluateSession(@PathVariable("id") Long id) {
        return Result.ok(guardrailService.evaluateSession(id));
    }

    @Operation(summary = "确认澄清结果：写 SPEC 草案（幂等）")
    @PostMapping("/clarify/confirm/{id}")
    public Result<ClarifyConfirmVO> confirm(@PathVariable("id") Long id,
                                            @RequestBody(required = false) ClarifyConfirmDTO dto) {
        return Result.ok(guardrailService.confirm(id, dto));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/clarify/{id}")
    public Result<GenClarifySession> session(@PathVariable("id") Long id) {
        return Result.ok(guardrailService.session(id));
    }

    @Operation(summary = "会话当前进度与下一问（刷新页面后恢复向导）")
    @GetMapping("/clarify/step/{id}")
    public Result<ClarifyStepVO> step(@PathVariable("id") Long id) {
        return Result.ok(guardrailService.step(id));
    }

    @Operation(summary = "项目会话列表")
    @PostMapping("/clarify/sessions")
    public Result<List<GenClarifySession>> sessions(@RequestBody GenClarifySession query) {
        return Result.ok(guardrailService.sessions(query.getProjectId()));
    }
}
