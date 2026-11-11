<template>
  <el-container class="layout-wrapper">
    <!-- 侧边栏 -->
    <el-aside :width="sidebar.opened ? '210px' : '64px'" class="layout-aside">
      <div class="logo" @click="$router.push('/')">
        <span class="logo-badge"><i class="el-icon-shopping-bag-1"></i></span>
        <span v-show="sidebar.opened" class="logo-text">CodeGen 平台</span>
      </div>

      <!-- 自定义导航：一级点击后二级菜单从右侧滑出 -->
      <nav class="side-nav">
        <div v-for="item in navItems" :key="item.key" class="nav-item-wrap">
          <!-- 单级菜单 -->
          <router-link
            v-if="!item.children"
            :to="item.path"
            class="nav-item"
            :class="{ active: activeMenu === item.path }"
          >
            <i :class="item.icon"></i>
            <span v-show="sidebar.opened" class="nav-label">{{ item.title }}</span>
          </router-link>

          <!-- 含子菜单：点击滑出 -->
          <div
            v-else
            class="nav-item has-children"
            :class="{ active: isGroupActive(item), open: openGroup === item.key }"
            @click.stop="toggleGroup(item)"
          >
            <i :class="item.icon"></i>
            <span v-show="sidebar.opened" class="nav-label">{{ item.title }}</span>
            <i v-show="sidebar.opened" class="el-icon-arrow-right nav-arrow"></i>
          </div>

          <!-- 右侧滑出的二级菜单 -->
          <transition name="fly">
            <div v-if="item.children && openGroup === item.key" class="flyout" @click.stop>
              <div class="flyout-title">{{ item.title }}</div>
              <router-link
                v-for="child in item.children"
                :key="child.path"
                :to="child.path"
                class="flyout-item"
                :class="{ active: activeMenu === child.path }"
                @click.native="closeFlyout"
              >
                {{ child.title }}
              </router-link>
            </div>
          </transition>
        </div>
      </nav>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="layout-header" height="50px">
        <i
          :class="sidebar.opened ? 'el-icon-s-fold' : 'el-icon-s-unfold'"
          class="hamburger"
          @click="$store.dispatch('app/toggleSideBar')"
        ></i>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="$route.meta.title && $route.path !== '/dashboard'">
            {{ $route.meta.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>

        <div class="right-menu">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <i class="el-icon-user-solid"></i>
              {{ name || '未登录' }}
              <i class="el-icon-arrow-down el-icon--right"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="home">返回首页</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="layout-main" @click.native="closeFlyout">
        <router-view :key="$route.fullPath" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'Layout',
  data() {
    return {
      openGroup: null // 当前滑出的一级菜单 key
    }
  },
  computed: {
    ...mapGetters(['sidebar', 'name', 'menus']),
    // 导航结构直接由后端下发的菜单树派生（V2.0 动态路由）：
    // 不再读 $router.options.routes —— addRoute 注册的路由不会回写这个数组，
    // 而且那样会把"用户没权限的菜单"也渲染出来。
    navItems() {
      const items = []
      for (const node of this.menus || []) {
        const children = (node.children || []).filter(c => c.type !== 3)
        if (node.type === 1) {
          if (children.length === 0) continue
          if (children.length === 1) {
            // 单子节点提升为一级菜单项
            items.push({
              key: node.path,
              title: children[0].title,
              icon: children[0].icon || node.icon,
              path: children[0].path
            })
          } else {
            items.push({
              key: node.path,
              title: node.title,
              icon: node.icon,
              children: children.map(c => ({ title: c.title, path: c.path }))
            })
          }
        } else {
          items.push({ key: node.path, title: node.title, icon: node.icon, path: node.path })
        }
      }
      return items
    },
    // 详情/编辑页通过 meta.activeMenu 高亮所属菜单
    activeMenu() {
      return this.$route.meta.activeMenu || this.$route.path
    }
  },
  mounted() {
    document.addEventListener('click', this.closeFlyout)
  },
  beforeDestroy() {
    document.removeEventListener('click', this.closeFlyout)
  },
  methods: {
    resolvePath(base, routePath) {
      if (routePath.startsWith('/')) return routePath
      return `${base}/${routePath}`.replace(/\/+/g, '/')
    },
    toggleGroup(item) {
      this.openGroup = this.openGroup === item.key ? null : item.key
    },
    closeFlyout() {
      this.openGroup = null
    },
    // 当前路由属于该分组时高亮一级菜单
    isGroupActive(item) {
      return this.$route.path === item.key || this.$route.path.startsWith(item.key + '/')
    },
    async handleCommand(command) {
      if (command === 'logout') {
        await this.$store.dispatch('user/logout').catch(() => {
          this.$store.dispatch('user/resetToken')
        })
        this.$router.push(`/login?redirect=${this.$route.fullPath}`)
      } else if (command === 'home') {
        this.$router.push('/')
      }
    }
  }
}
</script>

<style scoped>
.layout-wrapper {
  height: 100%;
}

.layout-aside {
  background: linear-gradient(180deg, var(--cg-aside-from) 0%, var(--cg-aside-to) 100%);
  transition: width 0.28s;
  overflow: visible; /* 允许二级菜单向右滑出 */
  box-shadow: var(--cg-aside-shadow);
  z-index: 10;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  white-space: nowrap;
}

.logo-badge {
  width: 34px;
  height: 34px;
  border-radius: var(--cg-radius-badge);
  background: linear-gradient(135deg, var(--cg-primary-solid), var(--cg-primary-active));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 10px var(--cg-primary-glow);
}

.logo-badge i {
  font-size: 20px;
  color: #fff;
}

.logo-text {
  margin-left: 10px;
  color: var(--cg-aside-text-active);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

/* ---------- 一级菜单 ---------- */
.side-nav {
  padding: 6px 0;
}

.nav-item-wrap {
  position: relative;
}

.nav-item {
  display: flex;
  align-items: center;
  height: 46px;
  margin: 4px 10px;
  padding: 0 14px;
  border-radius: var(--cg-radius-nav);
  color: var(--cg-aside-text);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s;
}

.nav-item > i:first-child {
  font-size: 18px;
  margin-right: 10px;
  flex-shrink: 0;
}

.nav-item:hover {
  background: var(--cg-aside-hover);
  color: var(--cg-aside-text-active);
}

.nav-item.active {
  background: linear-gradient(90deg, var(--cg-primary-solid), var(--cg-primary-active));
  color: #fff;
  box-shadow: 0 4px 10px var(--cg-primary-glow);
}

.nav-item.has-children.open {
  color: var(--cg-aside-text-active);
  background: var(--cg-aside-hover);
}

.nav-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-arrow {
  font-size: 12px;
  transition: transform 0.2s;
}

.nav-item.has-children.open .nav-arrow {
  transform: rotate(90deg);
}

/* 折叠时图标居中 */
.layout-aside[style*='64px'] .nav-item {
  justify-content: center;
  padding: 0;
}

.layout-aside[style*='64px'] .nav-item > i:first-child {
  margin-right: 0;
}

/* ---------- 右侧滑出的二级菜单 ---------- */
.flyout {
  position: absolute;
  left: calc(100% + 8px);
  top: 0;
  min-width: 152px;
  padding: 8px;
  background: var(--cg-aside-panel);
  border-radius: var(--cg-radius-nav);
  box-shadow: 0 10px 32px rgba(6, 12, 34, 0.55);
  z-index: 3000;
}

.flyout-title {
  padding: 6px 12px 8px;
  font-size: 12px;
  color: var(--cg-aside-text);
  opacity: 0.7;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  margin-bottom: 6px;
}

.flyout-item {
  display: block;
  padding: 9px 12px;
  border-radius: var(--cg-radius-sm);
  color: var(--cg-aside-text);
  font-size: 13px;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}

.flyout-item:hover {
  background: var(--cg-aside-hover);
  color: var(--cg-aside-text-active);
}

.flyout-item.active {
  background: linear-gradient(90deg, var(--cg-primary-solid), var(--cg-primary-active));
  color: #fff;
}

/* 滑出动画 */
.fly-enter-active,
.fly-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fly-enter,
.fly-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

/* ---------- 顶栏 ---------- */
.layout-header {
  display: flex;
  align-items: center;
  background: var(--cg-header-bg);
  box-shadow: var(--cg-header-shadow);
  position: relative;
  z-index: 5;
}

.hamburger {
  font-size: 20px;
  cursor: pointer;
  margin-right: 16px;
  color: var(--cg-text-regular);
  padding: 6px;
  border-radius: var(--cg-radius-sm);
  transition: background 0.2s;
}

.hamburger:hover {
  background: var(--cg-bg-hover);
}

.right-menu {
  margin-left: auto;
}

.user-info {
  cursor: pointer;
  color: var(--cg-text-regular);
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 20px;
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--cg-bg-hover);
}

.user-info .el-icon-user-solid {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--cg-primary-solid), var(--cg-primary-active));
  color: #fff;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
}

.layout-main {
  background: var(--cg-bg-page);
  padding: 0;
}
</style>
