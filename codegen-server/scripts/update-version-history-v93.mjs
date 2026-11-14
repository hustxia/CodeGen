// V9.3 版本历史真源更新（幂等）：把 codegen-server/docs/version-history.json 从 V9.1 补到 V9.3。
//
// 用法: node scripts/update-version-history-v93.mjs [codegen-server根]
//
// 背景：V9.2 发布时只跑了 sync-version-rows-v92.mjs / sync-root-docs-v92.mjs 直接改 README，
//       没有回写 version-history.json，导致真源停在 V9.1、i18n 停在 V9.1。
//       本脚本一次补齐：
//         1) versions 追加 V9.2（从 README.md / README_ZH.md 采集主题）、V9.3（本版主题）
//         2) V9.1 的 "current" 标记移除，current 移交 V9.3
//         3) i18n 46 语言各补 V9.2（从现有语言 README harvest）与 V9.3（本地化短语 + 共用技术串）
//         4) meta.currentVersion = V9.3
//       之后再跑 render_version_history.mjs + sync-version-rows-v93.mjs 把表渲染到各 README。
import fs from 'fs'
import path from 'path'

const ROOT = path.resolve(process.argv[2] || '.')
const JSON_PATH = path.join(ROOT, 'docs/version-history.json')
const data = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'))
const read = f => fs.readFileSync(path.join(ROOT, f), 'utf8')
const unbold = s => s.replace(/^\*\*(.*)\*\*$/, '$1').trim()

/** 从 markdown 版本表取某个版本行的主题单元格（去加粗） */
function themeOf(file, version) {
  const lines = read(file).split(/\r?\n/)
  const re = new RegExp('^\\|\\s*\\*{0,2}' + version.replace('.', '\\.') + '\\*{0,2}\\s*\\|')
  for (const l of lines) {
    if (re.test(l)) {
      const cells = l.split('|').slice(1, -1).map(s => s.trim())
      return unbold(cells[1] || '')
    }
  }
  throw new Error('未找到 ' + version + ' 行: ' + file)
}

/** 无版本表的语言把版本说明写成 "- **V9.2** · ..." 列表行 */
function listThemeOf(file, version) {
  const re = new RegExp('^-\\s+\\*\\*' + version.replace('.', '\\.') + '\\*\\*\\s*·\\s*(.+)$')
  for (const l of read(file).split(/\r?\n/)) {
    const m = re.exec(l.trimEnd())
    if (m) return m[1].trim()
  }
  throw new Error('未找到 ' + version + ' 列表行: ' + file)
}

const V92_EN = themeOf('README.md', 'V9.2')
const V92_ZH = themeOf('README_ZH.md', 'V9.2')

const V93_EN = 'Generated-artifact automated testing: extends the V9.2 Verification Center with S10 (real `mvn test` on generated projects + Surefire parsing), S11 (artifact API tests, Docker/TestContainers, honestly SKIPPED when unavailable) and S12 (artifact E2E, Playwright, SKIPPED when unavailable); adds the GEN trigger, `TC_GEN_ROOT` env injection, `POST /tc/runs/gen` manual entry point, `autoVerifyGen` write-hook (default off) and a front-end "Verify Generated Artifacts" action. End-to-end: a freshly built complete system (device asset management, 4 tables) ran the 12-step pipeline to `success`, and its real artifact GEN run was PASSED with 20/20 S10 cases and three-layer run/case/artifact persistence. Full-flow testing also fixed 4 pipeline defects (fat-jar self-check classpath, subprocess relative paths, Vite vs vue-cli startup, NOT_NULL stale fail_reason) and 3 Vue 2 leftovers in generated Vue 3 pages'
const V93_ZH = '生成产物自动化测试：在 V9.2 验证中心扩展 S10（对真实产物跑 `mvn test` + Surefire 解析）、S11（产物接口测试，需 Docker/TestContainers，缺失如实 SKIP）、S12（产物 E2E，需 Playwright，缺失如实 SKIP）；新增 GEN 触发器、`TC_GEN_ROOT` 注入、`POST /tc/runs/gen` 手动入口、`autoVerifyGen` 写盘钩子（默认关闭）与前端「验证生成产物」操作区。端到端实测：新建「设备资产管理系统」（4 张表）跑完 12 步流水线 status=success，用真实产物触发 GEN 验证 PASSED，S10 20/20 用例全过，run/case/artifact 三层落库。全流程实测顺带修掉 4 个流水线缺陷（肥 jar 自检 classpath、子进程相对路径、Vite/vue-cli 启动串台、NOT_NULL 残留 fail_reason）与生成产物 Vue3 页面的 3 处 Vue2 遗留'

