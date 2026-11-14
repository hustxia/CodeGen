package com.codegen.testcenter;

import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 执行器状态机：失败不短路、跳过不染红、依赖阻断、异常收敛、单运行锁。 */
class TestCenterExecutorTest {

    @TempDir
    Path temp;

    private final ObjectMapper mapper = new ObjectMapper();

    private static class Fixture {
        final InMemoryTcResultStore store = new InMemoryTcResultStore();
        final TcProperties props;
        final TestCenterExecutor executor;

        Fixture(Path temp, TcProcessLauncher launcher) {
            props = new TcProperties();
            props.setWorkdir(temp.resolve("runs").toString());
            executor = new TestCenterExecutor(store, launcher, props, new ObjectMapper());
        }

        TcRun seed(String runNo, List<String> ids) {
            TcRun run = new TcRun();
            run.setRunNo(runNo);
            run.setTriggerType("MANUAL");
            run.setSuiteIds(String.join(",", ids));
            run.setStatus(TcStatus.QUEUED);
            run.setGitCommit("2ffc550");
            run.setWorkDir(props.workDirOf(runNo).toString());
            store.createRun(run);
            return run;
        }
    }

    private static TcProcessLauncher.TcProcessResult passed(Map<String, Object> metrics) {
        return TcProcessLauncher.TcProcessResult.passed(12L, metrics);
    }

    private TcSummary summaryOf(Fixture f, TcRun run) throws Exception {
        return mapper.readValue(store(f, run).getSummaryJson(), TcSummary.class);
    }

    private TcRun store(Fixture f, TcRun run) {
        return f.store.findRun(run.getId());
    }

