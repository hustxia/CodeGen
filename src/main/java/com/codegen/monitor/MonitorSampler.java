package com.codegen.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 定时采样：把 Micrometer 的累计值换算成**区间差分**后放进环形缓冲。
 *
 * <p>为什么不直接存累计值：`http.server.requests` 的 count/totalTime 是从启动累计的，
 * 直接展示会让「近 30 分钟」的曲线一路向上，窗口聚合也失去意义。
 *
 * <p>采样失败只 warn，不抛出：抛出会刷错误日志，且 `fixedDelay` 任务异常后
 * 也不会补采，面板只会静默停更。
 */
@Component
public class MonitorSampler {

    private static final Logger log = LoggerFactory.getLogger(MonitorSampler.class);

    private static final String HTTP_METRIC = "http.server.requests";
    private static final double MB = 1024d * 1024d;

    private final MeterRegistry registry;
    private final MonitorStore store;

    /** 上一轮各 HTTP timer 的 [count, totalMs] */
    private Map<String, double[]> prevHttpTimers = new HashMap<>();
    /** 上一轮聚合后的直方图桶累计值 */
    private Map<Double, Long> prevBuckets = new HashMap<>();
    /** 上一轮业务指标累计值 */
    private Map<String, Double> prevBusiness = new HashMap<>();

