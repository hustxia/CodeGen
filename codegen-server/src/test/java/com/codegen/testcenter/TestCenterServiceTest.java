package com.codegen.testcenter;

import com.codegen.dto.TcRunRequest;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.codegen.vo.TcRunDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 服务层：ID 下发精度、请求安全、报告、日志路径白名单。 */
class TestCenterServiceTest {

    @TempDir
    Path temp;

    private final ObjectMapper mapper = new ObjectMapper();

    private static class Fixture {
        final InMemoryTcResultStore store = new InMemoryTcResultStore();
        final TcProperties props = new TcProperties();
        final TestCenterExecutor executor;
        final TestCenterService service;

        Fixture(Path temp, TcProcessLauncher launcher, ObjectMapper mapper) {
            props.setWorkdir(temp.resolve("runs").toString());
            executor = new TestCenterExecutor(store, launcher, props, mapper);
            service = new TestCenterService(executor, store, props, new TcReportRenderer(), mapper);
        }

        TcRun seed(String no, String summary) {
            TcRun r = new TcRun();
            r.setRunNo(no);
            r.setTriggerType("MANUAL");
            r.setSuiteIds("S4");
            r.setStatus(TcStatus.PASSED);
            r.setTotal(1);
            r.setPassed(1);
            r.setFailed(0);
            r.setSkipped(0);
            r.setSummaryJson(summary);
            r.setWorkDir(props.workDirOf(no).toString());
            return store.createRun(r);
        }
    }

    @Test
    void overviewDowngradesSnowflakeIdToString() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Fixture f = new Fixture(temp, spec -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return TcProcessLauncher.TcProcessResult.passed(1, java.util.Map.of());
        }, mapper);
        TcRun run = f.executor.submit(List.of("S4"), "MANUAL", "abc");
        Object active = f.service.overview().get("activeRunId");
        assertInstanceOf(String.class, active);
        assertEquals(String.valueOf(run.getId()), active);
        release.countDown();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        // 必须等异步 worker 真正结束：否则 @TempDir 清理时执行器还在写 run 目录，
        // Windows 上会报 "Failed to delete temp directory"（不是业务断言失败）。
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && TcStatus.isRunning(f.store.findRun(run.getId()).getStatus())) {
            Thread.sleep(20);
        }
        assertTrue(TcStatus.isTerminal(f.store.findRun(run.getId()).getStatus()));
    }

    @Test
    void idleOverviewHasNullActiveId() {
        Fixture f = new Fixture(temp, spec -> TcProcessLauncher.TcProcessResult.passed(1, java.util.Map.of()), mapper);
        assertNull(f.service.overview().get("activeRunId"));
        assertEquals(false, f.service.overview().get("busy"));
    }

    @Test
    void triggerRejectsForbiddenCommandField() {
        Fixture f = new Fixture(temp, spec -> TcProcessLauncher.TcProcessResult.passed(1, java.util.Map.of()), mapper);
        TcRunRequest req = new TcRunRequest();
        req.setSuiteIds(List.of("S4"));
        req.collectUnknown("command", "rm -rf /");
        TcException ex = assertThrows(TcException.class, () -> f.service.trigger(req));
        assertEquals(403, ex.getCode());
    }

    @Test
    void reportJsonReturnsStoredSummaryAndHtmlContainsRunNo() {
        Fixture f = new Fixture(temp, spec -> TcProcessLauncher.TcProcessResult.passed(1, java.util.Map.of()), mapper);
        TcRun run = f.seed("TC-REPORT-001", "{\"schema\":\"codegen.testcenter/v1\",\"status\":\"PASSED\"}");
        assertEquals(run.getSummaryJson(), f.service.report(run.getId(), "json"));
        String html = f.service.report(run.getId(), "html");
        assertTrue(html.contains("TC-REPORT-001"));
        assertTrue(html.contains("codegen.testcenter/v1"));
        TcRunDetailVO detail = f.service.detail(run.getId());
        assertInstanceOf(TcSummary.class, detail.getSummary());
    }

    @Test
    void logReadingRejectsPathOutsideRunDirectory() {
        Fixture f = new Fixture(temp, spec -> TcProcessLauncher.TcProcessResult.passed(1, java.util.Map.of()), mapper);
        TcRun run = f.seed("TC-LOG-001", "{}");
        TcCaseResult c = new TcCaseResult();
        c.setRunId(run.getId());
        c.setSuiteId("S4");
        c.setStatus(TcStatus.FAILED);
        c.setLogPath("../../etc/passwd");
        String text = f.service.logText(run, c, 200);
        assertTrue(text.contains("日志暂不可用"));
    }
}
