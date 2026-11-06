// 统一请求封装：Bearer token 注入 + Result 协议解包 + 401 跳登录
// baseURL：H5 走 vite 代理（/dev-api 剥前缀直连后端）；小程序直连 HTTPS，
// 经 .env 设 VITE_API_BASE 为完整域名（如 https://api.example.com）
const BASE_URL = import.meta.env.VITE_API_BASE || '/dev-api'

export function request(options) {
  const token = uni.getStorageSync('token')
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: Object.assign(
        { 'Content-Type': 'application/json' },
        token ? { Authorization: 'Bearer ' + token } : {},
        options.header || {}
      ),
      success: (res) => {
        if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('user')
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error('未登录或登录已过期'))
          return
        }
        // 后端统一 Result 协议：{ code, msg, data }
        const body = res.data
        if (res.statusCode >= 200 && res.statusCode < 300 && body && typeof body.code !== 'undefined') {
          if (body.code === 200) {
            resolve(body.data)
          } else {
            uni.showToast({ title: body.msg || '操作失败', icon: 'none' })
            reject(new Error(body.msg || '操作失败'))
          }
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body)
          return
        }
        const msg = (body && (body.msg || body.message)) || ('请求失败 ' + res.statusCode)
        uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      }
    })
  })
}

export const get = (url, data) => request({ url, data, method: 'GET' })
export const post = (url, data) => request({ url, data, method: 'POST' })
export const put = (url, data) => request({ url, data, method: 'PUT' })
export const del = (url, data) => request({ url, data, method: 'DELETE' })
