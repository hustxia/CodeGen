package com.codegen.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志脱敏的回归测试。
 *
 * <p>这类缺陷是「看起来有、其实没有」：正则只匹配字段名时，
 * 输出里 still 有 password 这几个字，肉眼扫一眼会以为脱敏生效了，
 * 但真实口令就在后面跟着（见 audit A-01）。所以这里断言的是**值不出现**，
 * 而不是「结果里有 ****」。
 */
class LogAspectMaskTest {

    @Test
    void 字符串口令被整段替换() {
        String json = "{\"username\":\"admin\",\"password\":\"root123\"}";
        String masked = LogAspect.maskSensitive(json);
        assertFalse(masked.contains("root123"), "口令值仍在：" + masked);
        assertTrue(masked.contains("\"password\":\"****\""), masked);
    }

    @Test
    void 多个敏感字段与嵌套结构都覆盖() {
        String json = "{\"token\":\"eyJhbGciOiJIUzI1NiwidHlwIjoiSldUIn0\","
                + "\"user\":{\"secret\":\"abc\",\"name\":\"张三\"},"
                + "\"authorization\":\"Bearer xxx\"}";
        String masked = LogAspect.maskSensitive(json);
        assertFalse(masked.contains("eyJhbGciOiJIUzI1NiwidHlwIjoiSldUIn0"), masked);
        assertFalse(masked.contains("\"abc\""), masked);
        assertFalse(masked.contains("Bearer xxx"), masked);
        // 非敏感字段一个都不能动
        assertTrue(masked.contains("\"name\":\"张三\""), masked);
    }

    @Test
    void 非字符串值也能盖住() {
        String masked = LogAspect.maskSensitive("{\"pwd\":123456}");
        assertFalse(masked.contains("123456"), masked);
    }

    @Test
    void 值里出现关键词不误伤() {
        // 只按字段名判断：业务文本里写 "password" 不该被替换
        String json = "{\"remark\":\"用户忘记 password 后走重置流程\"}";
        assertEquals(json, LogAspect.maskSensitive(json));
    }

    @Test
    void 脱敏后仍是合法JSON() {
        ObjectMapper mapper = new ObjectMapper();
        String masked = LogAspect.maskSensitive(
                "{\"username\":\"admin\",\"password\":\"root123\",\"profile\":{\"secret\":\"s\"}}");
        assertDoesNotThrow(() -> mapper.readTree(masked), "脱敏破坏了 JSON：" + masked);
    }

    @Test
    void 空值直接返回() {
        assertNull(LogAspect.maskSensitive(null));
    }
}
