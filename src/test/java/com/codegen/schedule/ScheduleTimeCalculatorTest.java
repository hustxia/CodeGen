package com.codegen.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9.18 定时规则纯函数单测：间隔编码、cron 归一化、下次执行时间、到期判定、展示裁剪。
 * 不依赖 Spring 容器 / 数据库，边界值全部显式传入。
 */
class ScheduleTimeCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 10, 30, 0);

    @Test
    void intervalRule_roundTrip() {
        String rule = ScheduleTimeCalculator.intervalRule(15);
        assertEquals("@every 15m", rule);
        assertEquals(Integer.valueOf(15), ScheduleTimeCalculator.intervalMinutesOf(rule));
        assertTrue(ScheduleTimeCalculator.isIntervalRule(rule));
    }

    @Test
    void intervalRule_rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleTimeCalculator.intervalRule(0));
        assertThrows(IllegalArgumentException.class, () -> ScheduleTimeCalculator.intervalRule(-5));
        assertThrows(IllegalArgumentException.class,
                () -> ScheduleTimeCalculator.intervalRule(ScheduleTimeCalculator.MAX_INTERVAL_MINUTES + 1));
    }

    @Test
    void cronRule_isNotInterval() {
        assertFalse(ScheduleTimeCalculator.isIntervalRule("0 */5 * * * ?"));
        assertNull(ScheduleTimeCalculator.intervalMinutesOf("0 */5 * * * ?"));
    }

    @Test
    void toStoredRule_intervalWinsThenCron() {
        assertEquals("@every 30m", ScheduleTimeCalculator.toStoredRule("0 */5 * * * ?", 30));
        assertEquals("0 */5 * * * ?", ScheduleTimeCalculator.toStoredRule("0 */5 * * * ?", null));
        assertEquals("", ScheduleTimeCalculator.toStoredRule(null, null));
        assertEquals("", ScheduleTimeCalculator.toStoredRule("  ", 0));
    }

    @Test
    void fiveFieldCron_getsLeadingSeconds() {
        assertEquals("0 */5 * * * *", ScheduleTimeCalculator.normalizeCron("*/5 * * * *"));
        assertEquals("0 */5 * * * ?", ScheduleTimeCalculator.normalizeCron("0 */5 * * * ?"));
    }

    @Test
    void interval_neverRun_isDueImmediately() {
        String rule = "@every 30m";
        assertNull(ScheduleTimeCalculator.intervalMinutesOf("0 */5 * * * ?"));
        assertEquals(NOW, ScheduleTimeCalculator.nextFireAt(rule, null, NOW));
        assertTrue(ScheduleTimeCalculator.isDue(rule, null, NOW));
    }

    @Test
    void interval_respectsElapsedMinutes() {
        String rule = "@every 30m";
        LocalDateTime justRan = NOW.minusMinutes(10);
        assertFalse(ScheduleTimeCalculator.isDue(rule, justRan, NOW));
        assertEquals(justRan.plusMinutes(30), ScheduleTimeCalculator.nextFireAt(rule, justRan, NOW));

        LocalDateTime longAgo = NOW.minusMinutes(45);
        assertTrue(ScheduleTimeCalculator.isDue(rule, longAgo, NOW));
    }

    @Test
    void cron_dueOnlyAfterNextBoundary() {
        String rule = "0 */5 * * * ?";
        LocalDateTime last = LocalDateTime.of(2026, 11, 24, 10, 25, 10);
        LocalDateTime before = LocalDateTime.of(2026, 11, 24, 10, 29, 59);
        LocalDateTime at = LocalDateTime.of(2026, 11, 24, 10, 30, 0);
        assertFalse(ScheduleTimeCalculator.isDue(rule, last, before));
        assertTrue(ScheduleTimeCalculator.isDue(rule, last, at));
        assertEquals(at, ScheduleTimeCalculator.nextFireAt(rule, last, at.minusSeconds(1)));
    }

    @Test
    void invalidRule_isNeverDue() {
        assertNull(ScheduleTimeCalculator.nextFireAt("not a cron", null, NOW));
        assertFalse(ScheduleTimeCalculator.isDue("not a cron", null, NOW));
        assertFalse(ScheduleTimeCalculator.isDue("", null, NOW));
    }

    @Test
    void displayNextFireAt_clampsOverdueToNowWhenEnabled() {
        String rule = "@every 5m";
        LocalDateTime overdue = NOW.minusMinutes(30);
        assertEquals(NOW, ScheduleTimeCalculator.displayNextFireAt(rule, overdue, true, NOW));
        // 停用任务不裁剪：保留真实计划点，便于排查「为什么没跑」
        assertEquals(overdue.plusMinutes(5),
                ScheduleTimeCalculator.displayNextFireAt(rule, overdue, false, NOW));
    }

    @Test
    void displayNextFireAt_keepsFuturePoint() {
        String rule = "@every 60m";
        LocalDateTime last = NOW.minusMinutes(10);
        assertEquals(last.plusMinutes(60), ScheduleTimeCalculator.displayNextFireAt(rule, last, true, NOW));
    }
}
