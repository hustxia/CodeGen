#!/usr/bin/env node
/**
 * smoke-v9.17-19.mjs —— V9.17~V9.19「能力包深度 / 质量可维护 / 建模收尾」冒烟测试
 *
 * 覆盖昨天交付的三批功能（与 smoke-v9.16.mjs 互补，后者测 Docker/H2/演示页）：
 *   A. V9.17 能力包：支付（JSAPI/H5/退款/pay_code）、工作流（进度图/会签/设计器）、
 *      打印（金额大写/CODE128 条码/二维码占位）、安全（限流/数据权限/密码策略）、
 *      唯一性（400 前后端对齐）—— 静态契约：模板/SQL/测试类/设计文档齐全
 *   B. V9.18 质量：模板影响面端点、child_save_mode 幂等加列、S8/S9 验证中心脚本
 *      —— 工具链实跑：dir-diff 单测 9/9
 *   C. V9.19 建模：DDL 分批、i18n 12 语言 × 60 键、模板市场端点与防穿越校验
 *      —— 工具链实跑：i18n-coverage --json 断言 12 语言 100%
 *   D. 动态冒烟（--with-server）：新 jar 起 H2 + 新增 GET 端点（模板清单/影响面/模板市场）
 *
 * 用法：
 *   node scripts/smoke-v9.17-19.mjs                         # 静态 + 工具链
 *   node scripts/smoke-v9.17-19.mjs --with-server           # 再加 H2 动态端点
 *   CG_JAVA_HOME="D:/tool/jdk-21.0.12.1+1" node scripts/smoke-v9.17-19.mjs --with-server
 *
 * 注意：动态层要求 jar 含 V9.18/19 新类（先 mvn package -DskipTests）；
 *       脚本会自动检测，缺类时 FAIL 并提示重新打包，避免测到旧 jar 假绿。
 */
