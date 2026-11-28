package com.codegen.support;

import com.codegen.common.exception.BusinessException;
import com.codegen.entity.GenGuardrailRule;
import com.codegen.vo.GuardrailEvalVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V9.25 确定性护栏规则引擎。
 *
 * <p>只解释四种 trigger_type 与 trigger_expr，不写任何行业业务词——业务知识全在
 * {@code gen_guardrail_rule} 表里。沿用 V9.4.1 坑 70 的护栏取舍：
 * 确定性命中就拦截/告警，不自动重试、不调用 LLM。
 */
@Component
public class GuardrailRuleEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 对启用的规则逐条判定，命中项带「为什么拦 + 怎么做才对」 */
    public GuardrailEvalVO evaluate(List<GenGuardrailRule> rules, String text, Map<String, String> answers) {
        GuardrailEvalVO vo = new GuardrailEvalVO();
        String content = text == null ? "" : text;
        Map<String, String> a = answers == null ? Map.of() : answers;
        if (rules == null) {
            return vo;
        }
        for (GenGuardrailRule rule : rules) {
            if (rule.getEnabled() != null && rule.getEnabled() == 0) {
                continue;
            }
            if (!matches(rule, content, a)) {
                continue;
            }
            GuardrailEvalVO.Hit hit = new GuardrailEvalVO.Hit();
            hit.setRuleCode(rule.getRuleCode());
            hit.setCategory(rule.getCategory());
            hit.setSeverity(rule.getSeverity());
            hit.setTitle(rule.getTitle());
            hit.setReason(rule.getReason());
            hit.setSuggestion(rule.getSuggestion());
            vo.getHits().add(hit);
            if (GenGuardrailRule.SEVERITY_BLOCK.equals(rule.getSeverity())) {
                vo.setBlockCount(vo.getBlockCount() + 1);
            } else if (GenGuardrailRule.SEVERITY_WARN.equals(rule.getSeverity())) {
                vo.setWarnCount(vo.getWarnCount() + 1);
            }
        }
        vo.setBlocked(vo.getBlockCount() > 0);
        return vo;
    }

    /** 单条规则判定，静态方法便于单测与复用 */
    public static boolean matches(GenGuardrailRule rule, String text, Map<String, String> answers) {
        String expr = rule.getTriggerExpr() == null ? "" : rule.getTriggerExpr().trim();
        String type = rule.getTriggerType() == null ? "" : rule.getTriggerType();
        String content = text == null ? "" : text;
        Map<String, String> a = answers == null ? Map.of() : answers;
        return switch (type) {
            case "KEYWORD" -> containsAny(content, split(expr));
            case "MISSING" -> !content.contains(stringOf(json(expr), "moduleKeyword"));
            case "COMBO" -> combo(expr, content, a);
            case "ANSWER" -> Objects.equals(a.get(stringOf(json(expr), "answerCode")), stringOf(json(expr), "expect"));
            case "CLARIFY" -> false;
            default -> false;
        };
    }

    private static boolean combo(String expr, String content, Map<String, String> answers) {
        Map<String, Object> cfg = json(expr);
        String qc = stringOf(cfg, "questionCode");
        String expected = stringOf(cfg, "answer");
        String keywords = stringOf(cfg, "keyword");
        return expected.equals(answers.get(qc)) && containsAny(content, split(keywords));
    }

    private static boolean containsAny(String content, String[] words) {
        for (String word : words) {
            if (!word.isEmpty() && content.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String[] split(String expr) {
        return expr.isEmpty() ? new String[0] : java.util.Arrays.stream(expr.split("\\|"))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    private static String stringOf(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String expr) {
        if (expr.isEmpty()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(expr, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "护栏规则表达式非法，无法解析 JSON: " + expr);
        }
    }
}