    @Test
    void allPassedRunWritesRunCasesArtifactsAndValidSummary() throws Exception {
        Fixture f = new Fixture(temp, spec -> passed(Map.of("tests", 312)));
        TcRun seeded = f.seed("TC-PASS-001", List.of("S1", "S2", "S4"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1", "S2", "S4")));

        TcRun run = store(f, seeded);
        assertEquals(TcStatus.PASSED, run.getStatus());
        assertEquals(3, run.getTotal());
        assertEquals(3, run.getPassed());
        assertEquals(0, run.getFailed());
        assertEquals(3, f.store.casesOf(run.getId()).size());
        assertEquals(3, f.store.artifactsOf(run.getId()).size());
        for (TcArtifact a : f.store.artifactsOf(run.getId())) {
            assertEquals("LOG", a.getArtifactType());
        }
        TcSummary summary = summaryOf(f, run);
        assertEquals(TcSummary.SCHEMA, summary.getSchema());
        assertEquals(List.of(), summary.validate());
    }

    @Test
    void failureDoesNotShortCircuitRemainingSuites() {
        List<String> executed = new ArrayList<>();
        Fixture f = new Fixture(temp, spec -> {
            executed.add(spec.suite().getId());
            if ("S2".equals(spec.suite().getId())) {
                return TcProcessLauncher.TcProcessResult.failed(TcStatus.FAILED, 1, 10, "boom");
            }
            return passed(Map.of());
        });
        TcRun seeded = f.seed("TC-FAIL-001", List.of("S1", "S2", "S4"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1", "S2", "S4")));

        assertEquals(List.of("S1", "S2", "S4"), executed);
        TcRun run = store(f, seeded);
        assertEquals(TcStatus.FAILED, run.getStatus());
        assertEquals(1, run.getFailed());
        TcCaseResult s2 = f.store.casesOf(run.getId()).stream()
                .filter(c -> "S2".equals(c.getSuiteId())).findFirst().orElseThrow();
        assertTrue(s2.getErrorExcerpt().contains("boom"));
    }

    @Test
    void skippedSuiteIsCountedSeparatelyAndDoesNotFailRun() {
        Fixture f = new Fixture(temp, spec -> "S7".equals(spec.suite().getId())
                ? new TcProcessLauncher.TcProcessResult(TcStatus.SKIPPED, 0, 0, "无 MySQL", Map.of("status", "SKIPPED"))
                : passed(Map.of()));
        TcRun seeded = f.seed("TC-SKIP-001", List.of("S1", "S7"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1", "S7")));
        TcRun run = store(f, seeded);
        assertEquals(TcStatus.PASSED, run.getStatus());
        assertEquals(1, run.getPassed());
        assertEquals(1, run.getSkipped());
        assertEquals(0, run.getFailed());
    }

    @Test
    void missingDependencyLeadsToSkippedCase() {
        Fixture f = new Fixture(temp, spec -> passed(Map.of()));
        TcRun seeded = f.seed("TC-DEP-001", List.of("S5"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S5")));
        TcCaseResult c = f.store.casesOf(seeded.getId()).get(0);
        assertEquals(TcStatus.SKIPPED, c.getStatus());
        assertTrue(c.getErrorExcerpt().contains("S1"));
    }

    @Test
    void failedDependencyLeadsToSkippedCase() {
        Fixture f = new Fixture(temp, spec -> "S1".equals(spec.suite().getId())
                ? TcProcessLauncher.TcProcessResult.failed(TcStatus.FAILED, 1, 1, "mvn 失败")
                : passed(Map.of()));
        TcRun seeded = f.seed("TC-DEP-002", List.of("S1", "S5"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1", "S5")));
        TcCaseResult s5 = f.store.casesOf(seeded.getId()).stream()
                .filter(c -> "S5".equals(c.getSuiteId())).findFirst().orElseThrow();
        assertEquals(TcStatus.SKIPPED, s5.getStatus());
    }

    @Test
    void launcherExceptionStillConvergesRunToTerminalState() {
        Fixture f = new Fixture(temp, spec -> { throw new IllegalStateException("spawn EINVAL"); });
        TcRun seeded = f.seed("TC-EX-001", List.of("S1", "S4"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1", "S4")));
        TcRun run = store(f, seeded);
        assertTrue(TcStatus.isTerminal(run.getStatus()));
        assertEquals(TcStatus.FAILED, run.getStatus());
        assertTrue(run.getErrorMsg().contains("未全绿套件"));
        assertTrue(run.getErrorMsg().contains("S1"));
        assertNotNull(run.getSummaryJson());
    }

    @Test
    void staleRunningRunsAreRecoveredAsTimeout() {
        Fixture f = new Fixture(temp, spec -> passed(Map.of()));
        TcRun a = f.seed("TC-STALE-001", List.of("S1"));
        TcRun b = f.seed("TC-STALE-002", List.of("S1"));
        a.setStatus(TcStatus.RUNNING);
        b.setStatus(TcStatus.QUEUED);
        f.store.updateRun(a);
        f.store.updateRun(b);
        TcRun done = f.seed("TC-DONE-001", List.of("S1"));
        done.setStatus(TcStatus.PASSED);
        f.store.updateRun(done);

        assertEquals(2, f.executor.recoverStaleRuns());
        assertEquals(TcStatus.TIMEOUT, store(f, a).getStatus());
        assertTrue(store(f, a).getErrorMsg().contains("TIMEOUT"));
        assertEquals(TcStatus.PASSED, store(f, done).getStatus());
    }

    @Test
    void secondConcurrentSubmitReturnsConflictCode() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Fixture f = new Fixture(temp, spec -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return passed(Map.of());
        });
        TcRun first = f.executor.submit(List.of("S4"), "MANUAL", "2ffc550");
        assertTrue(started.await(5, TimeUnit.SECONDS), "第一个套件应已启动");

        TcException conflict = assertThrows(TcException.class,
                () -> f.executor.submit(List.of("S1"), "MANUAL", "2ffc550"));
        assertEquals(409, conflict.getCode());
        release.countDown();

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && !TcStatus.isTerminal(store(f, first).getStatus())) {
            Thread.sleep(20);
        }
        assertEquals(TcStatus.PASSED, store(f, first).getStatus());
    }

    @Test
    void cancelOnUnknownOrFinishedRunIsSafe() {
        Fixture f = new Fixture(temp, spec -> passed(Map.of()));
        assertThrows(TcException.class, () -> f.executor.cancel(999999L));
        TcRun seeded = f.seed("TC-CANCEL-001", List.of("S1"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1")));
        assertEquals(false, f.executor.cancel(seeded.getId()));
    }

    @Test
    void failedSuiteIdsOnlyListsNonSuccessfulCases() {
        TcCaseResult a = new TcCaseResult();
        a.setSuiteId("S1");
        a.setStatus(TcStatus.PASSED);
        TcCaseResult b = new TcCaseResult();
        b.setSuiteId("S2");
        b.setStatus(TcStatus.FAILED);
        TcCaseResult c = new TcCaseResult();
        c.setSuiteId("S7");
        c.setStatus(TcStatus.SKIPPED);
        assertEquals(List.of("S2"), TestCenterExecutor.failedSuiteIds(List.of(a, b, c)));
    }

    @Test
    void everyRunHasStructuredMetricsJson() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tests", 312);
        metrics.put("failures", 0);
        Fixture f = new Fixture(temp, spec -> passed(metrics));
        TcRun seeded = f.seed("TC-METRICS-001", List.of("S1"));
        f.executor.executeRun(seeded, TcSuite.resolve(List.of("S1")));
        TcCaseResult c = f.store.casesOf(seeded.getId()).get(0);
        assertTrue(c.getMetricsJson().contains("\"tests\":312"));
    }
}
