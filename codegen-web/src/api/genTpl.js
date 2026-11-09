import request, { toPageData } from '@/utils/request'

// 代码模板接口：codegen-server 单体服务（直接 /gen/template）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

// 模板分页
export function getGenTplPage(params) {
  return request({
    url: '/gen/template/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

// 模板详情（含 content）
export function getGenTplDetail(id) {
  return request({
    url: `/gen/template/${id}`,
    method: 'get'
  })
}

// 模板列表（下拉用，不含 content）
export function getGenTplList(tableId) {
  return request({
    url: '/gen/template/list',
    method: 'get',
    params: tableId == null ? {} : { tableId }
  })
}

// 新增模板
export function createGenTpl(data) {
  return request({
    url: '/gen/template',
    method: 'post',
    data
  })
}

// 修改模板
export function updateGenTpl(id, data) {
  return request({
    url: '/gen/template',
    method: 'put',
    data: { ...data, id }
  })
}

// 删除模板（物理删除）
export function deleteGenTpl(id) {
  return request({
    url: `/gen/template/${id}`,
    method: 'delete'
  })
}

// 复制为表级模板
export function copyGenTpl(id, tableId) {
  return request({
    url: '/gen/template/copy',
    method: 'post',
    data: { id, tableId }
  })
}

// 模板语法校验
export function validateGenTpl(content, tplCode) {
  return request({
    url: '/gen/template/validate',
    method: 'post',
    data: { content, tplCode }
  })
}

/* ---------- V1.3-5：模板版本历史与回滚 ---------- */

// 历史版本列表（不含 content，按版本倒序）
export function getGenTplHistory(id) {
  return request({
    url: `/gen/template/${id}/history`,
    method: 'get'
  })
}

// 某个历史版本的完整内容
export function getGenTplHistoryDetail(id, version) {
  return request({
    url: `/gen/template/${id}/history/${version}`,
    method: 'get'
  })
}

// 回滚到指定版本（当前版本会先存快照，可再回滚回来）
export function rollbackGenTpl(id, version) {
  return request({
    url: `/gen/template/${id}/rollback/${version}`,
    method: 'post'
  })
}
