package com.codegen.testcenter;

import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.codegen.vo.TcRunDetailVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 报告：自包含、含摘要/用例/日志/产物，且对日志内容做 HTML 转义。 */
class TcReportRendererTest {

    private final TcReportRenderer renderer = new TcReportRenderer();

    private TcRunDetailVO detail() {
        TcRun run = new TcRun();
        run.setRunNo("TC20261113-001");
        run.setTriggerType("MANUAL");
        run.setGitCommit("2ffc550");
        run.setStatus(TcStatus.PASSED);
        run.setTotal(2);
        run.setPassed(1);
        run.setFailed(0);
        run.setSkipped(1);
        run.setDurationMs(412000L);
        run.setWorkDir("runs/TC20261113-001");
        run.setSummaryJson("{\"schema\":\"codegen.testcenter/v1\",\"status\":\"PASSED\"}");

        TcCaseResult c1 = new TcCaseResult();
        c1.setSuiteId("S1");
        c1.setStatus(TcStatus.PASSED);
        c1.setDurationMs(96000L);
        c1.setExitCode(0);
        c1.setMetricsJson("{\"tests\":312}");
        c1.setLogPath("TC20261113-001/S1.log");
        TcCaseResult c2 = new TcCaseResult();
        c2.setSuiteId("S7");
        c2.setStatus(TcStatus.SKIPPED);
        c2.setMetricsJson("{}");
        c2.setLogPath("TC20261113-001/S7.log");

        TcArtifact a = new TcArtifact();
        a.setArtifactType("LOG");
        a.setSuiteId("S1");
        a.setFileName("S1.log");
        a.setFileSize(1234L);

        TcRunDetailVO vo = new TcRunDetailVO();
        vo.setRun(run);
        vo.setCases(List.of(c1, c2));
        vo.setArtifacts(List.of(a));
        return vo;
    }

    @Test
    void htmlReportIsSelfContainedAndIncludesAllSections() {
        String html = renderer.render(detail(), c -> "PASS " + c.getSuiteId());
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("<style>"));
        assertTrue(html.contains("TC20261113-001"));
        assertTrue(html.contains("codegen.testcenter/v1"));
        assertTrue(html.contains("用例结果"));
        assertTrue(html.contains("日志尾部"));
        assertTrue(html.contains("产物清单"));
        assertTrue(html.contains("S1.log"));
        assertTrue(html.contains("312"));
        assertTrue(!html.contains("http://") && !html.contains("https://"));
    }

    @Test
    void logContentIsHtmlEscaped() {
        String html = renderer.render(detail(), c -> "<script>alert(1)</script> & 'x'");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(!html.contains("<script>alert"));
        assertTrue(html.contains("&amp;"));
    }

    @Test
    void emptyDetailStillProducesValidDocument() {
        TcRunDetailVO vo = new TcRunDetailVO();
        TcRun run = new TcRun();
        run.setRunNo("TC-EMPTY");
        run.setStatus(TcStatus.PASSED);
        vo.setRun(run);
        String html = renderer.render(vo, c -> "");
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("TC-EMPTY"));
    }
}
