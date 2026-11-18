#!/usr/bin/env node
/**
 * V9.2 门禁摘要：把 verify.sh 的逐步结果转成 codegen.testcenter/v1 JSON。
 *
 * 这是「本地与 CI 同一份事实」的关键：Java 执行器导出 TcSummary，
 * shell 门禁导出本脚本生成的同结构摘要，字段级一致，可以直接 diff。
 *
 * 用法：node scripts/testcenter-summary.mjs <steps.tsv> [summary.json]
 * steps.tsv 列：suiteId \t name \t status(PASS/FAIL/SKIP) \t durationMs \t logPath \t metricsJson
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const stepsFile = process.argv[2] || path.join(ROOT, 'logs', 'testcenter', 'steps.tsv')
const outFile = process.argv[3] || path.join(ROOT, 'logs', 'testcenter', 'summary.json')

function gitCommit() {
  try {
    return execFileSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: ROOT, encoding: 'utf8' }).trim()
  } catch {
    return 'unknown'
  }
}

const STATUS = { PASS: 'PASSED', FAIL: 'FAILED', SKIP: 'SKIPPED' }
const cases = []
let durationMs = 0
if (fs.existsSync(stepsFile)) {
  for (const line of fs.readFileSync(stepsFile, 'utf8').split(/\r?\n/)) {
    if (!line.trim()) continue
    const [suiteId, name, raw, dur, logPath, metricsRaw] = line.split('\t')
    const status = STATUS[raw] || raw
    const ms = Number(dur || 0)
    durationMs += Number.isFinite(ms) ? ms : 0
    let metrics = {}
    try { metrics = JSON.parse(metricsRaw || '{}') } catch { /* 指标缺失不影响摘要 */ }
    if (name) metrics.step = name
    cases.push({ suiteId, status, durationMs: ms, metrics, logPath: logPath || '' })
  }
}

const totals = { total: cases.length, passed: 0, failed: 0, skipped: 0 }
for (const c of cases) {
  if (c.status === 'PASSED') totals.passed++
  else if (c.status === 'SKIPPED') totals.skipped++
  else totals.failed++
}

const stamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
const summary = {
  schema: 'codegen.testcenter/v1',
  runNo: 'TC-CI-' + stamp,
  gitCommit: gitCommit(),
  status: totals.failed > 0 ? 'FAILED' : 'PASSED',
  durationMs,
  totals,
  cases
}

fs.mkdirSync(path.dirname(outFile), { recursive: true })
fs.writeFileSync(outFile, JSON.stringify(summary, null, 2))
console.log('TC_SUMMARY ' + outFile)
console.log(JSON.stringify(summary))
process.exit(totals.failed > 0 ? 1 : 0)
