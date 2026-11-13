package com.codegen.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * V9.2 接口执行器：JDK {@code HttpClient}，不依赖目标工程任何依赖。
 *
 * <p>连接超时 3s、请求超时 10s（与设计文档一致）。任何异常都转成
 * {@link ExecResult#error()} 返回，不向上抛——单条用例失败不应中断整批。
 * DELETE/GET 默认无请求体，只有显式传了 body 才发。
 */
@Component
public class ApiTestExecutor {

    private static final Logger log = LoggerFactory.getLogger(ApiTestExecutor.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 执行结果；error 非空表示没拿到 HTTP 响应（超时/连接失败/地址非法） */
    public record ExecResult(int status, String body, Map<String, String> headers, long durationMs, String error) {

        public boolean ok() {
            return error == null;
        }
    }

    public ExecResult execute(String baseUrl, String method, String path,
                              Map<String, String> headers, String body) {
        long start = System.nanoTime();
        String url = join(baseUrl, path);
        if (url == null) {
            return new ExecResult(-1, null, Map.of(), elapsed(start), "baseUrl 为空或非法: " + baseUrl);
        }
        try {
            String m = method == null || method.isBlank() ? "GET" : method.toUpperCase(Locale.ROOT);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT);

            Map<String, String> safeHeaders = headers == null ? Map.of() : headers;
            boolean hasContentType = safeHeaders.keySet().stream()
                    .anyMatch(k -> "content-type".equalsIgnoreCase(k));
            safeHeaders.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null) {
                    builder.header(k, v);
                }
            });

            if (body != null && !body.isBlank()) {
                if (!hasContentType) {
                    builder.header("Content-Type", "application/json;charset=UTF-8");
                }
                builder.method(m, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.method(m, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> resp = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            Map<String, String> respHeaders = new LinkedHashMap<>();
            resp.headers().map().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    respHeaders.put(k, v.get(0));
                }
            });
            return new ExecResult(resp.statusCode(), resp.body(), respHeaders, elapsed(start), null);
        } catch (Exception e) {
            log.debug("接口执行失败 url={} method={}: {}", url, method, e.toString());
            return new ExecResult(-1, null, Map.of(), elapsed(start),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static long elapsed(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    private static String join(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            return null;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String p = path == null ? "" : path.trim();
        if (p.isEmpty()) {
            return base;
        }
        return p.startsWith("/") ? base + p : base + "/" + p;
    }
}
