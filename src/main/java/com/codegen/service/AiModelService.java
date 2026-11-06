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

    /**
     * V7.1 种子数据 AI 增强：根据系统名/描述与表结构元数据，生成业务贴合的语义值池
     * （商品名/公司名/备注等 + 核心业务列的专属取值）。
     *
     * <p>结构约束仍由规则引擎保证——AI 只换取值来源，不直接产出行数据。
     * AI 未启用/调用失败/解析失败一律抛业务异常，由 seed 步回落规则词库，不阻断流水线。
     *
     * @param tables 表元数据（表名/注释 + 列名/注释），超过 60 张时由实现截断或分批
     */
    com.codegen.support.SeedValuePools seedPools(String systemName, String systemDesc,
                                                 java.util.List<com.codegen.support.GenSeedGenerator.SeedTableMeta> tables);
}
