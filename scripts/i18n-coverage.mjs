#!/usr/bin/env node
/**
 * 精译语言键覆盖矩阵（翻译贡献流程门禁）。
 *
 * 与 check-all-locales.mjs 的分工：
 *   - check-all-locales.mjs：平台全部 206 种语言是否都有语言包文件、键是否齐全；
 *   - 本脚本：只看「精译集合」（i18n/keys.json 的 builtinLocales），逐语言输出
 *     key 总数 / 已翻译 / 缺失 / 多余 / 覆盖率，供 PR 门禁与本地自查使用。
 *
 * 用法：
 *   node scripts/i18n-coverage.mjs
 *   node scripts/i18n-coverage.mjs --warn              # 缺失只警告，不阻断
 *   node scripts/i18n-coverage.mjs --min-coverage 70   # 放宽阈值（默认 100）
 *   node scripts/i18n-coverage.mjs --json              # JSON 输出，便于 CI 解析
 *
 * 退出码：0 通过；1 存在缺失/多余键或低于阈值；2 资源缺失或用法错误。
 * 完整明细写入 target/i18n-coverage.txt（构建产物目录，不污染源码树）。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const I18N = path.join(ROOT, 'src', 'main', 'resources', 'i18n')
const MSG = path.join(I18N, 'messages')
const DEFAULT_OUT = path.join(ROOT, 'target', 'i18n-coverage.txt')

function printHelp() {
  console.log(`精译语言键覆盖矩阵 —— 逐语言检查 key 覆盖情况。

用法:
  node scripts/i18n-coverage.mjs [选项]

选项:
  --baseline <loc>     基线语言（默认取 keys.json 的 sourceLocale，即 zh-CN）
  --min-coverage <n>   最低覆盖率 0-100（默认 100）
  --warn               缺失键只警告，退出码仍为 0
  --json               以 JSON 输出到 stdout
  --out <file>         完整明细输出路径（默认 target/i18n-coverage.txt）
  -h, --help           显示本帮助
`)
}

function parseArgs(argv) {
  const opts = { baseline: null, minCoverage: 100, warn: false, json: false, out: DEFAULT_OUT }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--baseline') opts.baseline = String(argv[++i] || '')
    else if (a === '--min-coverage') opts.minCoverage = Number(argv[++i])
    else if (a === '--warn') opts.warn = true
    else if (a === '--json') opts.json = true
    else if (a === '--out') opts.out = path.resolve(ROOT, String(argv[++i] || ''))
    else if (a === '-h' || a === '--help') { printHelp(); process.exit(0) }
    else if (a && !a.startsWith('-')) {
      console.error(`错误: 未知参数 ${a}`)
      printHelp()
      process.exit(2)
    }
  }
  if (!Number.isFinite(opts.minCoverage) || opts.minCoverage < 0 || opts.minCoverage > 100) {
    console.error('错误: --min-coverage 必须是 0-100 的数字')
    process.exit(2)
  }
  return opts
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

/** 读取语言包：返回 { values, hasBom }；文件不存在返回 null */
function loadMessages(locale) {
  const file = path.join(MSG, `${locale}.json`)
  if (!fs.existsSync(file)) return null
  const buf = fs.readFileSync(file)
  const hasBom = buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf
  const values = JSON.parse(buf.toString('utf8').replace(/^\uFEFF/, ''))
  return { values, hasBom }
}

function isBlank(v) {
  return v === null || v === undefined || String(v).trim() === ''
}

const CJK = /[\u4e00-\u9fff]/

function pad(s, width) {
  const str = String(s)
  const len = [...str].reduce((n, ch) => n + (CJK.test(ch) ? 2 : 1), 0)
  return str + ' '.repeat(Math.max(0, width - len))
}

