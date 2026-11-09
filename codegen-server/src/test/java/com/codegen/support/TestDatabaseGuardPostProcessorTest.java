package com.codegen.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-03 守卫：schema-test.sql 会 DROP TABLE，库名不是 _test 结尾必须起不来。
 */
class TestDatabaseGuardPostProcessorTest {

    private final TestDatabaseGuardPostProcessor guard = new TestDatabaseGuardPostProcessor();

    private MockEnvironment env(String url, boolean withSchemaInit) {
        MockEnvironment e = new MockEnvironment();
        if (url != null) {
            e.setProperty("spring.datasource.url", url);
        }
        if (withSchemaInit) {
            e.setProperty("spring.sql.init.schema-locations", "classpath:schema-test.sql");
        }
        return e;
    }

    @Test
    @DisplayName("库名不带 _test 且启用 schema-test.sql → 拒绝启动")
    void refusesDevDatabase() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(env(
                        "jdbc:mysql://localhost:3306/gen_platform?useSSL=false", true), null));
        assertTrue(ex.getMessage().contains("gen_platform"), ex.getMessage());
    }

    @Test
    @DisplayName("gen_platform_test → 放行")
    void allowsTestDatabase() {
        assertDoesNotThrow(() -> guard.postProcessEnvironment(env(
                "jdbc:mysql://localhost:3306/gen_platform_test?useSSL=false", true), null));
    }

    @Test
    @DisplayName("不启用 schema-test.sql 时不做限制（生产/本地启动不受影响）")
    void ignoresWhenNoDestructiveInit() {
        assertDoesNotThrow(() -> guard.postProcessEnvironment(env(
                "jdbc:mysql://localhost:3306/gen_platform?useSSL=false", false), null));
    }

    @Test
    @DisplayName("URL 解析不出库名 → 拒绝（宁可起不来也不要盲删）")
    void refusesUnparsableUrl() {
        assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(env("jdbc:mysql://localhost:3306", true), null));
    }

    @Test
    @DisplayName("库名解析：去掉查询参数，兼容无参数 URL")
    void parsesDatabaseName() {
        assertEquals("gen_platform_test",
                TestDatabaseGuardPostProcessor.databaseName(
                        "jdbc:mysql://127.0.0.1:3306/gen_platform_test?useSSL=false&serverTimezone=Asia/Shanghai"));
        assertEquals("codegen_v74_smoke",
                TestDatabaseGuardPostProcessor.databaseName("jdbc:mysql://localhost:3306/codegen_v74_smoke"));
        assertNull(TestDatabaseGuardPostProcessor.databaseName(null));
        assertNull(TestDatabaseGuardPostProcessor.databaseName("jdbc:mysql://localhost:3306"));
    }
}
