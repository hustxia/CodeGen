package com.codegen.service;

import com.codegen.vo.TestVO;

/**
 * V9.2 T3 按表生成冒烟用例。
 *
 * <p>生成粒度是「一个业务表 → 一个 api 套件 + 6~7 条用例」；
 * 重新生成时按「用例名」对齐，并用指纹判定是否被手工改过：
 * 改过的跳过、没改过的覆盖，覆盖过程可回滚（随生成批次走）。
 */
public interface TestSuiteGenService {

    /** 按 gen_table 生成/刷新冒烟套件，返回本次新增/更新/跳过数量 */
    TestVO.GenResult generate(Long tableId);
}
