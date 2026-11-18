import request from '@/utils/request'

// V6.1 审批流全局接口（前端统一走 body 风格 /flow 端点，与业务 controller 的路径风格互补）
export function flowSubmit(flowCode, bizId, comment) {
  return request({ url: '/flow/submit', method: 'post', data: { flowCode, bizId, comment } })
}

export function flowApprove(flowCode, bizId, comment) {
  return request({ url: '/flow/approve', method: 'post', data: { flowCode, bizId, comment } })
}

export function flowReject(flowCode, bizId, comment) {
  return request({ url: '/flow/reject', method: 'post', data: { flowCode, bizId, comment } })
}

export function getFlowRecords(flowCode, bizId) {
  return request({ url: '/flow/records', method: 'get', params: { flowCode, bizId } })
}

export function getFlowState(flowCode, bizId) {
  return request({ url: '/flow/state', method: 'get', params: { flowCode, bizId } })
}

export function getFlowTodo() {
  return request({ url: '/flow/todo', method: 'get' })
}

export function getFlowDone() {
  return request({ url: '/flow/done', method: 'get' })
}

export function getFlowTaskDetail(taskId) {
  return request({ url: `/flow/task/${'$'}{taskId}`, method: 'get' })
}

// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====
