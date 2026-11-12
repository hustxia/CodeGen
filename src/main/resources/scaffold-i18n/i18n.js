/**
 * V8.0 产物国际化运行时（零第三方依赖，Vue 2.7 原生 Vue.observable 驱动）。
 * 本文件由代码生成器写出，请勿手工编辑。
 *
 * 回落链（唯一实现）：当前语言非空译文 → 默认语言 → key 本身。
 * 切换语言只改 state.locale；所有在 render 期间调用过 $t 的组件会因为读取了
 * Vue.observable 的属性而自动重渲染，不需要手动刷新页面。
 */
import Vue from 'vue'
import { messagesOf, available } from '../locales'

const DEFAULT_LOCALE = '__DEFAULT_LOCALE__'
const STORAGE_KEY = 'app_locale'

export const state = Vue.observable({ locale: readLocale() })

function readLocale() {
  let saved = ''
  try { saved = localStorage.getItem(STORAGE_KEY) || '' } catch (e) { saved = '' }
  return available.some(function (l) { return l.locale === saved }) ? saved : DEFAULT_LOCALE
}

function interpolate(text, params) {
  return String(text).replace(/\{(\w+)\}/g, function (m, k) {
    return params && params[k] !== undefined && params[k] !== null ? params[k] : m
  })
}

/**
 * 文案翻译。
 *
 * @param {string} key 文案键，如 'common.query' / 'menu.demo.order' / 'crud.Order.title'
 * @param {object|string} [params]
 *    - 对象：插值参数，如 { count: 10 } 配合 '共 {count} 条'
 *    - 字符串：显式「源语言/回落文案」。动态数据（菜单名、实体名）没有词条时，
 *      直接把中文原文当回落值传进来，任何语言缺失译文都回落到它，天然支持 206 种语言。
 *      例：$t('menu.demo.order', '订单管理') —— 该键任何语言都没译文时显示「订单管理」。
 *
 * 回落链：当前语言非空译文 → 默认语言译文 → 第二参回文/键本身。
 */
export function t(key, params) {
  const current = messagesOf(state.locale)
  const fallback = messagesOf(DEFAULT_LOCALE)
  let value
  if (params !== undefined && typeof params !== 'object') {
    // 第二参是字符串：当作源语言文案（动态数据没有词条时的回落源）
    value = current[key] || fallback[key] || params
  } else {
    value = current[key] || fallback[key] || key
  }
  return interpolate(value, params && typeof params === 'object' ? params : undefined)
}

export function getLocale() {
  return state.locale
}

export function getDefaultLocale() {
  return DEFAULT_LOCALE
}

export function setLocale(locale) {
  const hit = available.find(function (l) { return l.locale === locale })
  if (!hit) return false
  state.locale = hit.locale
  try { localStorage.setItem(STORAGE_KEY, hit.locale) } catch (e) { /* 隐私模式下忽略 */ }
  // V7.5 已在 index.html 写死初始 lang/dir；切换时同步更新，RTL 立即生效
  document.documentElement.setAttribute('lang', hit.locale)
  document.documentElement.setAttribute('dir', hit.dir || 'ltr')
  return true
}

export function availableLocales() {
  return available
}

export function formatNumber(value, options) {
  try {
    return new Intl.NumberFormat(state.locale, options || {}).format(value)
  } catch (e) {
    return String(value)
  }
}

export function formatDate(value, options) {
  try {
    return new Intl.DateTimeFormat(state.locale, options || {}).format(
      value instanceof Date ? value : new Date(value))
  } catch (e) {
    return String(value)
  }
}

// 初始化时把 <html lang/dir> 与实际生效语言对齐（localStorage 可能保留了上次选择）
const initial = available.find(function (l) { return l.locale === state.locale })
if (initial) {
  document.documentElement.setAttribute('lang', initial.locale)
  document.documentElement.setAttribute('dir', initial.dir || 'ltr')
}

export default { t: t, setLocale: setLocale, getLocale: getLocale, state: state }
