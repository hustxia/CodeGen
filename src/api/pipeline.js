import request, { toPageData } from '@/utils/request'

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/* ---------- V5.0 一句话建系统 ---------- */

// 向导配置：AI 建模是否可用（未配 LLM key 时前端直接禁用 AI 选项）
export function getConfig() {
  return request({ url: '/gen/pipeline/config', method: 'get' })
}

// AI 领域建模：名称+一句话描述 → 候选表结构（moduleName 非空时只设计该模块的表，供完整系统模式逐模块调用）
// timeout 对齐后端 gen.pipeline.ai.timeout-seconds（本地 Ollama 纯 CPU 出 JSON 要 3-5 分钟，
// 60s 会在模型还在跑时报错"挂了"，实际后端正常完成——2026-10-25 实测坑）
export function aiModel(data) {
  return request({
    url: '/gen/pipeline/ai-model',
    method: 'post',
    data,
    timeout: 600000
  })
}

// AI 模块拆分：系统 → 业务模块清单（完整系统模式第一步，之后逐模块调 aiModel 再前端合并）
export function aiModules(data) {
  return request({
    url: '/gen/pipeline/ai-modules',
    method: 'post',
    data,
    timeout: 600000
  })
}

// V9.4 AI 模式 DDL 只读预览：与 prepare-db 共用服务端组装器，不创建任务、不写数据库
export function previewPipelineDdl(data) {
  return request({ url: '/gen/pipeline/ddl-preview', method: 'post', data })
}

// 创建流水线任务（建模结果一并落库）
export function createPipeline(data) {
  return request({ url: '/gen/pipeline', method: 'post', data })
}

// 建库建表 + 建系统台账 + 导入表配置
export function preparePipelineDb(id, data) {
  return request({ url: `/gen/pipeline/${id}/prepare-db`, method: 'post', data })
}

// 启动流水线（异步，进度走 SSE）
export function runPipeline(id) {
  return request({ url: `/gen/pipeline/${id}/run`, method: 'post' })
}

// 任务详情（头 + 全部步骤）
export function getPipeline(id) {
  return request({ url: `/gen/pipeline/${id}`, method: 'get' })
}

// M2.2 质量门禁报告详情：行级 ❌→✅
export function getQualityReport(reportId) {
  return request({ url: `/gen/pipeline/quality-report/${reportId}`, method: 'get' })
}

// 任务分页
export function getPipelinePage(params) {
  return request({ url: '/gen/pipeline/page', method: 'post', data: toPageQuery(params) }).then(toPageData)
}

// 停止：杀双进程并置 stopped
export function stopPipeline(id) {
  return request({ url: `/gen/pipeline/${id}/stop`, method: 'post' })
}

// 生成前置 lint：目标库审计列检查（只读 dry-run）
export function lintDb(dbName) {
  return request({ url: '/gen/pipeline/lint-db', method: 'post', data: { dbName } })
}

// lint 一键补齐（V5.1）：逐表 ALTER 补缺审计列，幂等，执行后复检
export function fixPipelineLint(id) {
  return request({ url: `/gen/pipeline/${id}/lint/fix`, method: 'post' })
}

// V5.2 数据初始化：存量系统补跑 seed（只补台账中 0 行的表，幂等）
export function seedPipeline(id) {
  return request({ url: `/gen/pipeline/${id}/seed`, method: 'post', timeout: 300000 })
}

// V7.0 AI 建议预览：与现值 diff（只读，零写库）
export function aiRecommendPreview(id) {
  return request({ url: `/gen/pipeline/${id}/ai-recommend/preview`, method: 'get', timeout: 300000 })
}

// V7.0 AI 建议应用：只传勾选清单，建议值以服务端产物为准
export function aiRecommendApply(id, data) {
  return request({ url: `/gen/pipeline/${id}/ai-recommend/apply`, method: 'post', data, timeout: 300000 })
}

/* ---------- V9.5 AI 建表推荐（独立模块，不动 V9.4） ---------- */

// V9.5 配置：AI 建模是否可用
export function aiBuildConfig() {
  return request({ url: '/gen/ai-build/config', method: 'post' })
}

// V9.5 AI 推荐表设计：规模分批建模 + 关系视图（单表/主从/树形）；只读
export function aiBuildPlan(data) {
  return request({ url: '/gen/ai-build/plan', method: 'post', data, timeout: 600000 })
}

// V9.9 行业模板清单（250+ 套，轻量：不含表明细）
export function aiBuildTemplates() {
  return request({ url: '/gen/ai-build/templates', method: 'get' })
}

// V9.9 行业模板分类清单（供分组筛选）
export function aiBuildTemplateCategories() {
  return request({ url: '/gen/ai-build/template-categories', method: 'get' })
}

// V9.9 行业模板明细（含模块与表结构，按需加载）
export function aiBuildTemplateDetail(key) {
  return request({ url: `/gen/ai-build/templates/${encodeURIComponent(key)}`, method: 'get' })
}

// V9.7 行业模板建表：克隆模板表结构（不调 LLM），走与 AI 相同的收口/编辑/落库链路
export function aiBuildPlanFromTemplate(data) {
  return request({ url: '/gen/ai-build/plan/template', method: 'post', data })
}

// V9.5 同源 DDL 预览：委托 V9.4 previewDdl
export function aiBuildDdlPreview(data) {
  return request({ url: '/gen/ai-build/ddl-preview', method: 'post', data })
}

/* ---------- V9.6 大型/超大型：分批建模任务 ---------- */

// 提交任务：立即返回 jobId，后台按模块分批并发建模
export function aiBuildPlanJob(data) {
  return request({ url: '/gen/ai-build/plan/job', method: 'post', data, timeout: 60000 })
}

// 进度轮询（含批次明细）
export function aiBuildJobStatus(id) {
  return request({ url: `/gen/ai-build/job/${id}`, method: 'get' })
}

// 取最终结果（未完成会返回业务错误）
export function aiBuildJobResult(id) {
  return request({ url: `/gen/ai-build/job/${id}/result`, method: 'get' })
}

// 取消任务
export function aiBuildJobCancel(id) {
  return request({ url: `/gen/ai-build/job/${id}/cancel`, method: 'post' })
}

// V9.5 确认落库/建库：draft=草稿 build=建库；micro=逐模块微服务拆分
export function aiBuildConfirm(data) {
  return request({ url: '/gen/ai-build/confirm', method: 'post', data, timeout: 600000 })
}
