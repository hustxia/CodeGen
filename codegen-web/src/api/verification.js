import request from '@/utils/request'

/**
 * V9.2 验证中心（/tc/*）
 *
 * 与「用例中心」/test/* 是两个子模块：这里跑的是平台自身的验证套件
 * （单测 / i18n 契约 / 206 语言键 / 产物校验），不管理业务接口用例。
 */

export function getSuites() {
  return request({ url: '/tc/suites', method: 'get' }).then(res => res.data)
}

export function getOverview() {
  return request({ url: '/tc/overview', method: 'get' }).then(res => res.data)
}

export function triggerRun(payload) {
  return request({ url: '/tc/runs', method: 'post', data: payload }).then(res => res.data)
}

/**
 * V9.3：手动「验证生成产物」（S10 产物单测 / S11 产物接口测试 / S12 产物 E2E）。
 * genRoot 为生成产物根目录（SysProject.backendRoot + backendPath），为空则按服务端定位。
 */
export function triggerGen(genRoot) {
  return request({ url: '/tc/runs/gen', method: 'post', data: { genRoot: genRoot || '' } }).then(res => res.data)
}

export function getRuns(params) {
  return request({ url: '/tc/runs', method: 'get', params }).then(res => res.data)
}

export function getRun(id) {
  return request({ url: `/tc/runs/${id}`, method: 'get' }).then(res => res.data)
}

export function getRunLog(id, caseId, tail = 200) {
  return request({
    url: `/tc/runs/${id}/log`,
    method: 'get',
    params: { caseId, tail }
  }).then(res => res.data)
}

export function cancelRun(id) {
  return request({ url: `/tc/runs/${id}/cancel`, method: 'post' }).then(res => res.data)
}

export function rerunCase(caseId) {
  return request({ url: `/tc/cases/${caseId}/rerun`, method: 'post' }).then(res => res.data)
}

export function rerunFailed(id) {
  return request({ url: `/tc/runs/${id}/rerun-failed`, method: 'post' }).then(res => res.data)
}

export function getReport(id, format = 'json') {
  return request({ url: `/tc/runs/${id}/report`, method: 'get', params: { format } }).then(res => res.data)
}

export function getTrends(limit = 20) {
  return request({ url: '/tc/trends', method: 'get', params: { limit } }).then(res => res.data)
}

export function downloadArtifact(id) {
  return request({ url: `/tc/artifacts/${id}`, method: 'get', responseType: 'blob' })
}
