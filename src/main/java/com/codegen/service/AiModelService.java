package com.codegen.service;

import com.codegen.vo.GenPipelineAiModulesVO;
import com.codegen.vo.GenPipelineAiVO;

/**
 * AI 领域建模：名称 + 一句话描述 → 候选表结构。
 *
 * <p>失败一律抛业务异常，由前端降级「手动粘贴 DDL」——
 * AI 不可用不该阻断整个建系统流程。
 *
 * <p>两种规模：
 * <ul>
 *   <li>精简：一次调用整体建模（3-5 张表）</li>
 *   <li>完整系统：先 {@link #modules} 拆模块，再带 moduleName 逐模块调 {@link #model}，
 *       前端循环合并——单次输出预算可控，本地小模型也能凑出几十张表</li>
 * </ul>
 */
public interface AiModelService {

    /**
     * 生成候选表结构（含中文注释，审计列由服务端统一补齐，AI 只管业务列）。
     *
     * @param moduleName 限定模块名；为空则整体建模（3-5 张表），
     *                   非空则只设计该模块的表（6-8 张）
     */
    GenPipelineAiVO model(String systemName, String systemDesc, String moduleName);

    /**
     * 把系统拆分为 8-12 个业务模块（完整系统模式第一步）
     */
    GenPipelineAiModulesVO modules(String systemName, String systemDesc);
}
