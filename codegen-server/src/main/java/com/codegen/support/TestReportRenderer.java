package com.codegen.support;

import com.codegen.entity.TestResult;
import com.codegen.entity.TestRun;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * V9.2 用例中心报告渲染：把一次执行批次（run + 用例结果）渲染成可导出格式。
 *
 * <p>当前支持 {@code junit}（JUnit XML，CI 直接消费）与 {@code html}（人读报告）。
 * 两种格式共用同一份数据，避免「本地绿、CI 红」两套判定。
 *
 * <p>所有外部字符串（用例名、断言信息、响应快照）一律 XML/HTML 转义，
 * 防止响应体里的 {@code <} / {@code &} 破坏结构。
 */
public final class TestReportRenderer {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TestReportRenderer() {
    }

    /** JUnit XML：<testsuite> 包裹 <testcase>，失败行内联 <failure> */
    public static String toJUnitXml(TestRun run, List<TestResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        int failures = 0;
        int skipped = 0;
        double seconds = run.getDurationMs() == null ? 0 : run.getDurationMs() / 1000.0;
        for (TestResult r : results) {
            if ("fail".equals(r.getStatus())) {
                failures++;
            } else if ("skip".equals(r.getStatus())) {
                skipped++;
            }
        }
        sb.append("<testsuite name=\"test-run-").append(run.getId()).append('\"')
                .append(" tests=\"").append(results.size()).append('\"')
                .append(" failures=\"").append(failures).append('\"')
                .append(" skipped=\"").append(skipped).append('\"')
                .append(" time=\"").append(String.format("%.3f", seconds)).append('\"')
                .append(" timestamp=\"").append(fmt(run.getStartedAt())).append("\">\n");
        for (TestResult r : results) {
            sb.append("  <testcase name=\"").append(esc(r.getCaseName())).append('\"')
                    .append(" classname=\"suite-").append(r.getCaseId() == null ? "0" : r.getCaseId()).append('\"')
                    .append(" time=\"").append(String.format("%.3f", r.getDurationMs() == null ? 0 : r.getDurationMs() / 1000.0)).append("\">\n");
            if ("skip".equals(r.getStatus())) {
                sb.append("    <skipped/>\n");
            } else if ("fail".equals(r.getStatus())) {
                String msg = r.getErrorMsg() != null ? r.getErrorMsg()
                        : (r.getFailedAssertJson() != null ? r.getFailedAssertJson() : "assertion failed");
                sb.append("    <failure message=\"").append(esc(msg)).append("\">")
                        .append(esc(truncate(r.getResponseSnapshot(), 2000))).append("</failure>\n");
            }
            sb.append("  </testcase>\n");
        }
        sb.append("</testsuite>\n");
        return sb.toString();
    }

    /** 人读 HTML 报告：汇总条 + 用例明细表 */
    public static String toHtml(TestRun run, List<TestResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>测试报告 #").append(run.getId()).append("</title>")
                .append("<style>body{font-family:-apple-system,Segoe UI,Microsoft YaHei,sans-serif;margin:32px;color:#1f2329;background:#fff}")
                .append(".bar{padding:12px 16px;border-radius:8px;margin-bottom:16px;font-weight:600}")
                .append(".ok{background:#e8f5e9;color:#1b5e20}.bad{background:#ffebee;color:#b71c1c}.mid{background:#fff8e1;color:#f57f17}")
                .append("table{border-collapse:collapse;width:100%;font-size:13px}")
                .append("th,td{border:1px solid #e5e6eb;padding:8px 10px;text-align:left}")
                .append("th{background:#f2f3f5}.pass{color:#1b5e20}.fail{color:#b71c1c}.skip{color:#f57f17}</style></head><body>");
        String status = run.getStatus() == null ? "" : run.getStatus();
        String cls = "ok".equals(status) ? "ok" : ("failed".equals(status) || "aborted".equals(status)) ? "bad" : "mid";
        sb.append("<div class=\"bar ").append(cls).append("\">批次 #").append(run.getId())
                .append(" · 状态 ").append(esc(status))
                .append(" · 通过 ").append(run.getPassed() == null ? 0 : run.getPassed())
                .append(" / 失败 ").append(run.getFailed() == null ? 0 : run.getFailed())
                .append(" / 跳过 ").append(run.getSkipped() == null ? 0 : run.getSkipped())
                .append(" · 耗时 ").append(run.getDurationMs() == null ? 0 : run.getDurationMs()).append("ms</div>");
        sb.append("<table><thead><tr><th>用例</th><th>结果</th><th>耗时</th><th>说明</th></tr></thead><tbody>");
        for (TestResult r : results) {
            String st = r.getStatus() == null ? "" : r.getStatus();
            String stCls = "pass".equals(st) ? "pass" : "skip".equals(st) ? "skip" : "fail";
            sb.append("<tr><td>").append(esc(r.getCaseName())).append("</td>")
                    .append("<td class=\"").append(stCls).append("\">").append(esc(st)).append("</td>")
                    .append("<td>").append(r.getDurationMs() == null ? 0 : r.getDurationMs()).append("ms</td>")
                    .append("<td>").append(esc(truncate(r.getErrorMsg(), 300))).append("</td></tr>");
        }
        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(TS);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
