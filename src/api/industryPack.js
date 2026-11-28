import request from '@/utils/request'

/**
 * V9.22 行业范式模板市场：四包清单/详情 + 安装机制。
 * 安装只生成 plan（复用 V9.7 克隆链路），不建库；响应为整包 body，页面统一取 res.data。
 */

export function listParadigms(params) {
  return request({ url: '/gen/industry-pack/list', method: 'get', params })
}

export function getParadigmDetail(packKey) {
  return request({ url: `/gen/industry-pack/detail/${encodeURIComponent(packKey)}`, method: 'get' })
}

export function installParadigm(data) {
  return request({ url: '/gen/industry-pack/install', method: 'post', data, timeout: 120000 })
}

export function listInstalled(projectId) {
  return request({ url: '/gen/industry-pack/installed', method: 'post', data: { projectId } })
}

export function removeInstall(id) {
  return request({ url: `/gen/industry-pack/install/${id}`, method: 'delete' })
}
