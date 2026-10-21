import request, { toPageData } from '@/utils/request'

// 系统接口：codegen-server 系统管理
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/* ---------- 用户管理 ---------- */

export function getSysUserList(params) {
  return request({
    url: '/user/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

export function createSysUser(data) {
  return request({
    url: '/user',
    method: 'post',
    data
  })
}

export function updateSysUser(id, data) {
  return request({
    url: '/user',
    method: 'put',
    data: { ...data, id }
  })
}

export function deleteSysUser(id) {
  return request({
    url: `/user/${id}`,
    method: 'delete'
  })
}

/* ---------- 角色管理 ---------- */

export function getRoleList(params) {
  return request({
    url: '/role/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

export function createRole(data) {
  return request({
    url: '/role',
    method: 'post',
    data
  })
}

export function updateRole(id, data) {
  return request({
    url: '/role',
    method: 'put',
    data: { ...data, id }
  })
}

export function deleteRole(id) {
  return request({
    url: `/role/${id}`,
    method: 'delete'
  })
}

/* ---------- 菜单管理 ---------- */

// 菜单树：菜单管理页要能看到禁用菜单，所以不传 status（全部）
export function getMenuTree() {
  return request({
    url: '/menu/tree',
    method: 'get'
  })
}

// 授权用的菜单树：只要启用中的菜单
export function getEnabledMenuTree() {
  return request({
    url: '/menu/tree',
    method: 'get',
    params: { status: 1 }
  })
}

export function createMenu(data) {
  return request({
    url: '/menu',
    method: 'post',
    data
  })
}

export function updateMenu(id, data) {
  return request({
    url: '/menu',
    method: 'put',
    data: { ...data, id }
  })
}

export function deleteMenu(id) {
  return request({
    url: `/menu/${id}`,
    method: 'delete'
  })
}

/* ---------- 角色授权 ---------- */

// 角色已授权的菜单 ID（含按钮）
export function getRoleMenuIds(roleId) {
  return request({
    url: '/role-menu/menu-ids',
    method: 'get',
    params: { roleId }
  })
}

// 整体覆盖角色菜单（后端事务：先删后插）
export function saveRoleMenus(roleId, menuIds) {
  return request({
    url: '/role-menu/batch',
    method: 'post',
    data: { roleId, menuIds }
  })
}
