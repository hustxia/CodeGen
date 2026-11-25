package com.codegen.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6.7 唯一性校验 400 断言（前后端查重对齐）——错误体结构契约。
 *
 * <p>产物工程的 BusinessException / GlobalExceptionHandler 由
 * {@link ScaffoldCommonWriter} 以字符串工厂产出，本测试钉住三件事：
 * <ol>
 *   <li>BusinessException 必须带 {@code field} 字段与三参构造（唯一查重专用）；</li>
 *   <li>GlobalExceptionHandler 对 code=400 返回真实 HTTP 400（前端 axios 走 error 分支
 *       才能拿到 {@code e.response.data} 做字段级提示）；</li>
 *   <li>错误体 {@code data.field} 指向冲突列；其余错误码维持 HTTP 200 + body code 旧契约。</li>
 * </ol>
 *
 * <p>纯字符串断言、不起 Spring：产物源码以字符串形式存在，编译期检查发生在
 * 产物侧（GenSelfCheckTestCompileTest 的 javac 自检链路）。
 */
@DisplayName("V6.7 唯一性错误体结构：BusinessException.field + HTTP 400 + data.field")
class UniqueConflictErrorBodyTest {

    private static final String PKG = "com.demo";

    private static String exceptionJava() {
        return ScaffoldCommonWriter.businessExceptionJava(PKG);
    }

    private static String handlerJava() {
        return ScaffoldCommonWriter.globalExceptionHandlerJava(PKG);
    }

    @Test
    @DisplayName("BusinessException 增加 field 字段与三参构造，旧构造不受影响")
    void exceptionHasFieldSupport() {
        String src = exceptionJava();
        assertTrue(src.contains("package " + PKG + ".common.exception;"), "包名必须拼对");
        assertTrue(src.contains("private final String field;"), "必须带冲突字段 field");
        assertTrue(src.contains("public BusinessException(int code, String message, String field)"),
                "必须提供 (code, message, field) 三参构造");
        // 旧三参以内的构造签名原样保留，既有生成代码不用重编译适配
        assertTrue(src.contains("public BusinessException(ResultCode resultCode)"), "ResultCode 构造保留");
        assertTrue(src.contains("public BusinessException(String message)"), "单参构造保留");
        assertTrue(src.contains("public BusinessException(int code, String message) {"), "两参构造保留");
        assertTrue(src.contains("this.field = null;"), "旧构造 field 必须置空，不污染非查重场景");
        assertTrue(src.contains("@Getter"), "field 取值走 lombok getter");
    }

    @Test
    @DisplayName("V9.17：唯一冲突工厂固定 code=400（取自 ResultCode.PARAM_ERROR），字段随行")
    void uniqueFactoryPins400() {
        String src = exceptionJava();
        assertTrue(src.contains("public static BusinessException unique(String message, String field)"),
                "必须提供唯一冲突专用工厂，避免调用点各写魔法数字 400");
        assertTrue(src.contains("return new BusinessException(ResultCode.PARAM_ERROR.getCode(), message, field);"),
                "工厂必须复用 ResultCode.PARAM_ERROR 并透传 field");
        String codes = ScaffoldCommonWriter.resultCodeJava(PKG);
        assertTrue(codes.contains("PARAM_ERROR(400, \"参数错误\")"), "PARAM_ERROR 必须仍为 400");
    }

    @Test
    @DisplayName("handleBusinessException 返回 ResponseEntity，code=400 映射真实 HTTP 400")
    void handlerMaps400ToRealHttp400() {
        String src = handlerJava();
        assertTrue(src.contains(
                        "public ResponseEntity<Result<Map<String, Object>>> handleBusinessException(BusinessException ex)"),
                "handleBusinessException 必须返回 ResponseEntity 且 data 能装下 field 结构");
        assertTrue(src.contains("if (ex.getCode() == 400) {"), "必须按 code=400 分支");
        assertTrue(src.contains("return ResponseEntity.badRequest().body(body);"),
                "code=400 必须映射真实 HTTP 400（前端查重对齐的前提）");
        assertTrue(src.contains("return ResponseEntity.ok().body(body);"),
                "其余错误码维持 HTTP 200 + body code 的既有契约，不扩大影响面");
    }

    @Test
    @DisplayName("错误体 data.field 指向冲突列；field 为空时 data 保持 null")
    void handlerAttachesFieldToBody() {
        String src = handlerJava();
        assertTrue(src.contains("if (ex.getField() != null && !ex.getField().isBlank())"),
                "field 空白时不得塞 data（非查重异常的错误体形状不变）");
        assertTrue(src.contains("data.put(\"field\", ex.getField());"),
                "错误体必须带 data.field");
        assertTrue(src.contains("Map<String, Object> data = new LinkedHashMap<>();"),
                "data 用 LinkedHashMap 保证序列化键序稳定");
    }

    @Test
    @DisplayName("处理器其余分支（参数校验/兜底 500）不被改动")
    void otherBranchesUntouched() {
        String src = handlerJava();
        assertTrue(src.contains("@ExceptionHandler(MethodArgumentNotValidException.class)"),
                "参数校验分支保留");
        assertTrue(src.contains("Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);"),
                "参数校验仍走 PARAM_ERROR");
        assertTrue(src.contains("@ExceptionHandler(Exception.class)"), "兜底分支保留");
        assertTrue(src.contains("Result.fail(ResultCode.FAIL);"), "兜底 500 保留");
        assertFalse(src.contains("@ResponseStatus"), "不得用类级/方法级 @ResponseStatus 把所有错误码一锅端");
    }
}
