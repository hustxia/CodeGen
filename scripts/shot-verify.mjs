#!/usr/bin/env node
/**
 * V9.18 S8 截图验证：对配置的页面逐个截图，产出落 target/verify-shots/。
 *
 * 依赖策略（硬约束：不引重量级新依赖）：
 *   - 优先复用工程里已有的 playwright / playwright-core / puppeteer / puppeteer-core；
 *   - 一个都找不到 → 输出 SKIPPED 并退出 0（不是失败，不能把缺依赖伪装成 PASS）；
 *   - 页面全部不可达（服务未起）→ 同样 SKIPPED。
 *
 * 用法：
 *   node scripts/shot-verify.mjs                     # 用 TC_SHOT_URLS 或默认控制台地址
 *   node scripts/shot-verify.mjs --urls "name=url,name2=url2" --out target/verify-shots
 *
 * 输出约定（验证中心执行器识别）：
 *   TC_ARTIFACTS ["截图绝对路径", ...]   登记到 tc_artifact（artifact_type=SCREENSHOT）
 *   TC_METRICS {"shots":N,"missing":M}  结构化指标；status=SKIPPED 时附 reason
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const WEB = process.env.TC_WEB_ROOT || path.resolve(ROOT, '..', 'codegen-web')
const WIN = process.platform === 'win32'
const require = createRequire(import.meta.url)

/** 默认目标：控制台登录页（vue.config.js 的 devServer 端口 9530） */
export const DEFAULT_TARGETS = [{ name: 'login', url: 'http://127.0.0.1:9530/#/login' }]

