import request from '@/utils/request'

/**
 * V9.25 M2.1 护栏澄清向导。
 * 后端 GenGuardrailController（/gen/guardrail），统一响应 {code,msg,data}，
 * 拦截器返回整包 body，页面取值一律 res.data。雪花 ID 字符串化。
 */

// F-01 规则列表：industry / category 可空
export function listRules(params) {
  return request({ url: '/gen/guardrail/rules', method: 'get', params })
}

// F-01 规则计数：total / byIndustry / byCategory
export function ruleCount() {
  return request({ url: '/gen/guardrail/rules/count', method: 'get' })
}

// F-04 独立检测（不建会话）
export function evaluateGuardrail(data) {
  return request({ url: '/gen/guardrail/evaluate', method: 'post', data })
}

// F-02 开始澄清：返回第 1 问
export function startClarify(data) {
  return request({ url: '/gen/guardrail/clarify/start', method: 'post', data })
}

// F-03 答复：返回下一问
export function answerClarify(data) {
  return request({ url: '/gen/guardrail/clarify/answer', method: 'post', data })
}

// F-04 会话检测
export function evaluateSession(id) {
  return request({ url: `/gen/guardrail/clarify/evaluate/${id}`, method: 'post' })
}

// F-06 确认写 SPEC（幂等）
export function confirmClarify(id, data) {
  return request({ url: `/gen/guardrail/clarify/confirm/${id}`, method: 'post', data })
}

// F-07 会话详情
export function clarifySession(id) {
  return request({ url: `/gen/guardrail/clarify/${id}`, method: 'get' })
}

// F-03 恢复向导进度与下一问
export function clarifyStep(id) {
  return request({ url: `/gen/guardrail/clarify/step/${id}`, method: 'get' })
}

// F-07 项目会话列表
export function clarifySessions(projectId) {
  return request({ url: '/gen/guardrail/clarify/sessions', method: 'post', data: { projectId } })
}
