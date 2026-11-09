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
