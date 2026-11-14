package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestCase;
import com.codegen.entity.TestEnv;
import com.codegen.entity.TestResult;
import com.codegen.entity.TestRun;
import com.codegen.entity.TestSuite;
import com.codegen.mapper.TestCaseMapper;
import com.codegen.mapper.TestEnvMapper;
import com.codegen.mapper.TestResultMapper;
import com.codegen.mapper.TestRunMapper;
import com.codegen.mapper.TestSuiteMapper;
import com.codegen.service.TestRunService;
import com.codegen.support.ApiTestExecutor;
import com.codegen.support.TestAssertEngine;
import com.codegen.support.TestJson;
import com.codegen.support.TestVarContext;
import com.codegen.vo.TestVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V9.2 执行编排实现。
 *
 * <p>执行是同步的：一次 HTTP 请求内串行跑完所选套件的用例。当前规模（单表 6~8 条）
 * 下这比引入异步队列更简单，也避免了「任务丢失」类问题；批次耗时上限由用例数与
 * 单用例 10s 超时共同约束。
 *
 * <p>中断用 {@link AtomicBoolean} 标记，在用例之间检查——已经发出的 HTTP 请求不强行打断，
 * 只保证后续用例不再执行，避免把目标环境打成半截状态。
 */
@Service
public class TestRunServiceImpl extends ServiceImpl<TestRunMapper, TestRun> implements TestRunService {

    private static final Logger log = LoggerFactory.getLogger(TestRunServiceImpl.class);
    private static final int SNAPSHOT_MAX = 64 * 1024;

    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    @Autowired
    private TestEnvMapper envMapper;

    @Autowired
    private TestSuiteMapper suiteMapper;

    @Autowired
    private TestCaseMapper caseMapper;

    @Autowired
    private TestResultMapper resultMapper;

    @Autowired
    private ApiTestExecutor executor;

    @Override
    public TestVO.RunDetail start(TestCenterDTO.RunRequest req) {
        if (req == null || req.getEnvId() == null) {
            throw new BusinessException(400, "请选择执行环境");
        }
        TestEnv env = envMapper.selectById(req.getEnvId());
        if (env == null) {
            throw new BusinessException(400, "环境不存在");
        }
        if (!Integer.valueOf(1).equals(env.getEnabled())) {
            throw new BusinessException(400, "环境已停用");
        }
        List<Long> suiteIds = req.getSuiteIds() == null
                ? List.of()
                : req.getSuiteIds().stream().filter(Objects::nonNull).distinct().toList();
        if (suiteIds.isEmpty()) {
            throw new BusinessException(400, "请选择要执行的套件");
        }

        Long uid = UserContext.getUserId();
        if (uid != null) {
            Long running = baseMapper.selectCount(new LambdaQueryWrapper<TestRun>()
                    .eq(TestRun::getCreateBy, uid)
                    .eq(TestRun::getStatus, "running"));
            if (running != null && running > 0) {
                throw new BusinessException(400, "已有执行中的批次，请等待完成或中断");
            }
        }

        TestRun run = new TestRun();
        run.setProjectId(env.getProjectId());
        run.setEnvId(env.getId());
        run.setTriggerType(req.getTriggerType() == null ? "manual" : req.getTriggerType());
        run.setSuiteIds(suiteIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        run.setStatus("running");
        run.setTotal(0);
        run.setPassed(0);
        run.setFailed(0);
        run.setSkipped(0);
        run.setStartedAt(LocalDateTime.now());
        save(run);

        AtomicBoolean cancel = new AtomicBoolean(false);
        cancelFlags.put(run.getId(), cancel);
        long startNano = System.nanoTime();
        int[] counts = new int[3];
        boolean[] aborted = {false};

        TestVarContext ctx = TestVarContext.create();
        ctx.putEnv("baseUrl", env.getBaseUrl());
        Map<String, String> envHeaders = new LinkedHashMap<>(TestJson.stringMap(env.getHeadersJson()));
        boolean prod = Integer.valueOf(1).equals(env.getIsProd());

        try {
            Map<Long, TestSuite> suiteMap = suiteMapper.selectBatchIds(suiteIds).stream()
                    .collect(Collectors.toMap(TestSuite::getId, Function.identity(), (a, b) -> a));
            for (Long sid : suiteIds) {
                TestSuite suite = suiteMap.get(sid);
                if (suite == null || !Integer.valueOf(1).equals(suite.getEnabled())) {
                    continue;
                }
                List<TestCase> cases = caseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getSuiteId, sid)
                        .eq(TestCase::getEnabled, 1)
                        .orderByAsc(TestCase::getOrderNo)
                        .orderByAsc(TestCase::getId));
                for (TestCase c : cases) {
                    if (cancel.get()) {
                        aborted[0] = true;
                        break;
                    }
                    executeCase(run, env, prod, envHeaders, ctx, c, counts);
                }
                if (aborted[0]) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("批次执行异常 runId={}", run.getId(), e);
            run.setErrorMsg(TestJson.truncate(e.getMessage(), 1000));
        } finally {
            run.setTotal(counts[0] + counts[1] + counts[2]);
            run.setPassed(counts[0]);
            run.setFailed(counts[1]);
            run.setSkipped(counts[2]);
            run.setStatus(aborted[0] || cancel.get() ? "aborted" : (counts[1] > 0 ? "failed" : "success"));
            run.setFinishedAt(LocalDateTime.now());
            run.setDurationMs((System.nanoTime() - startNano) / 1_000_000);
            updateById(run);
            cancelFlags.remove(run.getId());
        }
        return detail(run.getId());
    }

