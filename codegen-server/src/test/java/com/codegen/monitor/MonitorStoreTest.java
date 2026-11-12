package com.codegen.monitor;

import com.codegen.config.MonitorProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.10 环形缓冲：满了淘汰最旧，不扩容。
 */
class MonitorStoreTest {

    @Test
    void keepsOnlyCapacityAndEvictsOldest() {
        MonitorProperties props = new MonitorProperties();
        props.getSample().setBufferSize(5);
        MonitorStore store = new MonitorStore(props);

        for (int i = 1; i <= 8; i++) {
            store.add(snapshot(i));
        }

        assertEquals(5, store.size());
        assertEquals(8, store.latest().timestamp());
        List<MetricSnapshot> all = store.since(0);
        assertEquals(5, all.size());
        assertEquals(4, all.get(0).timestamp());
        assertEquals(8, all.get(4).timestamp());
    }

    @Test
    void sinceReturnsAscendingWindow() {
        MonitorProperties props = new MonitorProperties();
        props.getSample().setBufferSize(10);
        MonitorStore store = new MonitorStore(props);
        for (int i = 1; i <= 8; i++) {
            store.add(snapshot(i));
        }

        List<MetricSnapshot> window = store.since(6);
        assertEquals(3, window.size());
        assertEquals(6, window.get(0).timestamp());
        assertEquals(8, window.get(2).timestamp());
        assertEquals(0, store.since(100).size());
    }

    @Test
    void latestIsNullBeforeFirstSample() {
        MonitorStore store = new MonitorStore(new MonitorProperties());
        assertEquals(null, store.latest());
        assertEquals(0, store.lastSampleAt());
        assertTrue(store.size() == 0);
    }

    static MetricSnapshot snapshot(long ts) {
        Map<Double, Long> buckets = new HashMap<>();
        Map<String, double[]> byUri = new HashMap<>();
        Map<String, Double> business = new HashMap<>();
        return new MetricSnapshot(ts, 100, 1024, 50, 20, 0, 0, 0.1,
                100, 8, 0, 0, 0, 0, buckets, byUri, business);
    }
}
