package com.codegen.schedule;

import com.codegen.service.TestScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * V9.2 定时回归扫描器：每 {@code codegen.test.schedule.interval-ms}（默认 30s）扫描一次到期任务。
 *
 * <p>扫描本身轻量（只查启用任务 + cron 计算），真正的执行在 {@link TestScheduleService#scanAndTrigger()} 内
 * 逐任务加锁认领后同步触发。{@code running} 守卫保证上一次扫描未结束时不会重叠启动。
 * 启动兜底在 {@code ApplicationReadyEvent} 里复位残留认领哨兵。
 */
@Component
public class TestScheduleScheduler {

    private static final Logger log = LoggerFactory.getLogger(TestScheduleScheduler.class);

    @Autowired
    private TestScheduleService scheduleService;

    @Value("${codegen.test.schedule.enabled:true}")
    private boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        try {
            scheduleService.resetStuckClaims();
        } catch (Exception e) {
            log.warn("定时回归启动兜底失败（可忽略，下个周期重试）: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${codegen.test.schedule.interval-ms:30000}")
    public void tick() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduleService.scanAndTrigger();
        } catch (Exception e) {
            log.warn("定时回归扫描异常: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
