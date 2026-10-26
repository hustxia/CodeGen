package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * V5.0「一句话建系统」流水线配置。
 *
 * <p>本机开发默认值开箱即用；AI 建模的 key 只放 application-local.yml，
 * 仓库默认 enabled=false——没有 key 时前端降级「手动粘贴 DDL」，不影响其余步骤。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen.pipeline")
public class GenPipelineProperties {

    private Ai ai = new Ai();

    /** 打包后端用的 java 可执行文件目录（JDK 21+），留空用当前进程 java.home */
    private String javaHome;

    /** mvn 可执行文件路径，留空回落 gen.output.maven → PATH 里的 mvn */
    private String maven;

    /** npm 可执行文件（Windows 上是 npm.cmd），留空用 npm */
    private String npm = "npm";

    /** 后端端口分配起点（被占自动顺延） */
    private int backendPortBase = 8140;

    /** 前端 dev 端口分配起点 */
    private int frontendPortBase = 9040;

    /** 新建系统的自检 classpath（脚手架工程依赖与 demo 基座一致，默认复用 demo-lib） */
    private String selfcheckClasspath;

    /** 同一时刻允许的运行中流水线数，M1 固定 1 */
    private int maxRunning = 1;

    /** 子进程启动后等待端口就绪的超时秒数 */
    private int deployTimeoutSeconds = 120;

    /** 部署日志目录 */
    private String logDir = "logs/pipeline";

    @Data
    public static class Ai {
        /** 是否启用 AI 建模；false 时接口直接报错，前端降级手动 DDL */
        private boolean enabled = false;

        /** OpenAI 兼容接口地址，如 https://api.xxx.com/v1 */
        private String baseUrl;

        /** API Key（只放 local 配置） */
        private String apiKey;

        /** 模型名 */
        private String model;

        /** 请求超时秒数 */
        private int timeoutSeconds = 30;
    }
}
