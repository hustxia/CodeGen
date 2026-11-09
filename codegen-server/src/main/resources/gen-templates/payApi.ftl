import request from '@/utils/request'

// V6.3 支付能力包全局接口（amount 单位：元，后端统一转分存储）
export function createPayOrder(data) {
  return request({ url: '/pay/order', method: 'post', data })
}

export function getPayOrder(outTradeNo) {
  return request({ url: '/pay/order/' + outTradeNo, method: 'get' })
}

export function closePayOrder(outTradeNo) {
  return request({ url: '/pay/close/' + outTradeNo, method: 'post' })
}

// mock 渠道专用：本地模拟支付成功（真渠道下后端返回 404）
export function mockPay(outTradeNo) {
  return request({ url: '/pay/mock/pay/' + outTradeNo, method: 'post' })
}

export function payOrderPage(query) {
  return request({ url: '/pay/order/page', method: 'post', data: query })
}

// 当前支付渠道（Result<String>：拦截器返回整个 body，调用方取 res.data）
export function getPayChannel() {
  return request({ url: '/pay/channel', method: 'get' })
}

// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====
