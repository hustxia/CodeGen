package com.codegen.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.10 白名单收窄：只留探活，不允许整段 /actuator 开门。
 */
class AuthContextFilterWhitelistTest {

    @Test
    @SuppressWarnings("unchecked")
    void onlyHealthEndpointIsWhitelisted() throws Exception {
        Field field = AuthContextFilter.class.getDeclaredField("WHITE_LIST");
        field.setAccessible(true);
        List<String> whiteList = (List<String>) field.get(null);

        assertTrue(whiteList.contains("/actuator/health"));
        assertFalse(whiteList.contains("/actuator"), "整段 /actuator 放行会让 metrics/env 全部免鉴权");
        assertFalse(whiteList.contains("/health"), "/health 是死条目，指向不存在的端点");
        assertTrue(whiteList.contains("/auth/login"));
    }
}
