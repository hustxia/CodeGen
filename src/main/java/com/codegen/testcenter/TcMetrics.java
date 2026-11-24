package com.codegen.testcenter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从套件日志里抽取结构化基线值。
 *
 * <p>约定：{@code scripts/testcenter-suites.mjs} 每个套件结束前打印一行
 * {@code TC_METRICS {"tests":312,"failures":0}}，执行器只认这一行——
 * 不靠正则去猜人类可读的日志文本，日志文案改了不会把指标解析搞坏。
 */
public final class TcMetrics {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TcMetrics.class);

    public static final String PREFIX = "TC_METRICS ";
    /** 产物声明行：TC_ARTIFACTS ["D:/.../a.png","D:/.../diff.json"] */
    public static final String ARTIFACTS_PREFIX = "TC_ARTIFACTS ";
    /** 内部键：产物清单随 metrics 一起传回执行器，落库前必须剔除 */
    public static final String ARTIFACTS_KEY = "_artifacts";

    private TcMetrics() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String logTail, ObjectMapper mapper) {
        Map<String, Object> out = new LinkedHashMap<>();
        String last = lastPayload(logTail, PREFIX);
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
        } catch (Exception e) {
            // 指标解析失败不影响套件本身的 PASS/FAIL 判定
            log.debug("TC_METRICS 行解析失败，忽略指标提取: {}", last, e);
        }
        return out;
    }

    /**
     * 保留嵌套结构的版本：S9 的 diffList、外层包裹的布尔值都要原样进 metrics_json，
     * 供验证中心详情页渲染文件树。summary 仍用 {@link #parse} 的扁平口径。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseAll(String logTail, ObjectMapper mapper) {
        Map<String, Object> out = new LinkedHashMap<>();
        String last = lastPayload(logTail, PREFIX);
        if (last == null) {
            return out;
        }
        try {
            out.putAll(mapper.readValue(last, Map.class));
        } catch (Exception e) {
            log.debug("TC_METRICS 行解析失败（full），忽略指标提取: {}", last, e);
        }
        return out;
    }

    /**
     * 取日志里所有 TC_ARTIFACTS 声明的产物路径（去重保序）。
     * 解析失败的行直接跳过：产物登记失败不能改变套件结论。
     */
    public static List<String> parseArtifacts(String logTail, ObjectMapper mapper) {
        List<String> out = new ArrayList<>();
        if (logTail == null || logTail.isBlank()) {
            return out;
        }
        for (String line : logTail.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(ARTIFACTS_PREFIX)) {
                continue;
            }
            try {
                List<?> paths = mapper.readValue(trimmed.substring(ARTIFACTS_PREFIX.length()).trim(), List.class);
                for (Object p : paths) {
                    if (p instanceof String s && !s.isBlank() && !out.contains(s)) {
                        out.add(s);
                    }
                }
            } catch (Exception e) {
                log.debug("TC_ARTIFACTS 行解析失败，忽略: {}", trimmed, e);
            }
        }
        return out;
    }

    private static String lastPayload(String logTail, String prefix) {
        if (logTail == null || logTail.isBlank()) {
            return null;
        }
        String last = null;
        for (String line : logTail.split("\\r?\\n")) {
            if (line.trim().startsWith(prefix)) {
                last = line.trim().substring(prefix.length()).trim();
            }
        }
        return last;
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
