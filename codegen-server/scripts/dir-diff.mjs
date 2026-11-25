#!/usr/bin/env node
/**
 * V9.18 S9 产物等价性：纯 Node 递归目录比对。
 *
 * 为什么不用 `diff -r`：Windows 开发机默认没有 diff，Git Bash 的 diff 输出格式
 * 与 Linux 也不完全一致；把比对放进 Node 后，本地执行器与 CI 是同一份实现。
 *
 * 两种用法：
 *   1. 被 testcenter-suites.mjs / verify-center-cli.mjs 以子进程方式调用；
 *   2. 单独跑：node scripts/dir-diff.mjs --new <新目录> --baseline <基线目录> [--out diff.json]
 *
 * 退出码：0 = 比对完成（差异本身不是失败）；1 = 阈值超限且带 --enforce；2 = 参数/目录错误。
 * 差异是否阻断由阈值决定（见 judgeDiff），默认全放行，只列出清单。
 */
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

export const SCHEMA = 'codegen.verify.diff/v1'

/** 统一用 `/` 分隔，避免 Windows 反斜杠让前后端对比/展示不一致。
 *  同时替换两种分隔符：path.sep 在 Windows 是 `\`、在 Linux 是 `/`，
 *  但输入可能来自另一平台（如 CI 跑 POSIX、fixture 带 Windows 路径），
 *  因此显式处理 `\\`，保证跨平台结果一致。 */
export function normalizeRel(rel) {
  return String(rel || '').replace(/\\/g, '/').split(path.sep).join('/').replace(/^\.\//, '')
}

/**
 * 白名单：命中前缀或中间段即忽略（用于 node_modules / dist / .cache 这类不参与等价性的目录）。
 * 例：'node_modules' 命中 'node_modules/x/y' 与 'src/node_modules/x'，但不命中 'mynode_modules'。
 */
export function isWhitelisted(rel, whitelist = []) {
  const norm = normalizeRel(rel)
  for (const raw of whitelist) {
    const p = normalizeRel(String(raw || '').trim()).replace(/^\/+|\/+$/g, '')
    if (!p) continue
    if (norm === p || norm.startsWith(p + '/') || norm.includes('/' + p + '/')) return true
  }
  return false
}

function sha1(file) {
  const buf = fs.readFileSync(file)
  return crypto.createHash('sha1').update(buf).digest('hex')
}

/** 递归收集 root 下所有普通文件的相对路径（跳过白名单） */
function walk(root, { whitelist = [], hash = true } = {}) {
  const files = new Map()
  const visit = (rel) => {
    const abs = rel ? path.join(root, rel) : root
    let entries
    try {
      entries = fs.readdirSync(abs, { withFileTypes: true })
    } catch {
      return
    }
    for (const ent of entries) {
      const childRel = rel ? rel + '/' + ent.name : ent.name
      if (isWhitelisted(childRel, whitelist)) continue
      if (ent.isDirectory()) {
        visit(childRel)
      } else if (ent.isFile()) {
        const absFile = path.join(root, childRel)
        try {
          const st = fs.statSync(absFile)
          files.set(childRel, { size: st.size, hash: hash ? sha1(absFile) : null })
        } catch {
          // 读不到的文件（被占用/权限）当作不存在，不阻断整体比对
        }
      }
    }
  }
  visit('')
  return files
}

/**
 * 纯函数：比对两个目录，返回新增 / 删除 / 修改清单（已排序，便于测试与展示）。
 * 修改判定：大小不同，或（开启 hash 时）sha1 不同。
 */
export function diffDirs(newRoot, baseRoot, { whitelist = [], hash = true } = {}) {
  if (!newRoot || !baseRoot) {
    throw new Error('newRoot 与 baseRoot 都必须提供')
  }
  const next = walk(newRoot, { whitelist, hash })
  const base = walk(baseRoot, { whitelist, hash })
  const added = []
  const removed = []
  const modified = []
  let unchanged = 0
  for (const [rel, meta] of next) {
    const prev = base.get(rel)
    if (!prev) {
      added.push(rel)
    } else if (prev.size !== meta.size || (hash && prev.hash !== meta.hash)) {
      modified.push(rel)
    } else {
      unchanged++
    }
  }
  for (const rel of base.keys()) {
    if (!next.has(rel)) removed.push(rel)
  }
  added.sort()
  removed.sort()
  modified.sort()
  return {
    schema: SCHEMA,
    newRoot: path.resolve(newRoot),
    baselineRoot: path.resolve(baseRoot),
    added,
    removed,
    modified,
    unchanged,
    scanned: next.size
  }
}

/**
 * 纯函数：按阈值判定差异是否可接受。
 * 任一维度超限即 ok=false，并给出 exceeded 明细；差异清单本身不默认失败。
 */
export function judgeDiff(diff, limits = {}) {
  const maxAdded = Number.isFinite(Number(limits.maxAdded)) ? Number(limits.maxAdded) : Infinity
  const maxRemoved = Number.isFinite(Number(limits.maxRemoved)) ? Number(limits.maxRemoved) : Infinity
  const maxModified = Number.isFinite(Number(limits.maxModified)) ? Number(limits.maxModified) : Infinity
  const exceeded = []
  if (diff.added.length > maxAdded) exceeded.push({ kind: 'added', count: diff.added.length, limit: maxAdded })
  if (diff.removed.length > maxRemoved) exceeded.push({ kind: 'removed', count: diff.removed.length, limit: maxRemoved })
  if (diff.modified.length > maxModified) exceeded.push({ kind: 'modified', count: diff.modified.length, limit: maxModified })
  return { ok: exceeded.length === 0, exceeded, limits: { maxAdded, maxRemoved, maxModified } }
}

function parseArgs(argv) {
  const out = { whitelist: [], enforce: false }
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i]
    const val = () => argv[++i]
    switch (key) {
      case '--new': out.newDir = val(); break
      case '--baseline': out.baselineDir = val(); break
      case '--out': out.out = val(); break
      case '--max-added': out.maxAdded = Number(val()); break
      case '--max-removed': out.maxRemoved = Number(val()); break
      case '--max-modified': out.maxModified = Number(val()); break
      case '--whitelist': out.whitelist.push(...String(val() || '').split(',')); break
      case '--enforce': out.enforce = true; break
      default:
        if (key.startsWith('--')) {
          console.error(`未知参数 ${key}`)
          process.exit(2)
        }
    }
  }
  return out
}

