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

// V6.3 补全 退款：refundAmount 单位元（支持部分退款），refundNo 传同值重放幂等
export function refundPayOrder(outTradeNo, data) {
  return request({ url: '/pay/refund/' + outTradeNo, method: 'post', data })
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

<#--
  V9.17 能力开关（三态：NULL/0=关、1=开；缺省关 → 产物与 V6.3 版逐字节零 diff）。
  共享模板由 GenTemplateEngine.renderPayShared 以空模型渲染，故这里全部用 (key!0) 兜底；
  引擎/调用方需要开启时把下列 key 放进渲染 model 的 Map（无需新增 DTO）：
    payJsapiEnabled=1        微信 JSAPI 下单 + 收银台微信内 WeixinJSBridge 分支
    payH5Enabled=1           微信 H5 支付下单 + 跳转链接分支
    payRefundNoEnabled=1     退款弹窗暴露商户退款单号（幂等键）
    payRefundQueryEnabled=1  退款结果查询接口 + 支付记录页查询入口
    payAlipaySandboxEnabled=1 支付宝沙箱标识/扫码登录跳转分支
  appId / 商户号等敏感配置一律由产物后端从 yml 读取，前端只从 /pay/config 取，零硬编码。
-->
<#if (payJsapiEnabled!0) == 1 || (payAlipaySandboxEnabled!0) == 1>
// V9.17 运行时配置：wxAppId / alipaySandbox / alipayGateway / alipaySandboxLoginUrl 等由后端配置下发
export function getPayConfig() {
  return request({ url: '/pay/config', method: 'get' })
}

</#if>
<#if (payJsapiEnabled!0) == 1>
// V9.17 JSAPI：下单返回 prepayId/paySign 等；signType / nonceStr / timestamp 由后端签名后下发
export function createJsapiOrder(data) {
  return request({ url: '/pay/jsapi/order', method: 'post', data })
}

// V9.17 JSAPI：换 jsapi_ticket 并对当前页面 URL 签名（ticket 由后端按 appId 缓存）
export function getJsapiSignature(url) {
  return request({ url: '/pay/jsapi/signature', method: 'get', params: { url } })
}

</#if>
<#if (payH5Enabled!0) == 1>
// V9.17 H5：下单返回微信 H5 支付链接 payUrl，前端直接跳转（仅非微信内置浏览器使用）
export function createH5Order(data) {
  return request({ url: '/pay/h5/order', method: 'post', data })
}

</#if>
<#if (payRefundQueryEnabled!0) == 1>
// V9.17 退款结果查询：按商户单号查退款金额/时间/原因（金额单位分）
export function getRefundResult(outTradeNo) {
  return request({ url: '/pay/refund/' + outTradeNo + '/result', method: 'get' })
}

</#if>
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====
