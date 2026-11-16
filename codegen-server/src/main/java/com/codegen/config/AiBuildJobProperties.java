package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * V9.6 AI 建表分批任务配置。
 *
 * <p>{@code worker-threads} 不是越大越好：瓶颈在 LLM 侧。云端 API 可开到 4；
 * 本地纯 CPU 跑 7B/8B 时并发只会互相抢核，建议 1。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen.ai-build.job")
public class AiBuildJobProperties {

    /** 总开关：关闭后 large/huge 回落到同步串行（旧行为） */
    private boolean enabled = true;

    /** 并发执行的批次数 */
    private int workerThreads = 2;

    /** 调度扫描间隔（毫秒） */
    private long intervalMs = 2000;

    /** 单批超时秒数：超过则判为卡死，重置为 pending 重试 */
    private int batchTimeoutSeconds = 900;

    /** 单批最大重试次数（不含首次） */
    private int maxRetry = 2;

    /** 模块数上限，超出部分不建模（防止误配把任务排到明天） */
    private int moduleCap = 40;
}
