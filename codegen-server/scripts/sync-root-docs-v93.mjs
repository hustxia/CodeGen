// V9.3 主文档同步（幂等）：根 README / codegen-server README / 两份中文项目介绍 / ROADMAP。
// 用法: node codegen-server/scripts/sync-root-docs-v93.mjs <monorepo根>
// 前置: update-version-history-v93.mjs → render_version_history.mjs → sync-version-rows-v93.mjs
// 按行处理并保留每行原有 CRLF / LF，不把 CRLF 文件刷成 LF。
import fs from 'fs'
import path from 'path'

const ROOT = path.resolve(process.argv[2] || '.')
const hist = JSON.parse(fs.readFileSync(path.join(ROOT, 'codegen-server/docs/version-history.json'), 'utf8'))
const V93 = hist.versions.find(v => v.version === 'V9.3')
const read = f => fs.readFileSync(path.join(ROOT, f), 'utf8').split('\n')
const write = (f, L) => fs.writeFileSync(path.join(ROOT, f), Buffer.from(L.join('\n'), 'utf8'))
const body = l => (l.endsWith('\r') ? l.slice(0, -1) : l)
const eolOf = l => (l.endsWith('\r') ? '\r' : '')
const mk = (text, like) => text + eolOf(like)
const domEol = L => (L.filter(l => l.endsWith('\r')).length >= L.filter(l => !l.endsWith('\r')).length ? '\r' : '')

const ROW_EN = `| **V9.3** | **${V93.theme.en}** | V9.3 release, **current** |`
const ROW_ZH = `| **V9.3** | **${V93.theme.zh}** | V9.3 release, **当前版本** |`

const BANNER_EN = [
  '> **Current release — V9.3 (Generated-artifact Automated Testing) on top of V9.2 (Test Center & Verification Hub)**',
  '> Three new product suites extend the V9.2 Verification Center: **S10** runs a real `mvn test` on the generated project',
  '> (Surefire parsing), **S11** covers artifact API tests (Docker/TestContainers) and **S12** artifact E2E (Playwright);',
  '> S11/S12 report an honest SKIPPED when their toolchain is absent. A **GEN** trigger, `TC_GEN_ROOT` injection,',
  '> `POST /tc/runs/gen` manual entry point, an `autoVerifyGen` write-hook (default off) and a front-end action',
  '> "Verify Generated Artifacts" complete the loop. End-to-end verified on a freshly built **device asset management**',
  '> system (4 tables): 12-step pipeline `success`, real artifact GEN run **PASSED**, **20/20 S10 cases**, three-layer',
  '> run/case/artifact persistence. Full-flow testing also fixed 4 pipeline defects and 3 Vue 2 leftovers in Vue 3 pages.',
]

const STATUS_EN = '- The current released version is **V9.3** (Generated-artifact Automated Testing: S10 artifact unit tests with real `mvn test`, S11 artifact API tests and S12 artifact E2E on top of the V9.2 Verification Center; GEN trigger, `TC_GEN_ROOT`, `POST /tc/runs/gen` and a front-end verification action). It builds on **V9.2** (Test Center + Verification Hub), **V9.1** (byte-identical maintainability refactor), **V9.0** (Vue 3 + Element Plus + Vite generated artifacts), **V8.1** (quality gate) and **V8.0** (full-page i18n). Language support verified on 2026-11-14: 206/206 locales, 12 RTL, 8 translated word banks key-identical to zh-CN (60 keys each). Generated-artifact verification on 2026-11-14: real run PASSED with 20/20 S10 cases, S11/S12 honestly SKIPPED.'
const STATUS_ZH = '- 当前发布版本为 **V9.3**（生成产物自动化测试：在 V9.2 验证中心之上新增 S10 产物单测、S11 产物接口测试、S12 产物 E2E，配套 GEN 触发器、`TC_GEN_ROOT` 注入、`POST /tc/runs/gen` 与前端「验证生成产物」入口）；承接 V9.2 测试中心 + 验证中心、V9.1 巨型类拆分、V9.0 生成物 Vue 3 现代化、V8.1 质量门禁与 V8.0 全页面国际化（206 语言框架 / 8 种精译）。2026-11-14 实测：新建完整系统 12 步流水线通过，真实产物 GEN 验证 PASSED、S10 20/20 用例全过，S11/S12 缺工具如实 SKIP。'

