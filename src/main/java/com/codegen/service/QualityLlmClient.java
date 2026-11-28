package com.codegen.service;

import com.codegen.quality.QualityRule;
import com.codegen.vo.GenFileVO;

import java.util.List;

/**
 * 质量门禁的 LLM 语义引擎（M2.2）。
 *
 * <p>复用平台 OpenAI 兼容配置（{@code gen.pipeline.ai.*}）。实现必须严格 JSON、
 * 带超时；任何失败由调用方降级到确定性引擎，不允许阻断生成。
 */
public interface QualityLlmClient {

    /**
     * 配置是否可用（enabled + baseUrl + apiKey + model 齐全）
     */
    boolean available();

    /**
     * 批量语义审查。
     *
     * @param files 渲染产物（实现内部按大小截断）
     * @param rules 需要 LLM 执行的规则
     * @return 命中项
     * @throws Exception 网络/超时/解析失败
     */
    List<LlmIssue> analyze(List<GenFileVO> files, List<QualityRule> rules) throws Exception;

    record LlmIssue(String ruleId, String file, Long line, String severity,
                    String message, String badSnippet, String fixSnippet) {
    }
}
