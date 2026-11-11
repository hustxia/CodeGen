package com.codegen.monitor;

import com.codegen.config.MonitorProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.10 采样：失败隔离 + 累计值差分。
 */
class MonitorSamplerTest {

    @Test
    void failureIsIsolatedAndNextRoundStillRuns() {
        MonitorProperties props = new MonitorProperties();
        props.getSample().setBufferSize(10);
        MonitorStore store = new MonitorStore(props);
        int[] calls = {0};

        MonitorSampler sampler = new MonitorSampler(new SimpleMeterRegistry(), store) {
            @Override
            protected MetricSnapshot collect() {
                if (++calls[0] == 1) {
                    throw new IllegalStateException("boom");
                }
                return MonitorStoreTest.snapshot(System.currentTimeMillis());
            }
        };

        // 第一轮抛异常：不能冒到调度器，也不能写脏数据
        sampler.sampleOnce();
        assertEquals(0, store.size());
        assertEquals(0, store.lastSampleAt());

        // 第二轮恢复：证明任务没有停摆
        sampler.sampleOnce();
        assertEquals(1, store.size());
        assertTrue(store.lastSampleAt() > 0);
    }

    @Test
    void convertsCumulativeMetersToIntervalDeltas() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MonitorStore store = new MonitorStore(new MonitorProperties());
        MonitorSampler sampler = new MonitorSampler(registry, store);

        registry.timer("gen.write", "result", "success").record(Duration.ofMillis(30));
        registry.counter("gen.write.files", "status", "WRITTEN").increment(3);
        registry.timer("http.server.requests", "method", "GET", "uri", "/gen/table/{id}", "status", "200")
                .record(Duration.ofMillis(10));
        registry.timer("http.server.requests", "method", "GET", "uri", "/gen/table/{id}", "status", "500")
                .record(Duration.ofMillis(20));

        sampler.sampleOnce();
        MetricSnapshot first = store.latest();
        assertNotNull(first);
        assertEquals(2, first.httpCount());
        assertEquals(1, first.http5xx());
        assertEquals(1.0, first.business().get("gen.write.success.count"), 1e-9);
        assertEquals(30.0, first.business().get("gen.write.success.ms"), 1e-9);
        assertEquals(3.0, first.business().get("gen.write.files.WRITTEN"), 1e-9);
        assertTrue(first.httpByUri().containsKey("GET /gen/table/{id}"));

        // 第二轮没有新增请求/业务动作：差分必须是 0，不能把累计值再算一遍
        sampler.sampleOnce();
        MetricSnapshot second = store.latest();
        assertEquals(0, second.httpCount());
        assertEquals(0.0, second.business().get("gen.write.success.count"), 1e-9);
        assertTrue(second.httpByUri().isEmpty());
    }
}
