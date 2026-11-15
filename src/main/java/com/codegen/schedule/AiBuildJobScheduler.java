package com.codegen.schedule;

import com.codegen.config.AiBuildJobProperties;
import com.codegen.service.AiBuildJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * V9.6 分批建模任务调度器。
 *
 * <p>每 {@code interval-ms} 扫一次：先回收超时批次（进程被杀/连接挂死留下的 running），
 * 再按剩余空闲 worker 数认领 pending 批次。认领在 SQL 条件里完成，多实例并存也不会重复执行。
 *
 * <p>单次 tick 用 {@link AtomicBoolean} 串行化：避免上一轮还在认领时下一轮又进来，
 * 把同一批重复提交进线程池。
 */
@Slf4j
@Component
public class AiBuildJobScheduler {

    @Autowired
    private AiBuildJobService jobService;
    @Autowired
    private AiBuildJobProperties props;

    private final AtomicBoolean ticking = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${gen.ai-build.job.interval-ms:2000}")
    public void tick() {
        if (!props.isEnabled() || !ticking.compareAndSet(false, true)) {
            return;
        }
        try {
            jobService.resetStuck();
            int submitted = jobService.dispatch(Math.max(1, props.getWorkerThreads()));
            if (submitted > 0) {
                log.debug("V9.6 本轮认领 {} 个批次", submitted);
            }
        } catch (Exception e) {
            log.warn("V9.6 分批任务调度异常: {}", e.getMessage());
        } finally {
            ticking.set(false);
        }
    }
}
