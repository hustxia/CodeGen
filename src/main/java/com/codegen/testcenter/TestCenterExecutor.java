package com.codegen.testcenter;

import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 统一执行器：全局单运行锁 + 单线程串行 + 子进程隔离 + 结构化落库 + JSON 摘要。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li>命令只来自 {@link TcSuite#argv()}，请求体的任何 command/path 都进不来；</li>
 *   <li>失败不短路，跑完所有套件再汇总，一次运行能看到全部问题；</li>
 *   <li>无论成功、失败、超时、取消、异常，run 都必须落到终态，绝不残留 RUNNING。</li>
 * </ol>
 */
@Component
public class TestCenterExecutor {

    private static final Logger log = LoggerFactory.getLogger(TestCenterExecutor.class);
    private static final DateTimeFormatter RUN_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> TRIGGERS = Set.of("MANUAL", "CI", "SCHEDULE", "RERUN");

    private final TcResultStore store;
    private final TcProcessLauncher launcher;
    private final TcProperties props;
    private final ObjectMapper mapper;
    /**
     * 全局单运行标志。刻意不用 ReentrantLock：它在同一线程可重入，
     * 同线程连续两次触发会拿到锁、启动第二个 run（并发 409 契约失效）。
     * CAS 与调用线程无关，才是这里要的语义。
     */
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final AtomicLong runSeq = new AtomicLong();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "test-center-runner");
        t.setDaemon(true);
        return t;
    });
    private volatile long activeRunId = -1;

    public TestCenterExecutor(TcResultStore store, TcProcessLauncher launcher,
                              TcProperties props, ObjectMapper mapper) {
        this.store = store;
        this.launcher = launcher;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * 触发一次运行。已有运行进行中立即抛 409，不会启动第二个子进程。
     * 锁在异步任务真正结束时才释放，而不是在提交返回时释放。
     */
    public TcRun submit(List<String> suiteIds, String trigger, String gitCommit) {
        if (!props.isEnabled()) {
            throw TcException.badRequest("测试中心已停用（app.test-center.enabled=false）");
        }
        List<TcSuite> suites;
        try {
            suites = TcSuite.resolve(suiteIds);
        } catch (IllegalArgumentException e) {
            throw TcException.badRequest(e.getMessage());
        }
        String triggerType = trigger == null || trigger.isBlank() ? "MANUAL" : trigger.trim().toUpperCase();
        if (!TRIGGERS.contains(triggerType)) {
            throw TcException.badRequest("trigger 只能是 MANUAL/CI/SCHEDULE/RERUN");
        }
        if (!busy.compareAndSet(false, true)) {
            throw TcException.conflict("已有运行进行中（runId=" + activeRunId + "），请等待结束或先取消");
        }
        boolean submitted = false;
        try {
            TcRun run = newRun(suiteIds, suites, triggerType, gitCommit);
            store.createRun(run);
            activeRunId = run.getId();
            cancelFlags.put(run.getId(), new AtomicBoolean(false));
            final TcRun snapshot = run;
            worker.submit(() -> {
                try {
                    executeRun(snapshot, suites);
                } finally {
                    cancelFlags.remove(snapshot.getId());
                    activeRunId = -1;
                    busy.set(false);
                }
            });
            submitted = true;
            return run;
        } finally {
            if (!submitted) {
                activeRunId = -1;
                busy.set(false);
            }
        }
    }

    private TcRun newRun(List<String> suiteIds, List<TcSuite> suites, String triggerType, String gitCommit) {
        TcRun run = new TcRun();
        run.setRunNo("TC" + LocalDateTime.now().format(RUN_DAY)
                + String.format("-%03d", runSeq.incrementAndGet() % 1000)
                + String.format("%02d", System.currentTimeMillis() % 100));
        run.setTriggerType(triggerType);
        run.setSuiteIds(suites.stream().map(TcSuite::getId).collect(Collectors.joining(",")));
        run.setStatus(TcStatus.QUEUED);
        run.setTotal(0);
        run.setPassed(0);
        run.setFailed(0);
        run.setSkipped(0);
        run.setGitCommit(gitCommit == null ? null : gitCommit.trim());
        run.setWorkDir(props.workDirOf(run.getRunNo()).toString());
        // suiteIds 原始值只用于上面的顺序归一化，不参与命令构造
        if (suiteIds == null) {
            throw TcException.badRequest("suiteIds 不能为空");
        }
        return run;
    }

    /**
     * 串行执行全部套件。包可见：单测用假 launcher 直接驱动状态机，不启线程、不连库。
     */
    TcRun executeRun(TcRun run, List<TcSuite> suites) {
        long start = System.currentTimeMillis();
        Path workDir = props.workDirOf(run.getRunNo());
        TcSummary summary = new TcSummary();
        summary.setRunNo(run.getRunNo());
        summary.setGitCommit(run.getGitCommit());
        Map<String, String> finished = new java.util.HashMap<>();
        try {
            Files.createDirectories(workDir);
            run.setStatus(TcStatus.RUNNING);
            store.updateRun(run);

            for (TcSuite suite : suites) {
                TcCaseResult caseResult = runOne(run, suite, workDir, finished);
                finished.put(suite.getId(), caseResult.getStatus());
                store.saveCase(caseResult);
                saveLogArtifact(run, caseResult);

                TcSummary.Case sc = new TcSummary.Case();
                sc.setSuiteId(suite.getId());
                sc.setStatus(caseResult.getStatus());
                sc.setDurationMs(caseResult.getDurationMs() == null ? 0 : caseResult.getDurationMs());
                sc.setLogPath(caseResult.getLogPath());
                try {
                    sc.setMetrics(caseResult.getMetricsJson() == null
                            ? Map.of()
                            : mapper.readValue(caseResult.getMetricsJson(), Map.class));
                } catch (Exception ignored) {
                    sc.setMetrics(Map.of());
                }
                summary.getCases().add(sc);
            }

            summary.setDurationMs(System.currentTimeMillis() - start);
            summary.recount();
            fillRun(run, summary);
            if (!TcStatus.PASSED.equals(summary.getStatus())) {
                // 列表页不用点开详情就能看到本次为什么没全绿
                List<String> notPassed = summary.getCases().stream()
                        .filter(c -> !TcStatus.PASSED.equals(c.getStatus()))
                        .map(c -> c.getSuiteId() + "=" + c.getStatus())
                        .collect(Collectors.toList());
                run.setErrorMsg("未全绿套件: " + String.join(", ", notPassed));
            }
            run.setSummaryJson(mapper.writeValueAsString(summary));
            store.updateRun(run);
            return run;
        } catch (Exception e) {
            log.error("运行 {} 执行器异常", run.getRunNo(), e);
            // 执行器自身出错也必须收敛到终态，不能留 RUNNING
            summary.setDurationMs(System.currentTimeMillis() - start);
            if (summary.getCases().isEmpty()) {
                summary.getCases().addAll(suites.stream().map(s -> {
                    TcSummary.Case c = new TcSummary.Case();
                    c.setSuiteId(s.getId());
                    c.setStatus(TcStatus.FAILED);
                    return c;
                }).collect(Collectors.toList()));
            }
            summary.recount();
            summary.setStatus(TcStatus.FAILED);
            fillRun(run, summary);
            run.setErrorMsg("执行器异常: " + e.getMessage());
            try {
                run.setSummaryJson(mapper.writeValueAsString(summary));
            } catch (Exception ignored) {
                // 摘要序列化失败不影响状态落库
            }
            store.updateRun(run);
            return run;
        }
    }

    private TcCaseResult runOne(TcRun run, TcSuite suite, Path workDir, Map<String, String> finished) {
        LocalDateTime started = LocalDateTime.now().withNano(0);
        Path logFile = workDir.resolve(suite.getId() + ".log");
        TcCaseResult result = new TcCaseResult();
        result.setRunId(run.getId());
        result.setSuiteId(suite.getId());
        result.setStartedAt(started);
        result.setLogPath(TcPathGuard.relativeLogPath(workDir, logFile));

        AtomicBoolean cancelFlag = cancelFlags.get(run.getId());
        String blockedBy = blockedBy(suite, finished);
        TcProcessLauncher.TcProcessResult processResult;
        if (blockedBy != null) {
            processResult = new TcProcessLauncher.TcProcessResult(TcStatus.SKIPPED, 0, 0,
                    "依赖套件 " + blockedBy + " 未通过或未选中", Map.of("status", "SKIPPED", "reason", blockedBy));
            appendLog(logFile, "SKIPPED: " + processResult.errorExcerpt());
        } else if (cancelFlag != null && cancelFlag.get()) {
            processResult = TcProcessLauncher.TcProcessResult.failed(TcStatus.CANCELLED, -1, 0, "运行被取消");
            appendLog(logFile, "CANCELLED");
        } else {
            TcProcessLauncher.TcProcessSpec spec = new TcProcessLauncher.TcProcessSpec(
                    suite, workDir, logFile,
                    cancelFlag == null ? () -> false : () -> cancelFlag.get(), Map.of());
            try {
                processResult = launcher.run(spec);
            } catch (Exception e) {
                String excerpt = TcMetrics.tail(String.valueOf(e), 20);
                processResult = TcProcessLauncher.TcProcessResult.failed(TcStatus.FAILED, -1, 0, excerpt);
                appendLog(logFile, "启动子进程失败: " + e);
            }
        }

        result.setStatus(processResult.status());
        result.setExitCode(processResult.exitCode());
        result.setDurationMs(processResult.durationMs());
        result.setFinishedAt(LocalDateTime.now().withNano(0));
        try {
            result.setMetricsJson(mapper.writeValueAsString(processResult.metrics()));
        } catch (Exception e) {
            result.setMetricsJson("{}");
        }
        // SKIPPED 也要留下原因，否则页面只显示「跳过」而不知道是缺 MySQL 还是缺依赖
        if (!TcStatus.PASSED.equals(processResult.status())) {
            String excerpt = processResult.errorExcerpt() == null ? "" : processResult.errorExcerpt();
            result.setErrorExcerpt(TcMetrics.tail(excerpt, 20));
        }
        ensureLog(logFile, processResult);
        return result;
    }

    /** 依赖未选中或前置失败 → SKIPPED，避免级联误报（如 S5 依赖 S1 的 gen-matrix）。 */
    private String blockedBy(TcSuite suite, Map<String, String> finished) {
        for (String dep : suite.getDependsOn()) {
            String status = finished.get(dep);
            if (status == null || !TcStatus.isSuccess(status)) {
                return dep;
            }
        }
        return null;
    }

    private void saveLogArtifact(TcRun run, TcCaseResult caseResult) {
        String relative = caseResult.getLogPath();
        if (relative == null) {
            return;
        }
        Path file = props.runsRoot().resolve(relative).toAbsolutePath().normalize();
        TcArtifact artifact = new TcArtifact();
        artifact.setRunId(run.getId());
        artifact.setSuiteId(caseResult.getSuiteId());
        artifact.setArtifactType("LOG");
        artifact.setFileName(file.getFileName().toString());
        artifact.setFilePath(file.toString());
        try {
            artifact.setFileSize(Files.exists(file) ? Files.size(file) : 0L);
        } catch (Exception ignored) {
            artifact.setFileSize(0L);
        }
        artifact.setMimeType("text/plain");
        store.saveArtifact(artifact);
    }

    private void appendLog(Path logFile, String text) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, text + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志写失败不改变套件结论
        }
    }

    private void ensureLog(Path logFile, TcProcessLauncher.TcProcessResult result) {
        try {
            if (!Files.exists(logFile)) {
                appendLog(logFile, result.status() + " (无输出)");
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private void fillRun(TcRun run, TcSummary summary) {
        run.setStatus(summary.getStatus());
        run.setTotal(summary.getTotals().getTotal());
        run.setPassed(summary.getTotals().getPassed());
        run.setFailed(summary.getTotals().getFailed());
        run.setSkipped(summary.getTotals().getSkipped());
        run.setDurationMs(summary.getDurationMs());
    }

    /** 取消：只置标志位，由执行器/启动器在安全点 kill 子进程，状态由执行器统一收敛。 */
    public boolean cancel(long runId) {
        TcRun run = store.findRun(runId);
        if (run == null) {
            throw TcException.notFound("运行不存在: " + runId);
        }
        if (!TcStatus.isRunning(run.getStatus())) {
            return false;
        }
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag != null) {
            flag.set(true);
        }
        return true;
    }

    /** 服务重启兜底：把残留的 QUEUED/RUNNING 收敛为 TIMEOUT。 */
    public int recoverStaleRuns() {
        int count = 0;
        for (TcRun run : store.runningRuns()) {
            run.setStatus(TcStatus.TIMEOUT);
            run.setErrorMsg("服务重启或进程退出，残留运行自动收敛为 TIMEOUT");
            store.updateRun(run);
            count++;
        }
        return count;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public long getActiveRunId() {
        return activeRunId;
    }

    /** 供 Service 拼「仅失败项」重跑列表 */
    public static List<String> failedSuiteIds(List<TcCaseResult> cases) {
        List<String> out = new ArrayList<>();
        for (TcCaseResult c : cases) {
            if (!TcStatus.isSuccess(c.getStatus())) {
                out.add(c.getSuiteId());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(out));
    }
}