// 各语言「生成产物自动化测试」引导短语；数字与专名交给共用 TECH，避免误译
const TECH93 = '· Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted'
const LEAD93 = {
  ZH_TW: '生成產物自動化測試',
  JA: '生成成果物の自動テスト',
  KO: '생성 산출물 자동화 테스트',
  DE: 'Automatisierte Tests generierter Artefakte',
  FR: 'Tests automatisés des artefacts générés',
  ES: 'Pruebas automatizadas de artefactos generados',
  RU: 'Автоматизированное тестирование сгенерированных артефактов',
  PT: 'Testes automatizados de artefatos gerados',
  IT: 'Test automatici degli artefatti generati',
  NL: 'Geautomatiseerde tests van gegenereerde artefacten',
  PL: 'Automatyczne testy generowanych artefaktów',
  SV: 'Automatiserade tester av genererade artefakter',
  DA: 'Automatiserede tests af genererede artefakter',
  NO: 'Automatiserte tester av genererte artefakter',
  FI: 'Generoitujen artefaktien automatisoidut testit',
  CS: 'Automatizované testy generovaných artefaktů',
  SK: 'Automatizované testy generovaných artefaktov',
  HR: 'Automatizirano testiranje generiranih artefakata',
  RO: 'Testare automată a artefactelor generate',
  BG: 'Автоматизирано тестване на генерираните артефакти',
  UK: 'Автоматизоване тестування згенерованих артефактів',
  TR: 'Oluşturulan artefaktların otomatik testleri',
  EL: 'Αυτοματοποιημένες δοκιμές των παραγόμενων τεχνουργημάτων',
  HE: 'בדיקות אוטומטיות של תוצרים שנוצרו',
  FA: 'آزمون خودکار خروجی‌های تولیدشده',
  UR: 'تیار کردہ مصنوعات کی خودکار جانچ',
  UR_ROMA: 'Generated artifacts ki automatic testing',
  HI: 'उत्पन्न आर्टिफैक्ट्स का स्वचालित परीक्षण',
  BN: 'উৎপন্ন আর্টিফ্যাক্টের স্বয়ংক্রিয় পরীক্ষা',
  TA: 'உருவாக்கப்பட்ட ஆர்ட்டிஃபாக்ட்களின் தானியங்கி சோதனை',
  TE: 'రూపొందించిన ఆర్టిఫాక్ట్\u200Cల ఆటోమేటెడ్ పరీక్షలు',
  ML: 'സൃഷ്ടിച്ച ആർട്ടിഫാക്റ്റുകളുടെ ഓട്ടോമേറ്റഡ് പരിശോധന',
  ID: 'Pengujian otomatis artefak hasil generasi',
  TH: 'การทดสอบอัตโนมัติของผลลัพธ์ที่สร้างขึ้น',
  VI: 'Kiểm thử tự động sản phẩm được sinh ra',
  AR: 'الاختبار الآلي للمخرجات المُولّدة',
  ET: 'Genereeritud artefaktide automatiseeritud testimine',
  HY: 'Գեներացված արտեֆակտների ավտոմատացված թեստավորում',
  KA: 'გენერირებული არტეფაქტების ავტომატური ტესტირება',
  KM: 'ការធ្វើតេស្តដោយស្វ័យប្រវត្តិលើផលិតផលដែលបង្កើតឡើង',
  MY: 'ထုတ်လုပ်ထားသော artifact များကို အလိုအလျောက် စမ်းသပ်ခြင်း',
  SW: 'Majaribio ya kiotomatiki ya vitu vilivyozalishwa',
  SL: 'Avtomatizirano testiranje generiranih artefaktov',
  TL: 'Awtomatikong pagsubok sa mga nabuong artifact',
  HU: 'Generált artefaktumok automatizált tesztelése',
  PT_BR: 'Testes automatizados de artefatos gerados',
}
const desc93 = lang => `${LEAD93[lang]} ${TECH93}`

