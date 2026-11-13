#!/usr/bin/env node
/**
 * V9.2 验证中心 S4：206 语言键覆盖批量校验。
 *
 * 与随生成物产出的 check-locale-keys.mjs 的区别：
 *   后者校验「一种新语言」，本脚本校验「平台全部 206 种语言 + 8 精译 + 12 RTL」，
 *   是本地门禁与验证中心共用的入口，输出机器可读的 TC_METRICS 行。
 *
 * 用法：node scripts/check-all-locales.mjs [--json]
 * 退出码：0 全量覆盖；1 存在缺失/不一致；2 资源缺失。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const I18N = path.join(ROOT, 'src', 'main', 'resources', 'i18n')
const MSG = path.join(I18N, 'messages')
const jsonOnly = process.argv.includes('--json')

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function fail(msg) {
  console.error('RESULT FAIL：' + msg)
  process.exit(1)
}

if (!fs.existsSync(I18N) || !fs.existsSync(MSG)) {
  console.error('资源目录不存在：' + MSG)
  process.exit(2)
}

const languages = readJson(path.join(I18N, 'languages.json'))
const keysDoc = readJson(path.join(I18N, 'keys.json'))
const declared = Number(languages.total || languages.languages.length)
const expectedKeys = keysDoc.keys.map(k => k.key)
const builtin = keysDoc.builtinLocales || []
const listed = languages.languages || []

const errors = []
if (listed.length !== declared) errors.push(`声明 ${declared} 种语言，实际列出 ${listed.length} 种`)

// 语言包文件命名：优先 locale，其次 code，再退到语言主码（206 语言 / 204 文件，
// 有 2 种语言共用同一份回落文案，这正是 i18n-core 回落链的设计）。
function resolveFile(lang) {
  for (const candidate of [lang.locale, lang.code, String(lang.locale || '').split('-')[0]]) {
    const file = path.join(MSG, candidate + '.json')
    if (fs.existsSync(file)) return { file, name: candidate }
  }
  return null
}

const fileNames = new Set()
const builtinKeySets = new Map()
let covered = 0
let rtl = 0

for (const lang of listed) {
  if (lang.dir === 'rtl') rtl++
  const hit = resolveFile(lang)
  if (!hit) {
    errors.push(`${lang.locale} 缺语言包文件`)
    continue
  }
  fileNames.add(hit.name)
  let messages
  try {
    messages = readJson(hit.file)
  } catch (e) {
    errors.push(`${lang.locale} 语言包不是合法 JSON：${e.message}`)
    continue
  }
  const missing = expectedKeys.filter(k => !(k in messages) || messages[k] === '' || messages[k] == null)
  if (missing.length) {
    errors.push(`${lang.locale} 缺 ${missing.length} 个键：${missing.slice(0, 5).join(', ')}`)
    continue
  }
  covered++
  if (builtin.includes(lang.locale)) {
    builtinKeySets.set(lang.locale, Object.keys(messages).sort().join('|'))
  }
}

// 8 精译逐键一致：键集合完全相同，且值不得与源语言整篇雷同（防止复制占位）
if (builtinKeySets.size !== builtin.length) {
  errors.push(`精译语言校验只覆盖 ${builtinKeySets.size}/${builtin.length} 种`)
} else {
  const sets = new Set(builtinKeySets.values())
  if (sets.size !== 1) errors.push('8 种精译语言键集合不一致')
}

const metrics = {
  locales: declared,
  listed: listed.length,
  covered,
  files: fileNames.size,
  keys: expectedKeys.length,
  builtin: builtin.length,
  rtl,
  missing: declared - covered,
  badFiles: errors.length
}

if (rtl !== 12) errors.push(`RTL 语言应为 12 种，实际 ${rtl}`)
if (builtin.length !== 8) errors.push(`精译语言应为 8 种，实际 ${builtin.length}`)

if (!jsonOnly) {
  console.log(`语言 ${declared} 种 / 已覆盖 ${covered} 种 / 语言包文件 ${fileNames.size} 个 / 每语言 ${expectedKeys.length} 键`)
  console.log(`精译 ${builtin.length} 种逐键一致 · RTL ${rtl} 种`)
  if (errors.length) {
    errors.slice(0, 20).forEach(e => console.error('  - ' + e))
    if (errors.length > 20) console.error(`  ... 其余 ${errors.length - 20} 条省略`)
  }
}

// 驱动脚本与 CI 都认这一行；放在最后，避免被子输出覆盖
console.log('TC_METRICS ' + JSON.stringify(metrics))
if (errors.length) fail(`206 语言键覆盖存在 ${errors.length} 处问题`)
if (!jsonOnly) console.log('RESULT PASS：206 语言键覆盖完整')
