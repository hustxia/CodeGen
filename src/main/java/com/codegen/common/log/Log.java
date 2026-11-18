package com.codegen.common.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。打在 Controller 方法上，由 {@link LogAspect} 统一落库。
 *
 * <p>只标写操作（增删改、导入导出）——把查询也记进来会让日志表迅速膨胀，
 * 真正需要追溯的变更反而查不到。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /**
     * 模块标题，如「订单管理」
     */
    String title() default "";

    /**
     * 操作类型：INSERT/UPDATE/DELETE/IMPORT/EXPORT/OTHER。
     * 留空时按 HTTP 方法推断。
     */
    String type() default "";

    /**
     * 是否记录请求参数
     */
    boolean saveParam() default true;

    /**
     * 是否记录返回结果
     */
    boolean saveResult() default false;
}
