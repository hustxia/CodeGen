#!/usr/bin/env node
/**
 * nightly-fullgen.mjs —— 定时全量建表任务（Windows 计划任务入口）
 *
 * 功能：每天凌晨自动跑「完整系统」AI 建表建库：
 *   ai-modules(拆模块) → 逐模块 ai-model(建表，去重合并) → createPipeline
 *   → prepare-db → run → 轮询到 db 步成功即 stop（不进入 scaffold/gen/package）
 *
 * 配置：同目录 nightly-jobs.json（数组，每条一个系统）
 * 报告：codegen-server/logs/nightly/YYYY-MM-DD-report.md
 * 用法：
 *   node nightly-fullgen.mjs            # 正式执行
 *   node nightly-fullgen.mjs --dry-run  # 只做健康检查+打印任务清单，不执行
 *
 * 环境变量可覆盖：CG_BACKEND / CG_OLLAMA / CG_JAVA_HOME
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync } from 'node:fs'
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const CFG_FILE = path.join(__dirname, 'nightly-jobs.json')
const LOG_DIR = path.join(__dirname, '..', 'logs', 'nightly')
const BACKEND = process.env.CG_BACKEND || 'http://localhost:8120'
const OLLAMA = process.env.CG_OLLAMA || 'http://localhost:11434'
const JAVA_HOME = process.env.CG_JAVA_HOME || 'D:/tool/jdk-21.0.12.1+1'
const AI_TIMEOUT_MS = 600000            // 与后端 gen.pipeline.ai.timeout-seconds 对齐
const POLL_MS = 20 * 1000               // 流水线状态轮询间隔
const POLL_TIMEOUT_MIN = 90             // db 步完成轮询上限
const START_WAIT_MS = 90 * 1000         // 自动拉起后端/Ollama 的等待上限

const dryRun = process.argv.includes('--dry-run')
const now = () => new Date().toLocaleString('zh-CN', { hour12: false })
const sleep = ms => new Promise(r => setTimeout(r, ms))
const log = (...a) => console.log(`[${now()}]`, ...a)

/* 登录凭据（平台账号，用于拿 Bearer token） */
const CG_USER = process.env.CG_USER || 'admin'
const CG_PASS = process.env.CG_PASS || 'admin123'
let AUTH = {} // { Authorization: 'Bearer xxx' }

async function fetchJson(url, opts = {}, timeoutMs = AI_TIMEOUT_MS) {
  const ac = new AbortController()
  const timer = setTimeout(() => ac.abort(), timeoutMs)
  try {
    const res = await fetch(url, { ...opts, signal: ac.signal })
    const text = await res.text()
    let json
    try { json = JSON.parse(text) } catch { throw new Error(`HTTP ${res.status} 非JSON响应: ${text.slice(0, 200)}`) }
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${json.message || text.slice(0, 200)}`)
    return json
  } finally { clearTimeout(timer) }
}

/** 后端统一包装 {code,message,data}，宽容解析 */
function unwrap(json) {
  if (json && json.data !== undefined) return json.data
  return json
}

/* ---------- 健康检查与自动拉起 ---------- */

/** 有任何 HTTP 响应（含 401 未登录）即视为服务存活 */
async function healthy(url) {
  try {
    const ac = new AbortController()
    const timer = setTimeout(() => ac.abort(), 5000)
    try { await fetch(url, { signal: ac.signal }) } finally { clearTimeout(timer) }
    return true
  } catch { return false }
}

async function login() {
  const json = await fetchJson(`${BACKEND}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: CG_USER, password: CG_PASS })
  }, 15000)
  const data = unwrap(json)
  if (!data || !data.token) throw new Error('登录失败，未拿到 token')
  AUTH = { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' }
  log(`已登录平台账号 ${CG_USER}`)
}

async function startOllama() {
  log('Ollama 未启动，尝试拉起 ollama serve ...')
  try {
    spawn('ollama', ['serve'], { detached: true, stdio: 'ignore', shell: true }).unref()
  } catch (e) { log('拉起 Ollama 失败:', e.message) }
  for (let t = 0; t < START_WAIT_MS; t += 3000) {
    if (await healthy(`${OLLAMA}/api/tags`)) { log('Ollama 已就绪'); return true }
    await sleep(3000)
  }
  return false
}

async function startBackend() {
  log('后端未启动，尝试拉起 jar ...')
  const targetDir = path.join(__dirname, '..', 'target')
  if (!existsSync(targetDir)) { log('未找到 target 目录，无法自动拉起'); return false }
  const jars = readdirSync(targetDir).filter(f => f.endsWith('.jar') && !f.endsWith('.original'))
  if (!jars.length) { log('target 下没有 jar，无法自动拉起'); return false }
  const jar = path.join(targetDir, jars[0])
  log('启动', jar)
  try {
    spawn('java', ['-jar', jar, '--server.port=8120'], {
      detached: true, stdio: 'ignore', shell: true,
      env: { ...process.env, JAVA_HOME }
    }).unref()
  } catch (e) { log('拉起后端失败:', e.message) }
  for (let t = 0; t < START_WAIT_MS; t += 3000) {
    if (await healthy(`${BACKEND}/gen/pipeline/config`)) { log('后端已就绪'); return true }
    await sleep(3000)
  }
  return false
}

