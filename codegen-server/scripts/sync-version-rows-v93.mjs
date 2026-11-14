// V9.3 多语言版本历史同步（幂等）
//
// 用法: node scripts/sync-version-rows-v93.mjs <codegen-server根> [多语言项目介绍目录]
//
// 前置：先跑 update-version-history-v93.mjs 更新真源，再跑 render_version_history.mjs
//       （英文 README.md + --all 47 语言）。本脚本负责渲染脚本管不到的部分：
//  1) 各语言「当前版本」标记从 V9.2 移交 V9.3（commit 列本地化，render 只给非 en/zh 填英文 current）
//  2) 11 个无版本表语言：补 "- **V9.3** · <译文>" 列表行、修回被 V9.2 脚本误标成 V9.2 的 V9.1 历史行
//  3) 多语言项目介绍 48 份：补 V9.3 版本表行 / 列表行 / Version History 段 / 顶部横幅
//  4) 状态行、文档地图版本区间（V3.1 → V9.3）、§13 版本线列举
// 按字节逐行处理，保留每个文件原有 CRLF / LF 混排，不做全文件换行符转换。
import fs from 'fs'
import path from 'path'

const SUB = path.resolve(process.argv[2] || '.')
const INTRO = process.argv[3] ? path.resolve(process.argv[3]) : null
const hist = JSON.parse(fs.readFileSync(path.join(SUB, 'docs/version-history.json'), 'utf8'))
const V93 = hist.versions.find(v => v.version === 'V9.3')
if (!V93) throw new Error('version-history.json 尚未包含 V9.3，请先跑 update-version-history-v93.mjs')

// 复用 V9.0 脚本里的「当前版本」译文字典（47 语言 + 主文档）
const old = fs.readFileSync(path.join(SUB, 'scripts/sync-version-rows.mjs'), 'utf8')
const s = old.indexOf('const T = {')
const e = old.indexOf('\n}', s)
const T = new Function('return ' + old.slice(s + 10, e + 2))()
T.README_HU = { c: 'Jelenlegi verzió' }
T.README_PT_BR = { c: 'Versão atual' }
T.README = { c: 'Current version' }

const cur = key => {
  if (key === 'README') return 'current'
  if (key === 'README_ZH') return '当前版本'
  return (T[key] || T.README).c || 'Current version'
}
const desc = key => {
  if (key === 'README') return V93.theme.en
  if (key === 'README_ZH') return V93.theme.zh
  const lang = key.replace(/^README_/, '')
  const t = hist.i18n[lang] && hist.i18n[lang]['V9.3']
  if (!t) throw new Error('缺少 V9.3 译文: ' + key)
  return t
}

const body = l => (l.endsWith('\r') ? l.slice(0, -1) : l)
const eol = l => (l.endsWith('\r') ? '\r' : '')
const withBody = (l, text) => text + eol(l)
const readLines = f => fs.readFileSync(f).toString('utf8').split('\n')
const writeLines = (f, lines) => fs.writeFileSync(f, Buffer.from(lines.join('\n'), 'utf8'))

/** 通用行规则：状态行 / 文档地图 / 版本线列举 */
function patchLines(lines, key) {
  const c = cur(key)
  return lines.map(l => {
    const b = body(l)
    let n = b
    const isVersionRow = /^[-|]\s*\*\*V9\./.test(n)
    if (/^-\s/.test(n) && !isVersionRow && n.includes('**V9.2**')) {
      n = n.replace('**V9.2**', '**V9.3**')
    }
    n = n.replace(/V3\.1(\s*)(→|->|إلى)(\s*)V9\.2/g, 'V3.1$1$2$3V9.3')
    n = n.replace(/\(V8\.0 \/ V8\.1 \/ V9\.0 \/ V9\.1 \/ V9\.2\)/g, '(V8.0 / V8.1 / V9.0 / V9.1 / V9.2 / V9.3)')
    n = n.replace(/current released version is \*\*V9\.2\*\*/g, 'current released version is **V9.3**')
    n = n.replace(/当前规划版本线已达 \*\*V9\.2\*\*/g, '当前规划版本线已达 **V9.3**')
    return n === b ? l : withBody(l, n)
  })
}

