package com.codegen.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试数据源守卫（W-03）：在 {@code spring.sql.init} 执行之前拦截危险数据源。
 *
 * <p>{@code src/test/resources/schema-test.sql} 开头是 {@code DROP TABLE IF EXISTS}，
 * 只要测试 profile 生效就会先删表再建表。历史注释一直声称有本守卫（类名都写好了），
 * 但实现从未存在过——一旦 {@code application-test.yml} 的 URL 被改成 {@code gen_platform}，
 * 跑一次单测就等于清一次开发库。2026-11-07 的空库冒烟事故已经证明「参数被脚本内容/配置覆盖」
 * 这条路径是真实存在的，这里必须在最早期 fail-fast，而不是等 SQL 执行到 DROP 才发现。
 *
 * <p>注册方式：{@code src/test/resources/META-INF/spring.factories}，仅测试 classpath 可见，
 * 不影响生产启动。
 */
public class TestDatabaseGuardPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 触发破坏性初始化的 schema 脚本特征 */
    private static final String DESTRUCTIVE_SCHEMA_MARK = "schema-test";

    /** jdbc:mysql://host:port/db?params —— 取 db 段 */
    private static final Pattern DB_NAME = Pattern.compile("jdbc:mysql://[^/]+/([^?;]+)");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String schemaLocations = env.getProperty("spring.sql.init.schema-locations");
        if (schemaLocations == null || !schemaLocations.contains(DESTRUCTIVE_SCHEMA_MARK)) {
            return;
        }
        String url = env.getProperty("spring.datasource.url");
        String db = databaseName(url);
        if (db == null) {
            throw new IllegalStateException(
                    "拒绝启动：schema-test.sql 会 DROP TABLE，但数据源 URL 解析不出库名（url=" + url + "）");
        }
        if (!db.endsWith("_test")) {
            throw new IllegalStateException(
                    "拒绝启动：schema-test.sql 会 DROP TABLE，但数据源指向库 '" + db
                            + "'。测试库名必须以 _test 结尾（见 application-test.yml 三条硬约束）。");
        }
    }

    /** 从 JDBC URL 取出库名；解析不出返回 null */
    static String databaseName(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = DB_NAME.matcher(url);
        return m.find() ? m.group(1).trim() : null;
    }

    @Override
    public int getOrder() {
        // 早于配置处理，晚于日志系统初始化即可
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
