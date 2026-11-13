// V9.2 版本历史同步：给 47 个语言 README 与 48 个语言项目介绍补 V9.2 行。
// 幂等；按字节逐行处理，保留每个文件原有的 CRLF / LF 混排，不做全文件换行符转换。
// 用法: node scripts/sync-version-rows-v92.mjs <codegen-server根> [多语言项目介绍目录]
import fs from 'fs'
import path from 'path'

const SUB = path.resolve(process.argv[2] || '.')
const INTRO = process.argv[3] ? path.resolve(process.argv[3]) : null

// 复用 V9.0 脚本里的「当前版本」译文字典（47 语言 + 主文档）
const old = fs.readFileSync(path.join(SUB, 'scripts/sync-version-rows.mjs'), 'utf8')
const s = old.indexOf('const T = {')
const e = old.indexOf('\n}', s)
const T = new Function('return ' + old.slice(s + 10, e + 2))()
T.README_HU = { c: 'Jelenlegi verzió' }
T.README_PT_BR = { c: 'Versão atual' }
T.README = { c: 'Current version' }

// V9.2 技术数据串：数字/专名各语言共用，避免误译
const TECH = '· Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical'

// 各语言引导短语（本地化「测试中心与验证中心」）
const LEAD = {
  README_ZH: '测试中心与验证中心：把接口/UI 用例资产（7 张表）与平台验证资产（S1–S9 套件、三层落库、统一 JSON 摘要）合并为一个版本；产物零 diff，373/373 单测，206/206 语言键',
  README_ZH_TW: '測試中心與驗證中心：把介面/UI 用例資產（7 張表）與平台驗證資產（S1–S9 套件、三層落庫、統一 JSON 摘要）合併為一個版本；產物零差異，373/373 單測，206/206 語言鍵',
  README_JA: 'テストセンターと検証ハブ',
  README_KO: '테스트 센터 및 검증 허브',
  README_DE: 'Testzentrum & Verifizierungs-Hub',
  README_FR: 'Centre de tests et hub de vérification',
  README_ES: 'Centro de pruebas y centro de verificación',
  README_RU: 'Центр тестирования и центр проверок',
  README_PT: 'Central de testes e centro de verificação',
  README_IT: 'Centro di test e centro di verifica',
  README_NL: 'Testcentrum & verificatiehub',
  README_PL: 'Centrum testów i centrum weryfikacji',
  README_SV: 'Testcenter & verifieringshubb',
  README_DA: 'Testcenter & verifikationshub',
  README_NO: 'Testsenter & verifikasjonssenter',
  README_FI: 'Testikeskus & varmennuskeskus',
  README_CS: 'Testovací centrum & ověřovací centrum',
  README_SK: 'Testovacie centrum & overovacie centrum',
  README_HR: 'Testni centar & centar za provjeru',
  README_RO: 'Centru de testare & centru de verificare',
  README_BG: 'Център за тестване и проверка',
  README_UK: 'Центр тестування та перевірки',
  README_TR: 'Test merkezi & doğrulama merkezi',
  README_EL: 'Κέντρο δοκιμών & επαλήθευσης',
  README_HE: 'מרכז בדיקות ומרכז אימות',
  README_FA: 'مرکز آزمون و مرکز تأیید',
  README_UR: 'ٹیسٹ سینٹر اور تصدیقی مرکز',
  README_UR_ROMA: 'Test Center aur tasdeeqi markaz',
  README_HI: 'परीक्षण केंद्र एवं सत्यापन केंद्र',
  README_BN: 'পরীক্ষা কেন্দ্র ও যাচাই কেন্দ্র',
  README_TA: 'சோதனை மையம் & சரிபார்ப்பு மையம்',
  README_TE: 'పరీక్షా కేంద్రం & ధృవీకరణ కేంద్రం',
  README_ML: 'പരിശോധനാ കേന്ദ്രം & സ്ഥിരീകരണ കേന്ദ്രം',
  README_ID: 'Pusat pengujian & pusat verifikasi',
  README_TH: 'ศูนย์ทดสอบและศูนย์ตรวจสอบ',
  README_VI: 'Trung tâm kiểm thử & trung tâm xác minh',
  README_AR: 'مركز الاختبارات ومركز التحقق',
  README_ET: 'Testikeskus & kontrollikeskus',
  README_HY: 'Թեստավորման կենտրոն & ստուգման կենտրոն',
  README_KA: 'ტესტირების ცენტრი & ვერიფიკაციის ცენტრი',
  README_KM: 'មជ្ឈមណ្ឌលសាកល្បង និងផ្ទៀងផ្ទាត់',
  README_MY: 'စမ်းသပ်မှုစင်တာနှင့် အတည်ပြုစင်တာ',
  README_SW: 'Kituo cha majaribio & uthibitishaji',
  README_SL: 'Testni center & center za preverjanje',
  README_TL: 'Sentro ng pagsubok & sentro ng beripikasyon',
  README_HU: 'Tesztközpont & ellenőrzési központ',
  README_PT_BR: 'Central de testes e centro de verificação',
  README: 'Test Center & Verification Hub',
}

