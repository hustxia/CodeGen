#!/usr/bin/env node
/**
 * check-generated-js.mjs —— V7.4 P0-3 生成物 JS 语法校验
 *
 * 做什么：读 target/gen-matrix/manifest.json（由 GenCombinationMatrixTest 产出），
 *         对每个组合的 api.js 与 page.vue 的 <script> 段做 `new Function` 语法校验，
 *         输出「坏组合清单」并在有坏组合时以非 0 退出。
 *
 * 为什么必须做：模板渲染不抛异常 ≠ 产物可用。第五轮走查实测「import 块缺逗号」
 * 让 192 种开关组合渲染出 page.vue，FreeMarker 全绿、文件也写出来了，
 * 但浏览器打开就是白屏。这类问题只有真正的 JS 解析器能抓。
 *
 * 为什么就地在 Node 里解析：Java 侧没有可靠的 ES 语法解析器（Nashorn 已移除），
 * 而项目工具链本来就有 Node（scripts/*.mjs + 前端构建）。
 *
 * 预处理：SFC 的 script 是 ES Module（import / export default / export function），
 *         不能直接丢给 new Function。这里剥离 import 语句、把 export 改写成函数体内合法形式，
 *         只校验「语法」不执行任何代码。
 *
 * 用法：
 *   node scripts/check-generated-js.mjs                 # 校验，有坏组合即失败
 *   node scripts/check-generated-js.mjs --expect-bad 192  # 反向验证：必须至少 N 个坏组合
 *   node scripts/check-generated-js.mjs --json          # 机器可读输出
 *
 * 前置：先跑 `mvn -o -Dtest=GenCombinationMatrixTest test` 生成 target/gen-matrix
 */
import { readFileSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(__dirname, '..')
const MATRIX = path.join(ROOT, 'target', 'gen-matrix')
const MANIFEST = path.join(MATRIX, 'manifest.json')

const argv = process.argv.slice(2)
const EXPECT_BAD = (() => {
  const i = argv.indexOf('--expect-bad')
  if (i < 0) return null
  const n = Number(argv[i + 1])
  return Number.isFinite(n) ? n : null
})()
const JSON_OUT = argv.includes('--json')

if (!existsSync(MANIFEST)) {
  console.error(`找不到 ${MANIFEST}\n先执行：mvn -o -Dtest=GenCombinationMatrixTest test`)
  process.exit(2)
}

/**
 * 把 ES Module 片段改写成可被 new Function 解析的函数体。
 *
 * 关键：**不能整段删除 import**。第五轮的坏逗号（`createXxx` 后漏逗号、
 * 直接接 `flowSubmitXxx`）就发生在 `import { a, b } from '...'` 的修饰符列表里，
 * 删掉 import 等于把要校验的东西扔了。
 *
 * 生成的 import 有三种形态，都要覆盖：
 *   1. import request from '@/utils/request'
 *   2. import request, { toPageData } from '@/utils/request'   ← 实际产物就是这个
 *   3. import { a, b } from '@/api/order'
 * 命名列表 `{...}` 平移成对象字面量：逗号规则与 import 一致（同样不允许缺逗号、
 * 允许尾逗号），且是纯语法结构，不会被求值。
 */
let impSeq = 0
function toParsable(code) {
  return code
    .replace(/import\s+([\s\S]*?)\s+from\s*(['"])[^'"]*\2\s*;?/g, (_m, spec) => {
      const out = []
      const ns = spec.match(/\*\s+as\s+([A-Za-z_$][\w$]*)/)
      if (ns) out.push(`const ${ns[1]} = 0;`)
      const named = spec.match(/\{([\s\S]*?)\}/)
      if (named) out.push(`const __imp${impSeq++} = {${named[1]}};`)
      // default 名：去掉 {…}、* as x，剩下的裸标识符
      const def = spec
        .replace(/\{[\s\S]*?\}/g, '')
        .replace(/\*\s+as\s+[A-Za-z_$][\w$]*/g, '')
        .replace(/,/g, '')
        .trim()
      if (def && /^[A-Za-z_$][\w$]*$/.test(def)) out.push(`const ${def} = 0;`)
      return out.join(' ')
    })
    .replace(/import\s*['"][^'"]*['"]\s*;?/g, '')
    .replace(/\bexport\s+default\b/g, 'return')
    .replace(/\bexport\s+(function|const|let|var|class)\b/g, '$1')
}

/** 从 .vue 单文件组件里抽出全部 <script> 段 */
function scriptBlocks(vue) {
  const blocks = []
  const re = /<script[^>]*>([\s\S]*?)<\/script>/g
  let m
  while ((m = re.exec(vue)) !== null) {
    if (m[1].trim()) blocks.push(m[1])
  }
  return blocks
}

function syntaxError(code) {
  try {
    // eslint-disable-next-line no-new-func
    new Function(toParsable(code))
    return null
  } catch (e) {
    return e.message
  }
}

const manifest = JSON.parse(readFileSync(MANIFEST, 'utf8'))
const bad = []
let checked = 0
let javaSkipped = 0

for (const entry of manifest) {
  for (const file of entry.files || []) {
    // Java 产物不吃 JS 解析器（语法级校验走 javac，见 GenSelfCheckTestCompileTest 的独立 classpath）
    if (file.endsWith('.java')) {
      javaSkipped++
      continue
    }
    const abs = path.join(entry.dir, file)
    if (!existsSync(abs)) {
      bad.push({ key: entry.key, file, reason: '产物文件缺失' })
      continue
    }
    const content = readFileSync(abs, 'utf8')
    const blocks = file.endsWith('.vue') ? scriptBlocks(content) : [content]
    if (blocks.length === 0) {
      bad.push({ key: entry.key, file, reason: '没有可校验的脚本段' })
      continue
    }
    for (const block of blocks) {
      checked++
      const err = syntaxError(block)
      if (err) {
        bad.push({ key: entry.key, file, reason: err, gates: entry.gates })
        break
      }
    }
  }
}

const summary = {
  combinations: manifest.length,
  scriptsChecked: checked,
  javaSkipped,
  badCount: bad.length,
  bad,
}

if (JSON_OUT) {
  console.log(JSON.stringify(summary, null, 2))
} else {
  console.log(`组合数 ${manifest.length}，校验脚本段 ${checked}，坏组合 ${bad.length}`
    + (javaSkipped ? `（跳过 Java 产物 ${javaSkipped} 个）` : ''))
  if (bad.length) {
    console.log('\n坏组合清单（前 30 条）：')
    for (const b of bad.slice(0, 30)) {
      console.log(`  ${b.key}  ${b.file}  ${b.reason}`)
    }
    if (bad.length > 30) console.log(`  ... 其余 ${bad.length - 30} 条省略`)
    // 按门控归因，便于定位是哪个开关组合触发的
    const byGate = new Map()
    for (const b of bad) {
      const g = b.gates || {}
      const tag = Object.entries(g).filter(([, v]) => v === true).map(([k]) => k).join('+') || '(全关)'
      byGate.set(tag, (byGate.get(tag) || 0) + 1)
    }
    console.log('\n按门控归因：')
    for (const [tag, n] of [...byGate.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10)) {
      console.log(`  ${n.toString().padStart(4)}  ${tag}`)
    }
  }
}

if (EXPECT_BAD !== null) {
  if (bad.length < EXPECT_BAD) {
    console.error(`\n反向验证失败：期望至少 ${EXPECT_BAD} 个坏组合，实际只有 ${bad.length}。`
      + '\n说明校验链路没生效（模板可能已被修好，或矩阵产物是旧的）。')
    process.exit(1)
  }
  console.log(`\n反向验证通过：坏组合 ${bad.length} ≥ 期望 ${EXPECT_BAD}（校验链路确实能抓到问题）`)
  process.exit(0)
}

if (bad.length > 0) {
  console.error('\nRESULT FAIL：存在语法错误的生成物')
  process.exit(1)
}
console.log('\nRESULT PASS：全部生成物 JS 语法正确')
