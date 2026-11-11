import request from '@/utils/request'

/**
 * V6.10 系统监控
 *
 * 前端不直接访问 /actuator：所有指标都走后端聚合接口，字段与权限都可控。
 */

export function getMonitorOverview() {
  return request({ url: '/monitor/overview', method: 'get' }).then(res => res.data)
}

export function getMonitorSeries(metric, minutes = 30) {
  return request({ url: '/monitor/series', method: 'get', params: { metric, minutes } }).then(res => res.data)
}

export function getMonitorHttp(minutes = 30, topN = 10) {
  return request({ url: '/monitor/http', method: 'get', params: { minutes, topN } }).then(res => res.data)
}

export function getMonitorBusiness(minutes = 30) {
  return request({ url: '/monitor/business', method: 'get', params: { minutes } }).then(res => res.data)
}
