package com.codegen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * V6.10 平台可观测性：启用定时任务。
 *
 * <p>平台此前没有任何调度能力，这里单独放配置类，不污染主启动类。
 * 采样线程池大小在 application.yml 的 spring.task.scheduling.pool.size 配置（默认 2）。
 */
@Configuration
@EnableScheduling
public class MonitorConfig {
}
