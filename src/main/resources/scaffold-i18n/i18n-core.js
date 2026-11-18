/**
 * V8.1 i18n 纯函数核心（零依赖：不依赖 Vue / DOM / 运行时全局）。
 *
 * 由 V8.0 产物运行时 i18n.js 引用，并被单元测试（codegen-web/tests/i18n-core.test.mjs）
 * 直接覆盖 —— 把「回落链」这件最容易回归的事，从 Vue 适配层里摘出来单独测。
 *
 * V9.0 升级到 Vue 3 时，i18n.js 的 Vue 适配层（observable → reactive、
 * Vue.prototype.$t → app.config.globalProperties.$t）会改，但本文件不变。
 */
export function interpolate(text, params) {
  if (params == null) return String(text)
  return String(text).replace(/\{(\w+)\}/g, function (m, k) {
    return params[k] !== undefined && params[k] !== null ? params[k] : m
  })
}

/**
 * 文案回落链（唯一实现）：
 *   当前语言非空译文 → 默认语言译文 → 第二参回文 / 键本身。
 *
 * @param {Function} messagesOf (locale:string) => object  语言包读取器
 * @param {string} locale        当前语言
 * @param {string} defaultLocale 默认语言（回落基，通常是 zh-CN）
 * @param {string} key            文案键，如 'common.query' / 'crud.Order.title'
 * @param {object|string} [params]
 *    - 对象：插值参数，如 { count: 10 } 配合 '共 {count} 条'
 *    - 字符串：显式「源语言/回落文案」；动态数据（菜单名、实体名）没有词条时直接传中文原文，
 *      任何语言缺失译文都回落到它，天然支持 206 种语言
 * @returns {string}
 */
export function resolveText(messagesOf, locale, defaultLocale, key, params) {
  const current = messagesOf(locale) || {}
  const fallback = messagesOf(defaultLocale) || {}
  let value
  if (params !== undefined && typeof params !== 'object') {
    // 第二参是字符串：当作源语言文案（动态数据没有词条时的回落源）
    value = current[key] || fallback[key] || params
  } else {
    value = current[key] || fallback[key] || key
  }
  return interpolate(value, params && typeof params === 'object' ? params : undefined)
}

/** RTL 判定：语言元数据 dir === 'rtl'。 */
export function isRtl(meta) {
  return !!(meta && meta.dir === 'rtl')
}
