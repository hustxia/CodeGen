package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * V6.10 可观测性采样配置。
 *
 * <p>窗口是纯内存的：5s 一采、360 点 = 30 分钟；重启即丢、多实例不共享。
 */
@Data
@Component
@ConfigurationProperties(prefix = "monitor")
public class MonitorProperties {

    private Sample sample = new Sample();

    @Data
    public static class Sample {

        /** 采样周期（毫秒），同时决定 QPS 的分母口径 */
        private long intervalMs = 5000;

        /** 环形缓冲容量；360 = 30 分钟 / 5s */
        private int bufferSize = 360;
    }
}
