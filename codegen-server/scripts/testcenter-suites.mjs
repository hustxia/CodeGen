#!/usr/bin/env node
/**
 * V9.2 验证中心套件驱动 —— 本地执行器与 CI 唯一共用的命令入口。
 *
 * 为什么要有它：Java 侧 ProcessBuilder 只调 `node testcenter-suites.mjs <suiteId>`，
 * 把 Windows 的 .cmd / NODE_OPTIONS / JAVA_HOME / 路径分隔符差异全收在这一处。
 * 坑库「测试与基线 #6」：Node 18+ 在 Windows 上直接 spawn .cmd 会 EINVAL，必须 shell:true；
 * Git Bash 风格的 /d/tool/... 路径也要转成 D:/tool/...。
 *
 * 用法：node scripts/testcenter-suites.mjs S1
 * 输出：子进程日志原样转发到 stdout（Java 侧重定向到 run 的日志文件），
 *       最后一行固定为 `TC_METRICS {json}`，供执行器写入 tc_case_result.metrics_json。
 * 退出码：0 = PASSED / SKIPPED，1 = FAILED，2 = 未知套件。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const WEB = process.env.TC_WEB_ROOT || path.resolve(ROOT, '..', 'codegen-web')
const WIN = process.platform === 'win32'
const SUITE_ID = String(process.argv[2] || '').toUpperCase()

const normalizeBin = v => (v || '').replace(/^\/([a-zA-Z])\//, (_, d) => d.toUpperCase() + ':/')
const cmdName = name => (WIN ? name + '.cmd' : name)
const exists = p => { try { return fs.existsSync(p) } catch { return false } }

/** JDK：本机 java 不在 PATH，Maven/Javac 都靠它；CI 上一般已有 JAVA_HOME */
function javaHome() {
  const candidates = [process.env.JAVA_HOME, 'D:/tool/jdk-21.0.12.1+1', 'D:/tool/temurin-21-fresh']
  for (const c of candidates) {
    if (!c) continue
    const jh = normalizeBin(c)
    if (exists(path.join(jh, 'bin', WIN ? 'java.exe' : 'java'))) return jh
  }
  return ''
}
const JAVA_HOME = javaHome()
const javaBin = name => (JAVA_HOME ? path.join(JAVA_HOME, 'bin', WIN ? name + '.exe' : name) : name)

function mavenBin() {
  if (process.env.MAVEN_BIN) return normalizeBin(process.env.MAVEN_BIN)
  if (WIN) {
    const local = 'D:/tool/apache-maven-3.6.3/bin/' + cmdName('mvn')
    if (exists(local) || exists(local.replace(/\.cmd$/, ''))) return local
  }
  return cmdName('mvn')
}

function freemarkerJar() {
  if (process.env.FREEMARKER_JAR) return normalizeBin(process.env.FREEMARKER_JAR)
  const builtin = path.join(ROOT, 'scripts', 'i18n_snapshot', 'freemarker.jar')
  if (exists(builtin)) return builtin
  const m2 = path.join(process.env.USERPROFILE || process.env.HOME || '', '.m2', 'repository',
    'org', 'freemarker', 'freemarker')
  if (exists(m2)) {
    for (const ver of fs.readdirSync(m2)) {
      const jar = path.join(m2, ver, `freemarker-${ver}.jar`)
      if (exists(jar)) return jar
    }
  }
  return ''
}

/** 起子进程：shell:true 专治 Windows .cmd；NODE_OPTIONS 必须清空（AI 宿主 fs shim 坑） */
function run(label, cmd, args, cwd, extraEnv = {}) {
  return new Promise(resolve => {
    const env = { ...process.env, NODE_OPTIONS: '', ...extraEnv }
    if (JAVA_HOME) {
      env.JAVA_HOME = JAVA_HOME
      env.PATH = JAVA_HOME + '/bin' + (WIN ? ';' : ':') + (env.PATH || '')
    }
    console.log(`\n$ [${label}] ${cmd} ${args.join(' ')}   (cwd=${cwd})`)
    let output = ''
    let child
    try {
      child = spawn(cmd, args, { cwd, env, shell: WIN, windowsHide: true })
    } catch (err) {
      console.error('spawn 失败：' + err.message)
      return resolve({ code: -1, output: err.message })
    }
    const onData = chunk => {
      const s = chunk.toString()
      output += s
      process.stdout.write(s)
    }
    child.stdout && child.stdout.on('data', onData)
    child.stderr && child.stderr.on('data', onData)
    child.on('error', err => { output += '\n' + err.message; process.stdout.write(err.message + '\n') })
    child.on('close', code => resolve({ code: code == null ? -1 : code, output }))
  })
}

