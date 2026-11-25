#!/usr/bin/env node
/**
 * V9.18 验证中心 headless 入口 —— CI 合并门禁与本地一键复跑共用。
 *
 * 与页面执行器的关系：两者都调 `scripts/testcenter-suites.mjs <suiteId>`，
 * 保证「页面上跑的结果」与「CI 上跑的结果」是同一套判定逻辑，不存在第二套引擎。
 *
 * 用法：
 *   node scripts/verify-center-cli.mjs                 # 默认跑 S8,S9
 *   node scripts/verify-center-cli.mjs --dry-run       # 只打印将执行什么，不落地
 *   node scripts/verify-center-cli.mjs --suites S8,S9 --new-dir <目录> --baseline <目录>
 *
 * 退出码：0 = 无 FAIL；1 = 存在 FAIL（门禁阻断）；2 = 参数错误。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
export const DEFAULT_SUITES = ['S8', 'S9']
const SUITE_TIMEOUT_MS = Number(process.env.TC_SUITE_TIMEOUT_MS || 600000)

/** 解析 --key value 形式的参数；未识别的 -- 参数直接报错，避免拼错后静默跑默认套件 */
export function parseArgs(argv) {
  const out = { suites: [], dryRun: false }
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i]
    const val = () => argv[++i]
    switch (key) {
      case '--suites': out.suites = String(val() || '').split(',').map(s => s.trim().toUpperCase()).filter(Boolean); break
      case '--dry-run': out.dryRun = true; break
      case '--out': out.out = val(); break
      case '--new-dir': out.newDir = val(); break
      case '--baseline': out.baselineDir = val(); break
      default:
        if (key.startsWith('--')) throw new Error('未知参数 ' + key)
        throw new Error('多余参数 ' + key)
    }
  }
  if (!out.suites.length) out.suites = [...DEFAULT_SUITES]
  return out
}

/** 从驱动日志里取最后一条 RESULT 行；没有则按退出码推断 */
export function parseSuiteStatus(output, exitCode) {
  const line = [...String(output || '').split(/\r?\n/)].reverse()
    .find(l => l.trim().startsWith('RESULT '))
  if (line) {
    const [, status] = line.trim().split(/\s+/, 3)
    if (status === 'PASSED' || status === 'FAILED' || status === 'SKIPPED') return status
  }
  return exitCode === 0 ? 'SKIPPED' : 'FAILED'
}

function stamp() {
  return new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
}

/** 跑单个套件，边跑边转发输出，返回 {suiteId,status,output,code,ms} */
function runSuite(suiteId) {
  return new Promise((resolve) => {
    const started = Date.now()
    const child = spawn(process.execPath, [path.join(ROOT, 'scripts', 'testcenter-suites.mjs'), suiteId], {
      cwd: ROOT,
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true
    })
    let output = ''
    const collect = (chunk) => {
      const text = chunk.toString()
      output += text
      process.stdout.write(text)
    }
    child.stdout.on('data', collect)
    child.stderr.on('data', collect)
    const timer = setTimeout(() => {
      collect(Buffer.from(`\n[verify-center-cli] ${suiteId} 超时 ${SUITE_TIMEOUT_MS}ms，强制结束\n`))
      try { child.kill('SIGKILL') } catch { /* 进程已退出 */ }
    }, SUITE_TIMEOUT_MS)
    child.on('close', (code) => {
      clearTimeout(timer)
      resolve({ suiteId, status: parseSuiteStatus(output, code), output, code, ms: Date.now() - started })
    })
    child.on('error', (e) => {
      clearTimeout(timer)
      resolve({ suiteId, status: 'FAILED', output: output + '\n' + e.message, code: -1, ms: Date.now() - started })
    })
  })
}

function suiteMetrics(output) {
  const line = [...String(output || '').split(/\r?\n/)].reverse()
    .find(l => l.trim().startsWith('TC_METRICS '))
  if (!line) return {}
  try { return JSON.parse(line.trim().slice('TC_METRICS '.length)) } catch { return {} }
}

function suiteArtifacts(output) {
  const out = []
  for (const line of String(output || '').split(/\r?\n/)) {
    const t = line.trim()
    if (!t.startsWith('TC_ARTIFACTS ')) continue
    try {
      const arr = JSON.parse(t.slice('TC_ARTIFACTS '.length))
      if (Array.isArray(arr)) out.push(...arr)
    } catch { /* 忽略坏行 */ }
  }
  return [...new Set(out)]
}