    @Override
    public TestVO.RunDetail detail(Long runId) {
        TestRun run = getById(runId);
        if (run == null) {
            throw new BusinessException(400, "批次不存在");
        }
        List<TestResult> results = resultMapper.selectList(new LambdaQueryWrapper<TestResult>()
                .eq(TestResult::getRunId, runId)
                .orderByAsc(TestResult::getId));
        return new TestVO.RunDetail(run, results);
    }

    @Override
    public void abort(Long runId) {
        TestRun run = getById(runId);
        if (run == null) {
            throw new BusinessException(400, "批次不存在");
        }
        if (!"running".equals(run.getStatus())) {
            return;
        }
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag != null) {
            flag.set(true);
        }
        run.setStatus("aborted");
        run.setFinishedAt(LocalDateTime.now());
        updateById(run);
    }

    /** 执行单条用例并落结果；counts = [passed, failed, skipped] */
    private void executeCase(TestRun run, TestEnv env, boolean prod, Map<String, String> envHeaders,
                             TestVarContext ctx, TestCase c, int[] counts) {
        TestResult tr = new TestResult();
        tr.setRunId(run.getId());
        tr.setCaseId(c.getId());
        tr.setCaseName(c.getName());

        if (prod && !"GET".equalsIgnoreCase(c.getMethod())) {
            tr.setStatus("skip");
            tr.setErrorMsg("生产环境禁止写操作，已拦截");
            tr.setDurationMs(0L);
            resultMapper.insert(tr);
            counts[2]++;
            return;
        }

        Map<String, String> headers = new LinkedHashMap<>(envHeaders);
        headers.replaceAll((k, v) -> v == null ? null : ctx.resolve(v));
        TestJson.stringMap(c.getHeadersJson()).forEach((k, v) -> headers.put(k, ctx.resolve(v)));
        String path = ctx.resolve(c.getPath());
        String body = c.getBodyJson() == null || c.getBodyJson().isBlank() ? null : ctx.resolve(c.getBodyJson());

        ApiTestExecutor.ExecResult r = executor.execute(env.getBaseUrl(), c.getMethod(), path, headers, body);
        tr.setDurationMs(r.durationMs());
        tr.setRequestSnapshot(TestJson.truncate(
                c.getMethod() + " " + path + (body == null ? "" : "\n" + body), 4000));

        String status;
        if (!r.ok()) {
            status = "fail";
            tr.setErrorMsg(TestJson.truncate(r.error(), 1000));
        } else {
            ctx.extract(TestJson.objectMap(c.getExtractJson()), r.body());
            List<TestVO.AssertView> views = TestAssertEngine.evaluate(
                            TestJson.assertList(ctx.resolve(c.getAssertsJson())),
                            r.status(), r.headers(), r.body(), r.durationMs())
                    .stream()
                    .map(o -> new TestVO.AssertView(o.type(), o.target(), o.op(), o.expect(), o.actual(),
                            o.pass(), o.message()))
                    .toList();
            boolean pass = views.isEmpty()
                    ? r.status() >= 200 && r.status() < 300
                    : views.stream().allMatch(TestVO.AssertView::pass);
            status = pass ? "pass" : "fail";
            tr.setResponseSnapshot(TestJson.truncate(r.body(), SNAPSHOT_MAX));
            if (!pass) {
                List<TestVO.AssertView> failedViews = views.stream().filter(v -> !v.pass()).toList();
                tr.setFailedAssertJson(TestJson.write(failedViews));
            }
        }

        tr.setStatus(status);
        resultMapper.insert(tr);
        if ("pass".equals(status)) {
            counts[0]++;
        } else {
            counts[1]++;
        }

        TestCase patch = new TestCase();
        patch.setId(c.getId());
        patch.setLastResult(status);
        patch.setLastRunTime(LocalDateTime.now());
        caseMapper.updateById(patch);
    }
}