function main() {
  const opts = parseArgs(process.argv.slice(2))

  const keysFile = path.join(I18N, 'keys.json')
  const langFile = path.join(I18N, 'languages.json')
  for (const f of [keysFile, langFile]) {
    if (!fs.existsSync(f)) {
      console.error(`错误: 资源不存在 ${path.relative(ROOT, f)}`)
      process.exit(2)
    }
  }
  if (!fs.existsSync(MSG)) {
    console.error(`错误: 资源目录不存在 ${path.relative(ROOT, MSG)}`)
    process.exit(2)
  }

  const keysDoc = readJson(keysFile)
  const languages = readJson(langFile)
  const baseline = opts.baseline || keysDoc.sourceLocale || 'zh-CN'
  const allKeys = (keysDoc.keys || []).map((k) => k.key)
  const targets = keysDoc.builtinLocales || []
  if (!targets.length) {
    console.error('错误: keys.json 未声明 builtinLocales（精译集合）')
    process.exit(2)
  }
  if (!allKeys.length) {
    console.error('错误: keys.json 未声明任何文案键')
    process.exit(2)
  }

  const byLocale = new Map()
  for (const lang of languages.languages || []) {
    byLocale.set(lang.locale, lang)
  }

  const rows = []
  const detail = []
  let totalMissing = 0
  let totalExtra = 0

  for (const locale of targets) {
    const loaded = loadMessages(locale)
    const lang = byLocale.get(locale)
    const values = loaded ? loaded.values : {}
    const valueKeys = new Set(Object.keys(values))
    const missing = allKeys.filter((k) => !valueKeys.has(k) || isBlank(values[k]))
    const extra = Object.keys(values).filter((k) => !allKeys.includes(k)).sort()
    // 汉字文化圈（中日韩）的 script 本身就含汉字，不能当作「未翻译」；
    // 其余文字体系里出现汉字才说明残留了中文原文。
    const cjkScript = ['Hans', 'Hant', 'Jpan', 'Kore'].includes(lang && lang.script)
    const cjkKeys = cjkScript
      ? []
      : allKeys.filter((k) => valueKeys.has(k) && CJK.test(String(values[k])))
    const translated = allKeys.length - missing.length
    const rawCoverage = (translated / allKeys.length) * 100
    const coverage = Math.round(rawCoverage * 100) / 100
    const problems = []
    if (!loaded) problems.push('语言包文件缺失')
    if (loaded && loaded.hasBom) problems.push('文件含 UTF-8 BOM')
    if (missing.length) problems.push(`缺 ${missing.length} 键`)
    if (extra.length) problems.push(`多 ${extra.length} 键`)
    if (cjkKeys.length) problems.push(`含中文 ${cjkKeys.length} 键`)
    if (lang && lang.enabled !== true) problems.push('languages.json 未 enabled')

    const pass = rawCoverage >= opts.minCoverage && extra.length === 0
      && loaded !== null && !(loaded && loaded.hasBom) && cjkKeys.length === 0
      && (!lang || lang.enabled === true)

    rows.push({
      locale, enabled: !lang || lang.enabled === true, dir: lang ? lang.dir : '?',
      keyCount: allKeys.length, translated, missingCount: missing.length,
      extraCount: extra.length, cjkCount: cjkKeys.length, coverage, pass,
      hasFile: loaded !== null,
    })
    totalMissing += missing.length
    totalExtra += extra.length

    detail.push(`## ${locale}`)
    detail.push(`文件: ${loaded ? path.relative(ROOT, path.join(MSG, `${locale}.json`)) : '(缺失)'}`)
    detail.push(`键总数 ${allKeys.length} / 已翻译 ${translated} / 缺失 ${missing.length} / 多余 ${extra.length} / 覆盖率 ${coverage.toFixed(2)}%`)
    if (problems.length) detail.push(`问题: ${problems.join('；')}`)
    if (missing.length) detail.push(`缺失键:\n${missing.map((k) => `  - ${k}`).join('\n')}`)
    if (extra.length) detail.push(`多余键:\n${extra.map((k) => `  + ${k}`).join('\n')}`)
    if (cjkKeys.length) detail.push(`仍为中文的键:\n${cjkKeys.map((k) => `  ! ${k} = ${values[k]}`).join('\n')}`)
    detail.push('')
  }

  const failed = rows.filter((r) => !r.pass)
  const allPass = failed.length === 0

  const reportLines = [
    `精译覆盖矩阵  基线=${baseline}  key总数=${allKeys.length}  最低覆盖率=${opts.minCoverage}%  目标=${targets.length} 种`,
    '',
    `${pad('locale', 10)}${pad('语言包', 8)}${pad('键总数', 8)}${pad('已翻译', 8)}${pad('缺失', 6)}${pad('多余', 6)}${pad('中文残留', 10)}${pad('覆盖率', 10)}状态`,
  ]
  for (const r of rows) {
    reportLines.push(
      `${pad(r.locale, 10)}${pad(r.hasFile ? 'OK' : '缺失', 8)}${pad(r.keyCount, 8)}${pad(r.translated, 8)}`
      + `${pad(r.missingCount, 6)}${pad(r.extraCount, 6)}${pad(r.cjkCount, 10)}`
      + `${pad(r.coverage.toFixed(2) + '%', 10)}${r.pass ? 'PASS' : 'FAIL'}`
    )
  }
  const avg = rows.length
    ? rows.reduce((sum, r) => sum + r.coverage, 0) / rows.length
    : 0
  reportLines.push('')
  reportLines.push(`合计: ${targets.length} 种精译 / 平均覆盖率 ${avg.toFixed(2)}% / 缺失键 ${totalMissing} / 多余键 ${totalExtra}`)
  reportLines.push(`结论: ${allPass ? '通过 ✅' : `未通过 ❌（${failed.map((r) => r.locale).join(', ')}）`}`)
  reportLines.push(`明细: ${path.relative(ROOT, opts.out)}`)

  const outDir = path.dirname(opts.out)
  fs.mkdirSync(outDir, { recursive: true })
  fs.writeFileSync(opts.out,
    `# 精译语言键覆盖明细\n生成时间: ${new Date().toISOString()}\n基线: ${baseline}\n文案键: ${allKeys.length}\n\n`
    + detail.join('\n') + '\n', 'utf8')

  if (opts.json) {
    console.log(JSON.stringify({
      baseline,
      keyCount: allKeys.length,
      minCoverage: opts.minCoverage,
      targetCount: targets.length,
      averageCoverage: Math.round(avg * 100) / 100,
      totalMissing,
      totalExtra,
      pass: allPass,
      warnOnly: opts.warn,
      locales: rows,
      detailFile: path.relative(ROOT, opts.out),
    }, null, 2))
  } else {
    console.log(reportLines.join('\n'))
  }

  if (opts.warn && !allPass) {
    console.error('警告: 覆盖率存在缺口，--warn 模式不阻断。')
    process.exit(0)
  }
  process.exit(allPass ? 0 : 1)
}

main()