function patch(file, fn) {
  const p = path.join(ROOT, file)
  if (!fs.existsSync(p)) return console.log('SKIP (missing)', file)
  const L = read(file)
  const out = fn(L)
  if (!out || out.join('\n') === L.join('\n')) return console.log('SKIP (no change)', file)
  write(file, out)
  console.log('OK  ', file)
}

/** 版本表 / 状态行 / 区间：所有子步骤各自幂等 */
function common(L, { zh = false, banner = false } = {}) {
  let out = L.slice()

  // 1) 顶部横幅
  if (banner) {
    const i = out.findIndex(l => /^>\s+\*\*Current release — V9\.2/.test(body(l)))
    if (i >= 0) {
      const like = domEol(L)
      out[i] = mk('> **V9.2 — Test Center & Verification Hub (previous release)**', like)
      out.splice(i, 0, ...BANNER_EN.map(t => mk(t, like)), mk('>', like))
    }
    const j = out.findIndex(l => /^>\s+\*\*Current version V9\.1/.test(body(l)))
    if (j >= 0) {
      const like = domEol(L)
      out[j] = mk('> **V9.1 — maintainability refactor (previous release)**', like)
      out.splice(j, 0, ...BANNER_EN.map(t => mk(t, like)), mk('> **V9.2 — Test Center & Verification Hub (previous release)**', like), mk('>', like))
    }
  }

  // 2) 版本表：V9.2 去掉 current，其后插入 V9.3 行（render 已处理过的文件不再插）
  const has93 = out.some(l => /^\|\s*\*\*V9\.3\*\*/.test(body(l)))
  if (!has93) {
    const i = out.findIndex(l => /^\|\s*\*\*V9\.2\*\*/.test(body(l)))
    if (i >= 0) out.splice(i + 1, 0, mk(zh ? ROW_ZH : ROW_EN, out[i]))
  }
  for (let k = 0; k < out.length; k++) {
    const b = body(out[k])
    if (!/^\|\s*\*\*V9\.2\*\*/.test(b)) continue
    const n = b.replace(/,\s*\*\*[^*]+\*\*(\s*\|)\s*$/, ' $1')
    if (n !== b) out[k] = mk(n, out[k])
  }

  // 3) 状态行 / 文档地图 / 版本区间
  for (let k = 0; k < out.length; k++) {
    let b = body(out[k])
    const orig = b
    if (b.startsWith('- The current released version is **V9.2**')) b = STATUS_EN
    if (b.startsWith('- The current released version is **V9.3**') && !b.includes('Generated-artifact')) b = STATUS_EN
    if (b.startsWith('- 当前发布版本为 **V9.1**') || b.startsWith('- 当前发布版本为 **V9.2**')) b = STATUS_ZH
    b = b.replace(/V3\.1\s*→\s*V9\.(?:1|2|3)/g, 'V3.1 → V9.3')
    b = b.replace(/V3\.1\s*→\s*V7\.3/g, 'V3.1 → V9.3')
    b = b.replace(/\(V8\.0 \/ V8\.1 \/ V9\.0 \/ V9\.1 \/ V9\.2\)/g, '(V8.0 / V8.1 / V9.0 / V9.1 / V9.2 / V9.3)')
    b = b.replace(/当前规划版本线已达 \*\*V9\.2\*\*/g, '当前规划版本线已达 **V9.3**')
    if (b !== orig) out[k] = mk(b, out[k])
  }
  return out
}

patch('README.md', L => common(L, { banner: true }))
patch('codegen-server/README.md', L => common(L))
patch('多语言项目介绍/README.md', L => common(L, { banner: true }))
patch('多语言项目介绍/项目介绍.md', L => common(L, { zh: true }))