function desc(key) {
  const lead = LEAD[key]
  if (!lead) throw new Error('缺少 V9.2 译文: ' + key)
  return lead.startsWith('测试中心与验证中心') || lead.startsWith('測試中心與驗證中心')
    ? lead
    : `${lead} ${TECH}`
}

const body = l => (l.endsWith('\r') ? l.slice(0, -1) : l)
const eol = l => (l.endsWith('\r') ? '\r' : '')
const withBody = (l, text) => text + eol(l)
const readLines = f => fs.readFileSync(f).toString('utf8').split('\n')
const writeLines = (f, lines) => fs.writeFileSync(f, Buffer.from(lines.join('\n'), 'utf8'))
const escapeRe = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

function syncFile(file, key, mode) {
  let lines = readLines(file)
  if (lines.some(l => l.includes('**V9.2**'))) return 'skip'
  const d = desc(key)
  const c = (T[key] || T.README).c || 'Current version'
  const newRow = `| **V9.2** | **${d}** | V9.2 release, **${c}** |`

  // 1) 版本表：V9.1 行去掉「当前版本」标记，其后插入 V9.2 行
  let i = lines.findIndex(l => /^\| \*\*V9\.1\*\*/.test(body(l)))
  if (i >= 0) {
    const nl = body(lines[i]).replace(/,\s*\*\*[^*]+\*\*(\s*\|)\s*$/, ' $1')
    lines[i] = withBody(lines[i], nl)
    lines.splice(i + 1, 0, newRow + eol(lines[i]))
  } else if (mode === 'sub') {
    i = lines.findIndex(l => /^-\s/.test(body(l)) && body(l).includes('**V9.1**'))
    if (i >= 0) lines.splice(i + 1, 0, `- **V9.2** · ${d}` + eol(lines[i]))
  }

  // 2) 状态行 / 规划文档版本区间
  lines = lines.map(l => {
    const b = body(l)
    let n = b
    if (/^-\s/.test(n) && n.includes('**V9.1**')) n = n.replace('**V9.1**', '**V9.2**')
    n = n.replace(/V3\.1(\s*)(→|->|إلى)(\s*)V9\.1/g, 'V3.1$1$2$3V9.2')
    return n === b ? l : withBody(l, n)
  })

  if (mode === 'intro') {
    // 3) 无表语言的 Version History 段
    let j = lines.findIndex(l => /Version History\s*·\s*V9\.1/.test(body(l)))
    if (j >= 0) {
      lines[j] = withBody(lines[j], body(lines[j]).replace('V9.1', 'V9.2'))
      const nx = j + 1
      if (nx < lines.length && /^>/.test(body(lines[nx]))) {
        lines.splice(nx, 0, `> **V9.2** · ${d} — \`V9.2 release\`.` + eol(lines[nx]))
      }
    }

    // 4) 顶部横幅：插入 V9.2 行，并把原 V9.1 行的「当前版本」字样去掉
    j = lines.findIndex(l => /^> \*\*/.test(body(l)) && body(l).includes('V9.1'))
    if (j >= 0) {
      let b = body(lines[j])
      b = b.replace(new RegExp(escapeRe(c) + '\\s+V9\\.1'), 'V9.1')
      b = b.replace(/Current release\s+—\s+V9\.1/, 'V9.1 release')
      const e = eol(lines[j])
      lines[j] = b + e
      lines.splice(j, 0, `> **${c} V9.2 · ${d}**` + e)
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
    } catch (e) {
      stat.err.push(f + ' -> ' + e.message)
    }
  }
}

run(SUB, /^README_[A-Z_]+\.md$/, 'sub')
if (INTRO) run(INTRO, /^README(?:_[A-Z_]+)?\.md$/, 'intro')
console.log(`同步完成: ${stat.ok} 个文件 | 跳过 ${stat.skip} | 异常 ${stat.err.length}`)
stat.err.forEach(x => console.log('  ERR', x))
