// ⚠️ 一次性迁移脚本 —— 生成 docs/version-history.json 后【切勿再次运行】！
// 它的真源是 README.md / README_ZH.md（会从它们反推 en/zh），重跑会用 README 覆盖你在
// version-history.json 里手改的 en/zh 内容，并重新 harvest 旧 sync 脚本里的译本（早已过时）。
// 日常维护只改 version-history.json + 跑 render_version_history.mjs，本文件仅作迁移留档。
// 用法: node scripts/gen_version_json.mjs [codegen-server根]
import fs from 'fs'
import path from 'path'

const ROOT = process.argv[2] || '.'
const read = f => fs.readFileSync(path.join(ROOT, f), 'utf8')

// 定位某 markdown 文件里的「版本表」：三列表格，且首数据行以 V\d 开头
function parseVersionTable(content) {
  const lines = content.split(/\r\n|\n/)
  let sepIdx = -1
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i]
    if (/^\s*\|(\s*-+\s*\|){3,}\s*$/.test(l)) {
      const next = lines[i + 1] || ''
      const first = (next.split('|')[1] || '').trim()
      if (/^V\d/.test(first)) { sepIdx = i; break }
    }
  }
  if (sepIdx === -1) return null
  const body = []
  for (let i = sepIdx + 1; i < lines.length; i++) {
    const l = lines[i]
    if (!l.trim().startsWith('|')) break
    if (/^\s*\|(\s*-+\s*\|)+\s*$/.test(l)) continue
    body.push(l)
  }
  return { header: lines[sepIdx - 1], body }
}

const cells = line => {
  const p = line.split('|')
  return p.slice(1, p.length - 1).map(s => s.trim())
}
const unbold = s => s.replace(/^\*\*(.*)\*\*$/, '$1')
// 清理尾部游离的 ** 与空白（修复 ZH V7.4 遗留的 "跑  ** " 瑕疵）
const cleanTheme = s => unbold(s).replace(/\s*\*{0,2}\s*$/, '').trim()
const isBold = s => /^\*\*.+\*\*$/.test(s.trim())

function buildEntries(table) {
  return table.body.map(line => {
    const c = cells(line)
    return {
      version: unbold(c[0]),
      highlight: isBold(c[0]),
      theme: cleanTheme(c[1]),
      commit: c[2], // 原样保留（可能含 **）
    }
  })
}

const enEntries = buildEntries(parseVersionTable(read('README.md')))
const zhEntries = buildEntries(parseVersionTable(read('README_ZH.md')))
if (enEntries.length !== zhEntries.length) {
  throw new Error(`中英文版本数不一致: en=${enEntries.length} zh=${zhEntries.length}`)
}

// ---- 从两个旧 sync 脚本 harvest 47 语言译本（仅 V7.5~V9.1 这一易变块） ----
function extractObj(src, name) {
  const m = src.match(new RegExp('const ' + name + ' = (\\{[\\s\\S]*?\\n\\})'))
  if (!m) return null
  try { return new Function('return ' + m[1])() }
  catch { return null }
}
function extractObjRegex(src, name) {
  const m = src.match(new RegExp('const ' + name + ' = \\{([\\s\\S]*?)\\n\\}'))
  if (!m) return null
  const out = {}
  const re = /(\w+):\s*\{([^}]*)\}/g
  let e
  while ((e = re.exec(m[1]))) {
    const inner = {}
    const re2 = /(\w+):\s*'([^']*)'/g
    let f
    while ((f = re2.exec(e[2]))) inner[f[1]] = f[2]
    out[e[1]] = inner
  }
  return out
}

const s1 = read('scripts/sync-version-rows.mjs')
const s2 = read('scripts/sync-version-rows-v91.mjs')
const T = extractObj(s1, 'T') || extractObjRegex(s1, 'T')
const LEAD = extractObj(s2, 'LEAD') || extractObjRegex(s2, 'LEAD')
const tm = s2.match(/const TECH = '([^']*)'/)
const TECH = tm ? tm[1] : ''

const i18n = {}
const langs = new Set([...Object.keys(T || {}), ...Object.keys(LEAD || {})])
for (const key of langs) {
  if (key === 'README' || key === 'README_ZH') continue // 英/中走 JSON 主源
  const lang = key.replace('README_', '')
  const t = T[key] || {}
  const lead = LEAD[key] || ''
  const entry = {}
  if (t.v75) entry['V7.5'] = t.v75
  if (t.v80) entry['V8.0'] = t.v80
  if (t.v81) entry['V8.1'] = t.v81
  if (t.v90) entry['V9.0'] = t.v90
  if (lead) entry['V9.1'] = (lead.startsWith('可维护性重构') || lead.startsWith('可維護性重構')) ? lead : lead + ' ' + TECH
  if (Object.keys(entry).length) i18n[lang] = entry
}

const versions = enEntries.map((e, i) => ({
  version: e.version,
  highlight: e.highlight,
  theme: { en: e.theme, zh: zhEntries[i].theme },
  commit: { en: e.commit, zh: zhEntries[i].commit },
}))

const out = {
  meta: {
    currentVersion: 'V9.1',
    updated: '2026-11-13',
    source: 'README.md(en) + README_ZH.md(zh) 为权威源；sync-version-rows*.mjs 仅 harvest 其余语言的 V7.5~V9.1 译本',
    note: '改动版本表只需编辑本文件，再运行 node scripts/render_version_history.mjs',
  },
  columns: {
    en: ['Version', 'Theme / plan document', 'Landing commit'],
    zh: ['版本', '版本主题 / 规划文档', '落地提交'],
  },
  versions,
  i18n,
}

const dest = path.join(ROOT, 'docs/version-history.json')
fs.writeFileSync(dest, JSON.stringify(out, null, 2) + '\n')
console.log(`已生成 ${dest}`)
console.log(`  版本条目: ${versions.length}  en/zh 双语`)
console.log(`  i18n 译本语言数: ${Object.keys(i18n).length}`)
