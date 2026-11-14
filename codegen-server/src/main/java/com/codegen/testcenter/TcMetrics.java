package com.codegen.testcenter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从套件日志里抽取结构化基线值。
 *
 * <p>约定：{@code scripts/testcenter-suites.mjs} 每个套件结束前打印一行
 * {@code TC_METRICS {"tests":312,"failures":0}}，执行器只认这一行——
 * 不靠正则去猜人类可读的日志文本，日志文案改了不会把指标解析搞坏。
 */
public final class TcMetrics {

    public static final String PREFIX = "TC_METRICS ";

    private TcMetrics() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String logTail, ObjectMapper mapper) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (logTail == null || logTail.isBlank()) {
            return out;
        }
        String last = null;
        for (String line : logTail.split("\\r?\\n")) {
            if (line.trim().startsWith(PREFIX)) {
                last = line.trim().substring(PREFIX.length()).trim();
            }
        }
        if (last == null) {
            return out;
        }
        try {
            Map<String, Object> parsed = mapper.readValue(last, Map.class);
            for (Map.Entry<String, Object> e : parsed.entrySet()) {
                if (e.getValue() == null || e.getValue() instanceof Number
                        || e.getValue() instanceof String || e.getValue() instanceof Boolean) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception ignored) {
            // 指标解析失败不影响套件本身的 PASS/FAIL 判定
        }
        return out;
    }

    /** 日志尾部：按行截断，避免把 200 行堆栈整个塞进 error_excerpt。 */
    public static String tail(String text, int lines) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] all = text.split("\\r?\\n");
        int from = Math.max(0, all.length - Math.max(1, lines));
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < all.length; i++) {
            sb.append(all[i]).append('\n');
        }
        String out = sb.toString();
        int cap = 4000;
        return out.length() > cap ? out.substring(out.length() - cap) : out;
    }
}
