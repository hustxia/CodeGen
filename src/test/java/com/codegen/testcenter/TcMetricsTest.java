package com.codegen.testcenter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 指标解析：只认驱动脚本的 TC_METRICS 行，不靠正则猜日志文案。 */
class TcMetricsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesLastMetricsLine() {
        String log = "TC_METRICS {\"tests\":1,\"version\":\"v1\"}\n"
                + "some noise\n"
                + "TC_METRICS {\"tests\":312,\"failures\":0,\"minTests\":312}\n";
        Map<String, Object> m = TcMetrics.parse(log, mapper);
        assertEquals(312, ((Number) m.get("tests")).intValue());
        assertEquals(0, ((Number) m.get("failures")).intValue());
        assertEquals(3, m.size());
    }

    @Test
    void ignoresNestedObjectsAndBrokenJson() {
        Map<String, Object> m = TcMetrics.parse(
                "TC_METRICS {\"tests\":312,\"nested\":{\"a\":1},\"ok\":true}", mapper);
        assertEquals(312, ((Number) m.get("tests")).intValue());
        assertEquals(true, m.get("ok"));
        assertTrue(!m.containsKey("nested"));
        assertTrue(TcMetrics.parse("TC_METRICS {not json}", mapper).isEmpty());
    }

    @Test
    void emptyOrNullLogYieldsEmptyMetrics() {
        assertTrue(TcMetrics.parse(null, mapper).isEmpty());
        assertTrue(TcMetrics.parse("", mapper).isEmpty());
    }

    @Test
    void tailKeepsLastLinesAndCapsLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            sb.append("line").append(i).append('\n');
        }
        String tail = TcMetrics.tail(sb.toString(), 5);
        assertTrue(tail.contains("line300"));
        assertTrue(!tail.contains("line294"));
        String huge = "x".repeat(9000);
        assertTrue(TcMetrics.tail(huge, 200).length() <= 4000);
    }
}
