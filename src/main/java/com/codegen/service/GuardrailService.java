package com.codegen.service;

import com.codegen.dto.ClarifyAnswerDTO;
import com.codegen.dto.ClarifyConfirmDTO;
import com.codegen.dto.ClarifyStartDTO;
import com.codegen.dto.GuardrailEvaluateDTO;
import com.codegen.entity.GenClarifySession;
import com.codegen.entity.GenGuardrailRule;
import com.codegen.vo.ClarifyConfirmVO;
import com.codegen.vo.ClarifyStepVO;
import com.codegen.vo.GuardrailEvalVO;

import java.util.List;
import java.util.Map;

/** V9.25 护栏澄清向导服务 */
public interface GuardrailService {

    List<GenGuardrailRule> rules(String industry, String category);

    Map<String, Object> ruleCount();

    GuardrailEvalVO evaluate(GuardrailEvaluateDTO dto);

    ClarifyStepVO start(ClarifyStartDTO dto);

    ClarifyStepVO answer(ClarifyAnswerDTO dto);

    GuardrailEvalVO evaluateSession(Long sessionId);

    ClarifyConfirmVO confirm(Long sessionId, ClarifyConfirmDTO dto);

    GenClarifySession session(Long sessionId);

    ClarifyStepVO step(Long sessionId);

    List<GenClarifySession> sessions(Long projectId);
}
