import request from '@/utils/request'

// 项目语言配置：平台级只读语言清单（100+ 世界语言）
// 路径不带 /api —— dev 代理 /dev-api 会去前缀（项目适配约定）

/** 全量语言清单，可按 dir=ltr/rtl 筛选 */
export function listLanguages(dir) {
  return request({
    url: '/language/list',
    method: 'get',
    params: dir ? { dir } : {}
  })
}

/** 语言下拉选项：{ label, value, dir, script, code } */
export function listLanguageOptions() {
  return request({
    url: '/language/options',
    method: 'get'
  })
}

/** 单个语言详情（按 code 或 BCP47 标签） */
export function getLanguage(code) {
  return request({
    url: `/language/${encodeURIComponent(code)}`,
    method: 'get'
  })
}
