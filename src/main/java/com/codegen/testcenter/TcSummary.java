package com.codegen.testcenter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 JSON 摘要契约 {@code codegen.testcenter/v1}。
 *
 * <p>本地执行器、{@code scripts/testcenter-summary.mjs}（CI 门禁）与导出的 JSON 报告
 * 都输出这一份结构，避免「本地绿、CI 红」变成两套判定。字段顺序固定，便于 diff。
 * <strong>新增字段必须同步改 CI 脚本与契约单测</strong>。
 */
@Data
@JsonPropertyOrder({"schema", "runNo", "gitCommit", "status", "durationMs", "totals", "cases"})
public class TcSummary {

    public static final String SCHEMA = "codegen.testcenter/v1";

    private String schema = SCHEMA;
    private String runNo;
    private String gitCommit;
    private String status = TcStatus.QUEUED;
    private long durationMs;
    private Totals totals = new Totals();
    private List<Case> cases = new ArrayList<>();

    @Data
    @JsonPropertyOrder({"total", "passed", "failed", "skipped"})
    public static class Totals {
        private int total;
        private int passed;
        private int failed;
        private int skipped;

        public void count(String status) {
            total++;
            if (TcStatus.PASSED.equals(status)) {
                passed++;
            } else if (TcStatus.SKIPPED.equals(status)) {
                skipped++;
            } else {
                failed++;
            }
        }
    }

    @Data
    @JsonPropertyOrder({"suiteId", "status", "durationMs", "metrics", "logPath"})
    public static class Case {
        private String suiteId;
        private String status;
        private long durationMs;
        private Map<String, Object> metrics = new LinkedHashMap<>();
        private String logPath;
    }

    /** 按 cases 重算 totals，避免调用方手工维护导致契约自相矛盾。 */
    public TcSummary recount() {
        Totals t = new Totals();
        for (Case c : cases) {
            t.count(c.getStatus());
        }
        this.totals = t;
        if (t.getFailed() == 0) {
            this.status = TcStatus.PASSED;
        } else {
            // 整次运行都是同一种终态时保留原语义：取消就是取消，超时就是超时，
            // 不要一律压成 FAILED（否则页面上看不出这次是人为取消还是真跑挂了）。
            List<String> bad = cases.stream().map(Case::getStatus)
                    .filter(s -> !TcStatus.isSuccess(s)).distinct().toList();
            this.status = bad.size() == 1 ? bad.get(0) : TcStatus.FAILED;
        }
        return this;
    }

    /** 契约自检：字段级合法性，供单测与导出前调用。 */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (!SCHEMA.equals(schema)) {
            errors.add("schema 必须是 " + SCHEMA);
        }
        if (runNo == null || runNo.isBlank()) {
            errors.add("runNo 不能为空");
        }
        if (!TcStatus.isTerminal(status)) {
            errors.add("status 必须是终态，实际 " + status);
        }
        if (durationMs < 0) {
            errors.add("durationMs 不能为负");
        }
        if (totals == null || totals.getTotal() != cases.size()) {
            errors.add("totals.total 与 cases 数量不一致");
        }
        if (totals != null && totals.getTotal() != totals.getPassed() + totals.getFailed() + totals.getSkipped()) {
            errors.add("totals 分项之和不等于 total");
        }
        for (Case c : cases) {
            if (TcSuite.of(c.getSuiteId()) == null) {
                errors.add("未知套件 " + c.getSuiteId());
            }
            if (!TcStatus.PASSED.equals(c.getStatus())
                    && !TcStatus.FAILED.equals(c.getStatus())
                    && !TcStatus.SKIPPED.equals(c.getStatus())
                    && !TcStatus.TIMEOUT.equals(c.getStatus())
                    && !TcStatus.CANCELLED.equals(c.getStatus())) {
                errors.add("用例状态非法 " + c.getStatus());
            }
        }
        return errors;
    }
}
