package com.codegen.common.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP → 省 / 市 归属地解析。
 *
 * <p>操作日志要能回答「这次操作是从哪来的」，只记 IP 看不出地域，审计时还得人工查。
 *
 * <p>几个刻意的取舍：
 * <ul>
 *   <li><b>离线优先</b>：classpath 里的 ip2region 库命中即返回，不碰网络。
 *       公网免费接口实测极不稳定（六次请求只成一次），纯在线方案会让一半日志没有省市</li>
 *   <li><b>内网不发请求</b>：回环与私有网段直接给出「本机」「内网」，企业内网部署时零延迟</li>
 *   <li><b>在线兜底</b>：离线库查不到（新分配的段、只到国家粒度）时才走接口，
 *       并且受缓存保护，避免同一个 IP 反复等超时</li>
 *   <li><b>结果带缓存</b>：同一个 IP 24 小时内只查一次，失败也缓存（负缓存 5 分钟），
 *       避免接口不可达时每条日志都白等一个超时</li>
 *   <li><b>绝不抛异常</b>：解析失败返回 null，日志照常落库——省市是锦上添花，不能拖垮业务</li>
 *   <li><b>接口可换</b>：默认用 ip-api，配置 {@code app.ip-region.api} 可换成自建或商用接口，
 *       返回体里认 province/regionName/pro 与 city 几种常见键名</li>
 * </ul>
 */
@Component
public class IpRegionResolver {

    private static final Logger log = LoggerFactory.getLogger(IpRegionResolver.class);

    /**
     * 缓存上限：超过后清掉过期项，仍然超出就整体清空（日志场景没必要做精细 LRU）
     */
    private static final int MAX_CACHE = 2000;

    private static final long TTL_MS = 24 * 60 * 60 * 1000L;

    /**
     * 失败只缓存 5 分钟：接口偶发超时/限流很常见，缓存一整天会让这个 IP 永远没有归属地
     */
    private static final long FAIL_TTL_MS = 5 * 60 * 1000L;

    /**
     * 省级行政区划里不带「省」字的几个，拼全称才能和前端省市区选择器对上
     */
    private static final Map<String, String> PROVINCE_ALIAS = Map.ofEntries(
            Map.entry("内蒙古", "内蒙古自治区"),
            Map.entry("广西", "广西壮族自治区"),
            Map.entry("西藏", "西藏自治区"),
            Map.entry("宁夏", "宁夏回族自治区"),
            Map.entry("新疆", "新疆维吾尔自治区"),
            Map.entry("北京", "北京市"),
            Map.entry("上海", "上海市"),
            Map.entry("天津", "天津市"),
            Map.entry("重庆", "重庆市"),
            Map.entry("香港", "香港特别行政区"),
            Map.entry("澳门", "澳门特别行政区"));

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${app.ip-region.enabled:true}")
    private boolean enabled;

    /**
     * 是否启用离线库。默认开；关掉就退回纯在线模式（老部署没有库文件时的兼容路径）
     */
    @Value("${app.ip-region.offline:true}")
    private boolean offline;

    /**
     * 用 {ip} 占位；换成自建服务时保持同样的占位符即可
     */
    @Value("${app.ip-region.api:http://ip-api.com/json/{ip}?lang=zh-CN}")
    private String api;

    @Value("${app.ip-region.timeout-ms:3000}")
    private long timeoutMs;

    /**
     * 可选代理，形如 {@code http://127.0.0.1:7890}。
     *
     * <p>默认直连：JDK 的 HttpClient 会读系统代理设置，机器上只要有 HTTP_PROXY
     * 就会把请求转给代理，代理不通时表现为「连不上」，排查半天其实是环境变量。
     * 需要走代理的环境显式配这一项即可。
     */
    @Value("${app.ip-region.proxy:}")
    private String proxy;