/** 版本表：V9.2 去掉 current 标记；V9.3 行存在则本地化 commit，否则插入 */
function patchTable(lines, key) {
  const c = cur(key)
  const row = `| **V9.3** | **${desc(key)}** | V9.3 release, **${c}** |`
  let i93 = lines.findIndex(l => /^\|\s*\*\*V9\.3\*\*/.test(body(l)))
  if (i93 >= 0) {
    const b = body(lines[i93])
    const cells = b.split('|')
    cells[cells.length - 2] = ` V9.3 release, **${c}** `
    lines[i93] = withBody(lines[i93], cells.join('|'))
  } else {
    const i92 = lines.findIndex(l => /^\|\s*\*\*V9\.2\*\*/.test(body(l)))
    if (i92 >= 0) lines.splice(i92 + 1, 0, row + eol(lines[i92]))
  }
  // V9.2 行的 current 标记（en/zh 由 render 已清，这里兜底）
  for (let i = 0; i < lines.length; i++) {
    const b = body(lines[i])
    if (!/^\|\s*\*\*V9\.2\*\*/.test(b)) continue
    const n = b.replace(/,\s*\*\*[^*]+\*\*(\s*\|)\s*$/, ' $1')
    if (n !== b) lines[i] = withBody(lines[i], n)
  }
  return lines
}

/** 无版本表语言：倒序的 "- **V9.x** · ..." 列表 + 修回 V9.1 历史行 */
function patchList(lines, key, prefix) {
  const d = desc(key)
  // 幂等：已经插入过 V9.3 列表/段落行就不再插第二次（第二次运行 Version History 标题已变 V9.3）
  const doneRe = new RegExp('^' + prefix + '\\s*\\*\\*V9\\.3\\*\\*')
  if (lines.some(l => doneRe.test(body(l)))) return lines
  const re = new RegExp('^' + prefix + '\\s*\\*\\*V9\\.2\\*\\*\\s*·')
  const idx = []
  lines.forEach((l, i) => { if (re.test(body(l))) idx.push(i) })
  if (!idx.length) return lines
  // V9.2 脚本把 V9.1 历史行也误标成了 V9.2（第二行起、含巨型类拆分特征），修回
  for (const i of idx.slice(1)) {
    const b = body(lines[i])
    if (/GenScaffoldServiceImpl|5,072/.test(b)) {
      lines[i] = withBody(lines[i], b.replace('**V9.2**', '**V9.1**'))
    }
  }
  const first = idx[0]
  lines.splice(first, 0, `${prefix} **V9.3** · ${d}` + eol(lines[first]))
  return lines
}

function syncFile(file, key, mode) {
  const original = readLines(file)
  let lines = patchTable(original.slice(), key)
  const hasTable = lines.some(l => /^\|\s*\*\*V9\.3\*\*/.test(body(l)))
  if (!hasTable && mode === 'sub') lines = patchList(lines, key, '-')
  lines = patchLines(lines, key)

  if (mode === 'intro') {
    // 无表项目介绍：Version History 段（标题 + 版本行）
    const j = lines.findIndex(l => /Version History\s*·\s*V9\.2/.test(body(l)))
    if (j >= 0) {
      lines[j] = withBody(lines[j], body(lines[j]).replace('V9.2', 'V9.3'))
      lines.splice(j + 1, 0, `> **V9.3** · ${desc(key)} — \`V9.3 release\`.` + eol(lines[j]))
    } else {
      lines = patchList(lines, key, '>')
    }
    // 顶部横幅：去掉 V9.2 的「当前版本」语义，前置 V9.3 横幅（仅一次）
    const bannerDone = lines.some(l => {
      const b = body(l)
      return /^>\s+\*\*/.test(b) && /V9\.3\s*·/.test(b) && !/Version History/.test(b)
    })
    if (!bannerDone) {
      const k = lines.findIndex(l => /^>\s+\*\*/.test(body(l)) && body(l).includes('V9.2') && !body(l).includes('**V9.3**'))
      if (k >= 0) {
        const b = body(lines[k]).replace(/^(>\s+\*\*)[^*]*?V9\.2/, '$1V9.2 release')
        lines[k] = withBody(lines[k], b)
        lines.splice(k, 0, `> **${cur(key) === 'current' ? 'Current version' : cur(key)} V9.3 · ${desc(key)}**` + eol(lines[k]))
      }
    }
  }

  if (lines.join('\n') !== original.join('\n')) { writeLines(file, lines); return 'ok' }
  return 'same'
}

const stat = { ok: 0, same: 0, err: [] }
function run(dir, pattern, mode) {
  for (const f of fs.readdirSync(dir).filter(x => pattern.test(x)).sort()) {
    const key = f.replace(/\.md$/, '')
    try {
      const r = syncFile(path.join(dir, f), key, mode)
      stat[r]++
    } catch (err) {
      stat.err.push(f + ' -> ' + err.message)
    }
  }
}

run(SUB, /^README_[A-Z_]+\.md$/, 'sub')
if (INTRO) run(INTRO, /^README(?:_[A-Z_]+)?\.md$/, 'intro')
console.log(`同步完成: 更新 ${stat.ok} 个文件 | 无变化 ${stat.same} | 异常 ${stat.err.length}`)
stat.err.forEach(x => console.log('  ERR', x))