/* ---------- 流水线 API ---------- */

async function aiModules(job) {
  const json = await fetchJson(`${BACKEND}/gen/pipeline/ai-modules`, {
    method: 'POST', headers: { ...AUTH },
    body: JSON.stringify({ systemName: job.systemName, systemDesc: job.systemDesc })
  })
  const data = unwrap(json)
  return (data && data.modules) || []
}

async function aiModel(job, moduleName) {
  const json = await fetchJson(`${BACKEND}/gen/pipeline/ai-model`, {
    method: 'POST', headers: { ...AUTH },
    body: JSON.stringify({ systemName: job.systemName, systemDesc: job.systemDesc, ...(moduleName ? { moduleName } : {}) })
  })
  const data = unwrap(json)
  return (data && data.tables) || []
}

function normalizeTables(list) {
  return (list || []).map(t => ({
    tableName: t.tableName, tableComment: t.tableComment,
    columns: (t.columns || []).map(c => ({ name: c.name, type: c.type, comment: c.comment }))
  }))
}

/** 完整系统：逐模块生成 + 按表名去重合并；精简：一次生成 */
async function buildTables(job) {
  const tables = []
  const seen = new Set()
  const failedModules = []
  if (job.scale === 'full') {
    const modules = await aiModules(job)
    if (!modules.length) throw new Error('AI 未能拆分出业务模块')
    log(`  模块拆分完成，共 ${modules.length} 个模块`)
    for (let i = 0; i < modules.length; i++) {
      const m = modules[i]
      log(`  模块 ${i + 1}/${modules.length}：${m.name} ...`)
      try {
        for (const t of normalizeTables(await aiModel(job, m.name))) {
          const key = (t.tableName || '').trim().toLowerCase()
          if (key && !seen.has(key)) { seen.add(key); tables.push(t) }
        }
        log(`    累计合并 ${tables.length} 张表`)
      } catch (e) {
        failedModules.push(m.name)
        log(`    模块失败已跳过: ${e.message}`)
      }
    }
  } else {
    tables.push(...normalizeTables(await aiModel(job)))
  }
  if (!tables.length) throw new Error('所有模块均生成失败')
  return { tables, failedModules }
}

async function createPipeline(job, tables) {
  const json = await fetchJson(`${BACKEND}/gen/pipeline`, {
    method: 'POST', headers: { ...AUTH },
    body: JSON.stringify({
      systemName: job.systemName, systemDesc: job.systemDesc,
      dbName: job.dbName, mode: 'ai', ddlText: '', tables
    })
  })
  const data = unwrap(json)
  if (!data || !data.id) throw new Error('createPipeline 未返回 id: ' + JSON.stringify(json).slice(0, 200))
  return data
}

async function prepareDb(id, tables) {
  const json = await fetchJson(`${BACKEND}/gen/pipeline/${id}/prepare-db`, {
    method: 'POST', headers: { ...AUTH },
    body: JSON.stringify({ tables, ddlText: null })
  })
  return unwrap(json)
}

async function runPipeline(id) {
  await fetchJson(`${BACKEND}/gen/pipeline/${id}/run`, { method: 'POST', headers: { ...AUTH } }, 30000)
}

async function stopPipeline(id) {
  try { await fetchJson(`${BACKEND}/gen/pipeline/${id}/stop`, { method: 'POST', headers: { ...AUTH } }, 30000) }
  catch (e) { log('  stop 调用异常（可忽略，任务可能已结束）:', e.message) }
}

async function getDetail(id) {
  const json = await fetchJson(`${BACKEND}/gen/pipeline/${id}`, { headers: { ...AUTH } }, 30000)
  return unwrap(json)
}

/**
 * 轮询到 db 步成功 → stop（只跑到建表+建库为止）。
 * 返回 { outcome, failReason }，outcome: db_ok | failed | stopped_early | timeout
 */
async function waitUntilDbDone(id) {
  const deadline = Date.now() + POLL_TIMEOUT_MIN * 60 * 1000
  while (Date.now() < deadline) {
    let d
    try { d = await getDetail(id) } catch (e) { log('  轮询异常，重试:', e.message); await sleep(POLL_MS); continue }
    const steps = d.steps || []
    const dbStep = steps.find(s => s.stepKey === 'db')
    if (dbStep && dbStep.status === 'success') return { outcome: 'db_ok' }
    if (d.status === 'failed') return { outcome: 'failed', failReason: d.failReason }
    if (d.status === 'stopped') return { outcome: 'stopped_early', failReason: d.failReason }
    if (d.status === 'success') return { outcome: 'db_ok' } // 全流程跑完（理论不该发生）
    log(`  运行中 currentStep=${d.currentStep} db=${dbStep ? dbStep.status : '?'}`)
    await sleep(POLL_MS)
  }
  return { outcome: 'timeout', failReason: `超过 ${POLL_TIMEOUT_MIN} 分钟未完成 db 步` }
}

/* ---------- 主流程 ---------- */

