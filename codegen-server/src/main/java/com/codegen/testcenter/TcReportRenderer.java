package com.codegen.testcenter;

import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import com.codegen.vo.TcRunDetailVO;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 自包含 HTML 报告：不引外部 CSS/JS/CDN，双击即可离线打开，
 * 作为版本验收证据归档（FR-07 / 验收第 8 条）。
 */
@Component
public class TcReportRenderer {

    public String render(TcRunDetailVO detail, Function<TcCaseResult, String> logReader) {
        TcRun run = detail.getRun();
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<title>测试中心报告 ").append(esc(run.getRunNo())).append("</title>")
                .append("<style>")
                .append("body{font-family:-apple-system,Segoe UI,Microsoft YaHei,sans-serif;margin:32px;color:#1f2329;background:#fff}")
                .append("h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:28px 0 10px;border-left:4px solid #409eff;padding-left:8px}")
                .append(".sub{color:#6b7280;font-size:13px;margin-bottom:18px}")
                .append(".cards{display:flex;gap:12px;flex-wrap:wrap}.card{border:1px solid #e5e7eb;border-radius:8px;padding:12px 16px;min-width:120px}")
                .append(".card b{display:block;font-size:20px;margin-top:4px}.ok{color:#16a34a}.bad{color:#dc2626}")
                .append("table{border-collapse:collapse;width:100%;font-size:13px}th,td{border:1px solid #e5e7eb;padding:7px 9px;text-align:left;vertical-align:top}")
                .append("th{background:#f8fafc}pre{background:#0f172a;color:#e2e8f0;padding:10px;border-radius:6px;overflow:auto;font-size:12px;max-height:260px}")
                .append("code{font-family:Consolas,monospace}.tag{padding:2px 7px;border-radius:10px;font-size:12px;background:#eef2ff}")
                .append("</style></head><body>");
        sb.append("<h1>代码生成器 · 测试中心报告</h1>")
                .append("<div class=\"sub\">运行编号 ").append(esc(run.getRunNo()))
                .append(" · 触发 ").append(esc(run.getTriggerType()))
                .append(" · commit ").append(esc(run.getGitCommit() == null ? "-" : run.getGitCommit()))
                .append(" · 工作目录 ").append(esc(run.getWorkDir() == null ? "-" : run.getWorkDir()))
                .append("</div>");

        String status = run.getStatus();
        sb.append("<div class=\"cards\">")
                .append(card("状态", status, TcStatus.PASSED.equals(status) ? "ok" : "bad"))
                .append(card("套件总数", String.valueOf(run.getTotal()), ""))
                .append(card("通过", String.valueOf(run.getPassed()), "ok"))
                .append(card("失败", String.valueOf(run.getFailed()), "bad"))
                .append(card("跳过", String.valueOf(run.getSkipped()), ""))
                .append(card("总耗时", ms(run.getDurationMs()), ""))
                .append("</div>");

        sb.append("<h2>用例结果</h2><table><thead><tr><th>套件</th><th>状态</th><th>耗时</th>")
                .append("<th>退出码</th><th>指标</th><th>失败摘要</th></tr></thead><tbody>");
        for (TcCaseResult c : detail.getCases()) {
            sb.append("<tr><td><code>").append(esc(c.getSuiteId())).append("</code> ")
                    .append(esc(nameOf(c.getSuiteId()))).append("</td>")
                    .append("<td>").append(esc(c.getStatus())).append("</td>")
                    .append("<td>").append(ms(c.getDurationMs())).append("</td>")
                    .append("<td>").append(c.getExitCode() == null ? "-" : c.getExitCode()).append("</td>")
                    .append("<td><code>").append(esc(shorten(c.getMetricsJson(), 180))).append("</code></td>")
                    .append("<td>").append(esc(shorten(c.getErrorExcerpt(), 260))).append("</td></tr>");
        }
        sb.append("</tbody></table>");

        sb.append("<h2>日志尾部</h2>");
        for (TcCaseResult c : detail.getCases()) {
            String text = logReader == null ? "" : logReader.apply(c);
            sb.append("<h3><span class=\"tag\">").append(esc(c.getSuiteId())).append("</span> ")
                    .append(esc(c.getLogPath() == null ? "-" : c.getLogPath())).append("</h3>")
                    .append("<pre>").append(esc(text == null || text.isBlank() ? "(无日志)" : text)).append("</pre>");
        }

        sb.append("<h2>产物清单</h2><table><thead><tr><th>类型</th><th>套件</th><th>文件</th><th>大小</th></tr></thead><tbody>");
        for (TcArtifact a : detail.getArtifacts()) {
            sb.append("<tr><td>").append(esc(a.getArtifactType())).append("</td>")
                    .append("<td>").append(esc(a.getSuiteId())).append("</td>")
                    .append("<td>").append(esc(a.getFileName())).append("</td>")
                    .append("<td>").append(a.getFileSize() == null ? "-" : a.getFileSize() + " B").append("</td></tr>");
        }
        sb.append("</tbody></table>");
        sb.append("<h2>JSON 摘要（codegen.testcenter/v1）</h2><pre>")
                .append(esc(run.getSummaryJson() == null ? "(缺失)" : pretty(run.getSummaryJson())))
                .append("</pre>");
        sb.append("<div class=\"sub\">由 V9.2 测试中心生成 · 报告内不引用任何外部资源</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String card(String label, String value, String cls) {
        return "<div class=\"card\">" + esc(label) + "<b class=\"" + cls + "\">" + esc(value) + "</b></div>";
    }

    private String nameOf(String suiteId) {
        TcSuite s = TcSuite.of(suiteId);
        return s == null ? "" : s.getName();
    }

    private String ms(Long value) {
        if (value == null) {
            return "-";
        }
        return value < 1000 ? value + " ms" : String.format("%.1f s", value / 1000.0);
    }

    private String shorten(String text, int cap) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() > cap ? flat.substring(0, cap) + "…" : flat;
    }

    private String pretty(String json) {
        // 不依赖任何格式化库：摘要本身是单行 JSON，简单换行即可读
        return json.replace(",", ",\n ").replace("{", "{\n ").replace("}", "\n}");
    }

    private String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
