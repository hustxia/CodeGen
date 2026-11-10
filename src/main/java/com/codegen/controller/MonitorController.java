package com.codegen.controller;

import com.codegen.common.context.UserContext;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import com.codegen.common.result.ResultCode;
import com.codegen.config.MonitorProperties;
import com.codegen.monitor.HttpStatsAggregator;
import com.codegen.monitor.MetricSnapshot;
import com.codegen.monitor.MonitorStore;
import com.codegen.monitor.MonitorWindow;
import com.codegen.monitor.SeriesMetric;
import com.codegen.service.PermissionService;
import com.codegen.vo.MonitorBusinessVO;
import com.codegen.vo.MonitorHttpVO;
import com.codegen.vo.MonitorOverviewVO;
import com.codegen.vo.MonitorSeriesVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.10 系统监控聚合接口。
 *
 * <p>前端不直接打 Actuator：这里把 Micrometer 的原始结构收敛成页面要的形状，
 * 同时统一加 monitor:view 权限，避免把整个指标树暴露给任意登录用户。
 */
@Tag(name = "系统监控")
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Autowired
    private MonitorStore store;
    @Autowired
    private MonitorWindow window;
    @Autowired
    private MonitorProperties props;
    @Autowired
    private PermissionService permissionService;

    @Operation(summary = "实时概览")
    @GetMapping("/overview")
    public Result<MonitorOverviewVO> overview() {
        require();
        long interval = props.getSample().getIntervalMs();
        long last = store.lastSampleAt();
        MetricSnapshot latest = store.latest();

        MonitorOverviewVO vo = new MonitorOverviewVO();
        vo.setIntervalMs(interval);
        vo.setBufferSize(store.capacity());
        vo.setSampledPoints(store.size());
        vo.setLastSampleAt(last > 0 ? last : null);
        vo.setSampling(last > 0 && System.currentTimeMillis() - last <= interval * 3);
        if (latest == null) {
            return Result.ok(vo);
        }

        MonitorOverviewVO.Jvm jvm = vo.getJvm();
        jvm.setHeapUsedMb(r2(latest.heapUsedMb()));
        jvm.setHeapMaxMb(r2(latest.heapMaxMb()));
        jvm.setHeapUsedRatio(r2(latest.heapUsedRatio()));
        jvm.setNonHeapUsedMb(r2(latest.nonHeapUsedMb()));
        jvm.setThreadsLive(latest.threadsLive());
        jvm.setGcCount(latest.gcCount());
        jvm.setGcPauseMs(r2(latest.gcPauseMs()));
        jvm.setCpuUsage(r2(latest.cpuUsage() * 100));
        jvm.setCpuCount(latest.cpuCount());
        jvm.setUptimeSeconds(r2(latest.uptimeSeconds()));

        MonitorOverviewVO.Http http = vo.getHttp();
        http.setRequests(latest.httpCount());
        http.setQps(r2(latest.qps(window.intervalSeconds())));
        http.setAvgMs(r2(latest.avgMs()));
        http.setP95Ms(r2(HttpStatsAggregator.percentile(latest.httpBucketDelta(), 0.95)));
        http.setP99Ms(r2(HttpStatsAggregator.percentile(latest.httpBucketDelta(), 0.99)));
        http.setErrors4xx(latest.http4xx());
        http.setErrors5xx(latest.http5xx());
        return Result.ok(vo);
    }

    @Operation(summary = "指标趋势序列")
    @GetMapping("/series")
    public Result<MonitorSeriesVO> series(
            @RequestParam(value = "metric", required = false) String metric,
            @RequestParam(value = "minutes", required = false) Integer minutes) {
        require();
        SeriesMetric m = SeriesMetric.of(metric);
        int min = MonitorWindow.clampMinutes(minutes);
        List<MetricSnapshot> windowList = window.of(min);
        double intervalSeconds = window.intervalSeconds();

        MonitorSeriesVO vo = new MonitorSeriesVO();
        vo.setMetric(m.code());
        vo.setUnit(m.unit());
        vo.setMinutes(min);
        vo.setWarmup(window.warmup(windowList, min));
        List<MonitorSeriesVO.Point> points = new ArrayList<>(windowList.size());
        for (MetricSnapshot s : windowList) {
            points.add(new MonitorSeriesVO.Point(s.timestamp(), m.extract(s, intervalSeconds)));
        }
        vo.setPoints(points);
        return Result.ok(vo);
    }

    @Operation(summary = "HTTP 指标聚合与慢接口排行")
    @GetMapping("/http")
    public Result<MonitorHttpVO> http(
            @RequestParam(value = "minutes", required = false) Integer minutes,
            @RequestParam(value = "topN", required = false) Integer topN) {
        require();
        int min = MonitorWindow.clampMinutes(minutes);
        int limit = MonitorWindow.clampTopN(topN);
        List<MetricSnapshot> windowList = window.of(min);
        HttpStatsAggregator.Stats stats = HttpStatsAggregator.aggregate(windowList);

        MonitorHttpVO vo = new MonitorHttpVO();
        vo.setMinutes(min);
        vo.setWarmup(window.warmup(windowList, min));
        vo.setTotalRequests(stats.getTotalRequests());
        vo.setErrors4xx(stats.getErrors4xx());
        vo.setErrors5xx(stats.getErrors5xx());

        double seconds = windowSeconds(windowList);
        vo.setQps(r2(stats.getTotalRequests() / seconds));
        double avgMs = stats.getTotalRequests() > 0 ? stats.getTotalMs() / stats.getTotalRequests() : 0;
        vo.setAvgMs(r2(avgMs));
        vo.setP95Ms(r2(HttpStatsAggregator.percentile(stats.getBuckets(), 0.95)));
        vo.setP99Ms(r2(HttpStatsAggregator.percentile(stats.getBuckets(), 0.99)));
        long errors = stats.getErrors4xx() + stats.getErrors5xx();
        vo.setErrorRate(stats.getTotalRequests() > 0
                ? r2(errors * 100.0 / stats.getTotalRequests()) : 0);

        List<MonitorHttpVO.SlowUri> slow = new ArrayList<>();
        stats.getByUri().forEach((key, delta) -> {
            if (delta[0] <= 0) {
                return;
            }
            int split = key.indexOf(' ');
            MonitorHttpVO.SlowUri item = new MonitorHttpVO.SlowUri();
            item.setMethod(split > 0 ? key.substring(0, split) : "");
            item.setUri(split > 0 ? key.substring(split + 1) : key);
            item.setCount((long) delta[0]);
            item.setAvgMs(r2(delta[1] / delta[0]));
            item.setMaxMs(r2(delta[2]));
            slow.add(item);
        });
        slow.sort(Comparator.comparingDouble(MonitorHttpVO.SlowUri::getAvgMs).reversed());
        vo.setTopSlow(slow.size() > limit ? new ArrayList<>(slow.subList(0, limit)) : slow);
        return Result.ok(vo);
    }

    @Operation(summary = "业务指标聚合")
    @GetMapping("/business")
    public Result<MonitorBusinessVO> business(
            @RequestParam(value = "minutes", required = false) Integer minutes) {
        require();
        int min = MonitorWindow.clampMinutes(minutes);
        List<MetricSnapshot> windowList = window.of(min);
        Map<String, Double> sums = sumBusiness(windowList);

        MonitorBusinessVO vo = new MonitorBusinessVO();
        vo.setMinutes(min);
        vo.setWarmup(window.warmup(windowList, min));

        // 代码生成
        MonitorBusinessVO.Gen gen = vo.getGen();
        long success = Math.round(sum(sums, "gen.write.success.count"));
        long failed = Math.round(sum(sums, "gen.write.fail.count"));
        long batches = success + failed;
        gen.setBatches(batches);
        gen.setSuccess(success);
        gen.setFailed(failed);
        gen.setSuccessRate(batches > 0 ? r2(success * 100.0 / batches) : 0);
        gen.setAvgMs(batches > 0
                ? r2((sum(sums, "gen.write.success.ms") + sum(sums, "gen.write.fail.ms")) / batches) : 0);
        long fileTotal = 0;
        for (String status : List.of("WRITTEN", "MERGED", "SKIPPED", "ERROR")) {
            long n = Math.round(sum(sums, "gen.write.files." + status));
            gen.getWriteFiles().put(status, n);
            fileTotal += n;
        }
        gen.setWriteFileTotal(fileTotal);

        // 流水线
        MonitorBusinessVO.Pipeline pipeline = vo.getPipeline();
        long runSuccess = Math.round(sum(sums, "pipeline.run.success"));
        long runFailed = Math.round(sum(sums, "pipeline.run.failed"));
        long runStopped = Math.round(sum(sums, "pipeline.run.stopped"));
        pipeline.setSuccess(runSuccess);
        pipeline.setFailed(runFailed);
        pipeline.setStopped(runStopped);
        pipeline.setRuns(runSuccess + runFailed + runStopped);
        pipeline.setSteps(stepStats(sums));

        // 文档转换
        MonitorBusinessVO.Doc doc = vo.getDoc();
        long hit = Math.round(sum(sums, "doc.convert.hit.count"));
        long converted = Math.round(sum(sums, "doc.convert.converted.count"));
        long docFailed = Math.round(sum(sums, "doc.convert.failed.count"));
        doc.setHit(hit);
        doc.setConverted(converted);
        doc.setFailed(docFailed);
        long docTotal = hit + converted + docFailed;
        doc.setAvgMs(docTotal > 0
                ? r2((sum(sums, "doc.convert.hit.ms") + sum(sums, "doc.convert.converted.ms")
                + sum(sums, "doc.convert.failed.ms")) / docTotal) : 0);
        return Result.ok(vo);
    }

    // ---------------------------------------------------------------- 内部

    private Map<String, Double> sumBusiness(List<MetricSnapshot> windowList) {
        Map<String, Double> sums = new HashMap<>();
        for (MetricSnapshot s : windowList) {
            s.business().forEach((k, v) -> sums.merge(k, v, Double::sum));
        }
        return sums;
    }

    private List<MonitorBusinessVO.Pipeline.Step> stepStats(Map<String, Double> sums) {
        String prefix = "pipeline.step.";
        Map<String, double[]> agg = new LinkedHashMap<>();
        sums.forEach((key, value) -> {
            if (!key.startsWith(prefix) || !key.endsWith(".count")) {
                return;
            }
            String step = key.substring(prefix.length(), key.length() - ".count".length());
            agg.computeIfAbsent(step, k -> new double[2])[0] += value;
        });
        sums.forEach((key, value) -> {
            if (!key.startsWith(prefix) || !key.endsWith(".ms")) {
                return;
            }
            String step = key.substring(prefix.length(), key.length() - ".ms".length());
            agg.computeIfAbsent(step, k -> new double[2])[1] += value;
        });

        List<MonitorBusinessVO.Pipeline.Step> steps = new ArrayList<>();
        agg.forEach((step, v) -> {
            MonitorBusinessVO.Pipeline.Step item = new MonitorBusinessVO.Pipeline.Step();
            item.setStep(step);
            item.setCount((long) v[0]);
            item.setAvgMs(v[0] > 0 ? r2(v[1] / v[0]) : 0);
            steps.add(item);
        });
        steps.sort(Comparator.comparingDouble(MonitorBusinessVO.Pipeline.Step::getAvgMs).reversed());
        return steps;
    }

    /** 窗口实际跨度（秒），不足两个点时退回一个采样周期，避免除零 */
    private double windowSeconds(List<MetricSnapshot> windowList) {
        if (windowList.size() < 2) {
            return window.intervalSeconds();
        }
        long span = windowList.get(windowList.size() - 1).timestamp() - windowList.get(0).timestamp();
        return Math.max(window.intervalSeconds(), span / 1000.0 + window.intervalSeconds());
    }

    private double sum(Map<String, Double> sums, String key) {
        Double v = sums.get(key);
        return v == null ? 0 : v;
    }

    private double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /**
     * 权限码校验：超管直通，其余用户按「角色 → 菜单 perms」判定。
     */
    private void require() {
        Long userId = UserContext.getUserId();
        if (permissionService.isSuperAdmin(userId)) {
            return;
        }
        if (!permissionService.permissionsOf(userId).contains("monitor:view")) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 参数非法要真的返回 400：全局异常处理器会把业务异常包成 HTTP 200，
     * 而 AC5 要求 /monitor/series?metric=evil 直接 400。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        int status = e.getCode() == ResultCode.UNAUTHORIZED.getCode() ? 401
                : e.getCode() == ResultCode.FORBIDDEN.getCode() ? 403
                : e.getCode() == ResultCode.NOT_FOUND.getCode() ? 404
                : 400;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(e.getCode(), e.getMessage()));
    }
}