    public MonitorSampler(MeterRegistry registry, MonitorStore store) {
        this.registry = registry;
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${monitor.sample.interval-ms:5000}")
    public void sampleOnce() {
        try {
            store.add(collect());
        } catch (Exception e) {
            // 单轮失败不能让定时任务停摆：下一轮 fixedDelay 继续执行
            log.warn("指标采样失败（不影响业务）：{}", e.getMessage());
        }
    }

    /**
     * 采一轮。protected 是为了单测能覆盖成抛异常，验证失败隔离。
     */
    protected MetricSnapshot collect() {
        long now = System.currentTimeMillis();
        HttpDelta http = collectHttp();
        Map<String, Double> business = collectBusinessDelta();

        return new MetricSnapshot(
                now,
                sumGauges("jvm.memory.used", "area", "heap") / MB,
                sumGauges("jvm.memory.max", "area", "heap") / MB,
                sumGauges("jvm.memory.used", "area", "nonheap") / MB,
                (int) gauge("jvm.threads.live"),
                gcCount(),
                gcPauseMs(),
                finite(gauge("process.cpu.usage")),
                finite(gauge("process.uptime")),
                (int) gauge("system.cpu.count"),
                http.count,
                http.totalMs,
                http.e4xx,
                http.e5xx,
                http.buckets,
                http.byUri,
                business
        );
    }

    // ---------------------------------------------------------------- HTTP

    private HttpDelta collectHttp() {
        Map<String, double[]> current = new HashMap<>();
        Map<Double, Long> bucketTotals = new TreeMap<>();

        for (Timer timer : registry.find(HTTP_METRIC).timers()) {
            String method = timer.getId().getTag("method");
            String uri = timer.getId().getTag("uri");
            String status = timer.getId().getTag("status");
            String key = (method == null ? "?" : method) + "|" + (uri == null ? "unknown" : uri)
                    + "|" + (status == null ? "" : status);
            current.put(key, new double[]{
                    timer.count(),
                    timer.totalTime(TimeUnit.MILLISECONDS),
                    timer.max(TimeUnit.MILLISECONDS)
            });

            for (var bucket : timer.takeSnapshot().histogramCounts()) {
                bucketTotals.merge(bucket.bucket(), (long) bucket.count(), Long::sum);
            }
        }

        HttpDelta delta = new HttpDelta();
        for (Map.Entry<String, double[]> entry : current.entrySet()) {
            double[] cur = entry.getValue();
            double[] prev = prevHttpTimers.get(entry.getKey());
            long dc = (long) Math.max(0, cur[0] - (prev == null ? 0 : prev[0]));
            double dms = Math.max(0, cur[1] - (prev == null ? 0 : prev[1]));
            if (dc == 0) {
                continue;
            }
            delta.count += dc;
            delta.totalMs += dms;

            String[] parts = entry.getKey().split("\\|", -1);
            String status = parts.length > 2 ? parts[2] : "";
            if (status.startsWith("4")) {
                delta.e4xx += dc;
            } else if (status.startsWith("5")) {
                delta.e5xx += dc;
            }

            String uriKey = parts[0] + " " + parts[1];
            double[] acc = delta.byUri.computeIfAbsent(uriKey, k -> new double[3]);
            acc[0] += dc;
            acc[1] += dms;
            acc[2] = Math.max(acc[2], cur[2]);
        }
        prevHttpTimers = current;

        for (Map.Entry<Double, Long> entry : bucketTotals.entrySet()) {
            long prev = prevBuckets.getOrDefault(entry.getKey(), 0L);
            long d = Math.max(0, entry.getValue() - prev);
            if (d > 0) {
                delta.buckets.put(entry.getKey(), d);
            }
        }
        prevBuckets = bucketTotals;
        return delta;
    }

    // ---------------------------------------------------------------- 业务

    private Map<String, Double> collectBusinessDelta() {
        Map<String, Double> current = new LinkedHashMap<>();
        sumTimers(current, "gen.write", "result");
        sumTimers(current, "gen.preview", null);
        sumTimers(current, "pipeline.step", "step");
        sumTimers(current, "doc.convert", "result");
        sumCounters(current, "gen.write.files", "status");
        sumCounters(current, "pipeline.run", "state");

        Map<String, Double> delta = new HashMap<>();
        for (Map.Entry<String, Double> entry : current.entrySet()) {
            double prev = prevBusiness.getOrDefault(entry.getKey(), 0.0);
            delta.put(entry.getKey(), Math.max(0, entry.getValue() - prev));
        }
        prevBusiness = current;
        return delta;
    }

    private void sumTimers(Map<String, Double> out, String name, String tagKey) {
        for (Timer t : registry.find(name).timers()) {
            String prefix = name;
            if (tagKey != null) {
                String tag = t.getId().getTag(tagKey);
                if (tag != null) {
                    prefix = name + "." + tag;
                }
            }
            out.merge(prefix + ".count", (double) t.count(), Double::sum);
            out.merge(prefix + ".ms", t.totalTime(TimeUnit.MILLISECONDS), Double::sum);
        }
    }

    private void sumCounters(Map<String, Double> out, String name, String tagKey) {
        for (Counter c : registry.find(name).counters()) {
            String prefix = name;
            if (tagKey != null) {
                String tag = c.getId().getTag(tagKey);
                if (tag != null) {
                    prefix = name + "." + tag;
                }
            }
            out.merge(prefix, c.count(), Double::sum);
        }
    }

    // ---------------------------------------------------------------- JVM

    /** 同一个指标可能按内存池拆成多个 gauge，必须求和 */
    private double sumGauges(String name, String tagKey, String tagValue) {
        double sum = 0;
        for (Gauge g : registry.find(name).tag(tagKey, tagValue).gauges()) {
            double v = g.value();
            // jvm.memory.max 在某些池上返回 -1（未定义），跳过而不是把总量拉低
            if (Double.isFinite(v) && v > 0) {
                sum += v;
            }
        }
        return sum;
    }

    private double gauge(String name) {
        Gauge g = registry.find(name).gauge();
        return g == null ? 0 : g.value();
    }

    private long gcCount() {
        long sum = 0;
        for (Timer t : registry.find("jvm.gc.pause").timers()) {
            sum += t.count();
        }
        return sum;
    }

    private double gcPauseMs() {
        double sum = 0;
        for (Timer t : registry.find("jvm.gc.pause").timers()) {
            sum += t.totalTime(TimeUnit.MILLISECONDS);
        }
        return sum;
    }

    private double finite(double v) {
        return Double.isFinite(v) ? v : 0;
    }

    /** 一轮 HTTP 差分 */
    private static final class HttpDelta {
        private long count;
        private double totalMs;
        private long e4xx;
        private long e5xx;
        private final Map<Double, Long> buckets = new HashMap<>();
        private final Map<String, double[]> byUri = new HashMap<>();
    }
}
