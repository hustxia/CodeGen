package com.codegen.service;

import com.codegen.vo.GenVerifyVO;

import java.util.List;

/**
 * 前端产物真校验服务（四期 F14）：读磁盘实际文件做静态解析，
 * 与 {@link GenSelfCheckService}（校验内存渲染产物）互补。
 */
public interface GenVerifyService {

    /**
     * 按表配置校验其最近一次写盘批次的磁盘产物
     *
     * @param ids 表配置 ID 列表
     */
    GenVerifyVO check(List<Long> ids);
}
