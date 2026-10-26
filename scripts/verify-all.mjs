#!/usr/bin/env node
/**
 * 一条命令跑完三层护栏：后端测试 → 前端单测 → 前端生产构建。
 *
 * 为什么需要它：`mvn test`、`vitest run`、`build:prod` 分散在两个仓库里，
 * 全靠人记得「改完后端要跑测试、改前端要跑单测」，实际漏跑是常态。
 * 有了这个脚本，本地与 CI 用同一条命令、同一套退出码。
 *
 * 退出码约定（CI 靠它判断，不要改）：
 *   0  全部通过
 *   1  有真实失败（测试红 / 构建错）——必须修
 *   2  环境不满足（缺 mvn / JDK、沙箱拦 dist 删除等）——不是代码问题，但要人介入
 *
 * 用法：
 *   node scripts/verify-all.mjs                # 三步全跑
 *   node scripts/verify-all.mjs --skip-build   # 只跑测试（本地快速反馈）
 *   MAVEN_BIN=/d/tool/apache-maven-3.6.3/bin/mvn node scripts/verify-all.mjs
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const HERE = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
const WEB_DIR = path.resolve(HERE, '..')
const SERVER_DIR = process.env.SERVER_DIR || path.resolve(WEB_DIR, '..', 'codegen-server')
const WIN = process.platform === 'win32'

/** Git Bash 风格路径（/d/tool/x）转成 Windows 路径（D:/tool/x），Node 的 spawn 不认前者 */
function normalizeBin(value) {
  if (!value) {
    return ''
  }
  return value.replace(/^\/([a-zA-Z])\//, (_, drive) => drive.toUpperCase() + ':/')
}

const MAVEN = normalizeBin(process.env.MAVEN_BIN) || (WIN ? 'mvn.cmd' : 'mvn')
const skipBuild = process.argv.includes('--skip-build')

/** 环境类失败的识别特征：命中即算「环境不满足」，不算代码回归 */
const ENV_SIGNS = [
  'ENOENT',
  'spawn mvn',
  'mvn.cmd ENOENT',
  'Could not create the Java Virtual Machine',
  'No compiler is provided in this environment',
  'EPERM',
  'EBUSY',
  'operation not permitted',
  'is not recognized as an internal or external command',
  '不是内部或外部命令',
  'command not found'
]

const steps = [
  {
    name: '后端测试',
    cwd: SERVER_DIR,
    cmd: MAVEN,
    args: ['-o', '-q', 'test'],
    okHint: 'Tests run: N, Failures: 0'
  },
  {
    name: '前端单测',
    cwd: WEB_DIR,
    cmd: WIN ? 'npx.cmd' : 'npx',
    args: ['vitest', 'run'],
    okHint: 'Test Files passed'
  },
  {
    name: '前端构建',
    cwd: WEB_DIR,
    cmd: WIN ? 'npm.cmd' : 'npm',
    args: ['run', 'build:prod'],
    skip: skipBuild,
    okHint: 'Build complete'
  }
]

function run(step) {
  return new Promise(resolve => {
    const started = Date.now()
    const chunks = []
    let child
    try {
      // Windows 上 .cmd 不能直接 spawn（EINVAL），必须过 shell；
      // 参数里没有空格与特殊字符，过 shell 不会引入引号问题
      child = spawn(step.cmd, step.args, { cwd: step.cwd, shell: WIN, windowsHide: true })
    } catch (err) {
      resolve({ status: 'env', note: err.message, tail: [], ms: Date.now() - started })
      return
    }
    const collect = data => {
      chunks.push(data.toString())
      if (chunks.length > 400) {
        chunks.shift()
      }
    }
    child.stdout.on('data', collect)
    child.stderr.on('data', collect)
    child.on('error', err => {
      resolve({ status: 'env', note: err.message, tail: [], ms: Date.now() - started })
    })
    child.on('close', code => {
      const text = chunks.join('')
      const tail = text.split('\n').filter(Boolean).slice(-6)
      if (code === 0) {
        resolve({ status: 'pass', tail, ms: Date.now() - started })
        return
      }
      const envHit = ENV_SIGNS.find(sign => text.includes(sign))
      resolve({
        status: envHit ? 'env' : 'fail',
        note: envHit ? `环境限制：${envHit}` : `退出码 ${code}`,
        tail,
        ms: Date.now() - started
      })
    })
  })
}

const label = {
  pass: 'PASS',
  fail: 'FAIL',
  env: 'ENV-SKIP',
  skipped: 'SKIPPED'
}

const results = []
for (const step of steps) {
  if (step.skip) {
    results.push({ name: step.name, status: 'skipped', note: '按参数跳过', ms: 0, tail: [] })
    continue
  }
  if (!existsSync(step.cwd)) {
    results.push({ name: step.name, status: 'env', note: `目录不存在：${step.cwd}`, ms: 0, tail: [] })
    continue
  }
  process.stdout.write(`\n▶ ${step.name} ... `)
  const r = await run(step)
  process.stdout.write(`${label[r.status]}${r.note ? ' · ' + r.note : ''}\n`)
  results.push({ name: step.name, ...r })
}

process.stdout.write('\n========== 汇总 ==========\n')
for (const r of results) {
  const secs = r.ms ? (r.ms / 1000).toFixed(0) + 's' : '-'
  process.stdout.write(`${label[r.status].padEnd(9)} ${r.name.padEnd(6)} ${secs.padStart(5)}  ${r.note || ''}\n`)
  if (r.status !== 'pass' && r.tail && r.tail.length) {
    for (const line of r.tail) {
      process.stdout.write(`          ${line}\n`)
    }
  }
}

const failed = results.filter(r => r.status === 'fail')
const envBlocked = results.filter(r => r.status === 'env')
if (failed.length) {
  process.stdout.write(`\n结论：${failed.length} 项真实失败，必须修复\n`)
  process.exit(1)
}
if (envBlocked.length) {
  process.stdout.write(`\n结论：代码没有失败，但有 ${envBlocked.length} 项因环境无法验证（换完整环境再跑一次）\n`)
  process.exit(2)
}
process.stdout.write('\n结论：全部通过\n')
process.exit(0)
