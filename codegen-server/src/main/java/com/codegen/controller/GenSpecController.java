package com.codegen.controller;

import com.codegen.common.result.Result;
import com.codegen.entity.GenConvergeReport;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.service.GenConvergeService;
import com.codegen.service.GenSpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * SDD 规格工件链 + Converge 对账 Controller（V1.30 第一期）
 */
@Tag(name = "SDD规格工件链", description = "V1.30 第一期：规格工件版本化 + Converge 规格对账")
@RestController
@RequestMapping("/gen/spec")
public class GenSpecController {

    @Autowired
    private GenSpecService genSpecService;
    @Autowired
    private GenConvergeService genConvergeService;

    @Operation(summary = "工件树（五类工件各取最新版本）")
    @GetMapping("/tree")
    public Result<List<GenSpecArtifact>> tree(@RequestParam Long projectId) {
        return Result.ok(genSpecService.tree(projectId));
    }

    @Operation(summary = "工件详情")
    @GetMapping("/{id}")
    public Result<GenSpecArtifact> detail(@PathVariable Long id) {
        return Result.ok(genSpecService.detail(id));
    }

    @Operation(summary = "工件历史版本（version 倒序）")
    @GetMapping("/history")
    public Result<List<GenSpecArtifact>> history(@RequestParam Long projectId,
                                                 @RequestParam String artifactType) {
        return Result.ok(genSpecService.history(projectId, artifactType));
    }

    @Operation(summary = "保存工件（一律新增版本快照，DRAFT）")
    @PostMapping("/save")
    public Result<GenSpecArtifact> save(@RequestBody GenSpecArtifact artifact) {
        return Result.ok(genSpecService.save(artifact));
    }

    @Operation(summary = "确认工件（DRAFT → CONFIRMED）")
    @PostMapping("/confirm/{id}")
    public Result<GenSpecArtifact> confirm(@PathVariable Long id) {
        return Result.ok(genSpecService.confirm(id));
    }

    @Operation(summary = "初始化项目基本法（已有则返回最新）")
    @PostMapping("/constitution/init")
    public Result<GenSpecArtifact> initConstitution(@RequestParam Long projectId) {
        return Result.ok(genSpecService.initConstitution(projectId));
    }

    @Operation(summary = "执行 Converge 对账（以最新生效 SPEC 为依据）")
    @PostMapping("/converge/run")
    public Result<Map<String, Object>> runConverge(@RequestParam Long projectId) {
        GenConvergeReport report = genConvergeService.run(projectId);
        return Result.ok(Map.of(
                "reportId", report.getId(),
                "status", report.getStatus(),
                "okCount", report.getOkCount(),
                "missingCount", report.getMissingCount(),
                "extraCount", report.getExtraCount(),
                "specVersion", report.getSpecVersion()));
    }

    @Operation(summary = "对账报告列表（时间倒序）")
    @PostMapping("/converge/reports")
    public Result<List<GenConvergeReport>> convergeReports(@RequestParam Long projectId) {
        return Result.ok(genConvergeService.reports(projectId));
    }

    @Operation(summary = "对账报告详情（含明细，MISSING → EXTRA → OK 排序）")
    @PostMapping("/converge/report/{id}")
    public Result<GenConvergeService.ReportDetail> convergeReport(@PathVariable Long id) {
        return Result.ok(genConvergeService.reportDetail(id));
    }
}
