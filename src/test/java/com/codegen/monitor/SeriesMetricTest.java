package com.codegen.monitor;

import com.codegen.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V6.10 指标白名单与参数钳位。
 */
class SeriesMetricTest {

    @Test
    void parsesWhitelistedMetrics() {
        assertEquals(SeriesMetric.JVM_HEAP, SeriesMetric.of("jvm.heap"));
        assertEquals(SeriesMetric.JVM_THREADS, SeriesMetric.of("jvm.threads"));
        assertEquals(SeriesMetric.PROCESS_CPU, SeriesMetric.of("process.cpu"));
        assertEquals(SeriesMetric.HTTP_QPS, SeriesMetric.of("http.qps"));
        assertEquals(SeriesMetric.HTTP_P95, SeriesMetric.of("http.p95"));
        assertEquals("MB", SeriesMetric.JVM_HEAP.unit());
    }

    @Test
    void rejectsUnknownOrMissingMetric() {
        assertThrows(BusinessException.class, () -> SeriesMetric.of("evil"));
        assertThrows(BusinessException.class, () -> SeriesMetric.of(null));
        assertThrows(BusinessException.class, () -> SeriesMetric.of(" "));
    }

    @Test
    void minutesIsClampedInsteadOfRejected() {
        assertEquals(30, MonitorWindow.clampMinutes(null));
        assertEquals(1, MonitorWindow.clampMinutes(0));
        assertEquals(1, MonitorWindow.clampMinutes(-5));
        assertEquals(30, MonitorWindow.clampMinutes(999));
        assertEquals(7, MonitorWindow.clampMinutes(7));
    }

    @Test
    void topNIsClamped() {
        assertEquals(10, MonitorWindow.clampTopN(null));
        assertEquals(1, MonitorWindow.clampTopN(0));
        assertEquals(50, MonitorWindow.clampTopN(999));
        assertEquals(20, MonitorWindow.clampTopN(20));
    }

    @Test
    void extractsValuesFromSnapshot() {
        MetricSnapshot s = new MetricSnapshot(1L, 512, 1024, 100, 38, 1, 20, 0.25,
                3600, 8, 10, 500, 1, 0, new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertEquals(512, SeriesMetric.JVM_HEAP.extract(s, 5));
        assertEquals(38, SeriesMetric.JVM_THREADS.extract(s, 5));
        assertEquals(25, SeriesMetric.PROCESS_CPU.extract(s, 5));
        assertEquals(2, SeriesMetric.HTTP_QPS.extract(s, 5));
        assertEquals(0, SeriesMetric.HTTP_P95.extract(s, 5));
    }

    @Test
    void p95ComesFromBucketDelta() {
        Map<Double, Long> buckets = new HashMap<>();
        buckets.put(100.0, 9L);
        buckets.put(200.0, 1L);
        MetricSnapshot s = new MetricSnapshot(1L, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                10, 1000, 0, 0, buckets, new HashMap<>(), new HashMap<>());
        // 目标 9.5：100 处累计 9 不够，200 处累计 10 >= 9.5，插值得到 150
        assertEquals(150, SeriesMetric.HTTP_P95.extract(s, 5));
    }
}
