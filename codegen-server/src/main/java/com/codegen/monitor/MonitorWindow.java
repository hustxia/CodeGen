package com.codegen.monitor;

import com.codegen.config.MonitorProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询窗口的钳位与 warmup 判定。
 *
 * <p>分钟数越界不报错而是钳位：面板每 5s 轮询，参数写错时应该继续出图，
 * 而不是整页报红。
 */
@Component
public class MonitorWindow {

    public static final int DEFAULT_MINUTES = 30;
    public static final int MAX_MINUTES = 30;
    public static final int DEFAULT_TOP_N = 10;
    public static final int MAX_TOP_N = 50;

    private final MonitorStore store;
    private final MonitorProperties props;

    public MonitorWindow(MonitorStore store, MonitorProperties props) {
        this.store = store;
        this.props = props;
    }

    public static int clampMinutes(Integer minutes) {
        if (minutes == null) {
            return DEFAULT_MINUTES;
        }
        return Math.max(1, Math.min(MAX_MINUTES, minutes));
    }

    public static int clampTopN(Integer topN) {
        if (topN == null) {
            return DEFAULT_TOP_N;
        }
        return Math.max(1, Math.min(MAX_TOP_N, topN));
    }

    /** 采样周期换算成秒，作为 QPS 的分母 */
    public double intervalSeconds() {
        return Math.max(0.001, props.getSample().getIntervalMs() / 1000.0);
    }

    public List<MetricSnapshot> of(int minutes) {
        long from = System.currentTimeMillis() - minutes * 60_000L;
        return store.since(from);
    }

    /**
     * 窗口是否还没攒满。前端据此显示「数据积累中」，而不是把不足的折线当成断线。
     */
    public boolean warmup(List<MetricSnapshot> window, int minutes) {
        long expected = minutes * 60_000L / Math.max(1, props.getSample().getIntervalMs());
        return window.size() < Math.min(expected, store.capacity());
    }
}
