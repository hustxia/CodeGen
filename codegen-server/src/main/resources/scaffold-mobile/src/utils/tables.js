// 移动端表配置聚合：生成侧把每张表的声明式配置写进 src/config/tables/{module}/{table}.js，
// 这里用 import.meta.glob 一次性收编——新增表零注册，pages.json 恒定不重建（V6.5 设计定案）
const mods = import.meta.glob('../config/tables/**/*.js', { eager: true, import: 'default' })

export const allTables = Object.values(mods).filter((c) => c && c.table)

export function findTable(moduleName, tableName) {
  return allTables.find((c) => c.module === moduleName && c.table === tableName)
}

// 列显示值：枚举列按映射翻标签，其余原样（字典列暂显原值，V6.5 M1 口径）
export function displayValue(col, value) {
  if (value === null || value === undefined || value === '') return '-'
  if (col && Array.isArray(col.enum)) {
    const hit = col.enum.find((o) => String(o.value) === String(value))
    if (hit) return hit.label
  }
  return String(value)
}
