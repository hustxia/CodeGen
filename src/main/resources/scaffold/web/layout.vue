<template>
  <div class="layout">
    <!-- 一级：系统栏，只展示系统本身 -->
    <aside class="rail">
      <div class="brand">
        __BRAND_MARK__
      </div>
      <div class="rail-list">
        <div
          v-for="(sys, i) in menus"
          :key="sys.path"
          class="rail-item"
          :class="{ 'is-active': isSysActive(sys), 'is-open': panelSys && panelSys.path === sys.path }"
          @click="toggleSys(sys)"
        >
          <el-icon class="rail-icon"><component :is="sysIcon(i)" /></el-icon>
          <span class="rail-label">{{ shortTitle(sys.title) }}</span>
        </div>
      </div>
      <div class="rail-list rail-foot">
        <div class="rail-item" :class="{ 'is-active': $route.path === '/home' }" @click="go('/home')">
          <el-icon class="rail-icon"><HomeFilled /></el-icon>
          <span class="rail-label">{{ homeLabel }}</span>
        </div>
      </div>
    </aside>

    <!-- 二级：业务域面板，从一级栏右侧滑出 -->
    <div class="flyout flyout-sub" :class="{ 'is-open': !!panelSys }">
      <div v-if="panelSys" class="flyout-head">
        <div class="flyout-title">{{ panelSys.title }}</div>
        <div class="flyout-desc">{{ businessModulesHint }}</div>
      </div>
      <div v-if="panelSys" class="flyout-body">
        <div
          v-for="grp in panelSys.children"
          :key="grp.path"
          class="group-item"
          :class="{ 'is-active': isGrpActive(grp) }"
          @click="openGroup(grp)"
        >
          <span class="group-name">{{ grp.title }}</span>
          <span class="group-count">{{ grp.children.length || 1 }}</span>
          <el-icon class="group-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 三级：页面面板，从二级面板右侧继续滑出 -->
    <div class="flyout flyout-leaf" :class="{ 'is-open': !!panelGroup }">
      <div v-if="panelGroup" class="flyout-head leaf-head">
        <div class="leaf-crumb">{{ panelSys ? panelSys.title : '' }}</div>
        <div class="leaf-title">{{ panelGroup.title }}</div>
      </div>
      <div v-if="panelGroup" class="flyout-body">
        <div
          v-for="page in panelGroup.children"
          :key="page.path"
          class="page-item"
          :class="{ 'is-active': $route.path === page.path }"
          @click="go(page.path)"
        >
          <span class="page-dot"></span>
          <span class="page-name">{{ page.title }}</span>
        </div>
      </div>
    </div>

    <div class="main">
      <div class="header">
        <div class="header-left">
          <el-breadcrumb separator="/" class="crumbs">
            <el-breadcrumb-item v-for="(crumb, i) in crumbs" :key="i">{{ crumb }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="env-tag">DEV</span>
          <span class="user">{{ userName }}</span>__I18N_SWITCH__
        </div>
      </div>
      <div class="content" @click="closePanels">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script>
/**
 * 三级菜单：系统 → 业务域 → 页面。
 * 菜单树直接从路由表推导（this.$router.options.routes），
 * 路由与菜单永远同源，不需要再维护一份菜单配置；
 * 也不能静态 import 路由表——gen-routes 与本组件互相引用会形成循环依赖，
 * 一旦加载顺序不利，Layout 会被解析成 undefined，所有生成页面直接白屏。
 */
/**
 * V8.0 菜单国际化：把路由 meta 的中文 title 按当前语言解析。
 * - meta 带了 i18nKey 且运行时挂了 $t（开 i18n）：调用 $t(i18nKey, 中文)，缺译文回落中文。
 * - 没有 i18nKey（旧路由）或没挂 $t（关 i18n）：直接返回中文 title。
 * 第二参是中文回落源，保证 206 种语言里没预置译文的也能显示中文，不会空白。
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
    // 只保留「有子节点」或「有实际页面」的节点，Layout 壳子本身不进菜单
    const hasComponent = typeof route.component === 'function'
    if (!children.length && !hasComponent) return
    list.push({
      path,
      title: resolveTitle(route.meta, route.name || path),
      children
    })
  })
  return list
}

const ICONS = [
  'Platform',
  'Cooperation',
  'Shop',
  'DataAnalysis',
  'Select',
  'Setting'
]

export default {
  name: 'Layout',
  data() {
    return {
      userName: '管理员',
      panelSys: null,
      panelGroup: null
    }
  },
  computed: {
    menus() {
      return buildMenu(this.$router.options.routes, '')
        // 顶层 '/' 是 Layout 壳（包首页），侧栏底部已单独有「首页」入口，这里不重复
        .filter(m => m.path !== '/' && m.path !== '/home')
    },
    // V8.0 菜单国际化：面包屑同样按当前语言解析；回落「首页」
    crumbs() {
      const list = this.$route.matched
        .map(item => resolveTitle(item.meta, ''))
        .filter(Boolean)
      return list.length ? list : [this.homeLabel]
    },
    // 「首页」入口文案：开 i18n 时取译文，关 i18n / 未挂 $t 时回落中文
    homeLabel() {
      return (typeof window.$t === 'function') ? window.$t('common.home') : '首页'
    },
    // 业务模块计数文案：开 i18n 时按 {count} 插值解析，关时回落「N 个业务模块」
    businessModulesHint() {
      const count = (this.panelSys && this.panelSys.children) ? this.panelSys.children.length : 0
      if (typeof window.$t === 'function') {
        return window.$t('common.businessModules', { count: count })
      }
      return count + ' 个业务模块'
    }
  },
  watch: {
    '$route.path'() {
      this.closePanels()
    }
  },
  mounted() {
    // URL 带 ?_menu=1 时自动展开当前系统与所在分组，便于演示与自动化验证
    if (this.$route.query._menu) {
      const sys = this.menus.find(s => this.isSysActive(s)) || this.menus[0]
      if (sys) this.toggleSys(sys)
    }
  },
  methods: {
    sysIcon(i) {
      return ICONS[i % ICONS.length]
    },
    shortTitle(title) {
      // 侧栏较窄，"WMS 系统" → "WMS" 之外保留完整标题换行展示
      return title || ''
    },
    isSysActive(sys) {
      const p = this.$route.path
      return p === sys.path || p.indexOf(sys.path + '/') === 0
    },
    isGrpActive(grp) {
      const p = this.$route.path
      return p === grp.path || p.indexOf(grp.path + '/') === 0
    },
    toggleSys(sys) {
      if (this.panelSys && this.panelSys.path === sys.path) {
        this.closePanels()
        return
      }
      this.panelSys = sys
      this.panelGroup = null
      // 进入系统时自动展开当前所在分组，减少一次点击
      const hit = (sys.children || []).find(g => this.isGrpActive(g))
      if (hit && hit.children && hit.children.length) {
        this.panelGroup = hit
      }
    },
    openGroup(grp) {
      if (!grp.children || !grp.children.length) {
        this.go(grp.path)
        return
      }
      this.panelGroup = this.panelGroup && this.panelGroup.path === grp.path ? null : grp
    },
    go(path) {
      if (!path) return
      this.closePanels()
      if (this.$route.path !== path) this.$router.push(path)
    },
    closePanels() {
      this.panelSys = null
      this.panelGroup = null
    }
  }
}
</script>

<style>
/* V6.8 品牌主色：唯一来源是 sys_project.app_theme_color（空则 #e1251b），脚手架只替换这一个变量 */
:root {
  --app-primary: __THEME_COLOR__;
}
.layout {
  position: relative;
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #f4f6fa;
}

/* ---------- 一级系统栏 ---------- */
.rail {
  position: relative;
  z-index: 30;
  flex: 0 0 86px;
  width: 86px;
  display: flex;
  flex-direction: column;
  background: #131a2b;
}
.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.brand-mark {
  width: 34px;
  height: 34px;
  line-height: 34px;
  text-align: center;
  border-radius: 10px;
  background: var(--app-primary);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
}
.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  object-fit: contain;
  background: #fff;
  padding: 3px;
}
.rail-list {
  flex: 1;
  padding: 8px 6px;
  overflow-y: auto;
}
.rail-foot {
  flex: 0 0 auto;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding: 8px 6px 12px;
}
.rail-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 10px 4px;
  margin-bottom: 4px;
  border-radius: 10px;
  cursor: pointer;
  color: #93a0bb;
  transition: background 0.18s ease, color 0.18s ease;
}
.rail-icon {
  font-size: 19px;
}
.rail-label {
  font-size: 11px;
  line-height: 1.25;
  text-align: center;
  max-width: 76px;
  word-break: break-all;
}
.rail-item:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #dbe3f3;
}
.rail-item.is-active {
  color: #fff;
}
.rail-item.is-active::before,
.rail-item.is-open::before {
  content: '';
  position: absolute;
  left: -6px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 22px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #6f8dff 0%, #4d7cfe 100%);
}
.rail-item.is-open {
  background: rgba(77, 124, 254, 0.18);
  color: #fff;
}

