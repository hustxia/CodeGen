import { describe, it, expect } from 'vitest'
import {
  STRATEGY_SKIP,
  STRATEGY_SAFE,
  STRATEGY_OVERWRITE,
  resolveWriteFlags,
  countDrifted,
  countDriftedOverwrite,
  countSkipped,
  countProtected,
  strategyNote,
  rollbackDefaultSelection,
  isRollbackSelectable,
  rollbackConfirmText,
  opText,
  opTagType
} from '../src/utils/genStrategy.js'

const row = (status, drift) => ({ status, drift })

/** 一份覆盖三种形态的差异数据：新增 / 已存在未改 / 已存在被手改 */
const ROWS = [
  row('NEW', 'MISSING'),
  row('NEW', 'UNKNOWN'),
  row('MODIFIED', undefined),
  row('MODIFIED', 'DRIFTED'),
  row('UNCHANGED', 'SAME'),
  row('UNCHANGED', 'DRIFTED')
]

describe('resolveWriteFlags：策略 → 请求参数', () => {
  it('skip 只写新增', () => {
    expect(resolveWriteFlags(STRATEGY_SKIP)).toEqual({ overwrite: false, skipDrifted: false, force: false })
  })

  it('safe 覆盖但跳过已手改', () => {
    expect(resolveWriteFlags(STRATEGY_SAFE)).toEqual({ overwrite: true, skipDrifted: true, force: false })
  })

  it('overwrite 强制覆盖，不看漂移，并连受保护文件一起覆盖', () => {
    expect(resolveWriteFlags(STRATEGY_OVERWRITE)).toEqual({ overwrite: true, skipDrifted: false, force: true })
  })

  it('未知策略按 skip 处理，宁可少写', () => {
    expect(resolveWriteFlags('whatever')).toEqual({ overwrite: false, skipDrifted: false, force: false })
    expect(resolveWriteFlags(undefined)).toEqual({ overwrite: false, skipDrifted: false, force: false })
  })
})

describe('漂移计数', () => {
  it('只统计 DRIFTED', () => {
    expect(countDrifted(ROWS)).toBe(2)
  })

  it('空输入不抛异常', () => {
    expect(countDrifted(null)).toBe(0)
    expect(countDrifted([])).toBe(0)
  })

  it('保守覆盖只统计本来就要覆盖的已手改文件（NEW 不算）', () => {
    expect(countDriftedOverwrite(ROWS)).toBe(2)
    expect(countDriftedOverwrite([row('NEW', 'DRIFTED')])).toBe(0)
  })
})

describe('countSkipped：跳过数随策略变化', () => {
  it('skip 模式下非 NEW 的一律跳过', () => {
    expect(countSkipped(ROWS, STRATEGY_SKIP)).toBe(4)
  })

  it('safe 模式下只跳过已手改的已存在文件', () => {
    expect(countSkipped(ROWS, STRATEGY_SAFE)).toBe(2)
  })

  it('强制覆盖下不跳过任何文件', () => {
    expect(countSkipped(ROWS, STRATEGY_OVERWRITE)).toBe(0)
  })
})

describe('strategyNote：文案里的数字必须来自真实数据', () => {
  it('safe + 有手改文件时点出数量', () => {
    expect(strategyNote(STRATEGY_SAFE, 3)).toContain('3 个文件')
  })

  it('safe + 无手改文件时说明等同强制覆盖', () => {
    expect(strategyNote(STRATEGY_SAFE, 0)).toContain('等同强制覆盖')
  })

  it('三种策略文案互不相同', () => {
    const notes = [
      strategyNote(STRATEGY_SKIP, 0),
      strategyNote(STRATEGY_SAFE, 0),
      strategyNote(STRATEGY_OVERWRITE, 0)
    ]
    expect(new Set(notes).size).toBe(3)
  })
})

describe('回滚默认勾选', () => {
  const items = [
    { op: 'RESTORE', drifted: false },
    { op: 'RESTORE', drifted: true },
    { op: 'DELETE', drifted: false },
    { op: 'SKIP', drifted: false }
  ]

  it('只勾会动文件且未被手改的行', () => {
    expect(rollbackDefaultSelection(items).length).toBe(2)
    expect(rollbackDefaultSelection(items).every(i => i.op !== 'SKIP' && !i.drifted)).toBe(true)
  })

  it('空输入返回空数组', () => {
    expect(rollbackDefaultSelection(null)).toEqual([])
  })

  it('SKIP 行不可勾选', () => {
    expect(isRollbackSelectable({ op: 'SKIP' })).toBe(false)
    expect(isRollbackSelectable({ op: 'RESTORE' })).toBe(true)
    expect(isRollbackSelectable(null)).toBe(false)
  })
})

describe('回滚确认文案', () => {
  it('统计被手改的数量', () => {
    const text = rollbackConfirmText([
      { op: 'RESTORE', drifted: true },
      { op: 'RESTORE', drifted: false }
    ])
    expect(text).toContain('2 个文件')
    expect(text).toContain('1 个已被手改')
  })

  it('空选也给出可读文案', () => {
    expect(rollbackConfirmText([])).toContain('0 个文件')
  })
})

describe('回滚动作展示', () => {
  it('三种动作都有中文与颜色', () => {
    expect(opText('RESTORE')).toBe('还原')
    expect(opText('DELETE')).toBe('删除')
    expect(opText('SKIP')).toBe('跳过')
    expect(opTagType('RESTORE')).toBe('warning')
    expect(opTagType('DELETE')).toBe('danger')
    expect(opTagType('SKIP')).toBe('info')
  })

  it('未知动作原样展示，不显示 undefined', () => {
    expect(opText('UNKNOWN')).toBe('UNKNOWN')
    expect(opTagType('UNKNOWN')).toBe('info')
    expect(opText(undefined)).toBe('')
  })
})


describe('countProtected：受保护文件（既有 + 无台账快照）', () => {
  it('只统计 protectedFile=true 的行', () => {
    const rows = [
      { status: 'MODIFIED', protectedFile: true },
      { status: 'MODIFIED', protectedFile: false },
      { status: 'NEW' },
      null
    ]
    expect(countProtected(rows)).toBe(1)
  })

  it('非数组/空值不抛错', () => {
    expect(countProtected(null)).toBe(0)
    expect(countProtected([])).toBe(0)
  })
})
