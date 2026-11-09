import router from './router'
import store from './store'
import { Message } from 'element-ui'
import { getToken } from '@/utils/auth'

// 免登录白名单
const whiteList = ['/login', '/404', '/403']

router.beforeEach(async (to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 码匠 · CodeGen` : '码匠 · CodeGen'

  const hasToken = getToken()

  if (!hasToken) {
    if (whiteList.indexOf(to.path) !== -1) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
    return
  }

  if (to.path === '/login') {
    next({ path: '/' })
    return
  }

  try {
    if (!store.getters.name) {
      await store.dispatch('user/getInfo')
    }
    // 动态路由只拉一次：登录后按角色把菜单树注册进 router
    if (!store.getters.routeLoaded) {
      const routes = await store.dispatch('permission/generateRoutes')
      routes.forEach(route => router.addRoute(route))
      // 路由刚注册完，当前 to 还没匹配到新路由，重新走一遍导航
      next({ ...to, replace: true })
      return
    }
  } catch (err) {
    await store.dispatch('user/resetToken')
    Message.error(err.message || '登录状态失效，请重新登录')
    next(`/login?redirect=${to.path}`)
    return
  }

  // 越权判定：路径在系统菜单里存在，但不在当前用户的菜单树里 -> 403（而不是 404 白屏）
  const allPaths = store.getters.allPaths || []
  const accessiblePaths = store.getters.accessiblePaths || []
  if (allPaths.indexOf(to.path) !== -1 && accessiblePaths.indexOf(to.path) === -1) {
    next('/403')
    return
  }

  // 未匹配任何动态路由 -> 404（catch-all 的 path 是 '*'）
  if (to.matched.length && to.matched.every(m => m.path === '*')) {
    next('/404')
    return
  }

  next()
})
