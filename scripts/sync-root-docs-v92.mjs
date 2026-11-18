// V9.2 主文档同步：根 README / codegen-server README / 中文项目介绍 / ROADMAP / 规划文档。
// 幂等；按行处理并保留每行原有的 CRLF / LF，不把 CRLF 文件刷成 LF。
// 用法: node codegen-server/scripts/sync-root-docs-v92.mjs <monorepo根>
import fs from 'fs'
import path from 'path'

const ROOT = path.resolve(process.argv[2] || '.')
const read = f => fs.readFileSync(path.join(ROOT, f), 'utf8').split('\n')
const write = (f, L) => fs.writeFileSync(path.join(ROOT, f), Buffer.from(L.join('\n'), 'utf8'))
const eolOf = l => (l.endsWith('\r') ? '\r' : '')
const body = l => (l.endsWith('\r') ? l.slice(0, -1) : l)
const mk = (text, like) => text + eolOf(like)
// 插入的新行跟文件主导换行符走，而不是跟某一行（该行可能恰好是历史 LF 混排行）
const domEol = L => (L.filter(l => l.endsWith('\r')).length >= L.filter(l => !l.endsWith('\r')).length ? '\r' : '')

const V92_ROW_EN = '| **V9.2** | **Test Center & Verification Hub: merged `/test` case center (7 tables: env/suite/case/dataset/run/result/schedule) + `/tc/center` verification center (`tc_run` / `tc_case_result` / `tc_artifact`, 9 registered suites S1-S9, global single-run 409 lock, `codegen.testcenter/v1` JSON summary shared with CI). 373/373 tests (50 new), 206/206 locale keys, i18n contract 9/9, front-end build passed; `verify.sh` gate aligned (MIN_TESTS 312 + i18n + locales); generated output byte-identical** | V9.2 release, **current** |'
const V92_ROW_ZH = '| **V9.2** | **测试中心与验证中心：合并 `/test` 用例中心（7 张表）与 `/tc/center` 验证中心（tc_run / tc_case_result / tc_artifact，S1–S9 九个套件，全局单运行锁与 409，与 CI 共用的 `codegen.testcenter/v1` 摘要）；373/373 单测（新增 50 个）、206/206 语言键、i18n 契约 9/9、前端构建通过；verify.sh 门禁对齐（MIN_TESTS 312 + i18n + 语言键）；产物逐字节零差异** | V9.2 release, **当前版本** |'

const BANNER_EN = [
  '> **Current release — V9.2 (Test Center & Verification Hub) on top of V9.1 (maintainability)**',
  '> Two merged sub-modules: **/test** case center (environments / suites / cases / datasets / runs / results / schedules,',
  '> 7 tables) and **/tc/center** verification center (`tc_run` / `tc_case_result` / `tc_artifact`, 9 suites S1-S9,',
  '> a global single-run lock with HTTP 409, and a `codegen.testcenter/v1` JSON summary shared by CI).',
  '> Full suite **373/373 green** (50 new tests); i18n contract 9/9; 206/206 locale keys; console build passed.',
  '> `verify.sh` now gates i18n + locales and emits the same JSON summary; generated output stays byte-identical.',
  '>',
  '> **V9.1 — maintainability refactor (previous release)**',
]

function patch(file, fn) {
  const p = path.join(ROOT, file)
  if (!fs.existsSync(p)) return console.log('SKIP (missing)', file)
  const L = read(file)
  const out = fn(L)
  if (!out) return console.log('SKIP (already V9.2)', file)
  if (out.join('\n') === L.join('\n')) return console.log('SKIP (no change)', file)
  write(file, out)
  console.log('OK  ', file)
}

