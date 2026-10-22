package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代码生成器输出配置（全局默认值）。
 * 表级 gen_table.backend_root / frontend_root / backend_path 留空时回落到此处，
 * 避免每张表重复填写前后端仓库路径。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen.output")
public class GenOutputProperties {

    /**
     * 目标模块目录映射（V1.4-3）：gen_table.target_module 命中这里时，
     * 按模块的后端路径/包名/网关前缀输出，做到一套生成器输出多个微服务。
     * key 为模块名（admin / product / order ...），value 为模块级输出配置。
     */
    private Map<String, ModuleConfig> modules = new LinkedHashMap<>();

    /**
     * 单个模块的输出配置
     */
    @Data
    public static class ModuleConfig {

        /**
         * 后端输出基准相对路径（相对 backendRoot）
         */
        private String backendPath;

        /**
         * Java 包名
         */
        private String packageName;

        /**
         * 基座根包名（生成物 import Result/BaseEntity/PageQuery 用），留空沿用全局
         */
        private String rootPackage;

        /**
         * 网关服务前缀（前端请求 /api/xxx 中的 xxx）
         */
        private String gatewayPrefix;

        /**
         * 该模块前端输出相对路径（相对 frontend-root），留空沿用全局
         */
        private String frontendPath;

        /**
         * 模块后端仓库根目录（可选）。多仓库时填；留空沿用全局 backend-root
         */
        private String backendRoot;
    }

    /**
     * 后端仓库根目录（绝对路径），写盘基准
     */
    private String backendRoot;

    /**
     * 前端仓库根目录（绝对路径），写盘基准
     */
    private String frontendRoot;

    /**
     * 前端输出基准相对路径（相对 frontendRoot）。
     *
     * <p>模板里的产物路径形如 {@code src/api/xx.js}、{@code src/views/xx/index.vue}，
     * 这是「前端工程根」的相对路径。目标前端若不在仓库根（例如 monorepo 下的 demo-web/），
     * 把本项指向该子目录即可，无需为每种目录形态改模板。
     */
    private String frontendPath = "";

    /**
     * 后端输出基准相对路径（相对 backendRoot）
     */
    private String backendPath = "demo-server/src/main/java/com/demo";

    /**
     * 默认 Java 包名
     */
    private String packageName = "com.demo";

    /**
     * 默认基座根包名：生成物里的 Result / BaseEntity / PageQuery / BusinessException
     * 都按 {@code {rootPackage}.common.*} 引用，需与目标工程保持一致
     */
    private String rootPackage = "com.demo";

    /**
     * 默认网关服务前缀
     */
    private String gatewayPrefix = "demo";

    /**
     * maven 可执行文件（一键生成的可选编译阶段用）。
     * 留空依次尝试 MAVEN_HOME/bin/mvn、PATH 里的 mvn
     */
    private String maven;

    /**
     * 编译超时（秒）
     */
    private int compileTimeoutSeconds = 300;

    /**
     * 保留最近多少个批次的备份目录（V4.2 P1-2），<=0 表示不按批次数清理
     */
    private int backupKeepBatches = 20;

    /**
     * 保留最近多少天的备份目录（V4.2 P1-2），<=0 表示不按天数清理
     */
    private int backupKeepDays = 30;
}
