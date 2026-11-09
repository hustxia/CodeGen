import axios from 'axios'
import { Message, MessageBox } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'

// 创建 axios 实例
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API,
  timeout: 15000
})

// 请求拦截器：注入 token
service.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 登录态失效的统一处理：清 token 后重新登录，多请求并发时只弹一次
let relogging = false
function toLogin() {
  if (relogging) return
  relogging = true
  MessageBox.confirm('登录状态已失效，请重新登录', '提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    store.dispatch('user/resetToken').then(() => {
      // 回登录页并带上当前地址，登录后能回原位
      const redirect = encodeURIComponent(window.location.hash.replace(/^#/, '') || '/')
      window.location.href = `/#/login?redirect=${redirect}`
      window.location.reload()
    })
  }).catch(() => {
    relogging = false
  })
}

// 错误体取可读文案：二进制请求（arraybuffer）失败时，后端返回的是 JSON 体而非文件，需先解码
function messageOf(resp, fallback) {
  const data = resp && resp.data
  if (!data) {
    return fallback
  }
  if (data instanceof ArrayBuffer) {
    try {
      const json = JSON.parse(new TextDecoder('utf-8').decode(new Uint8Array(data)))
      return json.msg || json.message || fallback
    } catch (e) {
      return fallback
    }
  }
  return data.msg || data.message || fallback
}

// 响应拦截器：统一处理业务码与异常
service.interceptors.response.use(
  response => {
    // 二进制流（文件下载 / 文档预览）直接放行，不走业务码校验：
    // 这类端点成功时返回文件本身，失败时用 HTTP 4xx + JSON 体（见 DocController#error）
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }
    const res = response.data

    // 约定：code 为 0 / 200 表示成功，其余为业务错误（按后端约定调整）
    if (res.code !== 0 && res.code !== 200) {
      // 登录接口自身的 401 是"账号密码错误"，由登录页自己提示，不能当成登录态失效去跳转
      const isLoginApi = (response.config.url || '').indexOf('/auth/login') !== -1
      if (res.code === 401 && !isLoginApi) {
        toLogin()
        return Promise.reject(new Error(res.msg || '登录状态已失效'))
      }
      Message({ message: res.msg || res.message || '请求失败', type: 'error', duration: 5 * 1000 })
      return Promise.reject(new Error(res.msg || res.message || '请求失败'))
    }
    return res
  },
  error => {
    const resp = error.response
    // 后端鉴权失败会以 HTTP 401 返回（axios 走 error 分支），这里同样导回登录页
    if (resp && resp.status === 401) {
      toLogin()
      return Promise.reject(new Error(messageOf(resp, '登录状态已失效')))
    }
    Message({
      message: messageOf(resp, error.message),
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)

export default service

// MyBatis-Plus Page 结构适配：{ records, total } -> { list, total }（视图层统一使用 res.data.list）
export function toPageData(res) {
  const page = res.data || {}
  return {
    code: res.code,
    msg: res.msg,
    data: { list: page.records || [], total: page.total || 0 }
  }
}
