package com.codegen.monitor;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.10 窗口 HTTP 聚合与直方图分位数。
 */
class HttpStatsAggregatorTest {

    @Test
    void aggregatesWindowAndComputesPercentiles() {
        Map<Double, Long> b1 = new HashMap<>();
        b1.put(100.0, 9L);
        b1.put(200.0, 1L);
        Map<String, double[]> u1 = new HashMap<>();
        u1.put("GET /a", new double[]{10, 1000, 300});

        Map<Double, Long> b2 = new HashMap<>();
        b2.put(100.0, 5L);
        b2.put(1000.0, 5L);
        Map<String, double[]> u2 = new HashMap<>();
        u2.put("GET /a", new double[]{10, 2000, 500});

        List<MetricSnapshot> window = List.of(
                snapshot(1, 10, 1000, 1, 0, b1, u1),
                snapshot(2, 10, 2000, 0, 1, b2, u2));

        HttpStatsAggregator.Stats stats = HttpStatsAggregator.aggregate(window);
        assertEquals(20, stats.getTotalRequests());
        assertEquals(1, stats.getErrors4xx());
        assertEquals(1, stats.getErrors5xx());
        assertEquals(150, stats.getTotalMs() / stats.getTotalRequests());
        // 桶合计 {100:14, 200:1, 1000:5}，p95 目标 19 -> 200 + 800 * 0.8 = 840
        assertEquals(840, HttpStatsAggregator.percentile(stats.getBuckets(), 0.95), 0.001);
        assertEquals(150, stats.getByUri().get("GET /a")[1] / stats.getByUri().get("GET /a")[0]);
        assertEquals(500, stats.getByUri().get("GET /a")[2], 0.001);
    }

    @Test
    void emptyWindowIsSafe() {
        HttpStatsAggregator.Stats stats = HttpStatsAggregator.aggregate(List.of());
        assertEquals(0, stats.getTotalRequests());
        assertEquals(0, HttpStatsAggregator.percentile(stats.getBuckets(), 0.95));
        assertTrue(stats.getByUri().isEmpty());
        assertEquals(0, HttpStatsAggregator.percentile(null, 0.99));
    }

    private MetricSnapshot snapshot(long ts, long count, double totalMs, long e4xx, long e5xx,
                                    Map<Double, Long> buckets, Map<String, double[]> byUri) {
        return new MetricSnapshot(ts, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                count, totalMs, e4xx, e5xx, buckets, byUri, new HashMap<>());
    }
}
