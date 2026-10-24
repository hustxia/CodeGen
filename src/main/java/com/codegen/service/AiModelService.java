package com.codegen.service;

import com.codegen.vo.GenPipelineAiVO;

/**
 * AI 领域建模：名称 + 一句话描述 → 候选表结构。
 *
 * <p>失败一律抛业务异常，由前端降级「手动粘贴 DDL」——
 * AI 不可用不该阻断整个建系统流程。
 */
public interface AiModelService {

    /**
     * 生成候选表结构（≥3 张，含中文注释，审计列由服务端统一补齐，AI 只管业务列）
     */
    GenPipelineAiVO model(String systemName, String systemDesc);
}
