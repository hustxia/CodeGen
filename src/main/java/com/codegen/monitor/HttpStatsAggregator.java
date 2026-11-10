package com.codegen.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 窗口内 HTTP 指标聚合 + 直方图分位数。
 *
 * <p>所有样本存的都是区间差分，这里只做求和与分位数，不再和 Micrometer 的累计值打交道。
 */
public final class HttpStatsAggregator {

    private HttpStatsAggregator() {
    }

    public static Stats aggregate(List<MetricSnapshot> window) {
        Stats stats = new Stats();
        for (MetricSnapshot s : window) {
            stats.totalRequests += s.httpCount();
            stats.totalMs += s.httpTotalMs();
            stats.errors4xx += s.http4xx();
            stats.errors5xx += s.http5xx();
            s.httpBucketDelta().forEach((bound, count) -> stats.buckets.merge(bound, count, Long::sum));
            s.httpByUri().forEach((key, delta) -> {
                double[] acc = stats.byUri.computeIfAbsent(key, k -> new double[3]);
                acc[0] += delta[0];
                acc[1] += delta[1];
                acc[2] = Math.max(acc[2], delta[2]);
            });
        }
        return stats;
    }

    /**
     * 直方图桶上做线性插值，返回 p 分位（p 取 0~1）。
     *
     * <p>桶只给上界，因此结果是近似值；对「判断快慢」足够，不用于计费类精确统计。
     * 没有数据时返回 0，避免 NaN 传到前端。
     */
    public static double percentile(Map<Double, Long> buckets, double p) {
        if (buckets == null || buckets.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (Long v : buckets.values()) {
            if (v != null && v > 0) {
                total += v;
            }
        }
        if (total <= 0) {
            return 0;
        }
        double target = total * p;
        List<Double> bounds = new ArrayList<>(buckets.keySet());
        Collections.sort(bounds);
        double prevBound = 0;
        long cumulative = 0;
        for (double bound : bounds) {
            Long c = buckets.get(bound);
            if (c == null || c <= 0) {
                continue;
            }
            if (cumulative + c >= target) {
                double fraction = (target - cumulative) / c;
                fraction = Math.max(0, Math.min(1, fraction));
                return prevBound + (bound - prevBound) * fraction;
            }
            cumulative += c;
            prevBound = bound;
        }
        return prevBound;
    }

    /** 窗口汇总结果 */
    public static final class Stats {

        private long totalRequests;
        private double totalMs;
        private long errors4xx;
        private long errors5xx;
        private final Map<Double, Long> buckets = new HashMap<>();
        /** key = "METHOD uri"，value = [请求数, 总耗时ms, 累计最大耗时ms] */
        private final Map<String, double[]> byUri = new HashMap<>();

        public long getTotalRequests() {
            return totalRequests;
        }

        public double getTotalMs() {
            return totalMs;
        }

        public long getErrors4xx() {
            return errors4xx;
        }

        public long getErrors5xx() {
            return errors5xx;
        }

        public Map<Double, Long> getBuckets() {
            return buckets;
        }

        public Map<String, double[]> getByUri() {
            return byUri;
        }
    }
}
