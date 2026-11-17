<template>
  <div class="layout">
    <!-- 仓储风格：左侧常驻树形菜单，一级=业务域，二级=功能页面 -->
    <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
      <div class="logo-bar">
        __BRAND_MARK__
        <div v-show="!collapsed" class="logo-text">__PROJECT__</div>
      </div>

      <div v-show="!collapsed" class="menu-search">
        <el-input v-model="keyword" placeholder="搜索功能菜单" clearable size="small">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <div class="menu-scroll">
        <div v-show="!collapsed" class="section-title">工作台</div>
        <div class="menu-home" :class="{ 'is-active': $route.path === '/home' }" @click="go('/home')">
          <el-icon><HomeFilled /></el-icon>
          <span v-show="!collapsed">首页总览</span>
        </div>

        <div v-show="!collapsed" class="section-title section-title-second">业务导航</div>
        <div v-for="grp in groups" :key="grp.path" class="menu-group">
          <el-tooltip v-if="collapsed" :content="grp.title" placement="right">
            <div class="group-title" :class="{ 'is-active': isGrpActive(grp), 'is-open': openKey === grp.path }" @click="toggleGroup(grp)">
              <el-icon class="group-icon"><component :is="grp.icon || iconOf(grp)" /></el-icon>
            </div>
          </el-tooltip>
          <div v-else class="group-title" :class="{ 'is-active': isGrpActive(grp), 'is-open': openKey === grp.path }" @click="toggleGroup(grp)">
            <el-icon class="group-icon"><component :is="grp.icon || iconOf(grp)" /></el-icon>
            <span class="group-text">{{ grp.title }}</span>
            <span class="group-count">{{ grp.children.length }}</span>
            <el-icon class="group-arrow" :class="{ 'is-rotated': openKey === grp.path }"><ArrowDown /></el-icon>
          </div>
          <el-collapse-transition>
            <div v-show="openKey === grp.path && !collapsed" class="group-children">
              <div
                v-for="page in grp.children"
                :key="page.path"
                class="child-item"
                :class="{ 'is-active': $route.path === page.path }"
                @click="go(page.path)"
              >
                <span class="child-dot"></span>
                <span class="child-text">{{ page.title }}</span>
              </div>
            </div>
          </el-collapse-transition>
        </div>
        <div v-if="!groups.length && !collapsed" class="empty-tip">没有匹配的菜单</div>
      </div>

      <div class="collapse-bar" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        <span v-show="!collapsed">收起菜单</span>
      </div>
    </aside>

    <div class="main">
      <header class="header">
        <div class="header-left">
          <div class="crumb-home"><el-icon><House /></el-icon></div>
          <el-breadcrumb separator="/" class="crumbs">
            <el-breadcrumb-item v-for="(crumb, i) in crumbs" :key="i">{{ crumb }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="today">{{ today }}</span>
          <span class="split"></span>
          <el-tooltip content="通知公告" placement="bottom">
            <div class="header-icon"><el-icon><Bell /></el-icon><span class="bell-dot"></span></div>
          </el-tooltip>
          <div class="user-chip">
            <div class="avatar">管</div>
            <span class="user-name">{{ userName }}</span>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </div>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </div>
  </div>
</template>

<script>
/**
 * 仓储风格左侧菜单：菜单树直接由路由表推导，路由与菜单永远同源。
 * 不在组件里静态 import 路由表——gen-routes.js 与本组件互相引用会形成循环依赖。
 */
function resolveTitle(meta, fallback) {
  const raw = (meta && meta.title) || fallback
  if (meta && meta.i18nKey && typeof window.$t === 'function') {
    return window.$t(meta.i18nKey, raw)
  }
  return raw
}

function buildMenu(routes, parentPath) {
  const list = []
  ;(routes || []).forEach(route => {
    if (!route || route.hidden) return
    const path = route.path && route.path.charAt(0) === '/'
      ? route.path
      : (parentPath + '/' + route.path).replace(/\/{2,}/g, '/')
    const children = buildMenu(route.children, path)
    const hasComponent = typeof route.component === 'function'
    if (!children.length && !hasComponent) return
    list.push({
      path,
      title: resolveTitle(route.meta, route.name || path),
      icon: (route.meta && route.meta.icon) || '',
      children
    })
  })
  return list
}

const ICONS = [
  'OfficeBuilding', 'House', 'Location', 'User', 'Connection', 'DataAnalysis',
  'Flag', 'Document', 'ChatDotRound', 'Wallet', 'Tree', 'Setting'
]

export default {
  name: 'Layout',
  data() {
    return {
      userName: '管理员',
      keyword: '',
      collapsed: false,
      openKey: '',
      today: new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
    }
  },
  computed: {
    menus() {
      return buildMenu(this.$router.options.routes, '')
    },
    groups() {
      // 系统壳（/village_affairs）本身不作为一级菜单，它的 children 才是业务域
      const sys = this.menus.find(m => m.path !== '/' && m.path !== '/home' && m.children.length)
      const base = sys ? sys.children : this.menus.filter(m => m.path !== '/' && m.path !== '/home')
      // V9.12：生成物内置的用户/角色/菜单管理固定在菜单末尾（路由为静态 /system 分组）
      const all = base.concat(this.menus.filter(m => m.path === '/system'))
      const kw = this.keyword.trim().toLowerCase()
      if (!kw) return all
      return all.map(g => ({
        ...g,
        children: (g.children || []).filter(p => p.title.toLowerCase().includes(kw))
      })).filter(g => g.title.toLowerCase().includes(kw) || g.children.length)
    },
    crumbs() {
      const list = this.$route.matched
        .map(item => resolveTitle(item.meta, ''))
        .filter(t => t && t !== '__PROJECT__')
      return list.length ? list : ['首页']
    }
  },
  watch: {
    '$route.path'() { this.syncOpenGroup() },
    keyword(val) {
      if (val) {
        const first = this.groups.find(g => g.children.length)
        if (first) this.openKey = first.path
      }
    }
  },
  mounted() { this.syncOpenGroup() },
  methods: {
    iconOf(grp) {
      const i = this.groups.findIndex(g => g.path === grp.path)
      return ICONS[(i < 0 ? 0 : i) % ICONS.length]
    },
    isGrpActive(grp) {
      const p = this.$route.path
      return grp.path === p || p.indexOf(grp.path + '/') === 0
    },
    toggleGroup(grp) {
      if (this.collapsed) {
        this.collapsed = false
        this.openKey = grp.path
        return
      }
      this.openKey = this.openKey === grp.path ? '' : grp.path
    },
    syncOpenGroup() {
      const p = this.$route.path
      const hit = this.groups.find(g => g.path === p || p.indexOf(g.path + '/') === 0)
      if (hit) this.openKey = hit.path
    },
    go(path) {
      if (path && this.$route.path !== path) this.$router.push(path)
    }
  }
}
</script>

<style>
:root {
  --app-primary: __THEME_COLOR__;
  --el-color-primary: __THEME_COLOR__;
  --el-color-primary-light-3: color-mix(in srgb, var(--el-color-primary) 70%, white);
  --el-color-primary-light-5: color-mix(in srgb, var(--el-color-primary) 50%, white);
  --el-color-primary-light-7: color-mix(in srgb, var(--el-color-primary) 30%, white);
  --el-color-primary-light-8: color-mix(in srgb, var(--el-color-primary) 20%, white);
  --el-color-primary-light-9: color-mix(in srgb, var(--el-color-primary) 10%, white);
  --el-color-primary-dark-2: color-mix(in srgb, var(--el-color-primary) 80%, black);
}
.layout { display: flex; height: 100vh; overflow: hidden; background: #f6f8fc; }

/* ---------- 左侧菜单 ---------- */
.sidebar {
  position: relative; z-index: 20; flex: 0 0 230px; width: 230px;
  display: flex; flex-direction: column; color: #b9c4dc;
  background: linear-gradient(180deg, #16213c 0%, #111a31 100%);
  box-shadow: 2px 0 16px rgba(10, 18, 38, 0.12);
  transition: width 0.2s ease, flex-basis 0.2s ease;
}
.sidebar.is-collapsed { flex-basis: 64px; width: 64px; }
.logo-bar {
  height: 62px; flex: 0 0 62px; display: flex; align-items: center; gap: 10px;
  padding: 0 16px; border-bottom: 1px solid rgba(255,255,255,0.06); overflow: hidden;
}
.logo-mark {
  flex: 0 0 32px; width: 32px; height: 32px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #6d8bff, var(--app-primary)); color: #fff;
  font-size: 15px; font-weight: 700; box-shadow: 0 4px 12px rgba(79,70,229,0.35);
}
.logo-text { font-size: 15px; font-weight: 600; color: #fff; white-space: nowrap; letter-spacing: 0.6px; }
.menu-search { padding: 14px 12px 4px; }
.menu-search .el-input__wrapper {
  background: rgba(255,255,255,0.07); box-shadow: none !important;
  border: 1px solid rgba(255,255,255,0.08); border-radius: 8px;
}
.menu-search .el-input__inner { color: #e8eefb; font-size: 12px; }
.menu-search .el-input__inner::placeholder { color: #7383a6; }
.menu-scroll { flex: 1; overflow-y: auto; padding: 0 8px 10px; }
.menu-scroll::-webkit-scrollbar { width: 4px; }
.menu-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 4px; }
.section-title {
  padding: 14px 12px 7px; font-size: 10px; letter-spacing: 1.6px; text-transform: uppercase;
  color: rgba(185,196,220,0.42); white-space: nowrap; font-weight: 600;
}
.section-title-second { padding-top: 16px; }
.menu-home, .group-title {
  display: flex; align-items: center; gap: 10px; height: 39px; padding: 0 12px;
  margin-bottom: 2px; border-radius: 8px; font-size: 13px; cursor: pointer;
  transition: background .16s ease, color .16s ease; white-space: nowrap;
}
.menu-home:hover, .group-title:hover { background: rgba(255,255,255,0.07); color: #fff; }
.menu-home.is-active { background: var(--app-primary); color: #fff; box-shadow: 0 4px 12px rgba(79,70,229,0.28); }
.group-title.is-active { color: #fff; background: rgba(79,70,229,0.22); }
.group-title.is-open { color: #fff; }
.sidebar.is-collapsed .menu-home, .sidebar.is-collapsed .group-title { justify-content: center; padding: 0; }
.group-icon { flex: 0 0 18px; font-size: 16px; color: #8ea4d4; }
.group-title.is-active .group-icon { color: #b9c6ff; }
.group-text { flex: 1; overflow: hidden; text-overflow: ellipsis; }
.group-count {
  font-size: 10px; min-width: 18px; text-align: center; padding: 1px 4px;
  border-radius: 9px; background: rgba(255,255,255,0.08); color: #94a5c8;
}
.group-arrow { font-size: 11px; transition: transform .18s ease; opacity: .55; }
.group-arrow.is-rotated { transform: rotate(180deg); }
.group-children { padding: 2px 0 6px 0; }
.child-item {
  display: flex; align-items: center; gap: 9px; height: 33px; padding: 0 12px 0 32px;
  margin-bottom: 1px; border-radius: 7px; font-size: 12.5px; color: #93a2c4;
  cursor: pointer; transition: background .14s ease, color .14s ease;
}
.child-dot { width: 4px; height: 4px; border-radius: 50%; background: #5c6d97; flex: 0 0 4px; }
.child-text { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.child-item:hover { background: rgba(255,255,255,0.06); color: #e8eefb; }
.child-item.is-active { background: rgba(255,255,255,0.96); color: #3730a3; font-weight: 600; }
.child-item.is-active .child-dot { background: var(--app-primary); }
.empty-tip { padding: 16px 12px; font-size: 12px; color: #8494b5; text-align: center; }
.collapse-bar {
  flex: 0 0 44px; height: 44px; display: flex; align-items: center; justify-content: center; gap: 8px;
  border-top: 1px solid rgba(255,255,255,0.06); font-size: 12px; cursor: pointer;
  color: #8294bb; transition: color .16s ease, background .16s ease;
}
.collapse-bar:hover { color: #fff; background: rgba(255,255,255,0.06); }

/* ---------- 主区域 ---------- */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.header {
  flex: 0 0 62px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; background: #fff; border-bottom: 1px solid #edf0f5;
}
.header-left { display: flex; align-items: center; gap: 11px; }
.crumb-home {
  width: 28px; height: 28px; border-radius: 8px; display: flex; align-items: center; justify-content: center;
  background: #eef2ff; color: var(--app-primary); font-size: 14px;
}
.crumbs { font-size: 13px; }
.header-right { display: flex; align-items: center; gap: 14px; font-size: 12.5px; color: #77839a; }
.today { white-space: nowrap; }
.split { width: 1px; height: 16px; background: #e6eaf1; }
.header-icon {
  position: relative; width: 30px; height: 30px; border-radius: 8px; display: flex;
  align-items: center; justify-content: center; font-size: 16px; color: #6b7892; cursor: pointer;
  transition: background .15s ease, color .15s ease;
}
.header-icon:hover { background: #f4f6fb; color: var(--app-primary); }
.bell-dot {
  position: absolute; right: 5px; top: 5px; width: 6px; height: 6px; border-radius: 50%;
  background: #f56c6c; border: 1.5px solid #fff;
}
.user-chip { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.avatar {
  width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #818cf8, var(--app-primary)); color: #fff;
  font-size: 12px; font-weight: 600; box-shadow: 0 3px 8px rgba(79,70,229,0.25);
}
.user-name { color: #2d3748; font-weight: 500; }
.user-caret { font-size: 10px; color: #a5aec0; }
.content { flex: 1; padding: 20px 24px 26px; overflow-y: auto; background: #f6f8fc; }
</style>
