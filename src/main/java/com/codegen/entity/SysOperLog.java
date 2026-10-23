package com.codegen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codegen.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志。
 *
 * <p>只记「谁在什么时候对什么做了什么」——不记查询，
 * 否则一次列表页就把日志表打满，真正需要追溯的写操作反而被淹没。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {

    /**
     * 模块标题，如「订单管理」
     */
    private String title;

    /**
     * 操作类型：INSERT/UPDATE/DELETE/IMPORT/EXPORT/OTHER
     */
    private String operType;

    /**
     * 方法名（全限定名#方法）
     */
    private String method;

    /**
     * 请求地址
     */
    private String requestUri;

    /**
     * HTTP 方法
     */
    private String requestMethod;

    /**
     * 请求参数（已按字段名做敏感信息脱敏）
     */
    private String operParam;

    /**
     * 返回结果（截断保存）
     */
    private String jsonResult;

    /**
     * 1 成功 0 失败
     */
    private Integer status;

    /**
     * 失败时的错误消息
     */
    private String errorMsg;

    /**
     * 操作人 ID
     */
    private Long operUserId;

    /**
     * 操作人用户名
     */
    private String operName;

    /**
     * 操作 IP
     */
    private String operIp;

    /**
     * 省份（由 IP 解析，内网/本机时记「内网」「本机」）
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 耗时毫秒
     */
    private Long costMs;

    /**
     * 操作时间
     */
    private java.time.LocalDateTime operTime;
}
