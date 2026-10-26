package com.codegen.dto;

import lombok.Data;

/**
 * 操作日志分页查询条件
 */
@Data
public class SysOperLogPageDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /**
     * 模块标题，模糊匹配
     */
    private String title;

    /**
     * 操作类型精确匹配
     */
    private String operType;

    /**
     * 操作人用户名，模糊匹配
     */
    private String operName;

    /**
     * 1 成功 0 失败，null 为全部
     */
    private Integer status;

    /**
     * 起始时间 yyyy-MM-dd HH:mm:ss
     */
    private String startTime;

    /**
     * 结束时间 yyyy-MM-dd HH:mm:ss
     */
    private String endTime;
}