/** 统计 surefire XML 的用例数（与 verify.sh 的 <testcase 口径一致） */
function surefireCounts() {
  const dir = path.join(ROOT, 'target', 'surefire-reports')
  if (!exists(dir)) return { tests: 0, failures: 0, errors: 0 }
  let tests = 0, failures = 0, errors = 0
  for (const f of fs.readdirSync(dir).filter(x => x.endsWith('.xml'))) {
    const text = fs.readFileSync(path.join(dir, f), 'utf8')
    tests += (text.match(/<testcase /g) || []).length
    failures += (text.match(/<failure /g) || []).length
    errors += (text.match(/<error /g) || []).length
  }
  return { tests, failures, errors }
}

const ok = (metrics, note) => ({ status: 'PASSED', metrics, note })
const bad = (metrics, note) => ({ status: 'FAILED', metrics, note })
const skip = reason => ({ status: 'SKIPPED', metrics: {}, note: reason })

const SUITES = {
  /** S1 后端单测：先清旧报告再跑，避免把上次的 testcase 数累加进来 */
  async S1() {
    const reports = path.join(ROOT, 'target', 'surefire-reports')
    try { fs.rmSync(reports, { recursive: true, force: true }) } catch { /* 目录被 IDE 锁住时不阻断 */ }
    const r = await run('mvn -o test', mavenBin(), ['-o', 'test'], ROOT)
    const c = surefireCounts()
    const metrics = { ...c, minTests: 312 }
    if (r.code !== 0 || c.tests < 312 || c.failures > 0 || c.errors > 0) {
      return bad(metrics, `mvn 退出码 ${r.code}，${c.tests}/312 用例，失败 ${c.failures}`)
    }
    return ok(metrics, `${c.tests}/312 用例通过`)
  },

  /** S2 i18n 快照契约：离线 javac + java 跑 9 模式 OFF/ON 零 diff */
  async S2() {
    const jar = freemarkerJar()
    if (!jar) return bad({}, '找不到 freemarker-2.3.32.jar（设置 FREEMARKER_JAR 或放到 scripts/i18n_snapshot/）')
    const src = path.join(ROOT, 'scripts', 'i18n_snapshot')
    const out = path.join(ROOT, 'target', 'i18n-harness')
    fs.mkdirSync(out, { recursive: true })
    const javac = await run('javac', javaBin('javac'),
      ['-cp', jar, '-d', out, 'VuePageI18nContract.java'], src)
    if (javac.code !== 0) return bad({}, 'harness 编译失败')
    const cp = [jar, out].join(path.delimiter)
    const r = await run('java', javaBin('java'),
      ['-Dfile.encoding=UTF-8',
        '-Dftl.dir=' + path.join(ROOT, 'src', 'main', 'resources', 'gen-templates'),
        '-cp', cp, 'i18n_snapshot.VuePageI18nContract'], src)
    // 只数 PASS/FAIL 行，不解析「N/M 模式通过」这句中文：
    // Windows 控制台默认 GBK，中文文案在 UTF-8 解码后是乱码，按中文正则会误判成 FAILED。
    const passedCount = (r.output.match(/^PASS\s\s/gm) || []).length
    const failedCount = (r.output.match(/^FAIL\s\s/gm) || []).length
    const metrics = { patterns: 9, passed: passedCount, failed: failedCount }
    if (r.code !== 0 || passedCount !== 9 || failedCount !== 0) {
      return bad(metrics, `i18n 快照契约未全绿（退出码 ${r.code}，PASS ${passedCount}/9，FAIL ${failedCount}）`)
    }
    return ok(metrics, `${passedCount}/9 模式通过`)
  },

  /** S3 i18n-core 回落链 vitest */
  async S3() {
    if (!exists(WEB)) return bad({}, '找不到 codegen-web：' + WEB)
    const r = await run('vitest', cmdName('npx'), ['vitest', 'run', 'tests/i18n-core.test.mjs'], WEB)
    // vitest 输出带 ANSI 颜色码，且先给「Test Files 1 passed」再给「Tests 9 passed」。
    // 先剥色，再取所有 "N passed" 中的最大值，避免把文件数当成用例数。
    const clean = r.output.replace(/\x1b\[[0-9;]*m/g, '')
    const nums = [...clean.matchAll(/(\d+) passed/g)].map(m => Number(m[1]))
    const metrics = { tests: nums.length ? Math.max(...nums) : 0 }
    if (r.code !== 0) return bad(metrics, 'vitest 退出码 ' + r.code)
    return ok(metrics, `${metrics.tests} passed`)
  },

  /** S4 206 语言键覆盖 */
  async S4() {
    const r = await run('check-all-locales', process.execPath,
      [path.join(ROOT, 'scripts', 'check-all-locales.mjs')], ROOT)
    const line = [...r.output.split(/\r?\n/)].reverse().find(l => l.includes('TC_METRICS '))
    let metrics = {}
    try { metrics = JSON.parse(line.slice(line.indexOf('TC_METRICS ') + 11)) } catch { /* 解析失败只影响指标 */ }
    if (r.code !== 0) return bad(metrics, '206 语言键覆盖未通过')
    return ok(metrics, `${metrics.covered}/${metrics.locales} 语言`)
  },

  /** S5 生成物 JS 语法（依赖 S1 产出的 target/gen-matrix） */
  async S5() {
    const manifest = path.join(ROOT, 'target', 'gen-matrix', 'manifest.json')
    if (!exists(manifest)) return skip('缺少 target/gen-matrix/manifest.json（需先跑 S1 的 GenCombinationMatrixTest）')
    const r = await run('check-generated-js', process.execPath,
      [path.join(ROOT, 'scripts', 'check-generated-js.mjs')], ROOT)
    const m = r.output.match(/组合数\s*(\d+).*?坏组合\s*(\d+)/s)
    const metrics = { combinations: m ? Number(m[1]) : 0, badCombinations: m ? Number(m[2]) : -1 }
    if (r.code !== 0 || metrics.badCombinations > 0) return bad(metrics, '生成物存在坏组合')
    return ok(metrics, `${metrics.combinations} 组合 0 坏`)
  },

  /** S6 控制台前端构建 */
  async S6() {
    if (!exists(WEB)) return bad({}, '找不到 codegen-web：' + WEB)
    const r = await run('npm run build', cmdName('npm'), ['run', 'build'], WEB, { NODE_OPTIONS: '' })
    if (r.code !== 0) return bad({ build: 'failed' }, '前端构建失败')
    return ok({ build: 'success' }, 'build success')
  },

  /** S7 空库冒烟：没有 DB 凭据时如实标 SKIPPED，绝不静默当成通过 */
  async S7() {
    const pwd = process.env.CG_DB_PASSWORD
    if (!pwd) return skip('未设置 CG_DB_PASSWORD，按需求标 SKIPPED（本机 MySQL 可用时设置后即可跑）')
    const r = await run('smoke-fresh-db', process.execPath,
      [path.join(ROOT, 'scripts', 'smoke-fresh-db.mjs')], ROOT, { CG_DB_PASSWORD: pwd })
    if (r.code !== 0) return bad({ smoke: 'failed' }, '空库冒烟失败')
    return ok({ smoke: 'passed' }, 'schema + 幂等重跑通过')
  },

  /** S8/S9 是 P1 人工链路：如实标 SKIPPED，不伪造通过 */
  async S8() {
    return skip('P1 未自动化：需要 API 生成 + vite build + Chrome/Puppeteer 双服务链路（V9.2 默认不勾选）')
  },
  async S9() {
    return skip('P1 未自动化：需要新旧两个 jar 并行 + 同 DTO 双变体 diff -r（V9.2 默认不勾选）')
  }
}

async function main() {
  if (!SUITE_ID || !SUITES[SUITE_ID]) {
    console.error(`未知套件 ${SUITE_ID || '(空)'}，可用：${Object.keys(SUITES).join(',')}`)
    process.exit(2)
  }
  let result
  try {
    result = await SUITES[SUITE_ID]()
  } catch (e) {
    result = bad({ error: String(e.message || e) }, '套件驱动异常：' + (e.message || e))
  }
  const metrics = { suiteId: SUITE_ID, status: result.status, ...result.metrics }
  if (result.status === 'SKIPPED') metrics.reason = result.note
  console.log('TC_METRICS ' + JSON.stringify(metrics))
  console.log(`RESULT ${result.status} ${SUITE_ID}：${result.note}`)
  process.exit(result.status === 'FAILED' ? 1 : 0)
}

main()
