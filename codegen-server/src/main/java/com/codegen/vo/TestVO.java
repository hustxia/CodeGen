package com.codegen.vo;

import com.codegen.entity.TestResult;
import com.codegen.entity.TestRun;

import java.util.List;

/**
 * V9.2 测试中心出参。
 *
 * <p>{@code Env.secretRef} 一律脱敏下发——密钥引用只用于服务端解析，前端不需要看到原值。
 * 执行详情的断言结果用 {@link AssertView} 逐条下发，前端按 {@code pass} 直接染色。
 */
public final class TestVO {

    private TestVO() {
    }

    public record Env(Long id, Long projectId, String name, String baseUrl, String headersJson,
                      String secretRef, Integer isProd, Integer enabled, String remark) {
    }

    public record EnvCheck(boolean reachable, int status, long durationMs, String message) {
    }

    public record Suite(Long id, Long projectId, Long tableId, String name, String type,
                        String source, String moduleName, Integer enabled, String remark, Long caseCount) {
    }

    public record CaseView(Long id, Long suiteId, String name, String source, String method,
                           String path, String assertsJson, Integer orderNo, Integer enabled,
                           String lastResult, java.time.LocalDateTime lastRunTime) {
    }

    public record AssertView(String type, String target, String op, Object expect, Object actual,
                             boolean pass, String message) {
    }

    public record TryResult(int status, long durationMs, String body, List<AssertView> asserts,
                            boolean pass, String error) {
    }

    public record RunDetail(TestRun run, List<TestResult> results) {
    }

    /** 按表生成的结果：新建 / 覆盖 / 跳过（手工改过或被占用） */
    public record GenResult(Long suiteId, int created, int updated, int skipped, List<String> messages) {
    }
}
