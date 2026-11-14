import request, { toPageData } from '@/utils/request'

/**
 * V9.2 测试中心
 *
 * 约定与 doc.js 一致：分页参数 page/size → pageNum/pageSize，
 * 响应 {records,total} 由 toPageData 转成 {list,total}。
 */

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 20, ...query }
}

// ---------------- 环境 ----------------
export function getEnvPage(params) {
  return request({ url: '/test/env/page', method: 'post', data: toPageQuery(params) }).then(toPageData)
}

export function saveEnv(data) {
  return request({ url: '/test/env', method: data.id ? 'put' : 'post', data })
}

export function deleteEnv(id) {
  return request({ url: `/test/env/${id}`, method: 'delete' })
}

export function verifyEnv(id) {
  return request({ url: `/test/env/${id}/verify`, method: 'post' })
}

// ---------------- 套件 ----------------
export function getSuitePage(params) {
  return request({ url: '/test/suite/page', method: 'post', data: toPageQuery(params) }).then(toPageData)
}

export function saveSuite(data) {
  return request({ url: '/test/suite', method: data.id ? 'put' : 'post', data })
}

// ---------------- 用例 ----------------
export function getCasePage(params) {
  return request({ url: '/test/case/page', method: 'post', data: toPageQuery(params) }).then(toPageData)
}

export function getCaseDetail(id) {
  return request({ url: `/test/case/${id}`, method: 'get' })
}

export function saveCase(data) {
  return request({ url: '/test/case', method: data.id ? 'put' : 'post', data })
}

export function deleteCase(id) {
  return request({ url: `/test/case/${id}`, method: 'delete' })
}

/** 试运行：不落批次，结果只用于编辑页即时反馈 */
export function tryRunCase(id, envId) {
  return request({ url: `/test/case/${id}/try`, method: 'post', params: { envId } })
}

// ---------------- 执行 ----------------
export function startRun(envId, suiteIds) {
  return request({ url: '/test/run', method: 'post', data: { envId, suiteIds } })
}

export function getRunPage(params) {
  return request({ url: '/test/run/page', method: 'post', data: toPageQuery(params) }).then(toPageData)
}

export function getRunDetail(id) {
  return request({ url: `/test/run/${id}`, method: 'get' })
}

export function abortRun(id) {
  return request({ url: `/test/run/${id}/abort`, method: 'post' })
}