// ---- 1) versions：清 V9.1 current，追加 V9.2 / V9.3 ----
const byVer = new Map(data.versions.map(v => [v.version, v]))
const v91 = byVer.get('V9.1')
if (v91) {
  v91.commit.en = '654b8e0'
  v91.commit.zh = '654b8e0 → b0d081e'
}
if (!byVer.has('V9.2')) {
  data.versions.push({
    version: 'V9.2', highlight: true,
    theme: { en: V92_EN, zh: V92_ZH },
    commit: { en: 'V9.2 release', zh: 'V9.2 release' },
  })
}
if (!byVer.has('V9.3')) {
  data.versions.push({
    version: 'V9.3', highlight: true,
    theme: { en: V93_EN, zh: V93_ZH },
    commit: { en: 'V9.3 release, **current**', zh: 'V9.3 release, **当前版本**' },
  })
}

// ---- 2) i18n：V9.2 harvest + V9.3 译本 ----
// --refresh-v92 用于把早期「无版本表语言」回落成的英文通用串换成列表行里的真实译本
const REFRESH_V92 = process.argv.includes('--refresh-v92')
const stat = { v92table: 0, v92list: 0, v93: 0, missing: [] }
for (const lang of Object.keys(data.i18n)) {
  const bucket = data.i18n[lang]
  if (REFRESH_V92 || !bucket['V9.2']) {
    const f = `README_${lang}.md`
    try { bucket['V9.2'] = themeOf(f, 'V9.2'); stat.v92table++ }
    catch {
      try { bucket['V9.2'] = listThemeOf(f, 'V9.2'); stat.v92list++ }
      catch {
        if (!bucket['V9.2']) {
          bucket['V9.2'] = `${LEAD93[lang] || 'Test Center & Verification Hub'} · Test Center (7 tables) + Verification Center (S1-S9, three-layer persistence); 373/373 tests; 206/206 locales`
          stat.missing.push(lang)
        }
      }
    }
  }
  if (!bucket['V9.3']) {
    if (!LEAD93[lang]) throw new Error('缺少 V9.3 译文: ' + lang)
    bucket['V9.3'] = desc93(lang); stat.v93++
  }
}

// ---- 3) meta ----
data.meta.currentVersion = 'V9.3'
data.meta.updated = '2026-11-14'
data.meta.source = 'README.md(en) + README_ZH.md(zh) 为权威源；sync-version-rows*.mjs harvest 其余语言的 V7.5~V9.3 译本'
data.meta.note = '改动版本表只需编辑本文件，再运行 node scripts/render_version_history.mjs（英文 README.md）与 --all（其余语言）'

fs.writeFileSync(JSON_PATH, Buffer.from(JSON.stringify(data, null, 2) + '\n', 'utf8'))
console.log(`version-history.json 已更新: currentVersion=${data.meta.currentVersion} versions=${data.versions.length} i18n=${Object.keys(data.i18n).length} 语言`)
console.log(`  V9.2 译本 harvest ${stat.v92table} 条（表）+ ${stat.v92list} 条（列表行），回落 ${stat.missing.length}: ${stat.missing.join(',') || '无'}`)
console.log(`  V9.3 新增译本 ${stat.v93} 条`)
