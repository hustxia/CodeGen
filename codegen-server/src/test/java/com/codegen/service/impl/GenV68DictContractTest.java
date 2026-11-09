package com.codegen.service.impl;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.8 字典脚手架「同源契约」回归测试（纯函数 + 反射，不连库、不起 Spring）。
 *
 * <p>背景：目标工程 request 拦截器按 {@code res.code === 200} 判成功，
 * vuePage.ftl 的 loadDicts 取 {@code res.data}。dictControllerJava 若直接返回裸 List，
 * 每个带字典的列表页都会弹「请求失败」且字典翻译全空。
 * 这里把「后端生成物包装」与「前端模板消费端」两端同时锁住，防止任一侧单独回退。
 */
class GenV68DictContractTest {

    private String dictController() throws Exception {
        Class<?> clazz = Class.forName("com.codegen.service.impl.GenScaffoldServiceImpl");
        Object svc = clazz.getDeclaredConstructor().newInstance();
        Method m = clazz.getDeclaredMethod("dictControllerJava", String.class);
        m.setAccessible(true);
        return String.valueOf(m.invoke(svc, "com.demo"));
    }

    @Test
    void dictController_returnsWrappedResult() throws Exception {
        String code = dictController();
        assertTrue(code.contains("import com.demo.common.result.Result;"),
                "字典 Controller 必须 import 统一返回体 Result");
        assertTrue(code.contains("public Result<List<Map<String, String>>> byType("),
                "返回类型必须是 Result 包装，不能是裸 List");
        assertTrue(code.contains("return Result.ok(out);"), "正常分支必须 Result.ok 包装");
        assertTrue(code.contains("return Result.ok(List.of());"), "降级分支也必须包装，否则空字典会误报失败");
        assertFalse(code.contains("public List<Map<String, String>> byType("),
                "回退到裸数组返回会让所有字典页报错（V6.8 同源缺陷）");
    }

    @Test
    void vuePageTemplate_consumesWrappedData() throws Exception {
        String tpl;
        try (InputStream in = getClass().getResourceAsStream("/gen-templates/vuePage.ftl")) {
            assertNotNull(in, "classpath 下应有 vuePage.ftl 模板");
            tpl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(tpl.contains("system/dict/data/type/"), "页面模板应调用字典只读接口");
        assertTrue(tpl.contains("(res && res.data) || []"),
                "页面模板必须取 res.data；若改为取裸数组，后端包装后字典会全空");
    }
}
