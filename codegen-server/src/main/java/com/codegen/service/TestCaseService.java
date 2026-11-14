package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.entity.TestCase;
import com.codegen.vo.TestVO;

/**
 * V9.2 测试用例服务：CRUD + 试运行（不落执行批次）。
 */
public interface TestCaseService extends IService<TestCase> {

    /**
     * 试运行：内存执行一次，不写 test_run / 不更新 lastResult。
     * 生产环境下的写用例直接拒绝，不发请求。
     */
    TestVO.TryResult tryRun(Long caseId, Long envId);
}
