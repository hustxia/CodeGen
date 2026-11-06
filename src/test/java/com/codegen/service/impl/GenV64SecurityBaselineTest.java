package com.codegen.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.4 权限安全基线：脚手架产物内容断言（纯函数 + 反射，不连库、不起 Spring）。
 * 覆盖 P0-1~P0-4 的生成物关键片段——这些是安全基线，缺一段就是真实漏洞。
 */
class GenV64SecurityBaselineTest {

    private Object scaffold() throws Exception {
        Class<?> clazz = Class.forName("com.codegen.service.impl.GenScaffoldServiceImpl");
        return clazz.getDeclaredConstructor().newInstance();
    }

    private String invokeScaffold(String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Object svc = scaffold();
        Method m = svc.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return String.valueOf(m.invoke(svc, args));
    }

    // ---------- P0-1 Token 持久化 ----------

    @Test
    void seedSql_containsSessionAndPermissionTables() throws Exception {
        String sql = invokeScaffold("seedSql", new Class<?>[]{}, new Object[]{});
        // 会话表：token 持久化，重启不掉线
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_session"), "缺 sys_session 表");
        assertTrue(sql.contains("expire_time"), "缺过期时间列（滑动续期依据）");
        // 权限体系表基座：V3.0 菜单注册 SQL 的字段口径
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_role"), "缺 sys_role");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_menu"), "缺 sys_menu");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_user_role"), "缺 sys_user_role");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_role_menu"), "缺 sys_role_menu");
        assertTrue(sql.contains("role_key"), "缺 role_key（admin 直通判据）");
        // 登录日志
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_login_log"), "缺 sys_login_log");
        // 坑48：操作日志表必须随种子建，否则新库一调业务接口就 500
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sys_oper_log"), "缺 sys_oper_log（坑48）");
        // admin 直通角色种子
        assertTrue(sql.contains("超级管理员"), "缺 admin 角色种子");
    }

    @Test
    void tokenStore_isDualLayer() throws Exception {
        String code = invokeScaffold("tokenStoreJava", new Class<?>[]{String.class},
                new Object[]{"com.demo"});
        assertTrue(code.contains("INSERT INTO sys_session"), "签发必须落库");
        assertTrue(code.contains("SELECT username, expire_time FROM sys_session"), "校验必须回源查库");
        assertTrue(code.contains("cleanupExpiredIfDue"), "缺过期会话惰性清理");
        assertTrue(code.contains("sessionHours"), "会话时长必须可配");
        assertTrue(code.contains("plusHours(sessionHours)"), "缺滑动续期");
        assertFalse(code.contains("ConcurrentHashMap.Entry"), "不应再有纯内存实现");
    }

    // ---------- P0-2 BCrypt ----------

    @Test
    void seedSql_adminPasswordIsBcryptAndColumnIsAlgoAware() throws Exception {
        String sql = invokeScaffold("seedSql", new Class<?>[]{}, new Object[]{});
        assertTrue(sql.contains("password_algo"), "缺算法标记列（迁移分派依据）");
        assertTrue(sql.contains("varchar(72)"), "密码列必须容纳 BCrypt 60 位");
        assertTrue(sql.contains("$2a$10$"), "admin 种子必须是 BCrypt 预生成值");
        assertFalse(sql.contains("SHA2('admin123'"), "不允许再产 SHA-256 种子");
    }

    @Test
    void authController_migratesSha256Silently() throws Exception {
        String code = invokeScaffold("authControllerJava", new Class<?>[]{String.class},
                new Object[]{"com.demo"});
        assertTrue(code.contains("BCryptPasswordEncoder"), "缺 BCrypt 校验器");
        assertTrue(code.contains("password_algo"), "登录必须按 algo 分派");
        assertTrue(code.contains("UPDATE sys_user SET password = ?, password_algo = 'bcrypt'"), "缺无感迁移写回");
        assertTrue(code.contains("sha256(password).equalsIgnoreCase"), "旧算法比对路径必须保留");
    }

    // ---------- P0-3 接口权限点后端强制 ----------

    @Test
    void authFilter_hasTwoLevelCheck() throws Exception {
        // V6.6：签名加 fileEnabled（/uploads 直链进白名单）
        String code = invokeScaffold("authContextFilterJava",
                new Class<?>[]{String.class, boolean.class, boolean.class},
                new Object[]{"com.demo", false, false});
        assertTrue(code.contains("permissionChecker.check"), "缺第二级权限点校验");
        assertTrue(code.contains("writeForbidden"), "缺 403 响应");
        assertTrue(code.contains("SC_FORBIDDEN"), "403 必须是 HTTP 语义而不是 200 包错误码");
        assertTrue(code.contains("\"/auth/captcha\""), "验证码端点必须进白名单");
        assertFalse(code.contains("\"/uploads\""), "未开文件包时不应出现 /uploads 白名单");
    }

    @Test
    void authFilter_fileUploadWhiteList() throws Exception {
        // V6.6 文件包：图片直链是浏览器/img 标签请求，不带 token，必须进白名单
        String code = invokeScaffold("authContextFilterJava",
                new Class<?>[]{String.class, boolean.class, boolean.class},
                new Object[]{"com.demo", false, true});
        assertTrue(code.contains("\"/uploads\""), "开文件包时 /uploads 必须进白名单");
    }

    @Test
    void permissionChecker_isFailClosed() throws Exception {
        String code = invokeScaffold("permissionCheckerJava", new Class<?>[]{String.class},
                new Object[]{"com.demo"});
        assertTrue(code.contains("return \"GET\".equals(method)"), "缺省必须是读放行写拒绝");
        assertTrue(code.contains("*:*:*"), "缺超管通配");
        assertTrue(code.contains("lastIndexOf(':')"), "缺前缀通配匹配（demo_fee:*）");
        assertTrue(code.contains("CACHE_TTL_MS"), "缺 perms 60s 缓存");
        assertTrue(code.contains("role_key = 'admin'"), "缺 admin 直通判据");
        // 坑47：perms 挂在 sys_menu 上，sys_role_menu 只有 (role_id, menu_id)——
        // 必须三表联查，直接查 rm.perms 会让非 admin 用户查权限时 SQL 报错
        assertTrue(code.contains("JOIN sys_menu m ON m.id = rm.menu_id"), "perms 必须经 sys_menu 联查（坑47）");
        assertFalse(code.contains("rm.perms"), "不得直接查 sys_role_menu.perms（该表无此列）");
    }

    @Test
    void baseEndpoints_areExemptNotOpen() throws Exception {
        String flow = invokeScaffold("flowApiPermsJava", new Class<?>[]{String.class},
                new Object[]{"com.demo"});
        assertTrue(flow.contains("m.put(\"POST /flow/submit\", \"\")"), "流程端点应显式豁免而非漏配");
        String pay = invokeScaffold("payApiPermsJava", new Class<?>[]{String.class},
                new Object[]{"com.demo"});
        assertTrue(pay.contains("m.put(\"POST /pay/order\", \"\")"), "支付端点应显式豁免而非漏配");
    }

    @Test
    void loginLogController_isReadOnlyQuery() throws Exception {
        String code = invokeScaffold("loginLogControllerJava", new Class<?>[]{String.class},
                new Object[]{"com.demo"});
        assertTrue(code.contains("@PostMapping(\"/page\")"), "缺分页查询端点");
        assertFalse(code.contains("INSERT INTO sys_login_log"), "登录日志查询端点不允许有写路径");
    }

    // ---------- 生成侧：ApiPerms 随 Controller 同源产出 ----------

    @Test
    void apiPerms_generatedWithSamePermsSource() throws Exception {
        Method m = Class.forName("com.codegen.util.GenTemplateEngine")
                .getDeclaredMethod("apiPermsJava", Map.class);
        m.setAccessible(true);
        String code = String.valueOf(m.invoke(null, Map.of(
                "pkg", "com.v63.pay",
                "className", "DemoFee",
                "moduleName", "fee",
                "permsPrefix", "v63pay_demo_fee",
                "backendBase", "src/main/java/com/v63/pay",
                "hasFlow", Boolean.TRUE)));
        // 与 V3.0 五级回落 / 前端 v-hasPermi 同源的 perms 值
        assertTrue(code.contains("\"v63pay_demo_fee:list\""), "读端点应挂 :list");
        assertTrue(code.contains("\"v63pay_demo_fee:add\""), "新增应挂 :add");
        assertTrue(code.contains("\"v63pay_demo_fee:edit\""), "编辑应挂 :edit");
        assertTrue(code.contains("\"v63pay_demo_fee:remove\""), "删除应挂 :remove");
        assertTrue(code.contains("POST /fee\""), "新增端点路径必须与 @RequestMapping 对齐");
        assertTrue(code.contains("DELETE /fee/{id}"), "删除端点必须带 {id} 段通配");
        assertTrue(code.contains("POST /fee/flow/submit/{id}"), "流程提交端点应纳管");
        assertTrue(code.contains("class DemoFeeApiPerms implements ApiPermContributor"), "必须实现贡献者接口");
    }

    @Test
    void apiPerms_pathFollowsBackendBase() throws Exception {
        Method m = Class.forName("com.codegen.util.GenTemplateEngine")
                .getDeclaredMethod("apiPermsPath", Map.class);
        m.setAccessible(true);
        String path = String.valueOf(m.invoke(null, Map.of(
                "className", "DemoFee",
                "backendBase", "src/main/java/com/v63/pay")));
        assertEquals("src/main/java/com/v63/pay/common/auth/DemoFeeApiPerms.java", path);
    }
}
