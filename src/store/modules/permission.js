import Layout from '@/layout/index.vue'
import { getRouters } from '@/api/user'

/**
 * 动态路由（V2.0）
 *
 * 后端 /auth/routers 只下发"菜单长什么样"（path / component 字符串 / 标题 / 图标），
 * 这里负责把 component 字符串解析成真正的异步组件，并组装成 vue-router 能吃的结构。
 * 这样后端加一张表、注册一个菜单，前端不用改代码也不用重新打包。
 */

// V9.5「AI 建表推荐」静态入口：不依赖后端菜单表，前端注入即可在侧边栏出现并可达。
// 仅新增菜单节点，不改动任何 V9.4 路由/页面（AC-01）。
const V95_AIBUILD_MENU = {
  id: 'v95_aibuild',
  title: 'V9.5 AI 建表',
  icon: 'tool',
  type: 2,
  path: '/gen/aibuild',
  component: 'gen/aibuild/index',
  children: []
}

// 模板影响面预览静态入口：与 V9.5 同理，不依赖后端菜单表即可在侧边栏出现并可达。
// 正式环境若需权限管控，在 sys_menu 补 path=/gen/template-impact、component=gen/templateImpact 的记录即可。
const TEMPLATE_IMPACT_MENU = {
  id: 'gen_template_impact',
  title: '模板影响面',
  icon: 'documentation',
  type: 2,
  path: '/gen/template-impact',
  component: 'gen/templateImpact',
  children: []
}

// V9.19 模板市场静态入口：预置/用户包列表、导入导出、删除用户包。
// 与上面两个入口同理，只追加菜单节点，不改动既有路由与页面。
const TEMPLATE_MARKET_MENU = {
  id: 'gen_template_market',
  title: '模板市场',
  icon: 'shopping',
  type: 2,
  path: '/gen/template-market',
  component: 'gen/templateMarket',
  children: []
}

// V9.21 SDD 规格工件链 + Converge 对账静态入口：工件版本化（五类）+ 规格对账报告。
// 与上面入口同理，只追加菜单节点；正式权限走 sys_menu（path=/gen/spec、/gen/converge）。
const SPEC_MENU = {
  id: 'gen_spec',
  title: '规格工件链',
  icon: 'form',
  type: 2,
  path: '/gen/spec',
  component: 'gen/spec/index',
  children: []
}

const CONVERGE_MENU = {
  id: 'gen_converge',
  title: '规格对账',
  icon: 'monitor',
  type: 2,
  path: '/gen/converge',
  component: 'gen/converge/index',
  children: []
}

// V9.22 行业范式市场：四包清单 + 安装向导 + 我的安装（静态入口，正式权限走 sys_menu）
const INDUSTRY_PACK_MENU = {
  id: 'gen_industry_pack',
  title: '行业范式市场',
  icon: 'shopping',
  type: 2,
  path: '/gen/industry-pack',
  component: 'gen/industryPack/index',
  children: []
}
const INDUSTRY_INSTALL_MENU = {
  id: 'gen_industry_install',
  title: '范式安装',
  icon: 'plus',
  type: 2,
  path: '/gen/industry-pack/install',
  component: 'gen/industryPack/install',
  children: []
}
const INDUSTRY_INSTALLED_MENU = {
  id: 'gen_industry_installed',
  title: '我的范式安装',
  icon: 'list',
  type: 2,
  path: '/gen/industry-pack/installed',
  component: 'gen/industryPack/installed',
  children: []
}

// V9.25 M2.1 护栏澄清向导：一次一问 + 28 条行业规则（静态入口，正式权限走 sys_menu）。
const CLARIFY_MENU = {
  id: 'gen_clarify',
  title: '澄清向导',
  icon: 'edit-form',
  type: 2,
  path: '/gen/clarify',
  component: 'gen/clarify/index',
  children: []
}

const GUARDRAIL_MENU = {
  id: 'gen_guardrail',
  title: '护栏规则',
  icon: 'lock',
  type: 2,
  path: '/gen/guardrail',
  component: 'gen/guardrail/index',
  children: []
}

// V9.23 MCP Server：连接配置、工具目录与调用审计静态入口。
const MCP_MENU = {
  id: 'gen_mcp',
  title: 'MCP Server',
  icon: 'monitor',
  type: 2,
  path: '/gen/mcp',
  component: 'gen/mcp/index',
  children: []
}