/* ---------- 二级 / 三级飞出面板 ---------- */
.flyout {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  opacity: 0;
  pointer-events: none;
  transition: transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1), opacity 0.16s ease;
}
.flyout-sub {
  left: 86px;
  width: 208px;
  background: #1b2540;
  box-shadow: 10px 0 26px rgba(8, 12, 24, 0.28);
  transform: translateX(-14px);
}
.flyout-leaf {
  left: 294px;
  width: 244px;
  background: #fff;
  box-shadow: 14px 0 32px rgba(8, 12, 24, 0.12);
  transform: translateX(-18px);
}
.flyout.is-open {
  opacity: 1;
  pointer-events: auto;
}
.flyout-sub.is-open,
.flyout-leaf.is-open {
  transform: translateX(0);
}
.flyout-head {
  flex: 0 0 auto;
  padding: 0 16px;
  min-height: 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}
.flyout-title {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.flyout-desc {
  color: rgba(199, 210, 228, 0.55);
  font-size: 11px;
}
.flyout-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

/* 二级项 */
.group-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px;
  margin-bottom: 2px;
  border-radius: 8px;
  color: #c3cee2;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.group-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.group-count {
  font-size: 11px;
  color: rgba(199, 210, 228, 0.5);
}
.group-arrow {
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.16s ease;
}
.group-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.group-item:hover .group-arrow {
  opacity: 1;
}
.group-item.is-active {
  background: rgba(77, 124, 254, 0.22);
  color: #fff;
}
.group-item.is-active .group-count {
  color: #9db4ff;
}

/* 三级项 */
.leaf-head {
  border-bottom: 1px solid #eef1f7;
}
.leaf-crumb {
  font-size: 11px;
  color: #98a2b8;
}
.leaf-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.page-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 10px;
  margin-bottom: 2px;
  border-radius: 8px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.page-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #c8d1e2;
  flex: 0 0 4px;
}
.page-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.page-item:hover {
  background: #f3f6fc;
  color: #1f2937;
}
.page-item:hover .page-dot {
  background: #4d7cfe;
}
.page-item.is-active {
  background: #eef2ff;
  color: #3f6ae0;
  font-weight: 500;
}
.page-item.is-active .page-dot {
  background: #3f6ae0;
}

/* ---------- 主区域 ---------- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.header {
  flex: 0 0 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e8ecf3;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.crumbs {
  font-size: 13px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #5a6478;
}
.env-tag {
  padding: 1px 8px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 11px;
  font-weight: 600;
}
.content {
  flex: 1;
  padding: 18px 20px 24px;
  overflow-y: auto;
  background: #f4f6fa;
}
</style>
