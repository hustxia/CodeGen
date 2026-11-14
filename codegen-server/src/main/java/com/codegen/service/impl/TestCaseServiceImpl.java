package com.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.common.exception.BusinessException;
import com.codegen.entity.TestEnv;
import com.codegen.entity.TestCase;
import com.codegen.mapper.TestCaseMapper;
import com.codegen.service.TestCaseService;
import com.codegen.service.TestEnvService;
import com.codegen.support.ApiTestExecutor;
import com.codegen.support.TestAssertEngine;
import com.codegen.support.TestJson;
import com.codegen.support.TestVarContext;
import com.codegen.vo.TestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V9.2 用例试运行：一次内存执行，不落批次、不更新 lastResult。
 *
 * <p>「试运行」是编辑页的即时反馈，写库行为会干扰历史通过率，
 * 所以结果只回给前端，绝不持久化。
 */
@Service
public class TestCaseServiceImpl extends ServiceImpl<TestCaseMapper, TestCase> implements TestCaseService {

    @Autowired
    private TestEnvService envService;

    @Autowired
    private ApiTestExecutor executor;

    @Override
    public TestVO.TryResult tryRun(Long caseId, Long envId) {
        TestCase c = getById(caseId);
        if (c == null) {
            throw new BusinessException(400, "用例不存在");
        }
        TestEnv env = envId == null ? null : envService.getById(envId);
        if (env == null) {
            throw new BusinessException(400, "环境不存在，请先选择环境");
        }
        if (Integer.valueOf(1).equals(env.getIsProd()) && !"GET".equalsIgnoreCase(c.getMethod())) {
            return new TestVO.TryResult(-1, 0, null, List.of(), false, "生产环境禁止写操作，已拦截");
        }

        TestVarContext ctx = TestVarContext.create();
        ctx.putEnv("baseUrl", env.getBaseUrl());
        Map<String, String> headers = new LinkedHashMap<>(TestJson.stringMap(env.getHeadersJson()));
        headers.replaceAll((k, v) -> v == null ? null : ctx.resolve(v));
        TestJson.stringMap(c.getHeadersJson()).forEach((k, v) -> headers.put(k, ctx.resolve(v)));

        String path = ctx.resolve(c.getPath());
        String body = c.getBodyJson() == null || c.getBodyJson().isBlank() ? null : ctx.resolve(c.getBodyJson());

        ApiTestExecutor.ExecResult r = executor.execute(env.getBaseUrl(), c.getMethod(), path, headers, body);
        if (!r.ok()) {
            return new TestVO.TryResult(-1, r.durationMs(), null, List.of(), false, r.error());
        }

        ctx.extract(TestJson.objectMap(c.getExtractJson()), r.body());
        List<TestVO.AssertView> views = evaluate(c.getAssertsJson(), ctx, r);
        boolean pass = views.isEmpty()
                ? r.status() >= 200 && r.status() < 300
                : views.stream().allMatch(TestVO.AssertView::pass);
        return new TestVO.TryResult(r.status(), r.durationMs(), TestJson.truncate(r.body(), 4000), views, pass, null);
    }

    private static List<TestVO.AssertView> evaluate(String assertsJson, TestVarContext ctx,
                                                    ApiTestExecutor.ExecResult r) {
        var outcomes = TestAssertEngine.evaluate(TestJson.assertList(ctx.resolve(assertsJson)),
                r.status(), r.headers(), r.body(), r.durationMs());
        return outcomes.stream()
                .map(o -> new TestVO.AssertView(o.type(), o.target(), o.op(), o.expect(), o.actual(),
                        o.pass(), o.message()))
                .toList();
    }
}
