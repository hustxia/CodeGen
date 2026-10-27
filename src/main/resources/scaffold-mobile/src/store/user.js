import { defineStore } from 'pinia'

// token/用户态：uni.storage 持久化（H5 localStorage / 小程序 storage 同 API）
export const useUserStore = defineStore('user', {
  state: () => ({
    token: uni.getStorageSync('token') || '',
    username: uni.getStorageSync('user') ? uni.getStorageSync('user').username || '' : '',
    perms: uni.getStorageSync('perms') || []
  }),
  actions: {
    setAuth(data) {
      this.token = data.token || ''
      this.username = data.username || ''
      this.perms = Array.isArray(data.perms) ? data.perms : []
      uni.setStorageSync('token', this.token)
      uni.setStorageSync('user', { username: this.username })
      uni.setStorageSync('perms', this.perms)
    },
    logout() {
      this.token = ''
      this.username = ''
      this.perms = []
      uni.removeStorageSync('token')
      uni.removeStorageSync('user')
      uni.removeStorageSync('perms')
      uni.reLaunch({ url: '/pages/login/login' })
    }
  }
})
