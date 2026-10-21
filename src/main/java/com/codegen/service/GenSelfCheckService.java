package com.codegen.service;

import com.codegen.vo.GenSelfCheckVO;

import java.util.List;

/**
 * 生成自检：对渲染产物做编译级与结构级检查
 */
public interface GenSelfCheckService {

    /**
     * 检查指定表配置的生成产物
     *
     * @param ids 表配置 ID
     * @return 检查结果（不抛业务异常，问题以 issues 返回）
     */
    GenSelfCheckVO check(List<Long> ids);
}