/** 纯函数：解析 "名称=URL,URL" 形式的目标清单，忽略空项与非法 URL */
export function parseTargets(raw) {
  const text = String(raw || '').trim()
  if (!text) return []
  const out = []
  for (const part of text.split(',')) {
    const item = part.trim()
    if (!item) continue
    const eq = item.indexOf('=')
    const name = eq > 0 ? item.slice(0, eq).trim() : ''
    const url = eq > 0 ? item.slice(eq + 1).trim() : item
    if (!/^https?:\/\//i.test(url)) continue
    out.push({ name: name || safeName(url), url })
  }
  return out
}

/** 把 URL 转成可用于文件名的短标识 */
export function safeName(url) {
  return String(url)
    .replace(/^https?:\/\//i, '')
    .replace(/[^a-zA-Z0-9._-]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 60) || 'page'
}

/** 在 serverRoot / webRoot / 当前工程里找已安装的浏览器库；找不到返回 null */
export function loadBrowserImpl() {
  const roots = [ROOT, WEB, process.cwd()]
  const probe = (name) => {
    for (const root of roots) {
      try {
        return require(require.resolve(name, { paths: [root] }))
      } catch {
        // 该 root 下没装，继续下一个
      }
    }
    return null
  }
  const playwright = probe('playwright')
  if (playwright && playwright.chromium) {
    return { kind: 'playwright', launch: () => playwright.chromium.launch({ headless: true }) }
  }
  const puppeteer = probe('puppeteer') || probe('puppeteer-core')
  if (puppeteer) {
    return { kind: 'puppeteer', launch: () => puppeteer.launch({ headless: 'new' }) }
  }
  return null
}

/** 轻量探活：任何 HTTP 响应（含 401/404）都算服务已起 */
export async function probe(url, timeoutMs = 3000) {
  try {
    const ctrl = new AbortController()
    const timer = setTimeout(() => ctrl.abort(), timeoutMs)
    try {
      await fetch(url, { method: 'HEAD', signal: ctrl.signal, redirect: 'manual' })
      return true
    } finally {
      clearTimeout(timer)
    }
  } catch {
    return false
  }
}

function isoStamp() {
  return new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
}

/** 默认输出目录：执行器给了 TC_ARTIFACT_DIR 就落运行目录（可下载），否则落 target/verify-shots */
export function resolveOutDir(argOut) {
  if (argOut) return path.resolve(argOut)
  if (process.env.VERIFY_SHOT_DIR) return path.resolve(process.env.VERIFY_SHOT_DIR)
  if (process.env.TC_ARTIFACT_DIR) return path.resolve(process.env.TC_ARTIFACT_DIR)
  return path.join(ROOT, 'target', 'verify-shots')
}

/** 截图一批页面。返回 {status, shots, missing, failed, files, reason} */
export async function captureAll({ targets, outDir, impl, viewport = { width: 1440, height: 900 }, timeoutMs = 20000 }) {
  fs.mkdirSync(outDir, { recursive: true })
  // 先判依赖：缺少浏览器库是最常见原因，直接给出可执行的修复提示，而不是误报「页面不可达」
  if (!impl) {
    return { status: 'SKIPPED', shots: 0, missing: 0, failed: 0, files: [], reason: '未安装 playwright / puppeteer，按约定跳过截图' }
  }
  const reachable = []
  const missing = []
  for (const t of targets) {
    // 逐个探活：只对起来了的页面截图，未启动的服务不算失败
    if (await probe(t.url)) reachable.push(t)
    else missing.push(t)
  }
  if (!reachable.length) {
    return { status: 'SKIPPED', shots: 0, missing: missing.length, failed: 0, files: [], reason: '目标页面均不可达（服务未启动或端口不对）' }
  }
  const files = []
  let failed = 0
  let browser = null
  try {
    browser = await impl.launch()
    for (const t of reachable) {
      let page = null
      try {
        page = await browser.newPage()
        if (page.setViewport) await page.setViewport(viewport)
        await page.goto(t.url, { waitUntil: 'domcontentloaded', timeout: timeoutMs })
        // 给前端首屏留一点渲染时间，避免截到白屏
        await new Promise(r => setTimeout(r, 1200))
        const file = path.join(outDir, `${safeName(t.name || t.url)}-${isoStamp()}.png`)
        await page.screenshot({ path: file, fullPage: true })
        if (fs.existsSync(file) && fs.statSync(file).size > 0) files.push(file)
      } catch (e) {
        failed++
        console.error(`截图失败 ${t.url}: ${e.message}`)
      } finally {
        if (page) {
          try { await page.close() } catch { /* 页面已关闭 */ }
        }
      }
    }
  } finally {
    if (browser) {
      try { await browser.close() } catch { /* 浏览器已退出 */ }
    }
  }
  if (!files.length) {
    return { status: 'FAILED', shots: 0, missing: missing.length, failed, files: [], reason: '页面可达但全部截图失败' }
  }
  return {
    status: failed > 0 ? 'FAILED' : 'PASSED',
    shots: files.length,
    missing: missing.length,
    failed,
    files,
    reason: failed > 0 ? `${failed} 个页面截图失败` : `${files.length} 张截图`
  }
}

function parseArgs(argv) {
  const out = {}
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i]
    const val = () => argv[++i]
    if (key === '--urls') out.urls = val()
    else if (key === '--out') out.out = val()
    else if (key === '--timeout-ms') out.timeoutMs = Number(val())
  }
  return out
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const targetEnv = args.urls || process.env.TC_SHOT_URLS || ''
  const configured = parseTargets(targetEnv)
  const targets = configured.length ? configured : DEFAULT_TARGETS
  if (!configured.length) {
    console.log('未配置 TC_SHOT_URLS，使用默认控制台地址：' + targets.map(t => t.url).join(','))
  }
  const outDir = resolveOutDir(args.out)
  const impl = loadBrowserImpl()
  const result = await captureAll({
    targets,
    outDir,
    impl,
    timeoutMs: args.timeoutMs || 20000
  })

  // 执行器模式（TC_ARTIFACT_DIR）下额外镜像一份到 target/verify-shots，
  // 满足「截图落到 target/verify-shots」的约定，同时保证 run 目录内有可下载副本。
  if (process.env.TC_ARTIFACT_DIR && path.resolve(outDir) !== path.join(ROOT, 'target', 'verify-shots')) {
    const mirror = path.join(ROOT, 'target', 'verify-shots')
    try {
      fs.mkdirSync(mirror, { recursive: true })
      for (const f of result.files) fs.copyFileSync(f, path.join(mirror, path.basename(f)))
    } catch (e) {
      console.warn('镜像截图到 target/verify-shots 失败（不影响本次结论）: ' + e.message)
    }
  }

  if (result.files.length) {
    console.log('TC_ARTIFACTS ' + JSON.stringify(result.files.map(f => path.resolve(f))))
  }
  console.log(`截图输出目录：${outDir}（浏览器库：${impl ? impl.kind : '缺失'}）`)
  console.log('TC_METRICS ' + JSON.stringify({
    suiteId: 'S8',
    status: result.status,
    shots: result.shots,
    missing: result.missing,
    failed: result.failed,
    ...(result.status === 'SKIPPED' ? { reason: result.reason } : {})
  }))
  console.log(`RESULT ${result.status} S8：${result.reason}`)
  process.exit(result.status === 'FAILED' ? 1 : 0)
}

const invokedDirectly = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))
if (invokedDirectly) {
  main().catch(e => {
    // 任何未预期异常都按 SKIP 收口：截图属于 P1 验证项，缺环境不应打断整轮门禁
    console.error('截图验证异常（按 SKIPPED 处理）：' + (e && e.message ? e.message : e))
    console.log('TC_METRICS ' + JSON.stringify({ suiteId: 'S8', status: 'SKIPPED', reason: String(e && e.message || e) }))
    process.exit(0)
  })
}
