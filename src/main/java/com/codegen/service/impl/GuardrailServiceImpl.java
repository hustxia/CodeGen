package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codegen.common.exception.BusinessException;
import com.codegen.dto.ClarifyAnswerDTO;
import com.codegen.dto.ClarifyConfirmDTO;
import com.codegen.dto.ClarifyStartDTO;
import com.codegen.dto.GuardrailEvaluateDTO;
import com.codegen.entity.GenClarifyQuestion;
import com.codegen.entity.GenClarifySession;
import com.codegen.entity.GenGuardrailRule;
import com.codegen.entity.GenSpecArtifact;
import com.codegen.mapper.GenClarifyQuestionMapper;
import com.codegen.mapper.GenClarifySessionMapper;
import com.codegen.mapper.GenGuardrailRuleMapper;
import com.codegen.service.GenSpecService;
import com.codegen.service.GuardrailService;
import com.codegen.support.GuardrailRuleEngine;
import com.codegen.vo.ClarifyConfirmVO;
import com.codegen.vo.ClarifyStepVO;
import com.codegen.vo.GuardrailEvalVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V9.25 护栏澄清向导实现。
 *
 * <p>规则是数据、判定是代码；确认时写 SPEC 草案但不替用户 confirm，
 * 保留 V9.21 规格工件页的人工卡点。
 */
@Service
public class GuardrailServiceImpl implements GuardrailService {

    private static final Set<String> SUPPORTED_INDUSTRIES =
            Set.of("gov_affairs", "cm_city", "agr_crop", "park_park");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private GenGuardrailRuleMapper ruleMapper;
    @Autowired
    private GenClarifyQuestionMapper questionMapper;
    @Autowired
    private GenClarifySessionMapper sessionMapper;
    @Autowired
    private GuardrailRuleEngine engine;
    @Autowired
    private GenSpecService specService;

