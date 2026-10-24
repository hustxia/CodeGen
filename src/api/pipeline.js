import request, { toPageData } from '@/utils/request'

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/* ---------- V5.0 一句话建系统 ---------- */

// AI 领域建模：名称+一句话描述 → 候选表结构
export function aiModel(data) {
  return request({
    url: '/gen/pipeline/ai-model',
    method: 'post',
    data,
    timeout: 60000
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

// 生成前置 lint：目标库审计列检查
export function lintDb(dbName) {
  return request({ url: '/gen/pipeline/lint-db', method: 'post', data: { dbName } })
}
