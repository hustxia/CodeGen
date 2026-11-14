package com.codegen.testcenter;

/**
 * 测试中心业务异常，携带 HTTP 语义码，由 Controller 直接映射成 Result.fail。
 */
public class TcException extends RuntimeException {

    private final int code;

    public TcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TcException conflict(String message) {
        return new TcException(409, message);
    }

    public static TcException forbidden(String message) {
        return new TcException(403, message);
    }

    public static TcException badRequest(String message) {
        return new TcException(400, message);
    }

    public static TcException notFound(String message) {
        return new TcException(404, message);
    }
}
