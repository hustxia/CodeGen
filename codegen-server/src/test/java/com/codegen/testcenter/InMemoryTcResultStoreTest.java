package com.codegen.testcenter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.TcRunQuery;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 内存存储：无 DB 环境也能完整跑通页面与 API，同时支撑执行器状态机单测。 */
class InMemoryTcResultStoreTest {

    private TcRun run(String no, String status, String trigger) {
        TcRun r = new TcRun();
        r.setRunNo(no);
        r.setStatus(status);
        r.setTriggerType(trigger);
        return r;
    }

    private TcCaseResult kase(long runId, String suiteId, String status) {
        TcCaseResult c = new TcCaseResult();
        c.setRunId(runId);
        c.setSuiteId(suiteId);
        c.setStatus(status);
        return c;
    }

    @Test
    void createAssignsIdAndDefaultFields() {
        InMemoryTcResultStore store = new InMemoryTcResultStore();
        TcRun r = run("TC001", TcStatus.QUEUED, "MANUAL");
        store.createRun(r);
        assertNotNull(r.getId());
        assertEquals(0, r.getDeleted());
        assertEquals(r.getRunNo(), store.findRun(r.getId()).getRunNo());
    }

    @Test
    void pageRunsFiltersByStatusAndPaginatesInDescOrder() {
        InMemoryTcResultStore store = new InMemoryTcResultStore();
        for (int i = 1; i <= 5; i++) {
            store.createRun(run("TC00" + i, i % 2 == 0 ? TcStatus.FAILED : TcStatus.PASSED, "MANUAL"));
        }
        TcRunQuery q = new TcRunQuery();
        q.setPageNum(1);
        q.setPageSize(2);
        q.setStatus(TcStatus.FAILED);
        Page<TcRun> page = store.pageRuns(q);
        assertEquals(2, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertTrue(page.getRecords().get(0).getId() > page.getRecords().get(1).getId());
    }

    @Test
    void saveCaseUpsertsByRunAndSuite() {
        InMemoryTcResultStore store = new InMemoryTcResultStore();
        TcRun r = store.createRun(run("TC010", TcStatus.RUNNING, "MANUAL"));
        store.saveCase(kase(r.getId(), "S1", TcStatus.RUNNING));
        TcCaseResult again = kase(r.getId(), "S1", TcStatus.PASSED);
        store.saveCase(again);
        List<TcCaseResult> cases = store.casesOf(r.getId());
        assertEquals(1, cases.size());
        assertEquals(TcStatus.PASSED, cases.get(0).getStatus());
        assertEquals(again.getId(), cases.get(0).getId());
    }

    @Test
    void recentRunsExcludesRunningAndIsDescending() {
        InMemoryTcResultStore store = new InMemoryTcResultStore();
        store.createRun(run("TC020", TcStatus.PASSED, "MANUAL"));
        TcRun running = run("TC021", TcStatus.RUNNING, "MANUAL");
        store.createRun(running);
        store.createRun(run("TC022", TcStatus.FAILED, "RERUN"));
        List<TcRun> recent = store.recentRuns(10);
        assertEquals(2, recent.size());
        assertEquals("TC022", recent.get(0).getRunNo());
        assertEquals(List.of("TC021"), store.runningRuns().stream().map(TcRun::getRunNo).toList());
    }
}
