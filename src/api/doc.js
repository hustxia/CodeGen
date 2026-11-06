import request, { toPageData } from '@/utils/request'

/**
 * 平台文档中心（V6.9）
 *
 * 二进制端点（preview / raw / download）统一用 arraybuffer 取流：
 * 认证靠 Authorization 头，不把 token 拼进 URL（避免 token 进日志 / 历史 / Referrer）。
 */

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

export function getDocPage(params) {
  return request({
    url: '/doc/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/**
 * 上传。30MB 文件 + 转换耗时可能超过默认 15s，单独放宽超时。
 * onUploadProgress 由 el-upload 的 http-request 透传。
 */
export function uploadDoc(file, onUploadProgress) {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/doc/upload',
    method: 'post',
    data: form,
    timeout: 180000,
    onUploadProgress
  })
}

export function getDocMeta(id) {
  return request({
    url: `/doc/meta/${id}`,
    method: 'get'
  })
}

export function deleteDoc(id) {
  return request({
    url: `/doc/${id}`,
    method: 'delete'
  })
}

function fetchBuffer(url, timeout) {
  return request({
    url,
    method: 'get',
    responseType: 'arraybuffer',
    timeout
  }).then(res => res.data)
}

/** 预览用 PDF：原件是 PDF 直接返回，否则由服务端按引擎转换 */
export function fetchDocPreview(id) {
  return fetchBuffer(`/doc/preview/${id}`, 90000)
}

/** 原件（docx-preview 用），不做任何转换 */
export function fetchDocRaw(id) {
  return fetchBuffer(`/doc/raw/${id}`, 60000)
}

export function fetchDocDownload(id) {
  return fetchBuffer(`/doc/download/${id}`, 60000)
}
