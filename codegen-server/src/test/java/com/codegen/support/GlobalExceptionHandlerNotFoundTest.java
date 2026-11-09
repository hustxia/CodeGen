package com.codegen.support;

import com.codegen.common.exception.GlobalExceptionHandler;
import com.codegen.common.result.Result;
import com.codegen.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * V7.4 P0-4：资源不存在必须映射成真 404，不能落进 Exception 兜底变成
 * {@code HTTP 200 + code:500 "服务异常"}（Spring 6.1 起静态资源缺失抛
 * {@link NoResourceFoundException}，关掉 springdoc 后访问 /v3/api-docs 即命中）。
 */
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NoResourceFoundException → HTTP 404 + code 404，响应体仍是 Result 形状")
    void noResourceMapsTo404() {
        ResponseEntity<Result<Void>> resp =
                handler.handleNoResource(new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/v3/api-docs"));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals(404, resp.getBody().getCode());
        assertEquals(ResultCode.NOT_FOUND.getMsg(), resp.getBody().getMsg());
    }

    @Test
    @DisplayName("其它异常仍走 500 兜底，不被 404 分支吞掉")
    void otherExceptionStill500() {
        Result<Void> r = handler.handleException(new IllegalStateException("boom"));

        assertEquals(ResultCode.FAIL.getCode(), r.getCode());
        assertNotEquals(ResultCode.NOT_FOUND.getCode(), r.getCode());
    }

    @Test
    @DisplayName("404 分支不是 200：HTTP 状态与 body code 一致")
    void statusAndBodyConsistent() {
        ResponseEntity<Result<Void>> resp =
                handler.handleNoResource(new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/nope"));

        assertEquals(resp.getStatusCode().value(), resp.getBody().getCode());
    }
}
