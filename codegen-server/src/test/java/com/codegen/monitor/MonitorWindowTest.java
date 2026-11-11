package com.codegen.monitor;

import com.codegen.config.MonitorProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.10 查询窗口：参数越界不报错而是钳位，避免面板整页报红；窗口未满标记 warmup。
 */
class MonitorWindowTest {

    @Test
    void clampMinutesBounds() {
        assertEquals(1, MonitorWindow.clampMinutes(0));
        assertEquals(1, MonitorWindow.clampMinutes(-5));
        assertEquals(30, MonitorWindow.clampMinutes(30));
        assertEquals(30, MonitorWindow.clampMinutes(999));
        assertEquals(15, MonitorWindow.clampMinutes(15));
        assertEquals(30, MonitorWindow.clampMinutes(null));
    }

    @Test
    void clampTopNBounds() {
        assertEquals(1, MonitorWindow.clampTopN(0));
        assertEquals(1, MonitorWindow.clampTopN(-3));
        assertEquals(10, MonitorWindow.clampTopN(null));
        assertEquals(50, MonitorWindow.clampTopN(999));
        assertEquals(20, MonitorWindow.clampTopN(20));
    }

    @Test
    void warmupTrueWhenWindowSmallerThanExpected() {
        MonitorProperties props = new MonitorProperties();
        props.getSample().setIntervalMs(5000);
        MonitorStore store = new MonitorStore(props);
        MonitorWindow window = new MonitorWindow(store, props);
        assertTrue(window.warmup(store.since(0), 30), "空窗口应判定为积累中");
    }

    @Test
    void warmupFalseWhenFull() {
        MonitorProperties props = new MonitorProperties();
        props.getSample().setIntervalMs(5000);
        props.getSample().setBufferSize(360);
        MonitorStore store = new MonitorStore(props);
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 360; i++) {
            // 覆盖最近 30 分钟：最旧点恰好落在 30 分钟前，最新点在 now
            store.add(MonitorStoreTest.snapshot(now - (360 - i) * 5000L));
        }
        MonitorWindow window = new MonitorWindow(store, props);
        List<MetricSnapshot> full = store.since(now - 30L * 60_000L);
        assertEquals(360, full.size());
        assertFalse(window.warmup(full, 30), "满 30 分钟窗口不应再标 warmup");
    }
}
