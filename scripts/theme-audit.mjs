/**
 * 240 套主题对比度自检
 *
 * 为什么要有这个脚本：
 * 主题是「参数组合」生成出来的，人不可能逐套肉眼验收。
 * 但可读性是有客观标准的（WCAG 2.1），因此把验收变成断言：
 * 任意一套主题只要正文/按钮/边框对比度不达标，构建阶段就该报出来。
 *
 * 用法：node scripts/theme-audit.mjs
 * 退出码 0 = 全部通过；1 = 存在不达标项（可用于 CI 卡点）
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const themeSrc = path.join(__dirname, '../src/utils/theme.js')
const tmpFile = path.join(__dirname, '../src/utils/.__theme_audit.mjs')

// Node 会把 .js 按 package.json 的 type 解析成 CJS，这里复制成 .mjs 再动态 import
fs.copyFileSync(themeSrc, tmpFile)
const mod = await import(pathToFileURL(tmpFile).href)
let report
try {
  report = audit(mod)
} finally {
  fs.unlinkSync(tmpFile)
}

function audit({ HUES, SHAPES, MODES, buildThemeVars, contrastRatio }) {
  /* 校验项：名称 + 取色函数 + 门槛 */
  const CHECKS = [
    {
      name: '正文/容器',
      fg: v => v['--cg-text'],
      bg: v => v['--cg-bg-container'],
      min: 4.5,
      why: 'WCAG AA 正文标准'
    },
    {
      name: '常规文字/页面底',
      fg: v => v['--cg-text-regular'],
      bg: v => v['--cg-bg-page'],
      min: 4.5,
      why: '表格与列表的正文'
    },
    {
      name: '按钮文字/主色',
      fg: v => v['--cg-primary-text'],
      bg: v => v['--cg-primary-solid'],
      min: 4.5,
      why: '主按钮上的文字必须看得清'
    },
    {
      name: '次要文字/容器',
      fg: v => v['--cg-text-secondary'],
      bg: v => v['--cg-bg-container'],
      min: 3.0,
      why: '辅助说明文字，AA 大字号标准'
    },
    {
      name: '占位符/输入框',
      fg: v => v['--cg-text-placeholder'],
      bg: v => v['--cg-input-bg'],
      min: 2.2,
      why: '占位符允许更弱，但不能隐形'
    },
    {
      name: '侧栏文字/侧栏底',
      fg: v => v['--cg-aside-text'],
      bg: v => v['--cg-aside-from'],
      min: 3.0,
      why: '深色侧栏上的菜单文字'
    },
    {
      name: '边框/容器',
      fg: v => v['--cg-border'],
      bg: v => v['--cg-bg-container'],
      min: 1.15,
      why: '输入框与表格线必须能看见'
    },
    {
      name: '成功色/容器',
      fg: v => v['--cg-success'],
      bg: v => v['--cg-bg-container'],
      min: 3.0,
      why: '状态标签可辨识'
    },
    {
      name: '危险色/容器',
      fg: v => v['--cg-danger'],
      bg: v => v['--cg-bg-container'],
      min: 3.0,
      why: '删除按钮可辨识'
    }
  ]

  const failures = []
  const worst = {}
  let total = 0

  for (const h of HUES) {
    for (const m of MODES) {
      for (const s of Object.keys(SHAPES)) {
        total++
        const id = `${h.id}-${m.id}-${s}`
        const v = buildThemeVars(h.id, m.id, s)

        for (const c of CHECKS) {
          const ratio = contrastRatio(c.fg(v), c.bg(v))
          const key = c.name
          if (!worst[key] || ratio < worst[key].ratio) {
            worst[key] = { ratio, id }
          }
          if (ratio < c.min) {
            failures.push({
              id,
              check: c.name,
              ratio: Number(ratio.toFixed(2)),
              min: c.min,
              fg: c.fg(v),
              bg: c.bg(v)
            })
          }
        }
      }
    }
  }

  return { total, failures, worst, checks: CHECKS }
}

/* ---------------- 输出 ---------------- */
const { total, failures, worst, checks } = report

console.log(`\n主题对比度自检：共 ${total} 套 × ${checks.length} 项断言\n`)
console.log('各项最低值：')
for (const c of checks) {
  const w = worst[c.name]
  const ok = w.ratio >= c.min
  console.log(
    `  ${ok ? '✓' : '✗'} ${c.name.padEnd(8, '　')} 最低 ${String(w.ratio.toFixed(2)).padStart(5)} / ${c.min}  ` +
    `(${w.id})`
  )
}

if (failures.length === 0) {
  console.log('\n全部通过。\n')
  process.exit(0)
}

/* 按检查项聚合，方便判断是「个别主题」还是「系统性缺陷」 */
const byCheck = {}
for (const f of failures) {
  byCheck[f.check] = byCheck[f.check] || []
  byCheck[f.check].push(f)
}

console.log(`\n不达标 ${failures.length} 条：`)
for (const [check, list] of Object.entries(byCheck)) {
  console.log(`\n【${check}】${list.length} 套`)
  list.slice(0, 8).forEach(f => {
    console.log(`  ${f.id.padEnd(26)} ${f.ratio} < ${f.min}   ${f.fg} on ${f.bg}`)
  })
  if (list.length > 8) console.log(`  ...另有 ${list.length - 8} 套同类问题`)
}

console.log('')
process.exit(1)
