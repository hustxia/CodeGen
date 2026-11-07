package com.codegen.common.exception;

import com.codegen.common.result.Result;
import com.codegen.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.stream.Collectors;

/**
 * 全局异常兜底
 *
 * <p>参数类异常必须单独映射：它们本来全落到最后的 {@code Exception} 分支，
 * 表现是「前端传错参数」被包装成 500「服务异常」，日志里刷一整页堆栈，
 * 排查时根本分不清是调用方用错还是服务端 bug（见 audit B-02）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}, msg={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ":" + e.getDefaultMessage())
                .orElse(ResultCode.PARAM_ERROR.getMsg());
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 表单/查询参数绑定失败（@ModelAttribute、非 @RequestBody 的 DTO）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ":" + e.getDefaultMessage())
                .orElse(ResultCode.PARAM_ERROR.getMsg());
        log.warn("参数绑定失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 方法参数上的约束校验（@Validated + @Min/@NotBlank 直接标在参数上）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations() == null ? ResultCode.PARAM_ERROR.getMsg()
                : ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数约束校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 请求体不是合法 JSON，或字段类型对不上（如给数字字段传了字符串）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误，请检查 JSON 与字段类型");
    }

    /**
     * 路径变量/查询参数类型不匹配。
     *
     * <p>高频场景：{@code /gen/table/list} 被 {@code /gen/table/{id}} 吃掉，
     * "list" 转 Long 失败——原来表现为 500，看不出是路由冲突。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = "参数 " + ex.getName() + " 类型不匹配：" + ex.getValue();
        log.warn(msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        String msg = "缺少必填参数：" + ex.getParameterName();
        log.warn(msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 方法不支持 / 媒体类型不支持：属于调用方用错，不该记成系统异常
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public Result<Void> handleMethodOrMedia(Exception ex) {
        log.warn("请求方式或 Content-Type 不支持: {}", ex.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), ex.getMessage());
    }

    /**
     * 上传体积超限：Spring 在进入 controller 之前就抛了，
     * 不单独映射会落到最后的 Exception 分支变成 500「服务异常」。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超过限制: {}", ex.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "上传文件超过大小限制（单文件 30MB / 请求 35MB）");
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public Result<Void> handleMissingPathVariable(MissingPathVariableException ex) {
        String msg = "缺少路径变量：" + ex.getVariableName();
        log.warn(msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 静态资源 / 端点不存在（Spring 6.1 起抛 {@code NoResourceFoundException}）。
     *
     * <p>不单独映射会落到下面 {@code Exception.class} 兜底，表现是
     * {@code HTTP 200 + code:500 "服务异常"} 并打一整页 ERROR 堆栈：
     * V7.4 起服务实证，关闭 springdoc 后访问 /v3/api-docs 即命中。
     * 这里返回真正的 404，同时保持 {@code Result} 响应体形状不变。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        log.debug("资源不存在: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ResultCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.fail(ResultCode.FAIL);
    }
}
