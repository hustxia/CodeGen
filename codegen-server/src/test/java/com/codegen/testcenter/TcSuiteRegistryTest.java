package com.codegen.testcenter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.3 注册表：12 个套件（S1~S9 验证生成器自身 + S10/S11/S12 验证生成产物）、P0 默认勾选、命令白名单、依赖闭环。 */
class TcSuiteRegistryTest {

    @Test
    void registersNineSuitesWithUniqueIds() {
        assertEquals(12, TcSuite.all().size());
        assertEquals(List.of(), TcSuite.validate());
        assertEquals("S1", TcSuite.of("S1").getId());
        assertEquals("S10", TcSuite.of("S10").getId());
        assertEquals(null, TcSuite.of("S99"));
    }

    @Test
    void defaultSelectionContainsOnlyP0() {
        List<String> defaults = TcSuite.defaultSuiteIds();
        assertEquals(8, defaults.size());
        assertTrue(defaults.contains("S1"));
        assertTrue(defaults.contains("S7"));
        assertTrue(defaults.contains("S10"));
        assertFalse(defaults.contains("S8"));
        assertFalse(defaults.contains("S9"));
        assertFalse(defaults.contains("S11"));
        assertFalse(defaults.contains("S12"));
    }

    @Test
    void genProductSuitesAreS10S11S12() {
        assertEquals(List.of("S10", "S11", "S12"), TcSuite.genProductSuiteIds());
        assertTrue(TcSuite.of("S10").isGenProductSuite());
        assertTrue(TcSuite.of("S11").isGenProductSuite());
        assertTrue(TcSuite.of("S12").isGenProductSuite());
        assertFalse(TcSuite.of("S1").isGenProductSuite());
    }

    @Test
    void p1SuitesAreNeverSelectedByDefault() {
        for (TcSuite s : TcSuite.all()) {
            if ("P1".equals(s.getLevel())) {
                assertFalse(s.isDefaultSelected(), s.getId() + " 是 P1 却默认勾选");
            }
        }
    }

    @Test
    void argvComesOnlyFromServerSideTemplate() {
        for (TcSuite s : TcSuite.all()) {
            List<String> argv = s.argv();
            assertEquals(3, argv.size());
            assertEquals("scripts/testcenter-suites.mjs", argv.get(1));
            assertEquals(s.getId(), argv.get(2));
        }
    }

    @Test
    void resolveKeepsRegistryOrderAndDeduplicates() {
        List<TcSuite> resolved = TcSuite.resolve(List.of("S4", "S1", "S4"));
        assertEquals(List.of("S1", "S4"), resolved.stream().map(TcSuite::getId).toList());
    }

    @Test
    void resolveRejectsUnknownOrEmptyIds() {
        assertThrows(IllegalArgumentException.class, () -> TcSuite.resolve(List.of("S1", "S99")));
        assertThrows(IllegalArgumentException.class, () -> TcSuite.resolve(List.of()));
        assertThrows(IllegalArgumentException.class, () -> TcSuite.resolve(null));
    }

    @Test
    void everyDependencyExistsAndTimeoutsArePositive() {
        for (TcSuite s : TcSuite.all()) {
            assertTrue(s.getTimeoutMinutes() > 0, s.getId() + " 超时非正");
            for (String dep : s.getDependsOn()) {
                assertNotNull(TcSuite.of(dep), s.getId() + " 依赖了不存在的 " + dep);
            }
        }
        assertEquals(List.of("S1"), TcSuite.of("S5").getDependsOn());
    }
}
