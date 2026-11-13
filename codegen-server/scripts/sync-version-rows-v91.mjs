// V9.1 版本历史同步：给 47 个语言 README 与 48 个语言项目介绍补 V9.1 行。
// 幂等；按字节逐行处理，保留每个文件原有的 CRLF / LF 混排，不做全文件换行符转换。
// 用法: node scripts/sync-version-rows-v91.mjs <codegen-server根> [多语言项目介绍目录]
import fs from 'fs'
import path from 'path'

const SUB = path.resolve(process.argv[2] || '.')
const INTRO = process.argv[3] ? path.resolve(process.argv[3]) : null

// 复用 V9.0 脚本里的「当前版本」译文字典
const old = fs.readFileSync(path.join(SUB, 'scripts/sync-version-rows.mjs'), 'utf8')
const s = old.indexOf('const T = {')
const e = old.indexOf('\n}', s)
const T = new Function('return ' + old.slice(s + 10, e + 2))()
T.README_HU = { c: 'Jelenlegi verzió' }
T.README_PT_BR = { c: 'Versão atual' }
T.README = { c: 'Current version' }

// V9.1 各语言引导短语（技术数据串为各语言共用，避免数字/专名被误译）
const TECH = '· `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9'
const LEAD = {
  README_ZH: '可维护性重构：GenScaffoldServiceImpl 5072 → 931 行拆为 8 个 Writer；产物逐字节零差异（新旧 jar 共 119 + 62 文件 diff -r 为空）；312/312 单测；离线 i18n 契约 9/9',
  README_ZH_TW: '可維護性重構：GenScaffoldServiceImpl 5072 → 931 行拆為 8 個 Writer；產物逐位元組零差異（新舊 jar 共 119 + 62 檔案 diff -r 為空）；312/312 單測；離線 i18n 契約 9/9',
  README_JA: '保守性リファクタリング',
  README_KO: '유지보수성 리팩터링',
  README_DE: 'Wartbarkeits-Refactoring',
  README_FR: 'Refactorisation de maintenabilité',
  README_ES: 'Refactorización de mantenibilidad',
  README_RU: 'Рефакторинг ради удобства сопровождения',
  README_PT: 'Refatoração de manutenibilidade',
  README_IT: 'Refactoring per manutenibilità',
  README_NL: 'Onderhoudbaarheidsrefactoring',
  README_PL: 'Refaktoryzacja pod kątem utrzymywalności',
  README_SV: 'Underhållsrefaktorering',
  README_DA: 'Vedligeholdelsesrefaktorering',
  README_NO: 'Vedlikeholdsrefaktorering',
  README_FI: 'Ylläpidettävyyden refaktorointi',
  README_CS: 'Refaktoring pro udržovatelnost',
  README_SK: 'Refaktoring pre udržateľnosť',
  README_HR: 'Refaktoriranje održivosti',
  README_RO: 'Refactorizare pentru mentenabilitate',
  README_BG: 'Рефакторинг за по-лесна поддръжка',
  README_UK: 'Рефакторинг для зручності супроводу',
  README_TR: 'Bakım yapılabilirlik refaktörü',
  README_EL: 'Αναδιάρθρωση συντηρησιμότητας',
  README_HE: 'רפקטור לתחזוקתיות',
  README_FA: 'بازآرایی برای نگهداریپذیری',
  README_UR: 'دیکھ بھال کے لیے ری فیکٹرنگ',
  README_UR_ROMA: 'Dekh bhaal ke liye refactoring',
  README_HI: 'रखरखाव-योग्यता हेतु रीफ़ैक्टरिंग',
  README_BN: 'রক্ষণাবেক্ষণযোগ্যতার রিফ্যাক্টরিং',
  README_TA: 'பராமரிப்புத்திறன் மறுசீரமைப்பு',
  README_TE: 'నిర్వహణ సౌలభ్య పునర్నిర్మాణం',
  README_ML: 'പരിപാലനക്ഷമത പുനഃക്രമീകരണം',
  README_ID: 'Refaktor untuk kemudahan pemeliharaan',
  README_TH: 'การปรับโครงสร้างเพื่อความสามารถในการดูแลรักษา',
  README_VI: 'Tái cấu trúc để dễ bảo trì',
  README_AR: 'إعادة هيكلة لقابلية الصيانة',
  README_ET: 'Hooldatavuse refaktoreerimine',
  README_HY: 'Սպասարկելիության ռեֆակտորինգ',
  README_KA: 'მოვლადობის რეფაქტორინგი',
  README_KM: 'ការរៀបចំឡើងវិញដើម្បីងាយថែទាំ',
  README_MY: 'ပြုပြင်ထိန်းသိမ်းလွယ်မှု ပြန်လည်ဖွဲ့စည်းခြင်း',
  README_SW: 'Urekebishaji kwa matengenezo rahisi',
  README_SL: 'Refaktoriranje za vzdrževanje',
  README_TL: 'Refactoring para sa maintainability',
  README_HU: 'Karbantarthatósági refaktorálás',
  README_PT_BR: 'Refatoração de manutenibilidade',
  README: 'Maintainability refactor',
}

