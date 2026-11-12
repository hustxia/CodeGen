import request from '@/utils/request'

// V8.0 产物国际化：平台侧文案翻译接口
// 路径不带 /api —— dev 代理 /dev-api 会去前缀（项目适配约定）

/** 该系统可用语言 + 覆盖率 */
export function listI18nLocales(projectId) {
  return request({
    url: '/i18n/locales',
    method: 'get',
    params: { projectId }
  })
}

/** 全部文案键（key / category / sourceText） */
export function listI18nKeys() {
  return request({ url: '/i18n/keys', method: 'get' })
}

/** 词库版本诊断 */
export function i18nCatalog() {
  return request({ url: '/i18n/catalog', method: 'get' })
}

/** 译文分页：{ projectId, locale, keyword, pageNum, pageSize } */
export function pageI18nMessages(data) {
  return request({ url: '/i18n/message/page', method: 'post', data })
}

/** 批量保存：{ projectId, locale, items: [{ key, value }] } */
export function saveI18nMessages(data) {
  return request({ url: '/i18n/message/save', method: 'post', data })
}

/** 导出完整语言 JSON */
export function exportI18nMessages(projectId, locale) {
  return request({
    url: '/i18n/message/export',
    method: 'get',
    params: { projectId, locale }
  })
}

/** 导入：{ projectId, locale, items, overwrite } */
export function importI18nMessages(data) {
  return request({ url: '/i18n/message/import', method: 'post', data })
}

/** 系统下拉（复用项目台账） */
export function listProjects() {
  return request({ url: '/project/list', method: 'get' })
}
