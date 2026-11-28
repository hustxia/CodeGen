import request from '@/utils/request'

export function getMcpConfig() {
  return request({ url: '/gen/mcp/config', method: 'get' })
}

export function getMcpTools() {
  return request({ url: '/gen/mcp/tools', method: 'get' })
}

export function getMcpLogs(data) {
  return request({ url: '/gen/mcp/logs', method: 'post', data })
}
