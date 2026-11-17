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

        <div v-show="!collapsed" class="section-title">业务导航</div>
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
          <el-breadcrumb separator="/" class="crumbs">
            <el-breadcrumb-item v-for="(crumb, i) in crumbs" :key="i">{{ crumb }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="today">{{ today }}</span>
          <span class="split"></span>
          <span class="user-name"><el-icon><UserFilled /></el-icon>{{ userName }}</span>__I18N_SWITCH__
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
      // 系统壳（如 /xxx）本身不作为一级菜单，它的 children 才是业务域
      const sys = this.menus.find(m => m.path !== '/' && m.path !== '/home' && m.children.length)
      const all = sys ? sys.children : this.menus.filter(m => m.path !== '/' && m.path !== '/home')
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
    '$route.path'() {
      this.syncOpenGroup()
    },
    keyword(val) {
      if (val) {
        const first = this.groups.find(g => g.children.length)
        if (first) this.openKey = first.path
      }
    }
  },
  mounted() {
    this.syncOpenGroup()
  },
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
:root { --app-primary: __THEME_COLOR__; }
.layout { display: flex; height: 100vh; overflow: hidden; background: #f4f6fa; }

/* ---------- 左侧菜单 ---------- */
.sidebar {
  position: relative; z-index: 20; flex: 0 0 224px; width: 224px;
  display: flex; flex-direction: column; color: #c5d0e6;
  background: linear-gradient(180deg, #1c2b49 0%, #14213a 100%);
  transition: width 0.2s ease, flex-basis 0.2s ease;
}
.sidebar.is-collapsed { flex-basis: 64px; width: 64px; }
.logo-bar {
  height: 60px; flex: 0 0 60px; display: flex; align-items: center; gap: 10px;
  padding: 0 16px; border-bottom: 1px solid rgba(255,255,255,0.07); overflow: hidden;
}
.logo-mark {
  flex: 0 0 32px; width: 32px; height: 32px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #5b8cff, var(--app-primary)); color: #fff;
  font-size: 16px; font-weight: 700; box-shadow: 0 3px 10px rgba(0,0,0,0.25);
}
.brand-logo { flex: 0 0 32px; width: 32px; height: 32px; border-radius: 9px; object-fit: contain; background: #fff; padding: 3px; }
.logo-text { font-size: 15px; font-weight: 600; color: #fff; white-space: nowrap; letter-spacing: 1px; }
.menu-search { padding: 12px 12px 6px; }
.menu-search .el-input__wrapper {
  background: rgba(255,255,255,0.08); box-shadow: none; border: 1px solid rgba(255,255,255,0.08);
}
.menu-search .el-input__inner { color: #e6edf8; }
.menu-search .el-input__inner::placeholder { color: #7e8ca8; }
.menu-scroll { flex: 1; overflow-y: auto; padding: 0 8px 12px; }
.menu-scroll::-webkit-scrollbar { width: 4px; }
.menu-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.12); border-radius: 4px; }
.section-title {
  padding: 12px 12px 6px; font-size: 11px; letter-spacing: 1px;
  color: rgba(197,208,230,0.45); white-space: nowrap;
}
.menu-home, .group-title {
  display: flex; align-items: center; gap: 10px; height: 40px; padding: 0 12px;
  margin-bottom: 2px; border-radius: 8px; font-size: 13px; cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease; white-space: nowrap;
}
.menu-home:hover, .group-title:hover { background: rgba(255,255,255,0.08); color: #fff; }
.menu-home.is-active, .group-title.is-active { background: rgba(63,106,224,0.28); color: #fff; }
.group-title.is-open { color: #fff; }
.sidebar.is-collapsed .menu-home, .sidebar.is-collapsed .group-title { justify-content: center; padding: 0; }
.group-icon { flex: 0 0 18px; font-size: 16px; color: #8fa8dc; }
.group-text { flex: 1; overflow: hidden; text-overflow: ellipsis; }
.group-count {
  font-size: 10px; min-width: 18px; text-align: center; padding: 1px 4px;
  border-radius: 9px; background: rgba(255,255,255,0.09); color: #a9b9d8;
}
.group-arrow { font-size: 11px; transition: transform 0.18s ease; opacity: 0.7; }
.group-arrow.is-rotated { transform: rotate(180deg); }
.group-children { padding: 2px 0 6px 0; }
.child-item {
  display: flex; align-items: center; gap: 9px; height: 34px; padding: 0 12px 0 30px;
  margin-bottom: 1px; border-radius: 7px; font-size: 12.5px; color: #9fb0cf;
  cursor: pointer; transition: background 0.14s ease, color 0.14s ease;
}
.child-dot { width: 4px; height: 4px; border-radius: 50%; background: #63749a; flex: 0 0 4px; }
.child-text { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.child-item:hover { background: rgba(255,255,255,0.06); color: #e6edf8; }
.child-item.is-active { background: var(--app-primary); color: #fff; font-weight: 500; }
.child-item.is-active .child-dot { background: #fff; }
.empty-tip { padding: 16px 12px; font-size: 12px; color: #8494b5; text-align: center; }
.collapse-bar {
  flex: 0 0 44px; height: 44px; display: flex; align-items: center; justify-content: center; gap: 8px;
  border-top: 1px solid rgba(255,255,255,0.07); font-size: 12px; cursor: pointer;
  color: #8fa0c2; transition: color 0.16s ease, background 0.16s ease;
}
.collapse-bar:hover { color: #fff; background: rgba(255,255,255,0.06); }

/* ---------- 主区域 ---------- */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.header {
  flex: 0 0 60px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 22px; background: #fff; border-bottom: 1px solid #e7ebf2;
}
.crumbs { font-size: 13px; }
.header-right { display: flex; align-items: center; gap: 14px; font-size: 12.5px; color: #6b768c; }
.today { white-space: nowrap; }
.split { width: 1px; height: 16px; background: #e3e8f0; }
.user-name { display: inline-flex; align-items: center; gap: 6px; color: #33405c; font-weight: 500; }
.user-name .el-icon { color: var(--app-primary); font-size: 15px; }
.content { flex: 1; padding: 18px 20px 24px; overflow-y: auto; background: #f4f6fa; }
</style>
