package __PKG__.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ip2region 离线库查询（IPv4）。
 *
 * <p>为什么要有离线库：操作日志写一条就要解析一次 IP，走公网接口意味着
 * 每次都在赌对方的可用性——实测免费接口六次请求只成功一次，日志里的省市
 * 大半是空的。审计信息不全等于没有。离线库放在 classpath 里，纯内存二分，
 * 单次查询微秒级，不依赖网络也不依赖第三方限流。
 *
 * <p>库格式（ip2region v1）：
 * <pre>
 * [0,8)              superBlock：firstIndexPtr(u32) + lastIndexPtr(u32)
 * [firstIndexPtr..]  索引区：每条 12 字节 = sip(u32) + eip(u32) + dptr(u32)
 * [数据区]            dptr 高 8 位是块长度、低 24 位是块偏移；
 *                    块内 4 字节 cityId，之后是 region 串（长度 = 块长 - 4）
 * </pre>
 * region 串形如 {@code 国家|区域|省份|城市|ISP}，缺的段用 {@code 0} 占位。
 *
 * <p>8.7MB 的库整体读进内存，约 68 万个索引块；常驻堆占用可控，
 * 换来的是查询不需要任何 IO。库文件缺失时自动降级为「不可用」，
 * 由 {@link IpRegionResolver} 回落到在线接口。
 */
public class Ip2RegionSearcher {

    private static final Logger log = LoggerFactory.getLogger(Ip2RegionSearcher.class);

    /**
     * classpath 位置；脚手架生成的项目也会把库拷到同一路径，两边行为一致
     */
    public static final String RESOURCE = "/ip/ip2region.db";

    private static final int INDEX_BLOCK_LENGTH = 12;

    /**
     * 省级直辖市在库里不带后缀，统一补齐才能和前端省市区选择器对上
     */
    private static final Map<String, String> PROVINCE_ALIAS = Map.ofEntries(
            Map.entry("北京", "北京市"),
            Map.entry("上海", "上海市"),
            Map.entry("天津", "天津市"),
            Map.entry("重庆", "重庆市"),
            Map.entry("内蒙古", "内蒙古自治区"),
            Map.entry("广西", "广西壮族自治区"),
            Map.entry("西藏", "西藏自治区"),
            Map.entry("宁夏", "宁夏回族自治区"),
            Map.entry("新疆", "新疆维吾尔自治区"),
            Map.entry("香港", "香港特别行政区"),
            Map.entry("澳门", "澳门特别行政区"));

    private final byte[] data;

    private final long firstIndexPtr;

    private final int totalBlocks;

    private Ip2RegionSearcher(byte[] data, long firstIndexPtr, int totalBlocks) {
        this.data = data;
        this.firstIndexPtr = firstIndexPtr;
        this.totalBlocks = totalBlocks;
    }

    /**
     * 从 classpath 加载。资源不存在或格式异常时返回 null（调用方据此降级）。
     */
    public static Ip2RegionSearcher load() {
        return load(RESOURCE);
    }

    public static Ip2RegionSearcher load(String resource) {
        try (InputStream in = Ip2RegionSearcher.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.info("未找到离线 IP 库 {}，归属地解析将回落到在线接口", resource);
                return null;
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream(9 * 1024 * 1024);
            byte[] chunk = new byte[64 * 1024];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            byte[] data = buf.toByteArray();
            if (data.length < 16) {
                log.warn("离线 IP 库 {} 内容异常（{} 字节），忽略", resource, data.length);
                return null;
            }
            long first = readUInt32LE(data, 0);
            long last = readUInt32LE(data, 4);
            if (first <= 0 || last <= first || last >= data.length) {
                log.warn("离线 IP 库 {} 头部指针异常（first={}, last={}），忽略", resource, first, last);
                return null;
            }
            int blocks = (int) ((last - first) / INDEX_BLOCK_LENGTH + 1);
            log.info("离线 IP 库加载完成：{}，{} 条索引", resource, blocks);
            return new Ip2RegionSearcher(data, first, blocks);
        } catch (Exception e) {
            log.warn("离线 IP 库 {} 加载失败：{}", resource, e.getMessage());
            return null;
        }
    }

