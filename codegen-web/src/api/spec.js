import request from '@/utils/request'

/* ---------- V9.21 SDD 规格工件链与 Converge 对账 ----------
 * 后端 GenSpecController（/gen/spec），统一响应 {code,msg,data}，拦截器返回整包 body，
 * 页面取值一律 res.data。雪花 ID 字符串化，projectId 直接透传字符串即可。
 */

// F-01 工件树：五类工件各取最新版本
export function specTree(projectId) {
  return request({ url: '/gen/spec/tree', method: 'get', params: { projectId } })
}

// F-02 工件详情
export function specDetail(id) {
  return request({ url: `/gen/spec/${id}`, method: 'get' })
}

// F-05 工件历史版本（version 倒序）
export function specHistory(projectId, artifactType) {
  return request({ url: '/gen/spec/history', method: 'get', params: { projectId, artifactType } })
}

// F-02 保存工件（一律新增版本快照，DRAFT）
export function saveSpec(data) {
  return request({ url: '/gen/spec/save', method: 'post', data })
}

// F-03 确认工件（DRAFT → CONFIRMED）
export function confirmSpec(id) {
  return request({ url: `/gen/spec/confirm/${id}`, method: 'post' })
}

// F-04 初始化项目基本法（已有则返回最新，幂等）
export function initConstitution(projectId) {
  return request({ url: '/gen/spec/constitution/init', method: 'post', params: { projectId } })
}

// F-06 执行 Converge 对账（以最新生效 SPEC 为依据）
export function runConverge(projectId) {
  return request({ url: '/gen/spec/converge/run', method: 'post', params: { projectId } })
}

// F-07 对账报告列表（时间倒序）
export function convergeReports(projectId) {
  return request({ url: '/gen/spec/converge/reports', method: 'post', params: { projectId } })
}

// F-08 对账报告详情（含明细，MISSING → EXTRA → OK 排序）
export function convergeReport(id) {
  return request({ url: `/gen/spec/converge/report/${id}`, method: 'post' })
}