/** 通用：升级版本表、横幅、状态行、文档地图 */
function common(L, { zh = false } = {}) {
  if (L.some(l => l.includes('Current release — V9.2') || l.includes('**V9.2**'))) return null
  const out = L.slice()

  // 1) 顶部横幅：V9.1 首行替换为 V9.2 块 + V9.1 标题（英文主文档）
  const i = out.findIndex(l => body(l).startsWith('> **Current release — V9.1'))
  if (i >= 0) {
    const like = domEol(L)
    out.splice(i, 1, ...BANNER_EN.map(t => mk(t, like)))
  }

  // 2) 版本表：V9.1 行去掉 current，其后插入 V9.2 行
  const j = out.findIndex(l => /^\| \*\*V9\.1\*\*/.test(body(l)))
  if (j >= 0) {
    const b = body(out[j]).replace(/,\s*\*\*(current|当前版本)\*\*(\s*\|)\s*$/, ' $2')
    out[j] = mk(b, out[j])
    out.splice(j + 1, 0, mk(zh ? V92_ROW_ZH : V92_ROW_EN, out[j]))
  }

  // 3) 状态行 / 文档地图 / 版本区间
  for (let k = 0; k < out.length; k++) {
    let b = body(out[k])
    const orig = b
    if (b.includes('current released version is **V9.1**')) {
      b = b.replace('current released version is **V9.1**', 'current released version is **V9.2**')
    }
    if (b.startsWith('- The current released version is **V9.2**')) {
      b = '- The current released version is **V9.2** (Test Center + Verification Hub: `/test` case center with 7 tables and `/tc/center` verification center with 9 suites, structured run/case/artifact records and a CI-shared JSON summary). It builds on **V9.1** (byte-identical maintainability refactor), **V9.0** (Vue 3 + Element Plus + Vite generated artifacts), **V8.1** (quality gate) and **V8.0** (full-page i18n). Language support verified on 2026-11-13: 206/206 locales, 12 RTL, 8 translated word banks key-identical to zh-CN (60 keys each).'
    }
    b = b.replace(/V3\.1\s*→\s*V9\.1/g, 'V3.1 → V9.2')
    b = b.replace(/\(V8\.0 \/ V8\.1 \/ V9\.0 \/ V9\.1\)/g, '(V8.0 / V8.1 / V9.0 / V9.1 / V9.2)')
    b = b.replace(/当前规划版本线已达 \*\*V9\.1\*\*/g, '当前规划版本线已达 **V9.2**')
    if (b !== orig) out[k] = mk(b, out[k])
  }
  return out
}

patch('README.md', L => common(L))
patch('codegen-server/README.md', L => common(L))
patch('多语言项目介绍/README.md', L => common(L))
patch('多语言项目介绍/项目介绍.md', L => common(L, { zh: true }))

// ROADMAP：把 V9.2「需求阶段」整段替换为实现记录
patch('codegen-server/ROADMAP.md', L => {
  if (L.some(l => body(l).startsWith('## V9.2 —') && l.includes('已实现'))) return null
  const i = L.findIndex(l => body(l).startsWith('## V9.2 —'))
  if (i < 0) return null
  const like = domEol(L)
  const block = [
    '## V9.2 — 测试中心 + 验证中心（已实现，2026-11-13）',
    '',
    '> 用户拍板「两套合并」：工作区既有的接口/UI 用例中心（`test_*` 七表、`/test/*`）与本轮新增的',
    '> 验证资产收拢（`tc_*` 三表、`/tc/center`）作为同一版本的两个子模块交付，路由与菜单 id 互不冲突。',
    '> 完整需求基线见 `docs/代码生成器-V9.2-规划.md`；设计文档见 `docs/design/V9.2测试中心-设计.md`。',
    '',
    '**交付**',
    '- 用例中心：`docs/sql/V9.2__test_center.sql`（7 张表 + 菜单 5000–5040）、Controller/Service/执行器/断言引擎、前端 `views/test/index.vue`。',
    '- 验证中心：`docs/sql/V9.2__verification_center.sql`（tc_run / tc_case_result / tc_artifact + 菜单 3040–3043）、',
    '  `TcSuite` 注册表 S1–S9、`TestCenterExecutor`（CAS 单运行锁 / 串行 / 超时 kill / 取消 / 失败不短路）、',
    '  `TestCenterService` + `/tc/*` REST、前端 `views/verification/index.vue`、`codegen.testcenter/v1` 摘要契约。',
    '- 门禁对齐：`scripts/verify.sh` 升到 7 步（`MIN_TESTS` 240 → 312、新增 i18n 快照契约与 206 语言键），',
    '  日志改落 `target/testcenter/`（不再写 `/tmp`），末尾由 `scripts/testcenter-summary.mjs` 输出同一份 JSON 摘要。',
    '- 驱动脚本：`scripts/testcenter-suites.mjs`（9 套件，Windows `.cmd` / NODE_OPTIONS / JAVA_HOME 差异收敛）、',
    '  `scripts/check-all-locales.mjs`（206/206 键覆盖 + 8 精译 + 12 RTL）。',
    '',
    '**验证**',
    '- `mvn -o test` **373/373 全绿**（V9.2 新增 50 个单测：注册表 / 摘要契约 / 请求安全 / 路径白名单 / 执行器状态机 / 报告 / 存储 / 服务层）。',
    '- i18n 快照契约 **9/9**、i18n-core vitest **9 passed**、206 语言键 **206/206**、生成物 JS 1024 组合 0 坏、控制台 `npm run build` 成功。',
    '- 端到端：本机 MySQL 落两套 DDL（幂等重跑 2 次）、服务 8125 启动、登录 → `/tc/suites` 9 个 → `POST /tc/runs` 跑通 S4',
    '  → run/case/artifact 三层落库 → 并发第二次触发 **HTTP 409** → 夹带 `command` 字段 **HTTP 403**。',
    '- 修掉两个真问题：`ReentrantLock` 同线程可重入导致 409 失效（改 CAS）；`overview.activeRunId` 用 JSON number',
    '  下发 19 位雪花 ID 导致前端精度丢失、取消 404（改字符串，与 `BaseEntity` 口径一致）。',
    '- 产物零 diff：本版未改 `gen-templates` 与 8 个 Writer，新旧 jar 生成物对比沿用 V9.1 结论。',
    '',
    '**未做（按需求基线留到后续版本）**：S8 截图/构建自动化、S9 新旧 jar `diff -r` 自动化（P1，默认不勾选）、',
    '相邻 run 基线 diff、定时执行、把结果接成合并阻断门禁。',
  ].map(t => mk(t, like))
  return [...L.slice(0, i), ...block]
})

