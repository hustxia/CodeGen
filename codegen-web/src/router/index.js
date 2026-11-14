import Vue from 'vue'
import VueRouter from 'vue-router'

Vue.use(VueRouter)

// vue-router 3.1+ 的 push/replace 返回 Promise；守卫重定向（如登录后 next({...to, replace:true})）
// 或重复导航会使原调用 reject 成未捕获异常，这里统一吞掉（未传回调时）
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location, onResolve, onReject) {
  if (onResolve || onReject) return originalPush.call(this, location, onResolve, onReject)
  return originalPush.call(this, location).catch(err => err)
}
const originalReplace = VueRouter.prototype.replace
VueRouter.prototype.replace = function replace(location, onResolve, onReject) {
  if (onResolve || onReject) return originalReplace.call(this, location, onResolve, onReject)
  return originalReplace.call(this, location).catch(err => err)
}

// 落地页：登录后进入代码生成
const HOME_PATH = '/gen/table'


export const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
    hidden: true
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error-page/403.vue'),
    meta: { title: '403' },
    hidden: true
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error-page/404.vue'),
    meta: { title: '404' },
    hidden: true
  },
  // 根路径直接跳代码生成首页（业务路由全部由 /auth/routers 动态注册）
  { path: '/', redirect: HOME_PATH, hidden: true }
  // 注：全量兜底 404 不在此注册。登录后动态路由由 permission.js 的 generateRoutes
  // 在「业务路由之后」追加唯一的 { path: '*' }（见 store/modules/permission.js），
  // 保证它最后注册、且由守卫区分 404 / 403，避免双 catch-all 因注册顺序导致的脆弱假设。
]

export { HOME_PATH }

const createRouter = () => new VueRouter({
  mode: 'history',
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

// 重置路由（登出时使用）
export function resetRouter() {
  const newRouter = createRouter()
  router.matcher = newRouter.matcher
}

export default router
