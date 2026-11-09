import Layout from '@/layout/index.vue'
import { getRouters } from '@/api/user'

/**
 * 动态路由（V2.0）
 *
 * 后端 /auth/routers 只下发"菜单长什么样"（path / component 字符串 / 标题 / 图标），
 * 这里负责把 component 字符串解析成真正的异步组件，并组装成 vue-router 能吃的结构。
 * 这样后端加一张表、注册一个菜单，前端不用改代码也不用重新打包。
 */

// 一次性把所有页面收进 map，运行时按字符串取组件（webpack 会静态收集这里的依赖）
const viewContext = require.context('@/views', true, /\.vue$/)
const viewKeys = viewContext.keys()

function resolveComponent(component, menuId) {
  if (!component) {
    return null
  }
  const clean = String(component).replace(/^\/+/, '').replace(/\.vue$/, '')
  const key = `./${clean}.vue`
  if (viewKeys.indexOf(key) !== -1) {
    // require.context 返回的是模块对象，SFC 的组件定义在 .default 上
    const mod = viewContext(key)
    return mod.default || mod
  }
  // 菜单配了不存在的组件：不白屏、不落 404，给一个能看懂原因的占位页
  // eslint-disable-next-line no-console
  console.warn(`[动态路由] 菜单 ${menuId} 的组件 "${component}" 不存在（期望 src/views/${clean}.vue），已降级为占位页`)
  return () => import('@/views/error-page/not-implemented.vue')
}

function metaOf(node) {
  return {
    title: node.title,
    icon: node.icon || undefined,
    perms: node.perms || undefined,
    menuId: node.id
  }
}

function leafRoute(node) {
  return {
    path: node.path,
    name: `Menu${node.id}`,
    component: resolveComponent(node.component, node.id),
    meta: metaOf(node)
  }
}

/**
 * 菜单树 -> 路由表
 *
 * 目录（type=1）用 Layout 包一层，子菜单按绝对路径挂进去；
 * 顶层菜单（type=2）也用 Layout 包一层，子路由 path 为空串复用父级 URL，
 * 这样 98% 的"单页菜单"不会在侧边栏多出一层空目录。
 */
function buildRoutes(menus) {
  const routes = []
  const leafPaths = []
  for (const node of menus || []) {
    const children = node.children || []
    if (node.type === 1 || children.length > 0) {
      const kids = []
      for (const child of children) {
        if (child.type === 1) {
          // 目前只有两级；出现三级目录时把孙节点摊平，避免侧边栏错位
          for (const grand of child.children || []) {
            kids.push(leafRoute(grand))
          }
        } else {
          kids.push(leafRoute(child))
        }
      }
      const route = {
        path: node.path,
        component: Layout,
        meta: metaOf(node),
        children: kids
      }
      if (kids.length > 0) {
        route.redirect = kids[0].path
        leafPaths.push(...kids.map(k => k.path))
      }
      routes.push(route)
    } else {
      routes.push({
        path: node.path,
        component: Layout,
        meta: metaOf(node),
        children: [{ ...leafRoute(node), path: '' }]
      })
      leafPaths.push(node.path)
    }
  }
  return { routes, leafPaths }
}

function collectPaths(menus, acc = []) {
  for (const node of menus || []) {
    if (node.path) {
      acc.push(node.path)
    }
    if (node.children && node.children.length) {
      collectPaths(node.children, acc)
    }
  }
  return acc
}

const state = {
  // 已注册到 router 的路由
  routes: [],
  // 后端下发的原始菜单树（侧边栏直接用它渲染）
  menus: [],
  // 系统全部菜单路径（区分 404 与 403）
  allPaths: [],
  // 当前用户可访问的路径
  accessiblePaths: [],
  superAdmin: false,
  loaded: false
}

const mutations = {
  SET_ROUTES(state, payload) {
    state.routes = payload.routes
    state.menus = payload.menus
    state.allPaths = payload.allPaths
    state.accessiblePaths = payload.accessiblePaths
    state.superAdmin = payload.superAdmin
    state.loaded = true
  },
  RESET_STATE(state) {
    state.routes = []
    state.menus = []
    state.allPaths = []
    state.accessiblePaths = []
    state.superAdmin = false
    state.loaded = false
  }
}

const actions = {
  generateRoutes({ commit }) {
    return getRouters().then(res => {
      const data = res.data || {}
      const menus = data.routers || []
      const { routes, leafPaths } = buildRoutes(menus)
      // 根路径按角色落到"第一个能进的页面"，避免非管理员登录后停在空白 dashboard
      if (leafPaths.length > 0) {
        routes.push({ path: '/', redirect: leafPaths[0], hidden: true })
      }
      // 兜底 404 必须最后注册；用组件而不是 redirect，让守卫有机会先把
      // "菜单里存在但无权访问"的路径导去 403
      routes.push({
        path: '*',
        component: () => import('@/views/error-page/404.vue'),
        meta: { title: '404' }
      })
      commit('SET_ROUTES', {
        routes,
        menus,
        allPaths: data.allPaths || [],
        accessiblePaths: collectPaths(menus),
        superAdmin: !!data.superAdmin
      })
      return routes
    })
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
