package com.codegen.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V9.8 流水线并发闸门：全局上限 + 同系统互斥。
 *
 * <p>为什么同系统要互斥：同一系统（projectId）的两条流水线共用 backendRoot/frontendRoot
 * 与目标库，并发跑会互相覆盖产物。跨系统则完全并行。
 */
class PipelineRunGateTest {

    @Test
    void allowsUpToMaxConcurrent() {
        PipelineRunGate gate = new PipelineRunGate(2);
        gate.acquire(1L, 100L);
        gate.acquire(2L, 200L);
        assertEquals(2, gate.runningCount());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> gate.acquire(3L, 300L));
        assertEquals(true, e.getMessage().contains("上限 2"));
    }

    @Test
    void releaseFreesSlot() {
        PipelineRunGate gate = new PipelineRunGate(1);
        gate.acquire(1L, 100L);
        assertThrows(IllegalStateException.class, () -> gate.acquire(2L, 200L));
        gate.release(1L, 100L);
        assertDoesNotThrow(() -> gate.acquire(2L, 200L));
        assertEquals(1, gate.runningCount());
    }

    @Test
    void sameProjectRejectedEvenUnderLimit() {
        PipelineRunGate gate = new PipelineRunGate(5);
        gate.acquire(1L, 100L);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> gate.acquire(2L, 100L));
        assertEquals(true, e.getMessage().contains("同一系统"), "同系统互斥优先于并发余量");
        gate.release(1L, 100L);
        assertDoesNotThrow(() -> gate.acquire(2L, 100L), "释放后同系统可以再跑");
    }

    @Test
    void nullProjectIdSkipsMutualExclusion() {
        PipelineRunGate gate = new PipelineRunGate(5);
        gate.acquire(1L, null);
        assertDoesNotThrow(() -> gate.acquire(2L, null), "未建库（projectId=null）不做同系统互斥");
        assertEquals(2, gate.runningCount());
    }

    @Test
    void maxClampedToAtLeastOne() {
        PipelineRunGate gate = new PipelineRunGate(0);
        gate.acquire(1L, null);
        assertThrows(IllegalStateException.class, () -> gate.acquire(2L, null));
        assertEquals(1, gate.maxRunning(), "0/负数配置按 1 处理，绝不允许全拒");
    }

    @Test
    void reAcquireSameIdRejectedAndReleaseIdempotent() {
        PipelineRunGate gate = new PipelineRunGate(2);
        gate.acquire(1L, 100L);
        assertThrows(IllegalStateException.class, () -> gate.acquire(1L, 100L), "同一条流水线不能重复占坑");
        gate.release(1L, 100L);
        gate.release(1L, 100L);
        assertEquals(0, gate.runningCount(), "重复 release 幂等");
    }

    @Test
    void concurrentAcquireRespectsLimit() throws InterruptedException {
        PipelineRunGate gate = new PipelineRunGate(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(10);
        java.util.List<java.util.concurrent.atomic.AtomicInteger> rejected =
                java.util.List.of(new java.util.concurrent.atomic.AtomicInteger());
        for (int i = 0; i < 10; i++) {
            final long id = i;
            new Thread(() -> {
                try {
                    start.await();
                    gate.acquire(id, 1000L + id);
                } catch (Exception e) {
                    rejected.get(0).incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(2, gate.runningCount(), "10 个并发抢坑只能进去 2 个");
        assertEquals(8, rejected.get(0).get());
    }
}
