#!/usr/bin/env node
/**
 * V8.1 键覆盖校验脚本（随生成物一起产出到 scripts/check-locale-keys.mjs）。
 *
 * 用途：贡献一种新语言的精译后，检查它是否覆盖了基线语言（默认 zh-CN）
 *       的全部文案键，避免「某语言某个按钮仍是中文回落」的不一致。
 *
 * 用法：
 *   node scripts/check-locale-keys.mjs <locale>
 *   node scripts/check-locale-keys.mjs fr-FR
 *   node scripts/check-locale-keys.mjs fr-FR --baseline zh-CN --min-coverage 100
 *   node scripts/check-locale-keys.mjs fr-FR --json
 *
 * 退出码：覆盖达标 0；存在缺失键或低于阈值 1；用法错误 2。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

function printHelp() {
  console.log(`键覆盖校验 —— 检查目标语言包是否覆盖基线语言包的全部文案键。

用法:
  node check-locale-keys.mjs <locale> [选项]

参数:
  <locale>            要检查的目标语言，如 fr-FR / ja-JP / ar-SA

选项:
  --baseline <loc>   基线语言（默认 zh-CN，即完整译文源）
  --locales-dir <dir> 语言包目录（默认 src/locales，相对当前工作目录）
  --min-coverage <n> 最低覆盖率 0-100（默认 100，即必须全覆盖）
  --json             以 JSON 输出（便于 CI 解析）
  -h, --help         显示本帮助
`)
}

function parseArgs(argv) {
  const opts = { localesDir: 'src/locales', baseline: 'zh-CN', minCoverage: 100, json: false }
  const positional = []
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--baseline') opts.baseline = String(argv[++i])
    else if (a === '--locales-dir') opts.localesDir = String(argv[++i])
    else if (a === '--min-coverage') opts.minCoverage = Number(argv[++i])
    else if (a === '--json') opts.json = true
    else if (a === '-h' || a === '--help') { printHelp(); process.exit(0) }
    else if (a && !a.startsWith('-')) positional.push(a)
  }
  return { opts, target: positional[0] }
}

function flatten(obj, prefix = '', out = {}) {
  if (obj === null || obj === undefined) return out
  if (typeof obj !== 'object') { out[prefix] = obj; return out }
  if (Array.isArray(obj)) {
    obj.forEach((v, i) => flatten(v, prefix ? `${prefix}[${i}]` : `${i}`, out))
    return out
  }
  for (const k of Object.keys(obj)) {
    const nk = prefix ? `${prefix}.${k}` : k
    flatten(obj[k], nk, out)
  }
  return out
}

function loadLocale(dir, loc) {
  const p = path.join(dir, `${loc}.json`)
  if (!fs.existsSync(p)) return null
  return flatten(JSON.parse(fs.readFileSync(p, 'utf8')))
}

function main() {
  const { opts, target } = parseArgs(process.argv.slice(2))
  if (!target) {
    console.error('错误: 缺少目标语言参数。用法: node check-locale-keys.mjs <locale>')
    printHelp()
    process.exit(2)
  }
  const base = loadLocale(opts.localesDir, opts.baseline)
  if (!base) {
    console.error(`错误: 基线语言包不存在: ${opts.localesDir}/${opts.baseline}.json`)
    process.exit(2)
  }
  const tgt = loadLocale(opts.localesDir, target)
  if (!tgt) {
    console.error(`错误: 目标语言包不存在: ${opts.localesDir}/${target}.json`)
    process.exit(2)
  }

  const baseKeys = Object.keys(base).sort()
  const tgtKeys = new Set(Object.keys(tgt))
  const missing = baseKeys.filter((k) => !tgtKeys.has(k))
  const extra = Object.keys(tgt).filter((k) => !(k in base)).sort()
  const rawCoverage = baseKeys.length === 0
    ? 100
    : ((baseKeys.length - missing.length) / baseKeys.length) * 100
  const coverage = Math.round(rawCoverage * 100) / 100

  // 唯一闸门：未四舍五入的精确覆盖率 >= 阈值（默认 100 即要求零缺失键）。
  // 用未舍入值比较，避免「2000 键漏 1 键」被四舍五入成 100% 而放行。
  const pass = rawCoverage >= opts.minCoverage

  if (opts.json) {
    console.log(JSON.stringify({
      baseline: opts.baseline,
      target,
      baselineKeyCount: baseKeys.length,
      targetKeyCount: Object.keys(tgt).length,
      coverage,
      missingCount: missing.length,
      extraCount: extra.length,
      missing,
      extra,
      pass,
    }, null, 2))
  } else {
    console.log(`键覆盖校验: 基线 ${opts.baseline} → 目标 ${target}`)
    console.log(`  基线键数: ${baseKeys.length}`)
    console.log(`  目标键数: ${Object.keys(tgt).length}`)
    console.log(`  覆盖率:   ${coverage}%  (最低要求 ${opts.minCoverage}%)`)
    if (missing.length) {
      console.log(`  缺失键 (${missing.length}):`)
      for (const k of missing.slice(0, 50)) console.log(`    - ${k}`)
      if (missing.length > 50) console.log(`    ... 其余 ${missing.length - 50} 个省略`)
    }
    if (extra.length) {
      console.log(`  多余键 (${extra.length}, 仅提示不阻断):`)
      for (const k of extra.slice(0, 20)) console.log(`    + ${k}`)
      if (extra.length > 20) console.log(`    ... 其余 ${extra.length - 20} 个省略`)
    }
    console.log(pass ? '结果: 通过 ✅' : '结果: 未通过 ❌')
  }

  process.exit(pass ? 0 : 1)
}

main()
