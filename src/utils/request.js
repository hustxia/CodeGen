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

// 响应拦截器：统一处理业务码与异常
service.interceptors.response.use(
  response => {
    // blob 流（文件下载）直接放行，不走业务码校验
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data

    // 约定：code 为 0 / 200 表示成功，其余为业务错误（按后端约定调整）
    if (res.code !== 0 && res.code !== 200) {
      Message({ message: res.msg || res.message || '请求失败', type: 'error', duration: 5 * 1000 })

      // 401: 未登录或 token 过期 -> 重新登录
      if (res.code === 401) {
        MessageBox.confirm('登录状态已失效，请重新登录', '提示', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          store.dispatch('user/resetToken').then(() => {
            location.reload()
          })
        })
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    Message({
      message: (error.response && error.response.data && error.response.data.message) || error.message,
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