async function runJob(job) {
  const r = { systemName: job.systemName, dbName: job.dbName, scale: job.scale || 'full', tables: 0, failedModules: [], status: 'success', message: '' }
  log(`▶ 任务：${job.systemName} → 库 ${job.dbName}（${r.scale}）`)
  const { tables, failedModules } = await buildTables(job)
  r.tables = tables.length
  r.failedModules = failedModules
  log(`  建模完成，共 ${tables.length} 张表${failedModules.length ? `（${failedModules.length} 个模块失败跳过）` : ''}`)

  const created = await createPipeline(job, tables)
  const id = created.id
  log(`  流水线已创建 id=${id} no=${created.pipelineNo || '?'}`)
  r.pipelineNo = created.pipelineNo

  await prepareDb(id, tables)
  log('  prepare-db 完成，启动流水线 ...')
  await runPipeline(id)

  const w = await waitUntilDbDone(id)
  if (w.outcome !== 'db_ok') {
    r.status = w.outcome === 'failed' ? 'failed' : 'stopped'
    r.message = w.failReason || w.outcome
    // 失败也主动 stop，防止残留进程继续跑后面的步骤
    await stopPipeline(id)
    return r
  }
  await stopPipeline(id)
  log(`  ✅ db 步完成（${r.tables} 张表已入 ${job.dbName}），已停止后续代码生成步骤`)
  return r
}

async function main() {
  const reportLines = [`# 夜间全量建表报告 ${new Date().toISOString().slice(0, 10)}`, '', `执行时间：${now()}`, '']
  let skipReport = false // 开关关闭/dry-run 提前退出时不动当天报告
  try {
    if (!existsSync(CFG_FILE)) throw new Error('缺少配置文件 nightly-jobs.json')
    const raw = JSON.parse(readFileSync(CFG_FILE, 'utf8'))
    // 兼容两种配置形态：旧版纯数组（视为开关常开）；新版 { enabled, jobs }（总开关，默认关闭）
    const enabled = Array.isArray(raw) ? true : raw.enabled === true
    const jobs = Array.isArray(raw) ? raw : (raw.jobs || [])
    if (!Array.isArray(jobs) || !jobs.length) throw new Error('nightly-jobs.json 的 jobs 为空或不是数组')

    if (dryRun) {
      skipReport = true
      log(`dry-run 模式：总开关=${enabled ? '开' : '关（正式执行会直接退出）'}，任务清单如下（不执行）`)
      jobs.forEach((j, i) => log(`  ${i + 1}. [${j.enabled === false ? '已停用' : '启用'}] ${j.systemName} → ${j.dbName}（${j.scale || 'full'}）`))
      return
    }

    // 总开关：默认关闭，显式 enabled=true 才执行
    if (!enabled) {
      skipReport = true
      log('定时任务开关未开启（nightly-jobs.json enabled=false），本轮跳过不执行')
      return
    }

    const activeJobs = jobs.filter(j => j.enabled !== false)
    if (!activeJobs.length) {
      skipReport = true
      log('所有任务均已单条停用（job.enabled=false），本轮跳过不执行')
      return
    }

    log('健康检查 ...')
    let okOllama = await healthy(`${OLLAMA}/api/tags`)
    if (!okOllama) okOllama = await startOllama()
    let okBackend = await healthy(`${BACKEND}/gen/pipeline/config`)
    if (!okBackend) okBackend = await startBackend()
    reportLines.push(`- Ollama: ${okOllama ? '就绪' : '**不可用**'}`, `- 后端(8120): ${okBackend ? '就绪' : '**不可用**'}`, '')
    if (!okOllama || !okBackend) throw new Error(`服务不可用：Ollama=${okOllama} 后端=${okBackend}，任务终止`)

    await login()

    const results = []
    for (const job of activeJobs) {
      try { results.push(await runJob(job)) }
      catch (e) {
        log(`✗ 任务 ${job.systemName} 失败: ${e.message}`)
        results.push({ systemName: job.systemName, dbName: job.dbName, status: 'failed', message: e.message, tables: 0, failedModules: [] })
      }
    }

    reportLines.push('| 系统 | 目标库 | 规模 | 状态 | 表数 | 失败模块 | 说明 |', '|---|---|---|---|---|---|---|')
    for (const r of results) {
      reportLines.push(`| ${r.systemName} | ${r.dbName} | ${r.scale} | ${r.status} | ${r.tables} | ${r.failedModules.join('、') || '-'} | ${r.message || '-'} |`)
    }
    const failed = results.filter(r => r.status !== 'success').length
    reportLines.push('', `**结果：${results.length - failed}/${results.length} 成功**`)
  } catch (e) {
    log('✗ 致命错误:', e.message)
    reportLines.push(`**致命错误：${e.message}**`)
    process.exitCode = 1
  } finally {
    if (skipReport) return
    try {
      mkdirSync(LOG_DIR, { recursive: true })
      const file = path.join(LOG_DIR, `${new Date().toISOString().slice(0, 10)}-report.md`)
      writeFileSync(file, reportLines.join('\n'), 'utf8')
      log('报告已写入', file)
    } catch (e) { log('报告写入失败:', e.message) }
  }
}

main()
