import request, { toPageData } from '@/utils/request'

// 代码生成接口：管理服务 shop-service-admin（经网关 /api/gen）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/* ---------- 表配置 ---------- */

// 表配置分页
export function getGenTablePage(params) {
  return request({
    url: '/api/gen/table/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

// 表配置详情（含字段列）
export function getGenTableDetail(id) {
  return request({
    url: `/api/gen/table/${id}`,
    method: 'get'
  })
}

// 新建表配置（overwrite=true 覆盖同名配置）
export function createGenTable(data) {
  return request({
    url: '/api/gen/table',
    method: 'post',
    data
  })
}

// 更新表配置（含字段列）
export function updateGenTable(id, data) {
  return request({
    url: '/api/gen/table',
    method: 'put',
    data: { ...data, id }
  })
}

// 删除表配置
export function deleteGenTable(id) {
  return request({
    url: `/api/gen/table/${id}`,
    method: 'delete'
  })
}

/* ---------- DDL / 数据库导入 ---------- */

// 解析 CREATE TABLE 语句
export function parseDdl(ddl) {
  return request({
    url: '/api/gen/parseDdl',
    method: 'post',
    data: { ddl }
  })
}

// 生成输出全局配置（后端/前端根目录等，表级留空时生效）
export function getGenOutputConfig() {
  return request({
    url: '/api/gen/output-config',
    method: 'get'
  })
}

// 数据库 schema 列表
export function listDbSchemas() {
  return request({
    url: '/api/gen/db/schemas',
    method: 'get'
  })
}

// 指定库下的表清单 [{ name, comment }]
export function listDbTables(schema) {
  return request({
    url: '/api/gen/db/tables',
    method: 'get',
    params: { schema }
  })
}

// 批量导入数据库表，返回 [{ tableName, status, message }]
export function importDbTables(schema, tables) {
  return request({
    url: '/api/gen/db/import',
    method: 'post',
    data: { schema, tableNames: tables }
  })
}

/* ---------- 预览 / 写盘 / 下载 ---------- */

// 生成预览，返回 [{ path, content, tableName }]
export function previewGenCode(ids) {
  return request({
    url: '/api/gen/preview',
    method: 'post',
    data: { ids }
  })
}

// 写入磁盘，返回 [{ path, status, message }]
export function writeGenCode(ids, overwrite) {
  return request({
    url: '/api/gen/write',
    method: 'post',
    data: { ids, overwrite }
  })
}

// 下载 ZIP（blob 流，浏览器触发下载），返回文件清单（后端如经响应头提供可扩展）
export async function downloadGenCode(ids) {
  const response = await request({
    url: '/api/gen/download',
    method: 'post',
    data: { ids },
    responseType: 'blob'
  })
  const blob = new Blob([response.data], { type: 'application/zip' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'gen_code.zip'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
  return []
}

/* ---------- 生成自检 ---------- */

// 自检：内存编译生成的 Java 源码 + 结构检查
export function selfCheckGenCode(ids) {
  return request({
    url: '/api/gen/self-check',
    method: 'post',
    data: { ids }
  })
}

/* ---------- 菜单注册 ---------- */

// 注册菜单与按钮权限（幂等），同时重建前端路由文件
export function registerGenMenu(data) {
  return request({
    url: '/api/gen/menu/register',
    method: 'post',
    data
  })
}

// 仅重建前端路由文件
export function syncGenRoute(dryRun) {
  return request({
    url: '/api/gen/menu/route-sync',
    method: 'post',
    params: { dryRun: !!dryRun }
  })
}

/* ---------- V1.2：一键生成 / 批量 / 回滚 ---------- */

// 一键生成：自检 → 备份 → 写盘 →（编译）→ 注册菜单 → 重建路由
export function applyGenCode(data) {
  return request({
    url: '/api/gen/apply',
    method: 'post',
    data
  })
}

// 回滚某个写盘批次（有备份的还原，首次生成的文件删除）
export function rollbackGenBatch(batchNo) {
  return request({
    url: '/api/gen/history/rollback',
    method: 'post',
    params: { batchNo }
  })
}

// 最近的写盘批次
export function listGenBatches(limit = 10) {
  return request({
    url: '/api/gen/history/batches',
    method: 'get',
    params: { limit }
  })
}

// 批量设置输出参数 / 套用开关预设
export function batchUpdateGenTables(data) {
  return request({
    url: '/api/gen/table/batch',
    method: 'post',
    data
  })
}

// 批量删除表配置（连同菜单）
export function batchDeleteGenTables(ids) {
  return request({
    url: '/api/gen/table/batch-delete',
    method: 'post',
    data: { ids }
  })
}

/* ---------- V1.3：diff 预览 / 结构同步 ---------- */

// 写盘前的行级差异预览，返回 [{ tableName, path, absPath, status, additions, deletions, lines }]
export function diffGenCode(ids, overwrite = true, onlyChanged = true) {
  return request({
    url: '/api/gen/diff',
    method: 'post',
    data: { ids, overwrite, onlyChanged }
  })
}

// 结构漂移同步：把库里新增的列补进字段配置（removeMissing=true 时才删多余配置）
export function syncGenColumns(id, removeMissing = false) {
  return request({
    url: `/api/gen/table/${id}/sync-columns`,
    method: 'post',
    params: { removeMissing }
  })
}
