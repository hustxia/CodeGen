package com.codegen.monitor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时探针：确认运行时 http.server.requests 的直方图桶是否可用（S8 排查 p95 恒 0）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistogramConfigProbeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry registry;

    @Autowired(required = false)
    private org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties metricsProperties;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void httpServerRequestsHasHistogramBuckets() {
        System.out.println("[PROBE] bound percentiles-histogram = "
                + (metricsProperties == null ? "null"
                        : metricsProperties.getDistribution().getPercentilesHistogram()));
        var filters = context.getBeansOfType(io.micrometer.core.instrument.config.MeterFilter.class);
        var synthetic = new io.micrometer.core.instrument.Meter.Id("http.server.requests",
                io.micrometer.core.instrument.Tags.empty(), null, null,
                io.micrometer.core.instrument.Meter.Type.TIMER);
        filters.forEach((k, v) -> {
            var cfg = v.configure(synthetic, io.micrometer.core.instrument.distribution.DistributionStatisticConfig.DEFAULT);
            System.out.println("[PROBE] filter=" + k + " histogram=" + cfg.isPercentileHistogram()
                    + " buckets=" + (cfg.getMaximumExpectedValue() != null));
        });
        new RestTemplate().getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        var timers = registry.find("http.server.requests").timers();
        assertFalse(timers.isEmpty());
        int bucketBounds = 0;
        for (Timer t : timers) {
            bucketBounds += t.takeSnapshot().histogramCounts().length;
        }
        Timer manual = Timer.builder("http.server.requests")
                .tag("uri", "/probe-manual").tag("method", "GET").tag("status", "200")
                .register(registry);
        manual.record(java.time.Duration.ofMillis(120));
        int manualBuckets = manual.takeSnapshot().histogramCounts().length;
        Timer explicit = Timer.builder("probe.explicit").publishPercentileHistogram().register(registry);
        explicit.record(java.time.Duration.ofMillis(120));
        int explicitBuckets = explicit.takeSnapshot().histogramCounts().length;
        var isolated = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        Timer iso = Timer.builder("probe.iso").publishPercentileHistogram().register(isolated);
        iso.record(java.time.Duration.ofMillis(120));
        int isoBuckets = iso.takeSnapshot().histogramCounts().length;
        Timer slo = Timer.builder("probe.slo")
                .serviceLevelObjectives(java.time.Duration.ofMillis(50),
                        java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(500))
                .register(isolated);
        slo.record(java.time.Duration.ofMillis(120));
        int sloBuckets = slo.takeSnapshot().histogramCounts().length;
        Timer pct = Timer.builder("probe.pct").publishPercentiles(0.5, 0.95).register(isolated);
        pct.record(java.time.Duration.ofMillis(120));
        pct.record(java.time.Duration.ofMillis(300));
        int pctValues = pct.takeSnapshot().percentileValues().length;
        Timer minmax = Timer.builder("probe.minmax").publishPercentileHistogram()
                .minimumExpectedValue(java.time.Duration.ofMillis(1))
                .maximumExpectedValue(java.time.Duration.ofSeconds(30))
                .register(isolated);
        minmax.record(java.time.Duration.ofMillis(120));
        int minmaxBuckets = minmax.takeSnapshot().histogramCounts().length;
        System.out.println("[PROBE] timers=" + timers.size() + " bucketBoundCount=" + bucketBounds
                + " manualBuckets=" + manualBuckets + " explicitBuckets=" + explicitBuckets
                + " isoBuckets=" + isoBuckets + " sloBuckets=" + sloBuckets + " pctValues=" + pctValues
                + " minmaxBuckets=" + minmaxBuckets
                + " meterClass=" + timers.iterator().next().getClass().getName());
        assertTrue(bucketBounds > 0, "http.server.requests 直方图桶为空，percentiles-histogram 未生效");
    }
}
