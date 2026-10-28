/**
 * 生成器写盘/回滚的策略推导。
 *
 * 为什么单独抽出来：这些规则原来是散在 wizard/index.vue（648 行）与
 * output/index.vue 里的 computed 与模板表达式，改一次策略只能靠手点页面确认。
 * 抽成纯函数后可以单测，页面只负责展示结果。
 *
 * 三条约定（与后端 DTO 严格对应，改这里必须同步 GenWriteDTO / GenRollbackVO）：
 * - skip      只写新增文件，已存在的一律不写
 * - safe      覆盖，但跳过「磁盘相对上次生成被改过」的文件（skipDrifted=true）
 * - overwrite 强制覆盖，不看漂移
 */

export const STRATEGY_SKIP = 'skip'
export const STRATEGY_SAFE = 'safe'
export const STRATEGY_OVERWRITE = 'overwrite'

export const STRATEGY_OPTIONS = [
  { value: STRATEGY_SKIP, label: '同名跳过（推荐）' },
  { value: STRATEGY_SAFE, label: '覆盖，但跳过已手改' },
  { value: STRATEGY_OVERWRITE, label: '强制覆盖' }
]

/**
 * 策略 → 写盘请求参数。
 * 未知策略按 skip 处理：宁可少写，也不能因为参数写错把整个工程的代码覆盖掉。
 */
export function resolveWriteFlags(mode) {
  switch (mode) {
    case STRATEGY_OVERWRITE:
      // V6.8：强制覆盖连「受保护文件」（既有但无台账快照，可能手写）一起覆盖。
      // 只有这一档传 force=true——另外两档必须保留保护语义，否则手写代码会被静默吃掉。
      return { overwrite: true, skipDrifted: false, force: true }
    case STRATEGY_SAFE:
      return { overwrite: true, skipDrifted: true, force: false }
    case STRATEGY_SKIP:
    default:
      return { overwrite: false, skipDrifted: false, force: false }
  }
}

/**
 * 受保护文件数：存在但台账无快照（不是本平台写的，或台账丢失），
 * 无法判断是否手写 → 非 force 策略下默认跳过。
 */
export function countProtected(rows) {
  return asRows(rows).filter(r => r.protectedFile === true).length
}

/** 磁盘相对上次生成被改过的文件数 */
export function countDrifted(rows) {
  return asRows(rows).filter(r => r.drift === 'DRIFTED').length
}

/**
 * 保守覆盖下会被跳过的已手改文件数：只统计本来就要覆盖的。
 * NEW 文件在磁盘上不存在，谈不上漂移，不能算进跳过数。
 */
export function countDriftedOverwrite(rows) {
  return asRows(rows).filter(r => r.status !== 'NEW' && r.drift === 'DRIFTED').length
}

/** 当前策略下会跳过多少个文件 */
export function countSkipped(rows, mode) {
  const list = asRows(rows)
  if (mode === STRATEGY_SKIP) {
    return list.filter(r => r.status !== 'NEW').length
  }
  if (mode === STRATEGY_SAFE) {
    return countDriftedOverwrite(list)
  }
  return 0
}

/** 策略说明文案：数字必须来自真实行数据，不能写死 */
export function strategyNote(mode, driftCount) {
  const drifted = Number(driftCount) || 0
  if (mode === STRATEGY_SAFE) {
    return drifted
      ? `覆盖模式（保守）：已存在文件先备份再写入；其中 ${drifted} 个文件相对上次生成被手改过，将按策略跳过并在结果里标注「已手改」。`
      : '覆盖模式（保守）：已存在文件先备份再写入；当前没有检测到手改文件，行为等同强制覆盖。'
  }
  if (mode === STRATEGY_OVERWRITE) {
    return '强制覆盖：不看漂移，全部按合并规则写入，手改内容可能在块内被替换（备份仍保留，可回滚）。'
  }
  return '跳过模式：已存在文件一律不写，只有新增文件落盘（切「覆盖」可查看会改动哪些文件）。'
}

/**
 * 回滚弹窗的默认勾选：会真动文件、且没被手改的行。
 * 手改文件交给用户自己勾——默认勾上等于替他决定「覆盖手写代码」。
 */
export function rollbackDefaultSelection(items) {
  return asItems(items).filter(r => isRollbackSelectable(r) && !r.drifted)
}

/** SKIP 行没有可执行动作，勾了也不会生效 */
export function isRollbackSelectable(item) {
  return !!item && item.op !== 'SKIP'
}

/** 回滚动作标签文案（后端 op 只有三种） */
export function opText(op) {
  return { RESTORE: '还原', DELETE: '删除', SKIP: '跳过' }[op] || op || ''
}

/** 回滚动作标签颜色（Element UI tag type） */
export function opTagType(op) {
  return { RESTORE: 'warning', DELETE: 'danger', SKIP: 'info' }[op] || 'info'
}

/** 确认回滚时的提示文案，包含「有几个会被覆盖的手改」这个关键风险 */
export function rollbackConfirmText(picked) {
  const list = asItems(picked)
  const drifted = list.filter(r => r.drifted).length
  return `将对 ${list.length} 个文件执行回滚，其中 ${drifted} 个已被手改（会被覆盖）。`
}

function asRows(rows) {
  return Array.isArray(rows) ? rows.filter(Boolean) : []
}

function asItems(items) {
  return Array.isArray(items) ? items.filter(Boolean) : []
}
