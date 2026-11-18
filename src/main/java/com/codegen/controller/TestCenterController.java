package com.codegen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.common.log.Log;
import com.codegen.common.result.Result;
import com.codegen.dto.TcRunQuery;
import com.codegen.dto.TcRunRequest;
import com.codegen.entity.TcRun;
import com.codegen.testcenter.TcException;
import com.codegen.testcenter.TestCenterService;
import com.codegen.vo.TcRunDetailVO;
import com.codegen.vo.TcSuiteVO;
import com.codegen.vo.TcTrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * V9.2 测试中心 REST API。
 *
 * <p>写操作（触发/取消/重跑）都带 {@link Log}，由 LogAspect 统一落 sys_oper_log；
 * 拒绝自定义命令的请求同样会留痕，便于审计「谁尝试绕过白名单」。
 */
@Tag(name = "测试中心", description = "V9.2 统一验证入口")
@RestController
@RequestMapping("/tc")
public class TestCenterController {

    private final TestCenterService service;

    public TestCenterController(TestCenterService service) {
        this.service = service;
    }

    @Operation(summary = "套件注册表（含默认勾选与上次结果）")
    @GetMapping("/suites")
    public Result<List<TcSuiteVO>> suites() {
        return Result.ok(service.suites());
    }

    @Operation(summary = "总览：最近一次运行 / 通过率 / 是否运行中")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(service.overview());
    }

    @Operation(summary = "触发运行；已有运行进行中返回 409")
    @Log(title = "测试中心", type = "OTHER", saveParam = true)
    @PostMapping("/runs")
    public Result<TcRun> trigger(@RequestBody(required = false) TcRunRequest request) {
        return Result.ok(service.trigger(request));
    }

    @Operation(summary = "运行历史分页")
    @GetMapping("/runs")
    public Result<Page<TcRun>> runs(TcRunQuery query) {
        return Result.ok(service.runs(query));
    }

    @Operation(summary = "运行详情（用例 + 产物 + 摘要）")
    @GetMapping("/runs/{id}")
    public Result<TcRunDetailVO> detail(@PathVariable("id") long id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "日志尾部（前端 2s 轮询，不引 WebSocket）")
    @GetMapping("/runs/{id}/log")
    public Result<String> log(@PathVariable("id") long id,
                              @RequestParam(value = "caseId", required = false) Long caseId,
                              @RequestParam(value = "tail", required = false, defaultValue = "200") int tail) {
        return Result.ok(service.log(id, caseId, tail));
    }

    @Operation(summary = "取消运行")
    @Log(title = "测试中心", type = "OTHER", saveParam = true)
    @PostMapping("/runs/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable("id") long id) {
        return Result.ok(service.cancel(id));
    }

    @Operation(summary = "单个失败套件重跑")
    @Log(title = "测试中心", type = "OTHER", saveParam = true)
    @PostMapping("/cases/{id}/rerun")
    public Result<TcRun> rerun(@PathVariable("id") long id) {
        return Result.ok(service.rerun(id));
    }

    @Operation(summary = "仅失败项重跑")
    @Log(title = "测试中心", type = "OTHER", saveParam = true)
    @PostMapping("/runs/{id}/rerun-failed")
    public Result<TcRun> rerunFailed(@PathVariable("id") long id) {
        return Result.ok(service.rerunFailed(id));
    }

    @Operation(summary = "导出报告（json=摘要契约，html=自包含报告）")
    @GetMapping("/runs/{id}/report")
    public Result<String> report(@PathVariable("id") long id,
                                 @RequestParam(value = "format", required = false, defaultValue = "json") String format) {
        return Result.ok(service.report(id, format));
    }

    @Operation(summary = "产物下载（路径白名单校验）")
    @GetMapping("/artifacts/{id}")
    public ResponseEntity<byte[]> artifact(@PathVariable("id") long id) {
        TestCenterService.TcArtifactFile file = service.artifact(id);
        String name = new String(file.fileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(file.bytes());
    }

    @Operation(summary = "通过率与耗时趋势")
    @GetMapping("/trends")
    public Result<List<TcTrendVO>> trends(@RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        return Result.ok(service.trends(limit));
    }

    /** 冲突/禁止/参数错误带真实 HTTP 语义码，同时响应体仍是平台统一的 Result。 */
    @ExceptionHandler(TcException.class)
    public ResponseEntity<Result<Void>> handleTcException(TcException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                .body(Result.fail(ex.getCode(), ex.getMessage()));
    }
}