    /**
     * 回填线程池：归属地是锦上添花，绝不能卡住业务响应，所以查询走异步。
     * 队列满了直接丢弃（丢的是归属地，不是日志本身）。
     */
    private final java.util.concurrent.ExecutorService pool =
            new java.util.concurrent.ThreadPoolExecutor(1, 2, 0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(500),
                    r -> {
                        Thread t = new Thread(r, "ip-region");
                        t.setDaemon(true);
                        return t;
                    },
                    new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile HttpClient httpClient;

    /**
     * 离线库懒加载：加载要读 8.7MB，放在启动期会拖慢启动；
     * 第一次真正要解析归属地时才初始化，失败则永远是 null（走在线）。
     */
    private volatile Ip2RegionSearcher searcher;

    private volatile boolean searcherLoaded;

    /**
     * 解析结果。省市都可能为空串（接口只给了其中一个），但对象本身不为 null 表示「查过了」。
     */
    public record Region(String province, String city) {

        public boolean isEmpty() {
            return (province == null || province.isBlank()) && (city == null || city.isBlank());
        }
    }

    private record CacheEntry(Region region, long expireAt) {
    }

    /**
     * 解析 IP 归属地；无法解析时返回 null（调用方直接把省市留空即可）。
     */
    public Region resolve(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String key = ip.trim();
        Region local = localRegion(key);
        if (local != null) {
            return local;
        }
        if (!enabled) {
            return null;
        }
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expireAt() > System.currentTimeMillis()) {
            return hit.region();
        }
        // 先问离线库：命中就不碰网络，这一步覆盖绝大多数真实 IP
        Ip2RegionSearcher db = searcher();
        if (db != null) {
            Region region = db.search(key);
            if (region != null && !region.isEmpty()) {
                putCache(key, region, TTL_MS);
                return region;
            }
        }
        Region region = query(key);
        putCache(key, region, region == null ? FAIL_TTL_MS : TTL_MS);
        return region;
    }

    /**
     * 离线库实例；未启用或加载失败时返回 null。
     */
    private Ip2RegionSearcher searcher() {
        if (!offline) {
            return null;
        }
        if (!searcherLoaded) {
            synchronized (this) {
                if (!searcherLoaded) {
                    try {
                        searcher = Ip2RegionSearcher.load();
                    } catch (Exception e) {
                        log.warn("离线 IP 库初始化失败：{}", e.getMessage());
                        searcher = null;
                    }
                    searcherLoaded = true;
                }
            }
        }
        return searcher;
    }

    /**
     * 异步解析：回调在后台线程执行，调用方（日志切面）不阻塞。
     * 解析不出结果时不回调，省市保持为空。
     */
    public void resolveAsync(String ip, java.util.function.Consumer<Region> callback) {
        try {
            pool.execute(() -> {
                try {
                    Region region = resolve(ip);
                    if (region != null && callback != null) {
                        callback.accept(region);
                    }
                } catch (Exception e) {
                    log.debug("IP 归属地异步解析失败 {}：{}", ip, e.getMessage());
                }
            });
        } catch (Exception e) {
            // 队列满被拒时静默丢弃，不影响日志主流程
            log.debug("IP 归属地任务被丢弃 {}：{}", ip, e.getMessage());
        }
    }