function desc(key) {
  const lead = LEAD[key]
  if (!lead) throw new Error('缺少 V9.1 译文: ' + key)
  return lead.startsWith('可维护性重构') || lead.startsWith('可維護性重構') ? lead : `${lead} ${TECH}`
}

const body = l => (l.endsWith('\r') ? l.slice(0, -1) : l)
const eol = l => (l.endsWith('\r') ? '\r' : '')
const withBody = (l, text) => text + eol(l)
const readLines = f => fs.readFileSync(f).toString('utf8').split('\n')
const writeLines = (f, lines) => fs.writeFileSync(f, Buffer.from(lines.join('\n'), 'utf8'))
const escapeRe = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

function syncFile(file, key, mode) {
  let lines = readLines(file)
  if (lines.some(l => l.includes('**V9.1**'))) return 'skip'
  const d = desc(key)
  const c = (T[key] || T.README).c || 'Current version'
  const newRow = `| **V9.1** | **${d}** | 654b8e0 → b0d081e, **${c}** |`

  // 1) 版本表：V9.0 行去掉「当前版本」标记，其后插入 V9.1 行
  let i = lines.findIndex(l => /^\| \*\*V9\.0\*\*/.test(body(l)))
  if (i >= 0) {
    const nl = body(lines[i]).replace(/,\s*\*\*[^*]+\*\*(\s*\|)\s*$/, ' $1')
    lines[i] = withBody(lines[i], nl)
    lines.splice(i + 1, 0, newRow + eol(lines[i]))
  } else if (mode === 'sub') {
    // 无版本表的老翻译：在状态 bullet 后追加一行说明
    i = lines.findIndex(l => /^-\s/.test(body(l)) && body(l).includes('**V9.0**'))
    if (i >= 0) lines.splice(i + 1, 0, `- **V9.1** · ${d}` + eol(lines[i]))
  }

  // 2) 状态行 / 规划文档版本区间
  lines = lines.map(l => {
    const b = body(l)
    let n = b
    if (/^-\s/.test(n) && n.includes('**V9.0**')) n = n.replace('**V9.0**', '**V9.1**')
    n = n.replace(/V3\.1(\s*)(→|->|إلى)(\s*)V9\.0/g, 'V3.1$1$2$3V9.1')
    return n === b ? l : withBody(l, n)
  })

  if (mode === 'intro') {
    // 3) 无表语言的 Version History 段
    let j = lines.findIndex(l => /Version History\s*·\s*V9\.0/.test(body(l)))
    if (j >= 0) {
      lines[j] = withBody(lines[j], body(lines[j]).replace('V9.0', 'V9.1'))
      const nx = j + 1
      if (nx < lines.length && /^>/.test(body(lines[nx]))) {
        lines.splice(nx, 0, `> **V9.1** · ${d} — \`654b8e0 → b0d081e\`.` + eol(lines[nx]))
      }
    }

    // 4) 顶部横幅：插入 V9.1 行，并把原 V9.0 行的「当前版本」字样去掉
    j = lines.findIndex(l => /^> \*\*/.test(body(l)) && body(l).includes('V9.0'))
    if (j >= 0) {
      let b = body(lines[j])
      b = b.replace(new RegExp(escapeRe(c) + '\\s+V9\\.0'), 'V9.0')
      b = b.replace(/Current release\s+—\s+V9\.0/, 'V9.0 release')
      const e = eol(lines[j])
      lines[j] = b + e
      lines.splice(j, 0, `> **${c} V9.1 · ${d}**` + e)
    }
  }

  writeLines(file, lines)
  return 'ok'
}

const stat = { ok: 0, skip: 0, err: [] }
function run(dir, pattern, mode) {
  for (const f of fs.readdirSync(dir).filter(x => pattern.test(x)).sort()) {
    const key = f.replace(/\.md$/, '')
    try {
      const r = syncFile(path.join(dir, f), key, mode)
      stat[r]++
      if (r === 'err') stat.err.push(f)
    } catch (e) {
      stat.err.push(f + ' -> ' + e.message)
    }
  }
}

run(SUB, /^README_[A-Z_]+\.md$/, 'sub')
if (INTRO) run(INTRO, /^README(?:_[A-Z_]+)?\.md$/, 'intro')
console.log(`同步完成: ${stat.ok} 个文件 | 跳过 ${stat.skip} | 异常 ${stat.err.length}`)
stat.err.forEach(x => console.log('  ERR', x))
