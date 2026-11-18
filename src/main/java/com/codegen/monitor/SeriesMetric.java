package com.codegen.monitor;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;

/**
 * /monitor/series 的指标白名单。
 *
 * <p>不允许把请求参数直接透传给 MeterRegistry：那等于对外开放整个指标树，
 * 还容易被构造出高基数查询。新增曲线只能加枚举值。
 */
public enum SeriesMetric {

    JVM_HEAP("jvm.heap", "MB"),
    JVM_THREADS("jvm.threads", "个"),
    PROCESS_CPU("process.cpu", "%"),
    HTTP_QPS("http.qps", "req/s"),
    HTTP_P95("http.p95", "ms");

    private final String code;
    private final String unit;

    SeriesMetric(String code, String unit) {
        this.code = code;
        this.unit = unit;
    }

    public String code() {
        return code;
    }

    public String unit() {
        return unit;
    }

    /**
     * 按 code 解析；非法值直接 400，不做兜底默认值（静默回退会让人以为图是对的）。
     */
    public static SeriesMetric of(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "缺少指标参数 metric");
        }
        for (SeriesMetric m : values()) {
            if (m.code.equals(code)) {
                return m;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的指标：" + code);
    }

    /**
     * 从一个样本取该指标的值。
     *
     * @param intervalSeconds QPS 的分母（采样周期换算成秒）
     */
    public double extract(MetricSnapshot s, double intervalSeconds) {
        double v = switch (this) {
            case JVM_HEAP -> s.heapUsedMb();
            case JVM_THREADS -> s.threadsLive();
            case PROCESS_CPU -> s.cpuUsage() * 100;
            case HTTP_QPS -> s.qps(intervalSeconds);
            case HTTP_P95 -> HttpStatsAggregator.percentile(s.httpBucketDelta(), 0.95);
        };
        return Math.round(v * 100) / 100.0;
    }
}
