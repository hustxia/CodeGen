package com.codegen.monitor;

import com.codegen.config.MonitorProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标环形缓冲：写只发生在调度线程，读来自 HTTP 线程，因此方法统一 synchronized。
 *
 * <p>满了淘汰最旧样本，不扩容——宁可少一段历史，也不要内存随运行时长上涨。
 */
@Component
public class MonitorStore {

    private final int capacity;
    private final ArrayDeque<MetricSnapshot> buffer;
    private final AtomicLong lastSampleAt = new AtomicLong(0);

    public MonitorStore(MonitorProperties props) {
        this.capacity = Math.max(2, props.getSample().getBufferSize());
        this.buffer = new ArrayDeque<>(this.capacity);
    }

    public synchronized void add(MetricSnapshot snapshot) {
        if (buffer.size() >= capacity) {
            buffer.pollFirst();
        }
        buffer.addLast(snapshot);
        lastSampleAt.set(snapshot.timestamp());
    }

    /** 最新样本；服务刚启动还没采到时为 null */
    public synchronized MetricSnapshot latest() {
        return buffer.peekLast();
    }

    /** 时间升序返回不早于 fromTimestamp 的样本 */
    public synchronized List<MetricSnapshot> since(long fromTimestamp) {
        List<MetricSnapshot> list = new ArrayList<>();
        for (MetricSnapshot s : buffer) {
            if (s.timestamp() >= fromTimestamp) {
                list.add(s);
            }
        }
        return list;
    }

    public synchronized int size() {
        return buffer.size();
    }

    public int capacity() {
        return capacity;
    }

    /** 最近一次成功采样时间（epoch ms）；0 = 从未采样成功 */
    public long lastSampleAt() {
        return lastSampleAt.get();
    }

    /** 仅供单测/排障使用 */
    public synchronized void clear() {
        buffer.clear();
        lastSampleAt.set(0);
    }
}
