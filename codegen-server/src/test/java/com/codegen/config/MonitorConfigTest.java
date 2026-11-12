package com.codegen.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.10 安全基线：端点必须枚举放行，health 不吐细节，HTTP 直方图必须开。
 */
class MonitorConfigTest {

    @Test
    void exposureIsEnumeratedAndHealthHidesDetails() throws IOException {
        PropertySource<?> ps = load();
        String include = String.valueOf(ps.getProperty("management.endpoints.web.exposure.include"));

        assertTrue(include.contains("health"));
        assertTrue(include.contains("metrics"));
        assertFalse(include.contains("env"), "env 绝不能暴露，里面有数据库口令");
        assertFalse(include.contains("heapdump"));
        assertFalse(include.contains("threaddump"));
        assertFalse(include.contains("loggers"));
        assertEquals("never", ps.getProperty("management.endpoint.health.show-details"));
    }

    @Test
    void httpServerRequestsHistogramIsEnabled() throws IOException {
        PropertySource<?> ps = load();
        assertTrue(ps instanceof EnumerablePropertySource<?>);
        boolean enabled = false;
        for (String name : ((EnumerablePropertySource<?>) ps).getPropertyNames()) {
            if (name.contains("percentiles-histogram") && name.contains("http.server.requests")
                    && Boolean.parseBoolean(String.valueOf(ps.getProperty(name)))) {
                enabled = true;
                break;
            }
        }
        assertTrue(enabled, "management.metrics.distribution.percentiles-histogram 必须对 http.server.requests 开启");
    }

    private PropertySource<?> load() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        assertFalse(sources.isEmpty());
        return sources.get(0);
    }
}
