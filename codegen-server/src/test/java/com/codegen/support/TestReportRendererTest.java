package com.codegen.support;

import com.codegen.entity.TestResult;
import com.codegen.entity.TestRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-13 用例中心报告渲染：JUnit XML / HTML 结构与转义。
 */
class TestReportRendererTest {

    private static TestRun run(long id, String status, Long duration, Integer pass, Integer fail, Integer skip) {
        TestRun r = new TestRun();
        r.setId(id);
        r.setStatus(status);
        r.setDurationMs(duration);
        r.setPassed(pass);
        r.setFailed(fail);
        r.setSkipped(skip);
        return r;
    }

    private static TestResult result(long id, String name, String status, Long duration, String err, String resp) {
        TestResult r = new TestResult();
        r.setId(id);
        r.setCaseId(id);
        r.setCaseName(name);
        r.setStatus(status);
        r.setDurationMs(duration);
        r.setErrorMsg(err);
        r.setResponseSnapshot(resp);
        return r;
    }

    @Test
    void junitXml_okCase_hasNoFailure() {
        TestRun r = run(1L, "success", 1500L, 1, 0, 0);
        TestResult c = result(11L, "登录", "pass", 200L, null, "{\"code\":200}");
        String xml = TestReportRenderer.toJUnitXml(r, List.of(c));

        assertTrue(xml.startsWith("<?xml"), "应以 <?xml 开头");
        assertTrue(xml.contains("<testsuite name=\"test-run-1\""), "含 testsuite 名");
        assertTrue(xml.contains("tests=\"1\""), "用例数=1");
        assertTrue(xml.contains("failures=\"0\""), "失败数=0");
        assertTrue(xml.contains("<testcase name=\"登录\""), "含 testcase");
        assertFalse(xml.contains("<failure"), "通过用例不应有 <failure");
    }

    @Test
    void junitXml_failCase_hasFailureWithEscapedBody() {
        TestRun r = run(2L, "failed", 900L, 0, 1, 0);
        TestResult c = result(21L, "a < b & c", "fail", 100L, "assert code!=200", "<html>&</html>");
        String xml = TestReportRenderer.toJUnitXml(r, List.of(c));

        assertTrue(xml.contains("failures=\"1\""), "失败数=1");
        assertTrue(xml.contains("<failure"), "失败用例含 <failure");
        // 特殊字符必须被转义，不能破坏 XML 结构
        assertTrue(xml.contains("a &lt; b &amp; c"), "用例名尖括号/& 已转义");
        assertTrue(xml.contains("&lt;html&gt;&amp;&lt;/html&gt;"), "响应快照已转义");
    }

    @Test
    void junitXml_skipCase_hasSkippedElement() {
        TestRun r = run(3L, "success", 100L, 0, 0, 1);
        TestResult c = result(31L, "生产写操作", "skip", 0L, "生产环境禁止写操作", null);
        String xml = TestReportRenderer.toJUnitXml(r, List.of(c));
        assertTrue(xml.contains("skipped=\"1\""), "跳过数=1");
        assertTrue(xml.contains("<skipped/>"), "含 <skipped");
    }

    @Test
    void html_containsSummaryAndCase() {
        TestRun r = run(4L, "success", 1200L, 2, 0, 1);
        TestResult c = result(41L, "详情", "pass", 50L, null, null);
        String html = TestReportRenderer.toHtml(r, List.of(c));
        assertTrue(html.contains("<html"), "含 html 文档");
        assertTrue(html.contains("通过 2"), "汇总含通过数");
        assertTrue(html.contains("详情"), "含用例名");
    }
}
