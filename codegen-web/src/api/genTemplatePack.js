import request from '@/utils/request'

/**
 * V9.19 模板市场：行业模板包列表 / 导出 / 导入 / 删除用户包。
 *
 * 导出是二进制端点，用 blob 取流；认证仍走 Authorization 头（与 doc.js 一致）。
 */

export function listTemplatePacks() {
  return request({ url: '/gen/template-pack/list', method: 'get' })
}

export function exportTemplatePack(key) {
  return request({
    url: '/gen/template-pack/export',
    method: 'get',
    params: { key },
    responseType: 'blob',
    timeout: 60000
  })
}

export function exportTemplatePackZip(keys) {
  return request({
    url: '/gen/template-pack/export',
    method: 'get',
    params: { keys: (keys || []).join(',') },
    responseType: 'blob',
    timeout: 120000
  })
}

export function importTemplatePack(file, overwrite) {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/gen/template-pack/import',
    method: 'post',
    params: { overwrite: !!overwrite },
    data: form,
    timeout: 120000
  })
}

export function deleteTemplatePack(key) {
  return request({
    url: `/gen/template-pack/${encodeURIComponent(key)}`,
    method: 'delete'
  })
}
