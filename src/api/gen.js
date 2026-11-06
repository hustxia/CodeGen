import request, { toPageData } from '@/utils/request'

// 代码生成接口：codegen-server 单体服务（直接 /gen）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/* ---------- 表配置 ---------- */

// 表配置分页
export function getGenTablePage(params) {
  return request({
    url: '/gen/table/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

// 表配置详情（含字段列）
export function getGenTableDetail(id) {
  return request({
    url: `/gen/table/${id}`,
    method: 'get'
  })
}

// 新建表配置（overwrite=true 覆盖同名配置）
export function createGenTable(data) {
  return request({
    url: '/gen/table',
    method: 'post',
    data
  })
}

// 更新表配置（含字段列）
export function updateGenTable(id, data) {
  return request({
    url: '/gen/table',
    method: 'put',
    data: { ...data, id }
  })
}

// 删除表配置
export function deleteGenTable(id) {
  return request({
    url: `/gen/table/${id}`,
    method: 'delete'
  })
}

/* ---------- DDL / 数据库导入 ---------- */

// 解析 CREATE TABLE 语句
export function parseDdl(ddl) {
  return request({
    url: '/gen/parseDdl',
    method: 'post',
    data: { ddl }
  })
}

// 生成输出全局配置（后端/前端根目录等，表级留空时生效）
export function getGenOutputConfig() {
  return request({
    url: '/gen/output-config',
    method: 'get'
  })
}

// 数据库 schema 列表
export function listDbSchemas() {
  return request({
    url: '/gen/db/schemas',
    method: 'get'
  })
}

// 指定库下的表清单 [{ name, comment }]
export function listDbTables(schema) {
  return request({
    url: '/gen/db/tables',
    method: 'get',
    params: { schema }
  })
}

// 批量导入数据库表，返回 [{ tableName, status, message }]
export function importDbTables(schema, tables, projectId) {
  return request({
    url: '/gen/db/import',
    method: 'post',
    data: { schema, tableNames: tables, projectId }
  })
}

/* ---------- 预览 / 写盘 / 下载 ---------- */

// 生成预览，返回 [{ path, content, tableName }]
export function previewGenCode(ids) {
  return request({
    url: '/gen/preview',
    method: 'post',
    data: { ids }
  })
}

// 写入磁盘，返回 [{ path, status, message, backupPath }]
// skipDrifted：只写未漂移文件（磁盘已被手改的一律 SKIPPED），V4.2
export function writeGenCode(ids, overwrite, skipDrifted, force) {
  return request({
    url: '/gen/write',
    method: 'post',
    // force 只在「强制覆盖」策略下为 true：覆盖受保护文件（既有但无台账快照）
    data: { ids, overwrite, skipDrifted: !!skipDrifted, force: !!force }
  })
}

// 下载 ZIP（blob 流，浏览器触发下载），返回文件清单（后端如经响应头提供可扩展）
export async function downloadGenCode(ids) {
  const response = await request({
    url: '/gen/download',
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
    url: '/gen/self-check',
    method: 'post',
    data: { ids }
  })
}

/* ---------- 菜单注册 ---------- */

// 注册菜单与按钮权限（幂等），同时重建前端路由文件
export function registerGenMenu(data) {
  return request({
    url: '/gen/menu/register',
    method: 'post',
    data
  })
}

// 仅重建前端路由文件
export function syncGenRoute(dryRun) {
  return request({
    url: '/gen/menu/route-sync',
    method: 'post',
    params: { dryRun: !!dryRun }
  })
}

/* ---------- V1.2：一键生成 / 批量 / 回滚 ---------- */

// 一键生成：自检 → 备份 → 写盘 →（编译）→ 注册菜单 → 重建路由
export function applyGenCode(data) {
  return request({
    url: '/gen/apply',
    method: 'post',
    data
  })
}

// 回滚某个写盘批次（有备份的还原，首次生成的文件删除）
// payload: { ledgerIds?: [], paths?: [], skipDrifted?: bool }，不传即整批
export function rollbackGenBatch(batchNo, payload) {
  return request({
    url: '/gen/history/rollback',
    method: 'post',
    params: { batchNo },
    data: payload || {}
  })
}

// 回滚预览（dry-run）：返回将还原/将删除/将跳过与明细，不落盘
export function previewGenRollback(batchNo, payload) {
  return request({
    url: '/gen/history/rollback/preview',
    method: 'post',
    params: { batchNo },
    data: payload || {}
  })
}

// 产物漂移检测：磁盘内容 vs 台账 md5
export function scanGenDrift(ids) {
  return request({
    url: '/gen/drift',
    method: 'post',
    data: { ids: ids || null }
  })
}

// 最近的写盘批次
export function listGenBatches(limit = 10) {
  return request({
    url: '/gen/history/batches',
    method: 'get',
    params: { limit }
  })
}

// 批量设置输出参数 / 套用开关预设
export function batchUpdateGenTables(data) {
  return request({
    url: '/gen/table/batch',
    method: 'post',
    data
  })
}

// 批量删除表配置（连同菜单）
export function batchDeleteGenTables(ids) {
  return request({
    url: '/gen/table/batch-delete',
    method: 'post',
    data: { ids }
  })
}

/* ---------- V1.3：diff 预览 / 结构同步 ---------- */

// 写盘前的行级差异预览，返回 [{ tableName, path, absPath, status, additions, deletions, lines }]
export function diffGenCode(ids, overwrite = true, onlyChanged = true) {
  return request({
    url: '/gen/diff',
    method: 'post',
    data: { ids, overwrite, onlyChanged }
  })
}

// 结构漂移同步：把库里新增的列补进字段配置（removeMissing=true 时才删多余配置）
export function syncGenColumns(id, removeMissing = false) {
  return request({
    url: `/gen/table/${id}/sync-columns`,
    method: 'post',
    params: { removeMissing }
  })
}

/* ---------- P2-3：字典自动识别 ---------- */

// 识别某张表里可转成字典的列（只读，不落库）
export function detectGenDict(tableId) {
  return request({ url: '/gen/dict/detect', method: 'get', params: { tableId } })
}

// DDL 内联识别：导入前就能预览这张表会带出哪些字典
export function detectGenDictFromDdl(ddl, tableName) {
  return request({ url: '/gen/dict/detect-ddl', method: 'post', data: { ddl, tableName } })
}

// 落库：建字典类型 + 字典项，并回填 gen_table_column.dict_type
export function applyGenDict(tableId, dictCodes) {
  return request({ url: '/gen/dict/apply', method: 'post', data: { tableId, dictCodes } })
}

// DDL 文本一键导入：不执行 DDL，直接解析表/列/注释注册生成配置并自动建字典，
// 返回逐表校验报告（列数比对、缺注释、字典漏建预警）
export function importFromDdl(ddl, applyDict = true) {
  return request({ url: '/gen/ddl/import', method: 'post', data: { ddl, applyDict } })
}

/* ---------- P2-5：接口目录 ---------- */

// 按表聚合接口清单（权限码、前端方法名与真实生成物一致）
export function getApiCatalog(keyword) {
  return request({ url: '/gen/api-catalog', method: 'get', params: { keyword } })
}

/* ---------- P3-2：多对多中间表 ---------- */

// 扫描候选中间表（只读）：两个外键 + 联合唯一键 + 命名匹配
export function detectRelation(schema) {
  return request({ url: '/gen/relation/detect', method: 'get', params: { schema } })
}

// 标记为中间表：relation_type=middle，两个外键列 relation_key=1 并转成关联下拉
export function applyRelation(schema, middleTables) {
  return request({ url: '/gen/relation/apply', method: 'post', data: { schema, middleTables } })
}

/* ---------- V3.0：多系统台账 ---------- */

// 全部系统（含停用），管理页用
export function listProject() {
  return request({ url: '/project/list', method: 'get' })
}

// 启用中的系统，新建表的下拉用
export function listProjectOptions() {
  return request({ url: '/project/options', method: 'get' })
}

// 默认系统（新建表时预选）
export function getDefaultProject() {
  return request({ url: '/project/default', method: 'get' })
}

// 新建系统。copyFrom 非空时复制该系统的路径配置与生成开关默认值
// （编码、名称、根包名、网关前缀不复制——那两个属于系统身份，抄过来会生成两套同名包）
export function createProject(data, copyFrom) {
  return request({
    url: '/project',
    method: 'post',
    params: copyFrom ? { copyFrom } : {},
    data
  })
}

export function updateProject(data) {
  return request({ url: '/project', method: 'put', data })
}

export function deleteProject(id) {
  return request({ url: `/project/${id}`, method: 'delete' })
}

export function setDefaultProject(id) {
  return request({ url: `/project/${id}/default`, method: 'put' })
}

// 17 个生成开关恢复所属系统默认值（系统没配则按 1）
export function resetGenSwitchDefaults(id) {
  return request({ url: `/gen/table/${id}/switch-defaults/reset`, method: 'post' })
}

/* ---------- V7.3 P0-3 表级 AI 推荐 ---------- */

// 选中表直接出建议 diff（只读，零写库）；返回体带 token，应用时回传
export function aiRecommendTables(data) {
  return request({ url: '/gen/ai-recommend/tables/preview', method: 'post', data, timeout: 300000 })
}

// 应用勾选项：只传 token + 勾选清单，建议值以服务端产物为准
export function aiRecommendTablesApply(data) {
  return request({ url: '/gen/ai-recommend/tables/apply', method: 'post', data, timeout: 300000 })
}
