import { login, logout, getInfo } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { resetRouter } from '@/router'
import { applyTheme } from '@/utils/theme'

const getDefaultState = () => ({
  token: getToken(),
  name: '',
  avatar: '',
  roles: [],
  permissions: [],
  // 服务端已保存的界面主题偏好（仅用于展示，实际渲染看 document 上的 data-theme）
  themePref: ''
})

const state = getDefaultState()

const mutations = {
  RESET_STATE(state) {
    Object.assign(state, getDefaultState())
  },
  SET_TOKEN(state, token) {
    state.token = token
  },
  SET_NAME(state, name) {
    state.name = name
  },
  SET_AVATAR(state, avatar) {
    state.avatar = avatar
  },
  SET_ROLES(state, roles) {
    state.roles = roles
  },
  SET_PERMISSIONS(state, permissions) {
    state.permissions = permissions || []
  },
  SET_THEME_PREF(state, themeId) {
    state.themePref = themeId || ''
  }
}

const actions = {
  // 登录
  login({ commit }, userInfo) {
    const { username, password } = userInfo
    return new Promise((resolve, reject) => {
      login({ username: username.trim(), password })
        .then(res => {
          const { data } = res
          commit('SET_TOKEN', data.token)
          setToken(data.token)
          // 登录返回值里带用户已保存的主题偏好：立刻套用并写入本地缓存。
          // 这样登录成功后每一次刷新，public/index.html 的内联脚本都能在首帧
          // 直接命中正确皮肤，不会先闪一下默认蓝色。
          if (data.themeId) {
            const applied = applyTheme(data.themeId)
            commit('SET_THEME_PREF', applied)
          }
          // 换账号登录时清掉上一个账号的动态路由，否则会串菜单
          commit('permission/RESET_STATE', null, { root: true })
          resolve()
        })
        .catch(reject)
    })
  },

  // 获取用户信息
  getInfo({ commit }) {
    return new Promise((resolve, reject) => {
      getInfo()
        .then(res => {
          const { data } = res
          if (!data) {
            return reject(new Error('获取用户信息失败，请重新登录'))
          }
          // 后端 SysUserVO 无 name/roles 字段，nickname 作为显示名
          commit('SET_NAME', data.nickname || data.username)
          commit('SET_AVATAR', data.avatar || '')
          commit('SET_ROLES', data.roles || ['admin'])
          // 权限码由后端 /auth/info 下发（超管为 ['*:*:*']），按钮级控制靠它
          commit('SET_PERMISSIONS', data.permissions || [])
          // 刷新页面 / 换设备时兜底同步一次主题：本地缓存被清掉也能恢复
          if (data.themeId) {
            const applied = applyTheme(data.themeId)
            commit('SET_THEME_PREF', applied)
          }
          resolve(data)
        })
        .catch(reject)
    })
  },

  // 登出
  logout({ commit }) {
    return new Promise((resolve, reject) => {
      logout()
        .then(() => {
          commit('RESET_STATE')
          commit('permission/RESET_STATE', null, { root: true })
          removeToken()
          resetRouter()
          resolve()
        })
        .catch(reject)
    })
  },

  // 清除 token（前端直接清除）
  resetToken({ commit }) {
    return new Promise(resolve => {
      commit('RESET_STATE')
      removeToken()
      resolve()
    })
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