// ROADMAP：头部状态 + 追加 V9.3 实现记录
patch('codegen-server/ROADMAP.md', L => {
  let out = L.slice()
  for (let k = 0; k < out.length; k++) {
    let b = body(out[k])
    const orig = b
    if (b.startsWith('> 当前版本：**V9.1**')) {
      b = '> 当前版本：**V9.3** — 生成产物自动化测试（S10/S11/S12 + GEN 触发），基于 V9.2 测试中心 + 验证中心；V8.0 全页面国际化 → V8.1 质量加固 → V9.0 生成物 Vue3 现代化 → V9.1 巨型类拆分 → V9.2 测试中心/验证中心 → V9.3 产物自动化测试，均已落地并通过验证  '
    }
    if (b.startsWith('> 路线图建立：')) b = '> 路线图建立：2026-11-12　|　最近更新：2026-11-14  '
    if (b.startsWith('> 状态：V9.1 已完成')) {
      b = '> 状态：V9.3 已完成生成产物自动化测试；12 步全流程实测通过，真实产物 GEN 验证 PASSED（S10 20/20 用例、S11/S12 如实 SKIP、三层落库齐全）。'
    }
    if (b !== orig) out[k] = mk(b, out[k])
  }
  if (out.some(l => body(l).startsWith('## V9.3 —'))) return out
  const like = domEol(L)
  const block = [
    '', '---', '',
    '## V9.3 — 生成产物自动化测试（已实现，2026-11-14）', '',
    '> 主题由用户指定为「生成产物自动化测试」。在 V9.2 验证中心（S1–S9）之上扩展产物类套件，',
    '> 不重写测试中心；方案与实现记录见 `docs/代码生成器-V9.3-规划.md`。', '',
    '**交付**',
    '- 套件：`TcSuite` 新增 **S10 产物单测**（对真实产物跑 `mvn test` + Surefire 解析）、**S11 产物接口测试**',
    '  （Docker/TestContainers，缺失如实 SKIPPED）、**S12 产物 E2E**（Playwright，缺失如实 SKIPPED）；',
    '  `GEN_PRODUCT_SUITES` / `isGenProductSuite()` 统一标识，前端按 `TcSuiteVO.genProduct` 渲染，不硬编码套件号。',
    '- 触发：`TestCenterExecutor` 新增 `GEN` trigger 与 `TC_GEN_ROOT`（经 `TcProcessSpec.env` 注入子进程）；',
    '  `autoVerifyGen` 默认 `false` 控制写盘后自动钩子；`POST /tc/runs/gen` 手动触发忽略该开关。',
    '- 前端：`views/verification/index.vue` 新增「验证生成产物」操作区，`api/verification.js` 新增 `triggerGen()`，',
    '  运行历史的 GEN 触发类型高亮。',
    '- 安全：`TcGenRunRequest` 只接 `genRoot`，不接收命令 / path 黑名单字段。', '',
    '**验证**',
    '- `TcSuiteRegistryTest` 通过（12 套件 / 默认勾选 8 个）；`mvn -o compile` / `package`、控制台 `npm run build` 均通过。',
    '- 端到端：新建「设备资产管理系统」（`v93_asset_demo`，4 张表）真跑 12 步流水线 `status=success`、',
    '  `failReason=None`、后端 8140 / 前端 9040 / health 200。',
    '- 真实产物 GEN 验证：`TC20261114-00147`、`TC20261114-00109` 均 **PASSED**；S10 真跑产物自带的',
    '  4 个 ControllerTest **20/20 用例全过**，S11/S12 如实 SKIPPED，`tc_run` / `tc_case_result` /',
    '  `tc_artifact` 三层落库核对无误（1 行 / 3 行 / 3 个日志）。',
    '- 全流程实测修掉 4 个流水线缺陷（肥 jar 自检 classpath、子进程相对路径、Vite 与 vue-cli 启动串台、',
    '  MyBatis-Plus NOT_NULL 导致 `fail_reason` 残留）与生成产物 Vue3 页面 3 处 Vue2 遗留',
    '  （`gen:end` 尾部标记、`GroupView` 的 `h(RouterView)`、`>>>` 深度选择器），已沉淀坑 65–69。', '',
    '**未做**：S11/S12 真实接入（需 Docker + Playwright 且产物内置 TestContainers / RestAssured /',
    'Playwright 测试骨架）；`autoVerifyGen` 保持默认关闭，避免日常生成刷测试记录。',
  ].map(t => mk(t, like))
  return [...out, ...block]
})
