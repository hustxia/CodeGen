#!/usr/bin/env node
// codegen.mjs —— V6.2 CLI：无头生成指定系统的全部代码（CI 可接）
//
// 零依赖（Node 18+ 内置 fetch）。只读平台接口（login/table/page/download），写盘在本地完成。
//
//   node codegen.mjs gen --system <projectCode> --out <dir> [--force] [--base http://host:8120]
//   node codegen.mjs status --system <projectCode> [--base ...]
//
// 环境变量：CODEGEN_BASE（平台地址）、CODEGEN_USER/CODEGEN_PASS（登录，默认 admin/admin123）
// 退出码：0 成功 / 1 生成失败 / 2 环境不满足（连接不上、系统不存在）
import FS from 'node:fs'
import Path from 'node:path'
import Zlib from 'node:zlib'

const args = process.argv.slice(2)
const cmd = args[0]
const opt = {}
for (let i = 1; i < args.length; i++) {
  if (args[i].startsWith('--')) opt[args[i].slice(2)] = args[i + 1] ?? ''
}
const BASE = (opt.base || process.env.CODEGEN_BASE || 'http://127.0.0.1:8120').replace(/\/$/, '')

function die(code, msg) { console.error('[' + cmd + '] ' + msg); process.exit(code) }

async function j(url, opts = {}) {
  const r = await fetch(BASE + url, opts)
  if (!r.ok) throw new Error('HTTP ' + r.status + ' ' + url)
  const body = await r.json()
  if (body.code !== undefined && body.code !== 200 && body.code !== 0) {
    throw new Error('code=' + body.code + ' ' + (body.msg || ''))
  }
  return body.data
}

async function login() {
  return j('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.CODEGEN_USER || 'admin', password: process.env.CODEGEN_PASS || 'admin123' }) })
}

async function findProject(token, code) {
  const list = await j('/project/list', { headers: hh(token) })
  const rows = Array.isArray(list) ? list : []
  return rows.find(p => p.projectCode === code)
}

const hh = (token) => ({ 'Content-Type': 'application/json', Authorization: 'Bearer ' + token })

async function main() {
  if (!cmd || !['gen', 'status'].includes(cmd) || !opt.system) {
    die(2, '用法: node codegen.mjs gen|status --system <projectCode> [--out <dir>] [--base URL]')
  }
  let token
  try { token = (await login()).token } catch (e) { die(2, '平台不可达或登录失败: ' + e.message) }

  const project = await findProject(token, opt.system)
  if (!project) die(2, '系统不存在: ' + opt.system)
  console.log('系统: %s (%s) 引擎=%s', project.projectName, project.projectCode, project.flowEngine || 'builtin')

  const page = await j('/gen/table/page', { method: 'POST', headers: hh(token),
    body: JSON.stringify({ pageNum: 1, pageSize: 500, projectId: project.id }) })
  const tables = page.records || page.list || []
  if (cmd === 'status') {
    console.log('表台账: %d 张', tables.length)
    for (const t of tables) console.log('  - %s (%s)%s', t.tableName, t.tableComment || '', t.flowCode ? ' [flow:' + t.flowCode + ']' : '')
    return
  }
  if (!tables.length) die(1, '该系统没有表配置，先生成台账')
  const ids = tables.map(t => t.id)
  console.log('生成 %d 张表 → %s', ids.length, opt.out || '.')

  const r = await fetch(BASE + '/gen/download', { method: 'POST', headers: hh(token),
    body: JSON.stringify({ ids }) })
  if (!r.ok) die(1, '下载失败: HTTP ' + r.status)
  const buf = Buffer.from(await r.arrayBuffer())
  const files = unzip(buf)
  const outRoot = Path.resolve(opt.out || '.')
  let count = 0
  for (const [name, content] of files) {
    if (name === 'manifest.txt' || name.endsWith('.txt') && name.includes('snippet')) {
      // 说明类文件照写，供人看
    }
    const dest = Path.join(outRoot, name)
    if (FS.existsSync(dest) && !opt.force) {
      console.log('  skip(存在) %s', name)
      continue
    }
    FS.mkdirSync(Path.dirname(dest), { recursive: true })
    FS.writeFileSync(dest, content)
    count++
  }
  console.log('写盘 %d 个文件（manifest %d 项）→ %s', count, files.length, outRoot)
}

/** 最小 ZIP 读取：本地文件头 + central directory，支持 deflate/stored */
function unzip(buf) {
  const files = new Map()
  // 从 EOCD 定位 central directory
  let eocd = -1
  for (let i = buf.length - 22; i >= 0 && i > buf.length - 66000; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break }
  }
  if (eocd < 0) throw new Error('ZIP EOCD 未找到（响应不是合法 ZIP？）')
  let ptr = buf.readUInt32LE(eocd + 16)
  const count = buf.readUInt16LE(eocd + 10)
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(ptr) !== 0x02014b50) break
    const method = buf.readUInt16LE(ptr + 10)
    const compSize = buf.readUInt32LE(ptr + 20)
    const nameLen = buf.readUInt16LE(ptr + 28)
    const extraLen = buf.readUInt16LE(ptr + 30)
    const cmtLen = buf.readUInt16LE(ptr + 32)
    const localOff = buf.readUInt32LE(ptr + 42)
    const name = buf.slice(ptr + 46, ptr + 46 + nameLen).toString('utf8')
    // local header
    const lhNameLen = buf.readUInt16LE(localOff + 26)
    const lhExtraLen = buf.readUInt16LE(localOff + 28)
    const dataStart = localOff + 30 + lhNameLen + lhExtraLen
    const raw = buf.slice(dataStart, dataStart + compSize)
    files.set(name, method === 8 ? Zlib.inflateRawSync(raw) : Buffer.from(raw))
    ptr += 46 + nameLen + extraLen + cmtLen
  }
  return files
}

main().catch(e => die(1, e.message))