function printableConfig(args, browserLib) {
  return {
    serverRoot: ROOT,
    node: process.version,
    suites: args.suites,
    artifactRoot: path.join(ROOT, 'target'),
    screenshotLib: browserLib || '缺失（S8 将 SKIPPED）',
    diffNewDir: process.env.TC_DIFF_NEW || args.newDir || '',
    diffBaselineDir: process.env.TC_DIFF_BASELINE || args.baselineDir || '',
    thresholds: {
      maxAdded: process.env.VERIFY_CONTRACT_MAX_ADDED || '(服务端 verify.contract.max-added)',
      maxRemoved: process.env.VERIFY_CONTRACT_MAX_REMOVED || '(服务端 verify.contract.max-removed)',
      maxModified: process.env.VERIFY_CONTRACT_MAX_MODIFIED || '(服务端 verify.contract.max-modified)',
      whitelist: process.env.VERIFY_CONTRACT_WHITELIST || '(服务端 verify.contract.whitelist)'
    },
    shotUrls: process.env.TC_SHOT_URLS || '(默认控制台登录页)'
  }
}

async function main() {
  let args
  try {
    args = parseArgs(process.argv.slice(2))
  } catch (e) {
    console.error(e.message)
    console.error('用法：node scripts/verify-center-cli.mjs [--suites S8,S9] [--dry-run] [--new-dir <目录>] [--baseline <目录>]')
    process.exit(2)
  }
  // CLI 参数优先于环境变量，方便在 CI 里按 job 覆盖
  if (args.newDir) process.env.TC_DIFF_NEW = args.newDir
  if (args.baselineDir) process.env.TC_DIFF_BASELINE = args.baselineDir

  let browserLib = null
  try {
    const mod = await import('./shot-verify.mjs')
    const impl = mod.loadBrowserImpl()
    browserLib = impl ? impl.kind : null
  } catch { /* 探测失败按缺失处理 */ }

  if (args.dryRun) {
    console.log('=== 验证中心 dry-run（不执行任何子进程）===')
    console.log(JSON.stringify(printableConfig(args, browserLib), null, 2))
    for (const s of args.suites) {
      console.log(`将执行：node scripts/testcenter-suites.mjs ${s}`)
    }
    console.log('dry-run 结束：未产生截图/比对产物，也未写报告')
    process.exit(0)
  }

  const results = []
  for (const suiteId of args.suites) {
    console.log(`\n=== 验证中心执行 ${suiteId} ===`)
    const r = await runSuite(suiteId)
    results.push({ ...r, metrics: suiteMetrics(r.output), artifacts: suiteArtifacts(r.output) })
  }

  const outDir = path.resolve(args.out || path.join(ROOT, 'target', 'verify-center'))
  fs.mkdirSync(outDir, { recursive: true })
  const reportFile = path.join(outDir, `report-${stamp()}.json`)
  const report = {
    generatedAt: new Date().toISOString(),
    node: process.version,
    config: printableConfig(args, browserLib),
    results: results.map(r => ({ suiteId: r.suiteId, status: r.status, code: r.code, ms: r.ms, metrics: r.metrics, artifacts: r.artifacts }))
  }
  fs.writeFileSync(reportFile, JSON.stringify(report, null, 2), 'utf8')

  console.log('\n=== 验证中心汇总 ===')
  for (const r of results) {
    console.log(`${r.status.padEnd(8)} ${r.suiteId}  ${(r.ms / 1000).toFixed(1)}s  ${r.metrics && r.metrics.reason ? r.metrics.reason : ''}`)
  }
  console.log('报告：' + reportFile)
  const failed = results.filter(r => r.status === 'FAILED')
  if (failed.length) {
    console.error(`门禁结果：FAIL（${failed.map(r => r.suiteId).join(',')}）`)
    process.exit(1)
  }
  console.log('门禁结果：PASS（无 FAIL；SKIPPED 表示环境依赖缺失，不阻断）')
  process.exit(0)
}

const invokedDirectly = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))
if (invokedDirectly) {
  main().catch(e => {
    console.error('验证中心 CLI 异常：' + (e && e.message ? e.message : e))
    process.exit(2)
  })
}
