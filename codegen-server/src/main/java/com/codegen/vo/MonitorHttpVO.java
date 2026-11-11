package com.codegen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * V6.10 HTTP 指标窗口聚合。
 */
@Data
@Schema(description = "HTTP 指标聚合")
public class MonitorHttpVO {

    private int minutes;

    @Schema(description = "窗口还没攒满时为 true")
    private boolean warmup;

    private long totalRequests;
    private double qps;
    private double avgMs;
    private double p95Ms;
    private double p99Ms;

    @Schema(description = "错误率百分比（4xx+5xx）/ 总数")
    private double errorRate;

    private long errors4xx;
    private long errors5xx;

    private List<SlowUri> topSlow = new ArrayList<>();

    @Data
    @Schema(description = "慢接口（URI 为 Micrometer 模板）")
    public static class SlowUri {
        private String uri;
        private String method;
        private long count;
        private double avgMs;
        private double maxMs;
    }
}
