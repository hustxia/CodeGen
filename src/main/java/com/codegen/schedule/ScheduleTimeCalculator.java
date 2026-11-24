package com.codegen.schedule;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定时回归的「规则解析 + 下次执行时间」纯函数集合（V9.18）。
 *
 * <p>为什么不加列：间隔模式用 {@code @every 30m} 这种显式标记复用既有的 {@code cron} 列，
 * 语义清晰、可在 UI 上反向展示为「每 30 分钟」，也不需要 MySQL / H2 / docker 三处同步 DDL。
 * 普通 cron 仍按原样保存。
 *
 * <p>本类不依赖 Spring 容器与数据库，便于单测覆盖边界（从未执行 / 未到期 / 已到期 / 非法规则）。
 */
public final class ScheduleTimeCalculator {

    /** 间隔模式前缀，例如 "@every 15m" */
    public static final String INTERVAL_PREFIX = "@every ";
    private static final Pattern INTERVAL_PATTERN =
            Pattern.compile("^@every\\s+(\\d+)\\s*m$", Pattern.CASE_INSENSITIVE);
    /** 间隔上限 24 小时，再大建议直接用 cron */
    public static final int MAX_INTERVAL_MINUTES = 24 * 60;

    private ScheduleTimeCalculator() {
    }

    /** 间隔分钟 → 存储规则；非法区间直接抛 IllegalArgumentException，由调用方转成 400 */
    public static String intervalRule(int minutes) {
        if (minutes < 1 || minutes > MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("间隔分钟需在 1-" + MAX_INTERVAL_MINUTES + " 之间");
        }
        return INTERVAL_PREFIX + minutes + "m";
    }

    public static boolean isIntervalRule(String rule) {
        return rule != null && INTERVAL_PATTERN.matcher(rule.trim()).matches();
    }

    /** 规则里的间隔分钟；cron 规则返回 null */
    public static Integer intervalMinutesOf(String rule) {
        if (rule == null) {
            return null;
        }
        Matcher m = INTERVAL_PATTERN.matcher(rule.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            return Integer.valueOf(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 保存时归一化：间隔优先（用户在 UI 上明确选了间隔），否则用 cron。
     * 两者都空返回空串，由调用方报错。
     */
    public static String toStoredRule(String cron, Integer intervalMinutes) {
        if (intervalMinutes != null && intervalMinutes > 0) {
            return intervalRule(intervalMinutes);
        }
        return cron == null ? "" : cron.trim();
    }

    /** Spring CronExpression 需 6 段（秒 分 时 日 月 周）；5 段 Unix cron 补前导 0 秒 */
    public static String normalizeCron(String cron) {
        if (cron == null) {
            return null;
        }
        String trimmed = cron.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.chars().filter(ch -> ch == ' ').count() == 4 ? "0 " + trimmed : trimmed;
    }

    /**
     * 下一个应触发时间（不含「已错过则顺延」的裁剪）：
     * <ul>
     *   <li>间隔模式：上次触发 + N 分钟；从未触发 → now（立即到期）；</li>
     *   <li>cron 模式：从上次触发起算的下一个 cron 点；从未触发 → 从 MIN 起算（即立即到期）。</li>
     * </ul>
     *
     * @param rule       存储规则（cron 或 @every Nm）
     * @param lastFireAt 上次触发时间，null 表示从未触发
     * @param now        当前时间（显式传入，便于测试）
     * @return 下一个触发时间；规则非法返回 null
     */
    public static LocalDateTime nextFireAt(String rule, LocalDateTime lastFireAt, LocalDateTime now) {
        if (rule == null || rule.isBlank()) {
            return null;
        }
        Integer minutes = intervalMinutesOf(rule);
        if (minutes != null) {
            if (minutes < 1) {
                return null;
            }
            return lastFireAt == null ? now : lastFireAt.plusMinutes(minutes);
        }
        try {
            CronExpression expr = CronExpression.parse(normalizeCron(rule));
            return expr.next(Objects.requireNonNullElse(lastFireAt, LocalDateTime.MIN));
        } catch (Exception e) {
            return null;
        }
    }

    /** 是否到期：下一个触发点已到达（等于 now 也算到期） */
    public static boolean isDue(String rule, LocalDateTime lastFireAt, LocalDateTime now) {
        LocalDateTime next = nextFireAt(rule, lastFireAt, now);
        return next != null && !next.isAfter(now);
    }

    /** 展示用：任务启用且已到期时，下次扫描（≤30s）就会触发，因此按 now 显示，避免出现过去时间 */
    public static LocalDateTime displayNextFireAt(String rule, LocalDateTime lastFireAt, boolean enabled, LocalDateTime now) {
        LocalDateTime next = nextFireAt(rule, lastFireAt, now);
        if (next == null) {
            return null;
        }
        if (!enabled) {
            return next;
        }
        return next.isBefore(now) ? now : next;
    }
}
