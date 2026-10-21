import request from '@/utils/request'

// 认证接口：codegen-server /auth

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export function getInfo() {
  return request({
    url: '/auth/info',
    method: 'get'
  })
}

// 动态路由：按当前用户的角色下发菜单树（V2.0）
export function getRouters() {
  return request({
    url: '/auth/routers',
    method: 'get'
  })
}

// 后端为 JWT 无状态认证，无 logout 接口，本地清除 token 即可
export function logout() {
  return Promise.resolve({ code: 200, msg: 'ok', data: null })
}