function formatList(title, list, cap = 20) {
  console.log(`\n${title}（${list.length}）`)
  for (const f of list.slice(0, cap)) console.log('  ' + f)
  if (list.length > cap) console.log(`  ... 其余 ${list.length - cap} 个见 JSON 产物`)
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (!args.newDir || !args.baselineDir) {
    console.error('用法：node scripts/dir-diff.mjs --new <目录> --baseline <目录> [--out diff.json] [--enforce]')
    process.exit(2)
  }
  if (!fs.existsSync(args.newDir)) {
    console.error('新产物目录不存在：' + args.newDir)
    process.exit(2)
  }
  if (!fs.existsSync(args.baselineDir)) {
    console.error('基线目录不存在：' + args.baselineDir)
    process.exit(2)
  }
  const diff = diffDirs(args.newDir, args.baselineDir, { whitelist: args.whitelist })
  const verdict = judgeDiff(diff, args)
  const result = { ...diff, verdict, generatedAt: new Date().toISOString() }

  if (args.out) {
    fs.mkdirSync(path.dirname(path.resolve(args.out)), { recursive: true })
    fs.writeFileSync(args.out, JSON.stringify(result, null, 2), 'utf8')
    console.log('DIFF_JSON ' + path.resolve(args.out))
  }
  formatList('新增', diff.added)
  formatList('删除', diff.removed)
  formatList('修改', diff.modified)
  console.log(`\n合计：扫描 ${diff.scanned} 个文件，未变 ${diff.unchanged} 个；阈值判定 ${verdict.ok ? 'PASS' : 'FAIL'}`)
  if (!verdict.ok) {
    for (const e of verdict.exceeded) console.log(`  超限：${e.kind}=${e.count} > ${e.limit}`)
  }
  console.log('TC_METRICS ' + JSON.stringify({
    diffAdded: diff.added.length,
    diffRemoved: diff.removed.length,
    diffModified: diff.modified.length,
    diffScanned: diff.scanned,
    diffThresholdOk: verdict.ok
  }))
  if (args.enforce && !verdict.ok) process.exit(1)
  process.exit(0)
}

// 仅在直接执行时走 CLI；被 import 时只暴露纯函数
const invokedDirectly = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))
if (invokedDirectly) {
  main()
}
