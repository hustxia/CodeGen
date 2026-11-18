package com.codegen.testcenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 摘要契约：本地执行器与 CI 必须字段级一致。 */
class TcSummaryContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private TcSummary sample() {
        TcSummary s = new TcSummary();
        s.setRunNo("TC20261113-001");
        s.setGitCommit("2ffc550");
        s.setDurationMs(412000);
        addCase(s, "S1", TcStatus.PASSED);
        addCase(s, "S2", TcStatus.PASSED);
        addCase(s, "S7", TcStatus.SKIPPED);
        return s.recount();
    }

    private void addCase(TcSummary s, String id, String status) {
        TcSummary.Case c = new TcSummary.Case();
        c.setSuiteId(id);
        c.setStatus(status);
        c.setDurationMs(1000);
        c.setLogPath("runs/TC20261113-001/" + id + ".log");
        s.getCases().add(c);
    }

    @Test
    void recountDerivesTotalsAndStatusFromCases() {
        TcSummary s = sample();
        assertEquals(3, s.getTotals().getTotal());
        assertEquals(2, s.getTotals().getPassed());
        assertEquals(0, s.getTotals().getFailed());
        assertEquals(1, s.getTotals().getSkipped());
        assertEquals(TcStatus.PASSED, s.getStatus());
    }

    @Test
    void skippedDoesNotFailTheRunButFailureDoes() {
        TcSummary s = sample();
        addCase(s, "S6", TcStatus.FAILED);
        s.recount();
        assertEquals(1, s.getTotals().getFailed());
        assertEquals(TcStatus.FAILED, s.getStatus());
    }

    @Test
    void validSummaryPassesContractCheck() {
        assertEquals(List.of(), sample().validate());
    }

    @Test
    void allCancelledRunKeepsCancelledStatus() {
        TcSummary s = new TcSummary();
        s.setRunNo("TC20261113-002");
        addCase(s, "S1", TcStatus.CANCELLED);
        addCase(s, "S2", TcStatus.CANCELLED);
        s.recount();
        assertEquals(TcStatus.CANCELLED, s.getStatus());
        assertEquals(2, s.getTotals().getFailed());
        assertEquals(List.of(), s.validate());
    }

    @Test
    void contractRejectsWrongSchemaMismatchAndUnknownSuite() {
        TcSummary s = sample();
        s.setSchema("testcenter/v0");
        assertTrue(s.validate().stream().anyMatch(e -> e.contains("schema")));

        TcSummary t = sample();
        t.getTotals().setTotal(99);
        assertTrue(t.validate().stream().anyMatch(e -> e.contains("total")));

        TcSummary u = sample();
        u.getCases().get(0).setSuiteId("S999");
        assertTrue(u.validate().stream().anyMatch(e -> e.contains("未知套件")));
    }

    @Test
    void jacksonRoundTripKeepsSchemaAndCaseFields() throws Exception {
        String json = mapper.writeValueAsString(sample());
        assertTrue(json.contains("\"schema\":\"codegen.testcenter/v1\""));
        assertTrue(json.contains("\"totals\""));
        assertTrue(json.contains("\"cases\""));
        TcSummary back = mapper.readValue(json, TcSummary.class);
        assertEquals(TcSummary.SCHEMA, back.getSchema());
        assertEquals(3, back.getCases().size());
        assertEquals(List.of(), back.validate());
        // 字段顺序固定，便于 CI 与执行器摘要直接 diff
        assertFalse(json.startsWith("{\"cases\""));
    }
}