    /**
     * 查询归属地；IP 非法或库里查不到时返回 null。
     */
    public IpRegionResolver.Region search(String ip) {
        long ipNum = toLong(ip);
        if (ipNum < 0) {
            return null;
        }
        int low = 0;
        int high = totalBlocks - 1;
        long dataPtr = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int pos = (int) (firstIndexPtr + (long) mid * INDEX_BLOCK_LENGTH);
            long sip = readUInt32LE(data, pos);
            if (ipNum < sip) {
                high = mid - 1;
                continue;
            }
            long eip = readUInt32LE(data, pos + 4);
            if (ipNum > eip) {
                low = mid + 1;
                continue;
            }
            dataPtr = readUInt32LE(data, pos + 8);
            break;
        }
        if (dataPtr == 0) {
            return null;
        }
        int blockLen = (int) ((dataPtr >>> 24) & 0xFF);
        int offset = (int) (dataPtr & 0x00FFFFFF);
        // 块内前 4 字节是 cityId，真正的 region 串在它后面
        int strLen = blockLen - 4;
        if (offset <= 0 || strLen <= 0 || offset + blockLen > data.length) {
            return null;
        }
        String region = new String(data, offset + 4, strLen, StandardCharsets.UTF_8);
        return parse(region);
    }

    /**
     * region 串 → 省市。
     *
     * <p>国内 IP 直接取「省份|城市」；国外 IP 没有省市概念，
     * 用国家填省级、城市缺失时也用国家兜底，至少能看出「来自哪个国家」。
     */
    private IpRegionResolver.Region parse(String region) {
        String[] parts = region.split("\\|", -1);
        String country = part(parts, 0);
        String province = part(parts, 2);
        String city = part(parts, 3);

        if (country.isEmpty()) {
            // 库里没标国家，只能按国内处理
            country = "中国";
        }
        if (!"中国".equals(country)) {
            String c = city.isEmpty() ? country : city;
            return new IpRegionResolver.Region(country, c);
        }
        if (province.isEmpty()) {
            // 国内但只到国家粒度（多为汇聚段），交给在线接口再试一次
            return null;
        }
        return new IpRegionResolver.Region(normalizeProvince(province), normalizeCity(city));
    }

    private static String part(String[] parts, int index) {
        if (index >= parts.length) {
            return "";
        }
        String v = parts[index];
        if (v == null || v.isEmpty() || "0".equals(v)) {
            return "";
        }
        return v.trim();
    }

    private static String normalizeProvince(String raw) {
        String alias = PROVINCE_ALIAS.get(raw);
        if (alias != null) {
            return alias;
        }
        if (raw.endsWith("省") || raw.endsWith("市") || raw.endsWith("自治区") || raw.endsWith("特别行政区")) {
            return raw;
        }
        return raw + "省";
    }

    private static String normalizeCity(String raw) {
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.endsWith("市") || raw.endsWith("州") || raw.endsWith("盟")
                || raw.endsWith("地区") || raw.endsWith("区") || raw.endsWith("县")) {
            return raw;
        }
        return raw + "市";
    }

    /**
     * 点分十进制 → 无符号 32 位；非法返回 -1
     */
    static long toLong(String ip) {
        if (ip == null) {
            return -1;
        }
        String v = ip.trim();
        int colon = v.indexOf(':');
        if (colon >= 0) {
            return -1;
        }
        String[] seg = v.split("\\.");
        if (seg.length != 4) {
            return -1;
        }
        long out = 0;
        for (String s : seg) {
            int n;
            try {
                n = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (n < 0 || n > 255) {
                return -1;
            }
            out = (out << 8) | n;
        }
        return out;
    }

    private static long readUInt32LE(byte[] b, int pos) {
        return (b[pos] & 0xFFL)
                | ((b[pos + 1] & 0xFFL) << 8)
                | ((b[pos + 2] & 0xFFL) << 16)
                | ((b[pos + 3] & 0xFFL) << 24);
    }
}
