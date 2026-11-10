package com.codegen.monitor;

import java.util.Map;

/**
 * 一次采样的不可变快照。
 *
 * <p>HTTP 与业务指标存的都是**区间差分**（本轮累计值 − 上轮累计值），
 * 因此「曲线上的点」与「窗口聚合」是同一口径，不会出现「累计值混进 30 分钟窗口」。
 *
 * @param timestamp      采样时刻 epoch ms
 * @param httpBucketDelta 本区间直方图桶计数（桶上界 ms -> 命中数），用于区间 P95/P99
 * @param httpByUri      本区间按 URI 的差分：key = "METHOD uri"，value = [请求数, 总耗时ms, 累计最大耗时ms]
 * @param business       本区间业务指标增量：key 形如 gen.write.success.count / pipeline.run.success
 */
public record MetricSnapshot(
        long timestamp,
        double heapUsedMb,
        double heapMaxMb,
        double nonHeapUsedMb,
        int threadsLive,
        long gcCount,
        double gcPauseMs,
        double cpuUsage,
        double uptimeSeconds,
        int cpuCount,
        long httpCount,
        double httpTotalMs,
        long http4xx,
        long http5xx,
        Map<Double, Long> httpBucketDelta,
        Map<String, double[]> httpByUri,
        Map<String, Double> business
) {

    /** 堆使用率 0~1；未拿到 max 时返回 0，避免前端显示 Infinity */
    public double heapUsedRatio() {
        return heapMaxMb > 0 ? Math.min(1.0, heapUsedMb / heapMaxMb) : 0;
    }

    /** 本区间平均耗时（ms） */
    public double avgMs() {
        return httpCount > 0 ? httpTotalMs / httpCount : 0;
    }

    /** 本区间 QPS */
    public double qps(double intervalSeconds) {
        return intervalSeconds > 0 ? httpCount / intervalSeconds : 0;
    }
}
