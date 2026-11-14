// 从 docs/version-history.json 渲染「版本表」到各 README（单一 JSON 源 → 全量重生成）
//
// 用法:
//   node scripts/render_version_history.mjs [根目录] [--all] [--check]
//     （无参数）        仅渲染 README.md（英文）
//     --all            渲染目录下全部 README_*.md
//     --check          干跑，只报告哪些文件会变动，不落盘
//
// 规则:
//   - 版本表 = 三列表格且首数据行以 V\d 开头（与文档里另两张三列表格区分）
//   - 英文/中文走 JSON 主源(theme.en/zh, commit.en/zh)
//   - 其余语言: 优先 i18n[lang][版本]; 缺则回退到该文件现有译文(不丢翻译); 再缺回退英文
//   - 加粗风格: 已存在的行沿用原文件的加粗; 新增行按 highlight 加粗 版本+主题
//   - commit 列: 英/中用 JSON; 其余语言保留文件现有 commit(含本地化如 DE "Zeilen")
import fs from 'fs'
import path from 'path'

const args = process.argv.slice(2)
const CHECK = args.includes('--check')
const ALL = args.includes('--all')
const posArg = args.find(a => !a.startsWith('--'))
const singleFile = posArg && fs.existsSync(posArg) && fs.statSync(posArg).isFile() ? posArg : null
const ROOT = singleFile ? path.dirname(path.resolve(posArg)) : (posArg || '.')

const data = JSON.parse(fs.readFileSync(path.join(ROOT, 'docs/version-history.json'), 'utf8'))

function findTable(lines) {
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i]
    if (/^\s*\|(\s*-+\s*\|){3,}\s*$/.test(l)) {
      const first = ((lines[i + 1] || '').split('|')[1] || '').trim()
      if (/^V\d/.test(first)) return i
    }
  }
  return -1
}
const cells = line => {
  const p = line.split('|')
  return p.slice(1, p.length - 1).map(s => s.trim())
}
const isBoldCell = s => /^\*\*.+\*\*$/.test(s.trim())
const unbold = s => s.replace(/^\*\*(.*)\*\*$/, '$1')

function langOf(file) {
  const base = path.basename(file)
  if (base === 'README.md') return 'en'
  const m = base.match(/^README_(.+)\.md$/)
  return m ? m[1].toUpperCase() : 'en'
}

function renderFile(file) {
  const lang = langOf(file)
  let content = fs.readFileSync(file, 'utf8')
  // 统一按 \r?\n 切行（兼容旧脚本留下的混合换行损坏），并以主流换行符重写
  const crlfN = (content.match(/\r\n/g) || []).length
  const lfN = (content.match(/\n/g) || []).length - crlfN
  const nl = crlfN >= lfN ? '\r\n' : '\n'
  const lines = content.split(/\r?\n/)
  const sepIdx = findTable(lines)
  if (sepIdx === -1) return { file, lang, changed: false, reason: 'no-version-table' }

  // 解析现有行，记录每个版本的原文与加粗风格
  const existing = new Map()
  let endIdx = sepIdx + 1
  for (let i = sepIdx + 1; i < lines.length; i++) {
    const l = lines[i]
    if (!l.trim().startsWith('|')) { endIdx = i; break }
    if (/^\s*\|(\s*-+\s*\|)+\s*$/.test(l)) continue
    const c = cells(l)
    existing.set(unbold(c[0]), { theme: c[1], commit: c[2], boldV: isBoldCell(c[0]), boldT: isBoldCell(c[1]), line: l })
    endIdx = i + 1
  }

  const newBody = []
  for (const v of data.versions) {
    let theme, commit
    if (lang === 'en') { theme = v.theme.en; commit = v.commit.en }
    else if (lang === 'zh') { theme = v.theme.zh; commit = v.commit.zh }
    else {
      const ex = existing.get(v.version)
      theme = (data.i18n[lang] && data.i18n[lang][v.version]) || (ex ? unbold(ex.theme) : v.theme.en)
      commit = ex ? ex.commit : v.commit.en
    }
    const ex = existing.get(v.version)
    // 内容（去加粗后的主题 + commit 原文）与现有行一致 → 原样保留（字节级零 diff，容错尾部空格等）
    if (ex && unbold(ex.theme) === theme && ex.commit === commit) {
      newBody.push(ex.line)
      continue
    }
    const boldV = ex ? ex.boldV : v.highlight
    const boldT = ex ? ex.boldT : v.highlight
    const vCell = boldV ? `**${v.version}**` : v.version
    const tCell = boldT ? `**${theme}**` : theme
    newBody.push(`| ${vCell} | ${tCell} | ${commit} |`)
  }

  const before = lines.slice(sepIdx + 1, endIdx)
  let changed = newBody.length !== before.length
  if (!changed) for (let i = 0; i < newBody.length; i++) if (newBody[i] !== before[i]) { changed = true; break }

  if (changed && !CHECK) {
    const out = [...lines.slice(0, sepIdx + 1), ...newBody, ...lines.slice(endIdx)]
    fs.writeFileSync(file, out.join(nl))
  }
  return { file, lang, changed, rows: newBody.length, reason: changed ? 'updated' : 'identical' }
}

const files = ALL
  ? fs.readdirSync(ROOT).filter(f => /^README_[A-Z_]+\.md$/.test(f)).sort().map(f => path.join(ROOT, f))
  : (singleFile ? [singleFile] : [path.join(ROOT, 'README.md')])

let updated = 0, identical = 0, skipped = 0, changedFiles = []
for (const f of files) {
  const r = renderFile(f)
  if (r.reason === 'no-version-table') { skipped++; continue }
  if (r.changed) { updated++; changedFiles.push(path.basename(f) + (CHECK ? ' (would change)' : '')) }
  else identical++
}

console.log(`渲染完成 [${ALL ? 'ALL' : 'README.md'}${CHECK ? ' / CHECK' : ''}]`)
console.log(`  更新: ${updated}  无变化: ${identical}  跳过(无版本表): ${skipped}`)
if (changedFiles.length) { console.log('  变动文件:'); changedFiles.forEach(f => console.log('   - ' + f)) }