import { readFileSync, readdirSync, existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { spawn, spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(__dirname, '..')
const LOG_DIR = path.join(ROOT, 'logs', 'smoke')
const JAVA_HOME = process.env.CG_JAVA_HOME || process.env.JAVA_HOME || 'D:/tool/jdk-21.0.12.1+1'
const PORT = '8131'
const argv = process.argv.slice(2)
const WITH_SERVER = argv.includes('--with-server')

const lines = []
const say = (s) => { console.log(s); lines.push(s) }
let failures = 0, passes = 0
const ok = (s) => { passes++; say(`  PASS  ${s}`) }
const bad = (s) => { failures++; say(`  FAIL  ${s}`) }
const assert = (cond, msg) => { cond ? ok(msg) : bad(msg) }
const section = (s) => say(`\n== ${s} ==`)
const read = (p) => readFileSync(path.join(ROOT, p), 'utf8').replace(/^\uFEFF/, '')
const exists = (p) => existsSync(path.join(ROOT, p))

function finish(code) {
  mkdirSync(LOG_DIR, { recursive: true })
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const file = path.join(LOG_DIR, `smoke-v9.17-19-${stamp}.md`)
  writeFileSync(file, `# V9.17~V9.19 冒烟测试 ${new Date().toLocaleString('zh-CN')}\n\n` +
    `- ${passes} PASS / ${failures} FAIL\n- 动态层：${WITH_SERVER ? '启用' : '未启用（--with-server）'}\n\n` +
    `\`\`\`\n${lines.join('\n')}\n\`\`\`\n`, 'utf8')
  say(`\nRESULT ${failures === 0 ? 'PASS' : 'FAIL'}  (${passes} PASS / ${failures} FAIL)`)
  say(`REPORT ${file}`)
  process.exit(failures === 0 && code === 0 ? 0 : 1)
}

say(`V9.17~V9.19 冒烟测试（静态+工具链${WITH_SERVER ? '+动态' : ''}）root=${ROOT}`)

// ============================================================
// A. V9.17 能力包（静态契约）
// ============================================================
section('A. V9.17 能力包（静态契约）')
// 支付：模板 + scaffold-pay + 退款状态机 + pay_code SQL
const payTpls = ['payApi.ftl', 'payBizApi.ftl', 'payCashier.ftl', 'payOrderPage.ftl']
const payMissing = payTpls.filter((f) => !exists(`src/main/resources/gen-templates/${f}`))
assert(payMissing.length === 0, `支付模板 4 个齐全${payMissing.length ? `（缺 ${payMissing.join(',')}）` : ''}`)
assert(exists('src/main/resources/scaffold-pay/AbstractPayService.java'), 'scaffold-pay 支付骨架存在')
const paySvc = read('src/main/resources/scaffold-pay/AbstractPayService.java')
assert(/退款金额必须大于 0/.test(paySvc) && /退款金额超过可退余额/.test(paySvc), '支付退款金额校验（>0 与余额上限）')
assert(/refundNo\.equals\(order\.getRefundNo\(\)\)/.test(paySvc), '退款同号重放幂等短路')
assert(/linkBusiness\(order,\s*1\)/.test(paySvc), '支付成功联动业务表 pay_status')
const cashier = read('src/main/resources/gen-templates/payCashier.ftl')
assert(/QRCode\.toDataURL|qrcode/.test(cashier), '收银台二维码使用 qrcode 库真出码')
assert(/pay_code/.test(read('docs/sql/V9.17__pay_code.sql')), 'V9.17 pay_code SQL 存在')
// 工作流：模板 + Writer + 会签
const flowTpls = ['flowApi.ftl', 'flowTodo.ftl', 'flowDesigner.ftl']
const flowMissing = flowTpls.filter((f) => !exists(`src/main/resources/gen-templates/${f}`))
assert(flowMissing.length === 0, `工作流模板 3 个齐全${flowMissing.length ? `（缺 ${flowMissing.join(',')}）` : ''}`)
assert(exists('src/main/java/com/codegen/service/impl/ScaffoldFlowWriter.java'), '工作流 Writer 存在')
assert(/ALL|ANY|VETO/.test(read('src/main/resources/gen-templates/flowTodo.ftl')), '会签策略 ALL/ANY/VETO 模板存在')
// 打印：金额大写 + CODE128 条码 + 二维码占位
const print = read('src/main/resources/gen-templates/printPage.ftl')
assert(/CODE128|code128/.test(print), '打印模板含 CODE128 条码实现')
assert(/大写|rmb|RMB/.test(print), '打印模板含金额大写逻辑')
assert(/QR/.test(print) && !/QRCode\.toDataURL/.test(print), '打印二维码为占位（守零依赖红线）')
assert(/printTitle/.test(print), '打印模板含 printTitle 标题')
// 安全 + 唯一性
assert(/rate|limit|429/.test(read('src/main/java/com/codegen/service/impl/ScaffoldAuthWriter.java')), '鉴权 Writer 含限流逻辑')
assert(/UNIQUE_CONFLICT_MSG|unique\(/.test(read('src/main/resources/gen-templates/controller.ftl')), 'controller 唯一冲突 400 工厂')
assert(/\{field:\s*"\$\{c\.javaField\}"/.test(read('src/main/resources/gen-templates/saveDto.ftl')), 'saveDto 声明 data.field 字段定位')
assert(/body\.code !== 400/.test(read('src/main/resources/gen-templates/vuePage.ftl')), 'vuePage 前端 400 错误体解析')
// 契约测试类 + 设计文档
const v917Tests = ['PayRefundContractTest.java', 'V917PrintEnhanceTest.java', 'V917UniquenessAlignTemplateTest.java', 'GenV64SecurityHardeningTest.java']
// 递归收集测试文件名，避免路径写死误判
const testTree = []
const walk = (dir, rel) => {
  if (!existsSync(path.join(ROOT, rel))) return
  for (const e of readdirSync(path.join(ROOT, rel), { withFileTypes: true })) {
    if (e.isDirectory()) walk(dir, `${rel}/${e.name}`)
    else testTree.push(e.name)
  }
}
walk(ROOT, 'src/test/java')
const v917Missing = v917Tests.filter((f) => !testTree.includes(f))
assert(v917Missing.length === 0, `V9.17 契约测试类 4 个齐全${v917Missing.length ? `（缺 ${v917Missing.join(',')}）` : ''}`)
const designDir = path.join(ROOT, 'docs/design')
const designDocs = existsSync(designDir) ? readdirSync(designDir).filter((f) => f.startsWith('V9.17') && f.endsWith('.md')) : []
assert(designDocs.length >= 5, `V9.17 设计文档 >= 5 份（实际 ${designDocs.length}）`)

// ============================================================
// B. V9.18 质量（静态契约 + dir-diff 单测实跑）
// ============================================================
section('B. V9.18 质量（静态契约 + 工具链）')
assert(exists('src/main/java/com/codegen/support/ChildSavePlanner.java'), 'ChildSavePlanner 三路合并规划器存在')
const planner = read('src/main/java/com/codegen/support/ChildSavePlanner.java')
assert(/plan\(|planUpsert\(|planAssociation\(/.test(planner), 'ChildSavePlanner 含 plan/upsert/association 三种规划')
assert(exists('src/main/java/com/codegen/support/GenTemplateImpactSupport.java'), '模板影响面 Support 存在')
assert(/@GetMapping\("\/files"\)/.test(read('src/main/java/com/codegen/controller/GenTemplateController.java'))
  && /@GetMapping\("\/impact"\)/.test(read('src/main/java/com/codegen/controller/GenTemplateController.java')), '模板影响面 GET /files 与 /impact 端点存在')
// child_save_mode SQL 三处齐全（docs / docker init / h2）
const childSqlPaths = ['docs/sql/V9.18__child_save_mode.sql', 'scripts/docker/init/036_V9.18__child_save_mode.sql', 'src/main/resources/db/h2/V9.18__child_save_mode.sql']
const childMissing = childSqlPaths.filter((p) => !exists(p))
assert(childMissing.length === 0, `child_save_mode SQL 三处齐全（docs/docker/H2）${childMissing.length ? ` 缺=${childMissing.join(',')}` : ''}`)
assert(/child_save_mode/.test(read(childSqlPaths[0])) && /IF NOT EXISTS|information_schema/.test(read(childSqlPaths[0])), 'child_save_mode SQL 幂等加列')
const verify = read('.github/workflows/verify.yml')
assert(/verify-center-gate/.test(verify), 'verify.yml 含 V9.18 verify-center-gate 门禁')
assert(/branches: \[main, master, dev, xyh_dev\]/.test(verify), 'verify.yml 保留 main 触发（历史坑）')
assert(exists('scripts/dir-diff.mjs') && exists('scripts/shot-verify.mjs') && exists('scripts/verify-center-cli.mjs'), 'S8 截图/S9 diff/验证中心 CLI 脚本齐全')
// 工具链实跑：dir-diff 单测
const dd = spawnSync(process.execPath, ['--test', 'scripts/dir-diff.test.mjs'], { cwd: ROOT, encoding: 'utf8' })
const ddOut = (dd.stdout || '') + (dd.stderr || '')
const ddPass = (ddOut.match(/^# pass (\d+)/m) || [])[1]
const ddFail = (ddOut.match(/^# fail (\d+)/m) || [])[1]
assert(dd.status === 0 && Number(ddPass) >= 9 && Number(ddFail) === 0, `dir-diff 单测实跑 ${ddPass} PASS / ${ddFail} FAIL`)

// ============================================================
// C. V9.19 建模（静态契约 + i18n 覆盖矩阵实跑）
// ============================================================
section('C. V9.19 建模（静态契约 + 工具链）')
assert(exists('src/main/java/com/codegen/support/DdlBatchPlanner.java'), 'DdlBatchPlanner 分批规划器存在')
assert(/split\(|snippet\(/.test(read('src/main/java/com/codegen/support/DdlBatchPlanner.java')), 'DdlBatchPlanner 含 split/snippet')
assert(/db-batch-size|dbBatchSize/.test(read('src/main/java/com/codegen/config/GenPipelineProperties.java')), 'GenPipelineProperties 含 db-batch-size 配置')
assert(exists('src/main/java/com/codegen/support/TemplatePackValidator.java'), '模板市场校验器存在')
const validator = read('src/main/java/com/codegen/support/TemplatePackValidator.java')
assert(/IDENTIFIER_REGEX/.test(validator) && /validateSize/.test(validator), '模板市场含标识 regex 与体积校验（防穿越基础）')
const packCtrl = read('src/main/java/com/codegen/controller/GenTemplatePackController.java')
for (const ep of ['/list', '/export', '/import', '/{key}']) {
  assert(packCtrl.includes(`"${ep}"`), `模板市场端点 ${ep} 存在`)
}
const i18nContract = read('.github/workflows/i18n-contract.yml')
assert(/backend-i18n-coverage/.test(i18nContract) && /scripts\/i18n-coverage\.mjs/.test(i18nContract),
  'i18n-contract.yml 含 backend-i18n-coverage 门禁 job')
// H2 脚本不得为空：spring.sql.init 对空脚本抛 IllegalArgumentException（V9.19 踩坑）
const h2FilesAll = readdirSync(path.join(ROOT, 'src/main/resources/db/h2')).filter((f) => f.endsWith('.sql'))
const emptyH2 = h2FilesAll.filter((f) => {
  const body = read(`src/main/resources/db/h2/${f}`).split('\n').filter((l) => l.trim() && !l.trim().startsWith('--')).join('')
  return !body.trim()
})
assert(emptyH2.length === 0, `H2 脚本无空文件${emptyH2.length ? `（空：${emptyH2.join(',')}）` : ''}`)
// 磁盘上所有版本脚本必须纳入 application-h2.yml 的 schema-locations
// （V9.17 print_enhance.sql 曾漏配，导致 gen_table.print_title 缺失、impact 端点 500）
const h2Yml = read('src/main/resources/application-h2.yml')
const locs = [...h2Yml.matchAll(/classpath:db\/h2\/([^,\s]+\.sql)/g)].map((m) => m[1])
const versionScripts = h2FilesAll.filter((f) => /^V\d+\.\d+__.*\.sql$/.test(f))
const unlisted = versionScripts.filter((f) => !locs.includes(f))
assert(unlisted.length === 0, `H2 版本脚本全部纳入 schema-locations${unlisted.length ? `（漏配 ${unlisted.join(',')}）` : ''}`)
// 工具链实跑：i18n-coverage --json，断言 12 语言 × 60 键 100%
const ic = spawnSync(process.execPath, ['scripts/i18n-coverage.mjs', '--json'], { cwd: ROOT, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 })
let coverage = null
try { coverage = JSON.parse((ic.stdout || '').trim()) } catch { /* 输出非 JSON */ }
if (!coverage) {
  bad(`i18n 覆盖矩阵脚本执行失败：${((ic.stderr || ic.stdout || '').split('\n').slice(-3).join(' '))}`)
} else {
  const rows = coverage.locales || coverage.results || []
  assert(rows.length >= 12, `i18n 覆盖语言数 >= 12（实际 ${rows.length}）`)
  const allFull = rows.every((r) => r.keyCount >= 60 && r.coverage === 100 && r.pass)
  assert(allFull, `i18n 12 语言 × 60 键 100% 覆盖${allFull ? '' : `（未达标：${rows.filter((r) => !r.pass).map((r) => r.locale).join(',')}）`}`)
  const newLocales = ['ru-RU', 'pt-BR', 'id-ID', 'vi-VN']
  const haveNew = newLocales.every((l) => exists(`src/main/resources/i18n/messages/${l}.json`))
  assert(haveNew, `V9.19 新增 4 语言包齐全（${newLocales.join('/')}）`)
}

// ============================================================
// D. 动态冒烟：新 jar 起 H2，验证 V9.18/19 新增端点
// ============================================================
if (WITH_SERVER) {
  section('D. H2 动态冒烟（新增端点）')
  const javaCandidate = path.join(JAVA_HOME, 'bin', 'java.exe')
  const java = existsSync(javaCandidate) ? javaCandidate : path.join(JAVA_HOME, 'bin', 'java')
  const jar = path.join(ROOT, 'target/codegen-server.jar')
  if (!existsSync(jar) || !existsSync(java)) {
    bad(`动态冒烟前置缺失：jar=${existsSync(jar)} java=${existsSync(java)}（设 CG_JAVA_HOME）`)
  } else {
    // jar 版本守卫：必须含 V9.18/19 新类，否则动态断言无意义
    const zipCheck = spawnSync(process.execPath, ['-e', `
      const fs=require('fs'),z=require('zlib');
      const b=fs.readFileSync(process.argv[1]);
      const s=b.toString('latin1');
      const names=['ChildSavePlanner.class','DdlBatchPlanner.class','TemplatePackValidator.class'];
      for(const n of names){ if(!s.includes(n)){ console.log('MISSING:'+n); process.exit(1);} }
      console.log('OK');
    `, jar], { encoding: 'utf8' })
    const jarFresh = zipCheck.status === 0 && /OK/.test(zipCheck.stdout || '')
    assert(jarFresh, `jar 含 V9.18/19 新类${jarFresh ? '' : `（${(zipCheck.stdout || '').trim()}，先 mvn -o -q package -DskipTests）`}`)
    if (jarFresh) {
      const child = spawn(java, ['-jar', jar, '--spring.profiles.active=h2', `--server.port=${PORT}`],
        { cwd: ROOT, stdio: ['ignore', 'pipe', 'pipe'] })
      let out = ''
      child.stdout.on('data', (d) => { out += d.toString() })
      child.stderr.on('data', (d) => { out += d.toString() })
      const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
      const fetchJson = async (url, opts) => {
        const resp = await fetch(url, { signal: AbortSignal.timeout(3000), ...opts })
        return { status: resp.status, body: await resp.json().catch(() => null) }
      }
      try {
        let up = false
        for (let i = 0; i < 50; i++) {
          await sleep(1000)
          try {
            const r = await fetchJson(`http://127.0.0.1:${PORT}/actuator/health`)
            if (r.status === 200 && r.body?.status === 'UP') { up = true; break }
          } catch { /* 启动中 */ }
          if (child.exitCode !== null) break
        }
        assert(up, `H2 服务 50s 内 health=UP${up ? '' : `（退出码 ${child.exitCode}）`}`)
        if (up) {
          const lr = await fetchJson(`http://127.0.0.1:${PORT}/auth/login`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: 'admin', password: 'admin123' }),
          })
          const token = lr.body?.data?.token
          assert(!!token, 'admin 登录拿到 token')
          const authH = { Authorization: `Bearer ${token}` }
          for (const [ep, label] of [
            ['/gen/template/files', 'V9.18 模板清单'],
            ['/gen/template/impact', 'V9.18 模板影响面'],
            ['/gen/template-pack/list', 'V9.19 模板市场清单'],
          ]) {
            const r = await fetchJson(`http://127.0.0.1:${PORT}${ep}`, { headers: authH })
            assert(r.status === 200 && r.body?.code === 200, `${label} GET ${ep} 返回 200（HTTP ${r.status}, code ${r.body?.code}）`)
          }
        }
      } catch (e) {
        bad(`动态冒烟异常：${e.message}`)
      } finally {
        try { child.kill('SIGTERM') } catch { /* 已退出 */ }
        await sleep(500)
        if (child.exitCode === null) { try { child.kill('SIGKILL') } catch { /* 已退出 */ } }
      }
      const tail = out.split('\n').filter((l) => /ERROR|Exception|Caused by|Failed/i.test(l)).slice(-6)
      if (tail.length) say(`  INFO  启动日志错误摘要：\n    ${tail.join('\n    ')}`)
    }
  }
} else {
  section('D. 动态冒烟')
  say('  SKIP  未加 --with-server（需先 mvn package，再加该参数跑新端点）')
}

finish(0)
