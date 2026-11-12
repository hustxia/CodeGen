// V8.1 i18n 纯函数核心单测：回落链 + 插值 + RTL。
// 真源：../codegen-server/src/main/resources/scaffold-i18n/i18n-core.js
import { describe, it, expect } from 'vitest'
import { resolveText, interpolate, isRtl } from '../../codegen-server/src/main/resources/scaffold-i18n/i18n-core.js'

const zhCN = {
  'common.add': '新增',
  'common.query': '查询',
  'crud.Order.title': '订单',
  'greet': '你好 {name}',
}
const enUS = {
  'common.add': 'Add',
  'common.query': 'Search',
  // 故意缺失 crud.Order.title / greet，用于验证回落
}
const messagesOf = (loc) => ({ 'zh-CN': zhCN, 'en-US': enUS }[loc] || {})
const DEFAULT = 'zh-CN'

describe('resolveText 回落链', () => {
  it('当前语言有译文 → 直接返回', () => {
    expect(resolveText(messagesOf, 'en-US', DEFAULT, 'common.add')).toBe('Add')
  })
  it('当前语言缺失 → 回落默认语言', () => {
    expect(resolveText(messagesOf, 'en-US', DEFAULT, 'crud.Order.title')).toBe('订单')
  })
  it('当前与默认都缺失 → 第二参字符串作为回落文案', () => {
    expect(resolveText(messagesOf, 'en-US', DEFAULT, 'crud.Unknown.x', '自定义回落')).toBe('自定义回落')
  })
  it('当前与默认都缺失、无第二参 → 回落键本身（不空白）', () => {
    expect(resolveText(messagesOf, 'en-US', DEFAULT, 'crud.Unknown.x')).toBe('crud.Unknown.x')
  })
  it('第二参为字符串时仅作回落，不做插值', () => {
    // en-US / zh-CN 都没有 greet 之外的 key，这里用全新 key + 含占位符的第二参
    expect(resolveText(messagesOf, 'en-US', DEFAULT, 'crud.X.y', 'hi {name}')).toBe('hi {name}')
  })
})

describe('interpolate 插值', () => {
  it('对象参数替换 {key}', () => {
    expect(resolveText(messagesOf, 'zh-CN', DEFAULT, 'greet', { name: 'Tom' })).toBe('你好 Tom')
  })
  it('缺失占位符原样保留', () => {
    expect(resolveText(messagesOf, 'zh-CN', DEFAULT, 'greet', {})).toBe('你好 {name}')
  })
  it('纯函数：无 params 不报错', () => {
    expect(interpolate('纯文本', null)).toBe('纯文本')
  })
})

describe('isRtl', () => {
  it('rtl 元数据识别', () => {
    expect(isRtl({ dir: 'rtl' })).toBe(true)
    expect(isRtl({ dir: 'ltr' })).toBe(false)
    expect(isRtl(null)).toBe(false)
  })
})