/**
 * V9.26 按功能域重组：后端下发的 /gen 子项 + 11 个静态入口统一归类。
 * 目录全部为 type=1（Layout 包裹），叶子沿用绝对路径，component 解析逻辑不变。
 */
const FUNCTION_GROUPS = [
  { id: 'fg_project', title: '项目与数据', icon: 'el-icon-folder-opened', path: '/gen-project',
    order: ['/gen/project', '/gen/table', '/gen/i18n'] },
  { id: 'fg_spec', title: '规格与质量', icon: 'el-icon-document-checked', path: '/gen-spec',
    order: ['/gen/clarify', '/gen/guardrail', '/gen/spec', '/gen/converge'] },
  { id: 'fg_template', title: '模板与范式', icon: 'el-icon-files', path: '/gen-template',
    order: ['/gen/template', '/gen/template-market', '/gen/template-impact', '@industry'] },
  { id: 'fg_build', title: '生成作业', icon: 'el-icon-set-up', path: '/gen-build',
    order: ['/gen/pipeline', '/gen/aibuild', '/gen/wizard', '/gen/output'] },
  { id: 'fg_integration', title: '集成服务', icon: 'el-icon-connection', path: '/gen-integration',
    order: ['/gen/mcp'] }
]

// 展示名覆盖：消除 /gen/project 与 /system 重名、去版本号前缀、统一中文
const TITLE_OVERRIDES = {
  '/gen/project': '项目管理',
  '/gen/table': '数据表管理',
  '/gen/aibuild': 'AI 智能建表',
  '/gen/mcp': 'MCP 服务',
  '/gen/industry-pack': '范式市场'
}

const STATIC_MENUS = [
  V95_AIBUILD_MENU, TEMPLATE_IMPACT_MENU, TEMPLATE_MARKET_MENU, SPEC_MENU, CONVERGE_MENU,
  INDUSTRY_PACK_MENU, INDUSTRY_INSTALL_MENU, INDUSTRY_INSTALLED_MENU, MCP_MENU,
  CLARIFY_MENU, GUARDRAIL_MENU
]

function regroupMenus(serverRouters) {
  const byPath = new Map()
  const others = []
  for (const node of serverRouters || []) {
    if (node.path === '/gen') {
      for (const child of node.children || []) byPath.set(child.path, child)
    } else {
      others.push(node)
    }
  }
  for (const menu of STATIC_MENUS) byPath.set(menu.path, menu)

  const groups = FUNCTION_GROUPS.map(g => {
    const children = []
    for (const key of g.order) {
      if (key === '@industry') {
        const industryChildren = ['/gen/industry-pack', '/gen/industry-pack/install', '/gen/industry-pack/installed']
          .map(p => byPath.get(p)).filter(Boolean)
          .map(n => ({ ...n, title: TITLE_OVERRIDES[n.path] || n.title }))
        children.push({ id: 'fg_industry', title: '行业范式', icon: 'el-icon-menu', type: 1, path: '/gen-industry', children: industryChildren })
      } else {
        const node = byPath.get(key)
        if (node) children.push({ ...node, title: TITLE_OVERRIDES[key] || node.title })
      }
    }
    return { id: g.id, title: g.title, icon: g.icon, type: 1, path: g.path, children }
  })

  // 后端新增但未登记的 /gen 子项保底挂到第一组，避免菜单丢失
  const claimed = new Set()
  groups.forEach(g => g.children.forEach(c => {
    if (c.type === 1) (c.children || []).forEach(x => claimed.add(x.path))
    else claimed.add(c.path)
  }))
  byPath.forEach((node, path) => {
    if (!claimed.has(path)) groups[0].children.push({ ...node, title: TITLE_OVERRIDES[path] || node.title })
  })

  return groups.concat(others)
}

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
      const menus = regroupMenus(data.routers || [])
      const { routes, leafPaths } = buildRoutes(menus)
      // 兼容旧书签：/gen 历史上是代码生成目录，重定向到新的第一个功能组
      routes.push({ path: '/gen', redirect: leafPaths[0] || '/gen/project', hidden: true })
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
      // allPaths 由菜单树自动收集，避免历史硬编码漏项导致 404/403 误判
      const systemPaths = (data.allPaths || []).filter(p => p !== '/gen')
      const allPaths = systemPaths.concat(collectPaths(menus))
      commit('SET_ROUTES', {
        routes,
        menus,
        allPaths: Array.from(new Set(allPaths)),
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