    /**
     * 本机 / 内网地址：不查接口，直接给结论。
     */
    private Region localRegion(String ip) {
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)
                || "localhost".equalsIgnoreCase(ip)) {
            return new Region("本机", "本机");
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress()) {
                return new Region("本机", "本机");
            }
            if (addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                return new Region("内网", "内网");
            }
            // 0:0:0:0:0:0:0:1 之外的 IPv6 常见于容器直连，同样按内网处理
            if (addr instanceof Inet6Address && (addr.isAnyLocalAddress() || addr.isMCGlobal())) {
                return new Region("内网", "内网");
            }
            if (addr instanceof Inet4Address) {
                byte[] b = addr.getAddress();
                int first = b[0] & 0xFF;
                int second = b[1] & 0xFF;
                // 100.64/10 运营商级 NAT
                if (first == 100 && second >= 64 && second <= 127) {
                    return new Region("内网", "内网");
                }
            }
        } catch (Exception ignored) {
            // 解析不了 IP 就当公网处理，交给 query 兜底
        }
        return null;
    }

    private Region query(String ip) {
        // 首次解析要建连接（DNS + TCP），偶尔会顶到超时；重试一次就能命中连接复用。
        // 只有异常才重试：接口明确说「查不到这个 IP」时重试没有意义
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return queryOnce(ip);
            } catch (Exception e) {
                if (attempt == 1) {
                    log.debug("IP 归属地解析失败 {}：{}", ip, e.getMessage());
                }
            }
        }
        return null;
    }

    private Region queryOnce(String ip) throws Exception {
        String url = api.replace("{ip}", ip);
        HttpResponse<byte[]> resp = client().send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("User-Agent", "codegen-oper-log/1.0")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            return null;
        }
        String body = new String(resp.body(), StandardCharsets.UTF_8);
        // pconline 这类接口会把 JSON 包在 JS 变量里，截取第一个 { 到最后一个 }
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        JsonNode node = objectMapper.readTree(body.substring(start, end + 1));
        if (node.has("status") && !"success".equalsIgnoreCase(node.path("status").asText())) {
            // ip-api 的失败态（保留地址、限流）
            return null;
        }
        String province = normalizeProvince(firstText(node, "province", "regionName", "pro", "region"));
        String city = normalizeCity(firstText(node, "city", "cityName"));
        Region region = new Region(province, city);
        return region.isEmpty() ? null : region;
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText().trim();
            }
        }
        return "";
    }

    /**
     * 补全省级后缀。接口有的给「山东」有的给「山东省」，统一成「山东省」；
     * 直辖市与自治区按官方全称，避免前端省市区组件对不上。
     */
    private String normalizeProvince(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String v = raw.trim();
        String alias = PROVINCE_ALIAS.get(v);
        if (alias != null) {
            return alias;
        }
        if (v.endsWith("省") || v.endsWith("市") || v.endsWith("自治区") || v.endsWith("特别行政区")) {
            return v;
        }
        return v + "省";
    }

    /**
     * 地级市同理：自治州、地区、盟本身就是完整名称，只有纯名字才补「市」。
     */
    private String normalizeCity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String v = raw.trim();
        if (v.endsWith("市") || v.endsWith("州") || v.endsWith("盟") || v.endsWith("地区")
                || v.endsWith("区") || v.endsWith("县")) {
            return v;
        }
        return v + "市";
    }

    private void putCache(String ip, Region region, long ttl) {
        if (cache.size() > MAX_CACHE) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(e -> e.getValue().expireAt() <= now);
            if (cache.size() > MAX_CACHE) {
                // 清完过期项还是太多，说明短时间涌入大量不同 IP，整体清掉比逐个淘汰省事
                cache.clear();
            }
        }
        cache.put(ip, new CacheEntry(region, System.currentTimeMillis() + ttl));
    }

    private HttpClient client() {
        HttpClient c = httpClient;
        if (c == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(timeoutMs))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .proxy(proxySelector())
                            .build();
                }
                c = httpClient;
            }
        }
        return c;
    }

    /**
     * 空配置 → 直连；配了 proxy → 走指定代理。
     */
    private java.net.ProxySelector proxySelector() {
        if (proxy == null || proxy.isBlank()) {
            return java.net.ProxySelector.of(null);
        }
        try {
            java.net.URI uri = java.net.URI.create(proxy.trim());
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            return java.net.ProxySelector.of(java.net.InetSocketAddress.createUnresolved(uri.getHost(), port));
        } catch (Exception e) {
            log.warn("app.ip-region.proxy 配置无法解析（{}），改为直连", proxy);
            return java.net.ProxySelector.of(null);
        }
    }

    /**
     * 调试用：看看当前缓存里都记了什么
     */
    public Map<String, Region> snapshot() {
        Map<String, Region> out = new java.util.LinkedHashMap<>();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> e = it.next();
            if (e.getValue().expireAt() > now) {
                out.put(e.getKey(), e.getValue().region());
            }
        }
        return out;
    }
}