    @Override
    public List<GenGuardrailRule> rules(String industry, String category) {
        LambdaQueryWrapper<GenGuardrailRule> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(industry)) {
            w.eq(GenGuardrailRule::getIndustry, industry.trim());
        }
        if (StringUtils.hasText(category)) {
            w.eq(GenGuardrailRule::getCategory, category.trim());
        }
        w.orderByAsc(GenGuardrailRule::getSortNo);
        return ruleMapper.selectList(w);
    }

    @Override
    public Map<String, Object> ruleCount() {
        List<GenGuardrailRule> all = rules(null, null);
        Map<String, Integer> byIndustry = new LinkedHashMap<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (GenGuardrailRule r : all) {
            byIndustry.merge(r.getIndustry(), 1, Integer::sum);
            byCategory.merge(r.getCategory(), 1, Integer::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("byIndustry", byIndustry);
        result.put("byCategory", byCategory);
        return result;
    }

    @Override
    public GuardrailEvalVO evaluate(GuardrailEvaluateDTO dto) {
        requireIndustry(dto.getIndustry());
        if (!StringUtils.hasText(dto.getSystemName())) {
            throw new BusinessException(400, "系统名称不能为空");
        }
        String text = textOf(dto.getSystemName(), dto.getSystemDesc(), dto.getModuleNames(), dto.getAnswers());
        return engine.evaluate(rules(dto.getIndustry(), null), text, dto.getAnswers());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClarifyStepVO start(ClarifyStartDTO dto) {
        requireIndustry(dto.getIndustry());
        if (dto.getProjectId() == null) {
            throw new BusinessException(400, "项目ID不能为空");
        }
        if (!StringUtils.hasText(dto.getSystemName())) {
            throw new BusinessException(400, "系统名称不能为空");
        }
        GenClarifySession s = new GenClarifySession();
        s.setProjectId(dto.getProjectId());
        s.setIndustry(dto.getIndustry().trim());
        s.setSystemName(dto.getSystemName().trim());
        s.setSystemDesc(dto.getSystemDesc());
        s.setModuleNames(json(dto.getModuleNames() == null ? List.of() : dto.getModuleNames()));
        s.setAnswersJson("{}");
        s.setHitsJson("[]");
        s.setOverriddenCodes("");
        s.setAdvancedMode(0);
        s.setQuestionIndex(0);
        s.setStatus(GenClarifySession.STATUS_CLARIFYING);
        sessionMapper.insert(s);
        return step(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClarifyStepVO answer(ClarifyAnswerDTO dto) {
        GenClarifySession s = requireSession(dto.getSessionId());
        List<GenClarifyQuestion> questions = questions(s.getIndustry());
        int target = -1;
        for (int i = 0; i < questions.size(); i++) {
            if (Objects.equals(questions.get(i).getQuestionCode(), dto.getQuestionCode())) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            throw new BusinessException(400, "问题不属于当前行业: " + dto.getQuestionCode());
        }
        int current = s.getQuestionIndex() == null ? 0 : s.getQuestionIndex();
        if (target > current) {
            throw new BusinessException(400, "请先回答当前问题: " + questions.get(current).getQuestion());
        }
        Map<String, String> answers = answersOf(s);
        answers.put(dto.getQuestionCode(), dto.getAnswer() == null ? "" : dto.getAnswer().trim());
        s.setAnswersJson(json(answers));
        if (target == current && current < questions.size()) {
            s.setQuestionIndex(current + 1);
            s.setStatus(GenClarifySession.STATUS_CLARIFYING);
        }
        sessionMapper.updateById(s);
        return step(s);
    }

    @Override
    public GuardrailEvalVO evaluateSession(Long sessionId) {
        GenClarifySession s = requireSession(sessionId);
        Map<String, String> answers = answersOf(s);
        String text = textOf(s.getSystemName(), s.getSystemDesc(), parseList(s.getModuleNames()), answers);
        GuardrailEvalVO vo = engine.evaluate(rules(s.getIndustry(), null), text, answers);
        s.setHitsJson(toJson(vo.getHits()));
        if (!GenClarifySession.STATUS_CONFIRMED.equals(s.getStatus())) {
            s.setStatus(GenClarifySession.STATUS_READY);
        }
        sessionMapper.updateById(s);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClarifyConfirmVO confirm(Long sessionId, ClarifyConfirmDTO dto) {
        GenClarifySession s = requireSession(sessionId);
        if (GenClarifySession.STATUS_CONFIRMED.equals(s.getStatus()) && s.getSpecId() != null) {
            return confirmedVo(s); // 幂等：重复确认不新建版本
        }
        Map<String, String> answers = answersOf(s);
        String text = textOf(s.getSystemName(), s.getSystemDesc(), parseList(s.getModuleNames()), answers);
        GuardrailEvalVO vo = engine.evaluate(rules(s.getIndustry(), null), text, answers);
        Set<String> overridden = asSet(dto == null ? null : dto.getOverriddenCodes());
        boolean advanced = dto != null && dto.isAdvancedMode();
        List<String> blocking = new ArrayList<>();
        for (GuardrailEvalVO.Hit hit : vo.getHits()) {
            if (GenGuardrailRule.SEVERITY_BLOCK.equals(hit.getSeverity())
                    && !(advanced && overridden.contains(hit.getRuleCode()))) {
                blocking.add(hit.getRuleCode());
            }
        }
        if (!blocking.isEmpty()) {
            throw new BusinessException(400, "存在 " + blocking.size() + " 条未处理拦截（"
                    + String.join("、", blocking) + "），请先修正或开启高级模式显式忽略");
        }

        List<String> nonGoals = new ArrayList<>();
        for (GenGuardrailRule r : rules(s.getIndustry(), GenGuardrailRule.CAT_BOUNDARY)) {
            if (StringUtils.hasText(r.getSuggestion())) {
                nonGoals.add(r.getSuggestion());
            }
        }
        for (GuardrailEvalVO.Hit hit : vo.getHits()) {
            if (overridden.contains(hit.getRuleCode()) && StringUtils.hasText(hit.getSuggestion())) {
                nonGoals.add("[高级模式忽略] " + hit.getSuggestion());
            }
        }
        boolean hasOverride = advanced && !overridden.isEmpty();

        GenSpecArtifact spec = new GenSpecArtifact();
        spec.setProjectId(s.getProjectId());
        spec.setArtifactType(GenSpecArtifact.TYPE_SPEC);
        spec.setTitle("规格（护栏澄清）：" + s.getSystemName());
        spec.setYamlMeta(metaJson(s, hasOverride, nonGoals));
        spec.setContentMd(specMarkdown(s, answers, nonGoals, hasOverride));
        spec.setStructuredJson("{\"tables\":[],\"enums\":[]}");
        GenSpecArtifact saved = specService.save(spec);

        s.setStatus(GenClarifySession.STATUS_CONFIRMED);
        s.setSpecId(saved.getId());
        s.setHitsJson(toJson(vo.getHits()));
        s.setOverriddenCodes(String.join(",", overridden));
        s.setAdvancedMode(advanced ? 1 : 0);
        sessionMapper.updateById(s);

        ClarifyConfirmVO result = new ClarifyConfirmVO();
        result.setSpecId(String.valueOf(saved.getId()));
        result.setSpecVersion(saved.getVersion());
        result.setOverridden(hasOverride);
        return result;
    }

    @Override
    public GenClarifySession session(Long sessionId) {
        return requireSession(sessionId);
    }

    @Override
    public ClarifyStepVO step(Long sessionId) {
        return step(requireSession(sessionId));
    }

    @Override
    public List<GenClarifySession> sessions(Long projectId) {
        LambdaQueryWrapper<GenClarifySession> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            w.eq(GenClarifySession::getProjectId, projectId);
        }
        w.orderByDesc(GenClarifySession::getCreateTime);
        return sessionMapper.selectList(w);
    }

    private ClarifyStepVO step(GenClarifySession s) {
        List<GenClarifyQuestion> questions = questions(s.getIndustry());
        int index = s.getQuestionIndex() == null ? 0 : s.getQuestionIndex();
        ClarifyStepVO vo = new ClarifyStepVO();
        vo.setSession(s);
        vo.setQuestion(index < questions.size() ? questions.get(index) : null);
        vo.setDone(index >= questions.size());
        vo.setTotalQuestions(questions.size());
        int answered = 0;
        Map<String, String> answers = answersOf(s);
        for (GenClarifyQuestion q : questions) {
            if (StringUtils.hasText(answers.get(q.getQuestionCode()))) {
                answered++;
            }
        }
        vo.setAnsweredCount(answered);
        return vo;
    }

    private List<GenClarifyQuestion> questions(String industry) {
        LambdaQueryWrapper<GenClarifyQuestion> w = new LambdaQueryWrapper<>();
        w.eq(GenClarifyQuestion::getEnabled, 1)
                .and(q -> q.eq(GenClarifyQuestion::getIndustry, "")
                        .or().eq(GenClarifyQuestion::getIndustry, industry))
                .orderByAsc(GenClarifyQuestion::getSortNo);
        return questionMapper.selectList(w);
    }

    private GenClarifySession requireSession(Long id) {
        GenClarifySession s = sessionMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(400, "澄清会话不存在: " + id);
        }
        return s;
    }

    private static void requireIndustry(String industry) {
        if (!StringUtils.hasText(industry) || !SUPPORTED_INDUSTRIES.contains(industry.trim())) {
            throw new BusinessException(400, "暂不支持该行业的护栏规则: " + industry);
        }
    }

    private static String textOf(String name, String desc, List<String> modules, Map<String, String> answers) {
        StringBuilder sb = new StringBuilder(name == null ? "" : name)
                .append(' ').append(desc == null ? "" : desc).append(' ');
        if (modules != null) {
            for (String m : modules) {
                sb.append(m).append(' ');
            }
        }
        if (answers != null) {
            answers.values().forEach(v -> sb.append(v == null ? "" : v).append(' '));
        }
        return sb.toString();
    }

    private static Map<String, String> answersOf(GenClarifySession s) {
        if (!StringUtils.hasText(s.getAnswersJson())) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(s.getAnswersJson(), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "会话答案 JSON 非法: " + s.getAnswersJson());
        }
    }

    private static List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String metaJson(GenClarifySession s, boolean overridden, List<String> nonGoals) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("domain", s.getIndustry());
        meta.put("systemName", s.getSystemName());
        meta.put("guardrailPassed", true);
        meta.put("guardrailOverridden", overridden);
        meta.put("nonGoals", nonGoals);
        return json(meta);
    }

    private static String specMarkdown(GenClarifySession s, Map<String, String> answers,
                                       List<String> nonGoals, boolean overridden) {
        StringBuilder md = new StringBuilder("# 规格（护栏澄清）：").append(s.getSystemName()).append("\n\n");
        md.append("> 由 M2.1 澄清向导生成草稿；请人工复核后在「规格工件」页确认生效。\n\n");
        md.append("## 一、目标\n").append(s.getSystemDesc() == null ? "（未填写）" : s.getSystemDesc()).append("\n\n");
        md.append("## 二、范围\n- 行业：").append(s.getIndustry())
                .append("\n- 候选模块：").append(String.join("、", parseList(s.getModuleNames()))).append("\n\n");
        md.append("## 三、澄清结论\n");
        answers.forEach((k, v) -> md.append("- ").append(k).append("：").append(v).append('\n'));
        md.append("\n## 四、非目标\n");
        if (nonGoals.isEmpty()) {
            md.append("- （本行业未标注边界项）\n");
        } else {
            nonGoals.forEach(n -> md.append("- ").append(n).append('\n'));
        }
        md.append("\n> 护栏状态：").append(overridden ? "已通过（高级模式显式忽略部分拦截）" : "已通过")
                .append("。命中明细见澄清会话。\n");
        return md.toString();
    }

    private ClarifyConfirmVO confirmedVo(GenClarifySession s) {
        GenSpecArtifact a = specService.detail(s.getSpecId());
        ClarifyConfirmVO vo = new ClarifyConfirmVO();
        vo.setSpecId(String.valueOf(s.getSpecId()));
        vo.setSpecVersion(a == null ? null : a.getVersion());
        vo.setOverridden(s.getAdvancedMode() != null && s.getAdvancedMode() == 1);
        return vo;
    }

    private static Set<String> asSet(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        Set<String> result = new java.util.LinkedHashSet<>();
        codes.stream().filter(StringUtils::hasText).forEach(c -> result.add(c.trim()));
        return result;
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "JSON 序列化失败: " + e.getMessage());
        }
    }

    private static String toJson(Object o) {
        return json(o);
    }
}
