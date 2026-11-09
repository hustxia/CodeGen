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
  { path: '/', redirect: HOME_PATH, hidden: true },
  // 兜底放最后：动态路由未匹配时落到 404，而不是白屏
  { path: '*', component: () => import('@/views/error-page/404.vue'), hidden: true }
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
