package com.codegen.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 业务埋点的安全包装。
 *
 * <p>Micrometer 的调用全部包在 try/catch 里：指标坏掉不能连带生成主流程失败。
 * 失败只记 debug——采样本身有告警条，埋点异常不值得刷业务日志。
 */
@Component
public class MonitorMetrics {

    private static final Logger log = LoggerFactory.getLogger(MonitorMetrics.class);

    private final MeterRegistry registry;

    public MonitorMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    /** 停止计时并打上 tag；tags 按 key,value 成对传入 */
    public void stop(Timer.Sample sample, String name, String... tags) {
        try {
            Timer.Builder builder = Timer.builder(name);
            for (int i = 0; i + 1 < tags.length; i += 2) {
                builder.tag(tags[i], tags[i + 1]);
            }
            sample.stop(builder.register(registry));
        } catch (Exception e) {
            log.debug("指标埋点失败（已忽略）：{} - {}", name, e.getMessage());
        }
    }

    public void count(String name, long amount, String... tags) {
        try {
            Counter.Builder builder = Counter.builder(name);
            for (int i = 0; i + 1 < tags.length; i += 2) {
                builder.tag(tags[i], tags[i + 1]);
            }
            builder.register(registry).increment(amount);
        } catch (Exception e) {
            log.debug("指标埋点失败（已忽略）：{} - {}", name, e.getMessage());
        }
    }

    /**
     * 写盘收尾：一次 timer + 按结果状态的文件数。
     *
     * @param fileStatusCounts 状态 -> 文件数（WRITTEN / MERGED / SKIPPED / ERROR）
     */
    public void finishWrite(Timer.Sample sample, boolean success, Map<String, Integer> fileStatusCounts) {
        try {
            fileStatusCounts.forEach((status, count) ->
                    count("gen.write.files", count, "status", status));
        } catch (Exception e) {
            log.debug("写盘文件数埋点失败（已忽略）：{}", e.getMessage());
        }
        stop(sample, "gen.write", "result", success ? "success" : "fail");
    }
}
