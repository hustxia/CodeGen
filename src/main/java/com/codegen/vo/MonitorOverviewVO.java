package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * V6.10 监控页实时概览。
 */
@Data
@Schema(description = "监控实时概览")
public class MonitorOverviewVO {

    @Schema(description = "采样是否在走（15s 内有新样本）")
    private boolean sampling;

    @Schema(description = "最近一次采样时间 epoch ms；从未采样为 null")
    private Long lastSampleAt;

    private long intervalMs;
    private int bufferSize;
    private int sampledPoints;

    private Jvm jvm = new Jvm();
    private Http http = new Http();

    @Data
    @Schema(description = "JVM / 进程快照")
    public static class Jvm {
        private double heapUsedMb;
        private double heapMaxMb;
        private double heapUsedRatio;
        private double nonHeapUsedMb;
        private int threadsLive;
        private long gcCount;
        private double gcPauseMs;
        private double cpuUsage;
        private int cpuCount;
        private double uptimeSeconds;
    }

    @Data
    @Schema(description = "最近一个采样区间的 HTTP 指标")
    public static class Http {
        private double qps;
        private double avgMs;
        private double p95Ms;
        private double p99Ms;
        private long requests;
        private long errors4xx;
        private long errors5xx;
    }
}