// 规划文档：状态行 + 第 13 节
patch('codegen-server/docs/代码生成器-V9.2-规划.md', L => {
  if (L.some(l => l.includes('状态：已实现'))) return null
  const out = L.slice()
  for (let k = 0; k < out.length; k++) {
    let b = body(out[k])
    const orig = b
    if (b.startsWith('> **状态：需求基线 / 待评审，尚未实现**')) {
      b = '> **状态：已实现（V9.2）｜M1–M3 完成，M4 的 P1 项按计划未自动化**　｜　建立 / 更新：2026-11-13　｜　承接：V9.1（巨型类拆分）'
    }
    if (b.startsWith('V9.2 **尚未实现**')) {
      b = 'V9.2 **已实现并升为当前版本**：README 版本历史表、ROADMAP「当前版本」、47+48 个语言 README 的当前版本标记已统一同步到 V9.2。'
    }
    if (b.startsWith('ROADMAP 仅在文末登记')) {
      b = 'ROADMAP 已改为 V9.2 实现记录；多语言同步走 `scripts/sync-version-rows-v92.mjs` 与 `scripts/sync-root-docs-v92.mjs`（幂等、保留 CRLF）。'
    }
    if (b !== orig) out[k] = mk(b, out[k])
  }
  const like = domEol(L)
  const tail = [
    '',
    '---',
    '',
    '## 14. 实现说明（2026-11-13）',
    '',
    '**方向调整**：用户拍板「两套合并为 V9.2」。工作区在需求基线之前已存在一套 `test_*` 七表的接口/UI 用例中心',
    '（`docs/design/V9.2测试中心-设计.md`），与本文档描述的「验证资产收拢」不是同一件事。合并后：',
    '',
    '| 子模块 | 路由 | 数据 | 页面 | 职责 |',
    '| --- | --- | --- | --- | --- |',
    '| 测试中心（用例中心） | `/test/*` | `test_env` / `test_suite` / `test_case` / `test_dataset` / `test_run` / `test_result` / `test_schedule` | `views/test/index.vue` | 业务接口与 UI 用例资产、执行器、断言 |',
    '| 验证中心 | `/tc/*` | `tc_run` / `tc_case_result` / `tc_artifact` | `views/verification/index.vue` | 平台自身验证套件 S1–S9、结构化留痕、报告 |',
    '',
    '两者表名、菜单 id（5000–5040 / 3040–3043）、REST 前缀均不冲突；DDL 分成两个幂等迁移脚本，',
    '避免覆盖已有文件。`codegen.testcenter/v1` 摘要是验证中心与 `verify.sh` 共用的唯一事实。',
    '',
    '**验收结果（对照第 10 节）**：373/373 单测（新增 50 个）、9/9 i18n 契约、206/206 语言键、1024 组合 0 坏、',
    '前端 build 成功、三层落库率 100%、并发 409、夹带 command 403、摘要契约字段级一致、HTML 报告可离线打开。',
    'S8/S9 按 P1 保持 SKIPPED 并如实记录，未伪造通过；M4 的截图预览与基线 diff 留待后续版本。',
  ]
  for (const line of tail) out.push(mk(line, like))
  return out
})
