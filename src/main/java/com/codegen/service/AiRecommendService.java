package com.codegen.service;

import com.codegen.vo.GenAiRecommendVO;

/**
 * V7.0 AI 页面与菜单推荐：表结构就绪后，自动推荐三层配置——
 * 列级（查询/列表/表单/字典/排序）、表级（模块分组/树形/主子）、菜单组织。
 *
 * <p>与 AI 建模同一原则：AI 不可用不该阻断建系统流程。
 * 任何失败一律抛业务异常，由 pipeline 步骤降级「规则推断」兜底（V6.6 基线）。
 *
 * <p>输出经 {@link com.codegen.support.AiRecommendSanitizer} 白名单清洗：
 * 非法枚举、引用不存在的列/表/字典、主子成环逐项丢弃，
 * 被丢弃的字段在应用时自然回落到 V6.6 规则推断的现值。
 */
public interface AiRecommendService {

    /**
     * 为一个系统的全部表生成三层建议（按模块分批调用，单批输出预算可控）。
     *
     * @param projectId sys_project.id
     * @return 清洗后的建议产物；tables 可能为空（AI 全废或全被丢弃），调用方按降级处理
     * @throws com.codegen.common.exception.BusinessException AI 未启用 / 超时 / 解析失败
     */
    GenAiRecommendVO recommend(Long projectId);
}
