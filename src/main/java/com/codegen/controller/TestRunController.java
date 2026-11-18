package com.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.result.Result;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestRun;
import com.codegen.service.TestRunService;
import com.codegen.support.TestReportRenderer;
import com.codegen.vo.TestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V9.2 执行批次接口。
 *
 * <p>发起执行是同步的，返回时批次已结束，前端拿到的就是完整报告；
 * 若后续用例量增长，再改为异步 + 轮询，接口形态不变（仍返回 RunDetail）。
 */
@Tag(name = "V9.2 测试执行", description = "发起执行、查看报告、中断")
@RestController
@RequestMapping("/test/run")
public class TestRunController {

    @Autowired
    private TestRunService runService;

    @Operation(summary = "发起执行")
    @PostMapping
    public Result<TestVO.RunDetail> start(@RequestBody TestCenterDTO.RunRequest req) {
        return Result.ok(runService.start(req));
    }

    @Operation(summary = "分页查询执行批次")
    @PostMapping("/page")
    public Result<Page<TestRun>> page(@RequestBody TestCenterDTO.RunPage dto) {
        Page<TestRun> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<TestRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getProjectId() != null, TestRun::getProjectId, dto.getProjectId());
        wrapper.eq(StringUtils.hasText(dto.getStatus()), TestRun::getStatus, dto.getStatus());
        wrapper.orderByDesc(TestRun::getStartedAt);
        runService.page(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "批次详情（含用例结果）")
    @GetMapping("/{id}")
    public Result<TestVO.RunDetail> detail(@PathVariable Long id) {
        return Result.ok(runService.detail(id));
    }

    @Operation(summary = "中断批次")
    @PostMapping("/{id}/abort")
    public Result<Void> abort(@PathVariable Long id) {
        runService.abort(id);
        return Result.ok();
    }

    @Operation(summary = "导出报告：format=junit(默认) 或 html")
    @GetMapping("/{id}/report")
    public ResponseEntity<String> report(@PathVariable Long id,
                                        @RequestParam(defaultValue = "junit") String format) {
        TestVO.RunDetail detail = runService.detail(id);
        String body;
        String mediaType;
        if ("html".equalsIgnoreCase(format)) {
            body = TestReportRenderer.toHtml(detail.run(), detail.results());
            mediaType = "text/html;charset=UTF-8";
        } else {
            body = TestReportRenderer.toJUnitXml(detail.run(), detail.results());
            mediaType = "application/xml;charset=UTF-8";
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mediaType)).body(body);
    }
}
