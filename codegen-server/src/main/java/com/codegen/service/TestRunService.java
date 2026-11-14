package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestRun;
import com.codegen.vo.TestVO;

/**
 * V9.2 执行编排：串行执行套件内用例、保存结果、回写批次汇总。
 */
public interface TestRunService extends IService<TestRun> {

    /** 发起执行；同一用户同时只允许一个 running 批次 */
    TestVO.RunDetail start(TestCenterDTO.RunRequest req);

    /** 批次详情：批次头 + 用例结果列表 */
    TestVO.RunDetail detail(Long runId);

    /** 中断：标记后续用例为跳过，已发出的请求不强行打断 */
    void abort(Long runId);
}
