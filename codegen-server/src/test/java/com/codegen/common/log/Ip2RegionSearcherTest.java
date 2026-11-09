package com.codegen.common.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 离线 IP 库的回归测试。
 *
 * <p>归属地解析是纯函数式的字节查找，最怕的是「格式漂移」——
 * 库换了版本、字段偏移变了，结果不会报错，只会静默返回 null，
 * 表现为日志里的省市全空。所以这里钉死几个有代表性的 IP。
 */
class Ip2RegionSearcherTest {

    private static Ip2RegionSearcher searcher() {
        Ip2RegionSearcher s = Ip2RegionSearcher.load();
        assertNotNull(s, "离线库未加载，检查 classpath:/ip/ip2region.db");
        return s;
    }

    @Test
    void 解析华东电信出口() {
        IpRegionResolver.Region r = searcher().search("58.56.1.1");
        assertNotNull(r);
        assertEquals("山东省", r.province());
        assertEquals("济南市", r.city());
    }

    @Test
    void 解析华南电信出口() {
        IpRegionResolver.Region r = searcher().search("202.96.128.86");
        assertNotNull(r);
        assertEquals("广东省", r.province());
        assertEquals("广州市", r.city());
    }

    @Test
    void 解析阿里云网段() {
        IpRegionResolver.Region r = searcher().search("223.5.5.5");
        assertNotNull(r);
        assertEquals("浙江省", r.province());
        assertEquals("杭州市", r.city());
    }

    @Test
    void 解析北京网段并补齐直辖市后缀() {
        IpRegionResolver.Region r = searcher().search("1.2.4.8");
        assertNotNull(r);
        assertEquals("北京市", r.province());
        assertEquals("北京市", r.city());
    }

    @Test
    void 国外IP用国家兜底() {
        IpRegionResolver.Region r = searcher().search("8.8.8.8");
        assertNotNull(r);
        assertEquals("美国", r.province());
    }

    @Test
    void 非法IP返回空() {
        Ip2RegionSearcher s = searcher();
        assertNull(s.search("not-an-ip"));
        assertNull(s.search("256.1.1.1"));
        assertNull(s.search("::1"));
        assertNull(s.search(null));
    }

    @Test
    void 点分十进制转换() {
        assertEquals(0x7F000001L, Ip2RegionSearcher.toLong("127.0.0.1"));
        assertEquals(0xDF050505L, Ip2RegionSearcher.toLong("223.5.5.5"));
        assertEquals(-1L, Ip2RegionSearcher.toLong("223.5.5"));
    }
}
