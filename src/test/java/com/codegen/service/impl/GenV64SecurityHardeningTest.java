package com.codegen.service.impl;

import com.codegen.dto.GenScaffoldDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.4 安全加固三件套基线：登录限流（IP+账号滑动窗口）/ 单用户互踢 / 密码强度策略。
 *
 * <p>与 {@link GenV64SecurityBaselineTest} 同风格：纯函数产物内容断言，
 * 不连库、不起 Spring、不编译产物。三件套都是生成物侧能力，
 * 配置段由 {@code ScaffoldPomWriter#applicationYml} 下发，默认值保守（不改=与原行为兼容）。
 *
 * <p>红线：不得动 {@code src/test/resources/gen-baseline/}（该基线只锁 13 个 FTL
 * 模板产物，不含认证基座，本改动不应引起基线 diff）。
 */
class GenV64SecurityHardeningTest {

    private static final String PKG = "com.demo";

    // ---------- 能力 1：登录限流（IP+账号滑动窗口） ----------

    @Test
    void authController_rateLimitConfigDefaults() {
        String code = ScaffoldAuthWriter.authControllerJava(PKG);
        // 参数可配 + 保守默认值：60s 窗口 5 次失败锁 300s，默认开启
        assertTrue(code.contains("@Value(\"${auth.login-rate-limit.enabled:true}\")"), "限流开关必须可配且默认开启");
        assertTrue(code.contains("@Value(\"${auth.login-rate-limit.window-seconds:60}\")"), "窗口时长必须可配（默认 60s）");
        assertTrue(code.contains("@Value(\"${auth.login-rate-limit.max-failures:5}\")"), "失败上限必须可配（默认 5 次）");
        assertTrue(code.contains("@Value(\"${auth.login-rate-limit.lock-seconds:300}\")"), "锁定时长必须可配（默认 300s）");
    }

    @Test
    void authController_rateLimitIsSlidingWindowInMemory() {
        String code = ScaffoldAuthWriter.authControllerJava(PKG);
        // 滑动窗口：key = ip|username 双维度，Deque 时间戳 + 过期修剪
        assertTrue(code.contains("ip + \"|\" + username"), "限流维度必须同时包含 IP 与账号");
        assertTrue(code.contains("Deque<Long>"), "滑动窗口必须用时间戳队列");
        assertTrue(code.contains("window.peekFirst()"), "缺窗口过期修剪（非滑动窗口=固定窗口误伤）");
        assertTrue(code.contains("synchronized (window)"), "窗口操作必须并发安全");
        assertTrue(code.contains("loginFailures = new ConcurrentHashMap<>()"), "内存实现必须并发容器");
        // 零第三方依赖：不允许引入 Redis/Guava/Bucket4j
        assertFalse(code.contains("redis"), "内存限流不允许依赖 Redis");
        assertFalse(code.contains("Bucket4j"), "内存限流不允许依赖 Bucket4j");
        assertFalse(code.contains("guava"), "内存限流不允许依赖 Guava");
    }

    @Test
    void authController_rateLimitGateBeforeCaptcha() {
        String code = ScaffoldAuthWriter.authControllerJava(PKG);
        // 闸门必须在验证码消费之前，否则攻击者可用错误验证码免费探测
        int gate = code.indexOf("rateLimitGate(username, ip)");
        int captcha = code.indexOf("captchas.remove(captchaId)");
        assertTrue(gate >= 0, "缺限流闸门调用");
        assertTrue(captcha > gate, "限流闸门必须在验证码消费之前");
        // 触发限流返回明确错误码 429 + 剩余秒数
        assertTrue(code.contains("\"code\", 429"), "触发限流必须返回 429 错误码");
        assertTrue(code.contains("秒后再试"), "限流响应必须包含剩余时间提示");
        // 失败计数与成功清零
        assertTrue(code.contains("recordLoginFailure(username, ip)"), "失败路径必须计数");
        assertTrue(code.contains("clearLoginFailures(username, ip)"), "成功登录必须清零限流状态");
    }

    // ---------- 能力 2：单用户互踢 ----------

    @Test
    void tokenStore_kickEnabledByDefaultAndConfigurable() {
        String code = ScaffoldAuthWriter.tokenStoreJava(PKG);
        assertTrue(code.contains("@Value(\"${auth.kick-enabled:true}\")"), "互踢开关必须可配且默认开启");
        assertTrue(code.contains("kickPreviousSessions"), "签发必须执行互踢");
        assertTrue(code.contains("if (!kickEnabled)"), "互踢必须受开关控制");
        // 踢旧会话：标记 deleted=1 + 同步清内存缓存（否则 60s 缓存 TTL 内旧 token 仍可用）
        assertTrue(code.contains("UPDATE sys_session SET deleted = 1 WHERE username = ? AND token <> ?"), "旧会话必须标记失效");
        assertTrue(code.contains("cache.remove(old)"), "旧 token 必须同步清内存缓存");
        assertTrue(code.contains("token <> ? AND deleted = 0"), "互踢范围必须是同账号的其他有效会话");
    }

    @Test
    void tokenStore_stateOfDistinguishesKickedFromExpired() {
        String code = ScaffoldAuthWriter.tokenStoreJava(PKG);
        assertTrue(code.contains("public AuthState stateOf(String token)"), "必须提供 stateOf 区分被踢与自然失效");
        assertTrue(code.contains("public record AuthState(String username, boolean kicked)"), "AuthState 必须携带 kicked 标记");
        assertTrue(code.contains("SELECT COUNT(*) FROM sys_session WHERE token = ? AND deleted = 1"), "被踢判定依赖 deleted=1 痕迹");
        assertTrue(code.contains("AuthState state = stateOf(token);\n"
                + "        return state == null ? null : state.username();"), "usernameOf 必须委托 stateOf（/auth/me 兼容）");
        // 既有双层结构不回退（GenV64SecurityBaselineTest 的红线在此重复锁一道）
        assertTrue(code.contains("INSERT INTO sys_session"), "签发必须落库");
        assertTrue(code.contains("SELECT username, expire_time FROM sys_session"), "校验必须回源查库");
        assertTrue(code.contains("plusHours(sessionHours)"), "缺滑动续期");
    }

    @Test
    void authFilter_kickReturns401WithDedicatedCode() {
        String code = ScaffoldAuthWriter.authContextFilterJava(PKG, false, false);
        assertTrue(code.contains("tokenStore.stateOf(token)"), "过滤器必须走 stateOf 细分被踢场景");
        assertTrue(code.contains("state.kicked()"), "缺被踢分支");
        assertTrue(code.contains("writeKicked"), "缺被踢响应方法");
        assertTrue(code.contains("Result.fail(4011,"), "被踢必须返回专属业务码 4011");
        assertTrue(code.contains("账号已在其他设备登录"), "被踢文案必须明确");
        // HTTP 语义仍是 401（未授权），业务码细分原因
        assertTrue(code.contains("SC_UNAUTHORIZED"), "被踢必须是 HTTP 401");
        // 既有两级鉴权不回退
        assertTrue(code.contains("permissionChecker.check"), "缺第二级权限点校验");
        assertTrue(code.contains("writeForbidden"), "缺 403 响应");
    }

    // ---------- 能力 3：密码强度策略 ----------

    @Test
    void sysUser_passwordPolicyConfigDefaults() {
        String code = ScaffoldAuthWriter.sysUserControllerJava(PKG);
        assertTrue(code.contains("@Value(\"${auth.password-policy.min-length:6}\")"), "最小长度必须可配（默认 6，兼容种子 admin123）");
        assertTrue(code.contains("@Value(\"${auth.password-policy.require-letter-and-digit:false}\")"),
                "字母+数字开关必须可配（默认关，兼容默认口令 123456）");
        assertTrue(code.contains("@Value(\"${auth.password-policy.pattern:}\")"), "正则策略必须可配（默认空=关闭）");
    }

    @Test
    void sysUser_passwordPolicyAppliedOnSaveAndResetPwd() {
        String code = ScaffoldAuthWriter.sysUserControllerJava(PKG);
        assertTrue(code.contains("private String pwdViolation(String password)"), "缺强度校验方法");
        assertTrue(code.contains("密码长度不能少于"), "长度违规必须有明确提示");
        assertTrue(code.contains("密码必须同时包含字母和数字"), "字母数字违规必须有明确提示");
        assertTrue(code.contains("密码不符合安全策略要求"), "正则违规必须有明确提示");
        // save 与 resetPwd 两个入口都必须校验（=2 处调用）
        int calls = countOccurrences(code, "return fail(pwdViolation);");
        assertEquals(2, calls, "save 与 resetPwd 都必须接入强度校验，实际调用数=" + calls);
    }

    // ---------- 配置段：application.yml 生成物下发 ----------

    @Test
    void applicationYml_authHardeningSection() {
        String yml = ScaffoldPomWriter.applicationYml(
                new GenScaffoldDTO(), PKG, "demo", 8080, "demo", false, false, false);
        assertTrue(yml.contains("auth:\n"), "缺 auth 配置段");
        assertTrue(yml.contains("session-hours: 24"), "既有 session-hours 不回退");
        assertTrue(yml.contains("login-rate-limit:\n"
                + "    enabled: true\n"
                + "    window-seconds: 60\n"
                + "    max-failures: 5\n"
                + "    lock-seconds: 300"), "限流配置段缺省值不正确");
        assertTrue(yml.contains("kick-enabled: true"), "互踢开关缺省值不正确");
        assertTrue(yml.contains("password-policy:\n"
                + "    min-length: 6\n"
                + "    require-letter-and-digit: false\n"
                + "    pattern: \"\""), "密码策略配置段缺省值不正确");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
