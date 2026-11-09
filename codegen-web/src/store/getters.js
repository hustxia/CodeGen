const getters = {
  sidebar: state => state.app.sidebar,
  token: state => state.user.token,
  name: state => state.user.name,
  avatar: state => state.user.avatar,
  roles: state => state.user.roles,
  permissions: state => state.user.permissions,
  themePref: state => state.user.themePref,
  // 动态路由（V2.0）
  menus: state => state.permission.menus,
  allPaths: state => state.permission.allPaths,
  accessiblePaths: state => state.permission.accessiblePaths,
  routeLoaded: state => state.permission.loaded,
  superAdmin: state => state.permission.superAdmin
}

export default getters
