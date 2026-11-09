<template>
  <div v-if="!item.hidden">
    <!-- 只有一个可见子节点：渲染为单级菜单 -->
    <el-menu-item
      v-if="singleChild"
      :index="resolvePath(singleChild.path)"
    >
      <i :class="singleChild.meta.icon || (item.meta && item.meta.icon)"></i>
      <span slot="title">{{ singleChild.meta.title }}</span>
    </el-menu-item>

    <!-- 多个子节点：渲染为子菜单 -->
    <el-submenu v-else :index="resolvePath(item.path)">
      <template slot="title">
        <i :class="item.meta && item.meta.icon"></i>
        <span slot="title">{{ item.meta && item.meta.title }}</span>
      </template>
      <sidebar-item
        v-for="child in visibleChildren"
        :key="child.path"
        :item="child"
        :base-path="resolvePath(item.path)"
      />
    </el-submenu>
  </div>
</template>

<script>
export default {
  name: 'SidebarItem',
  props: {
    item: { type: Object, required: true },
    basePath: { type: String, default: '' }
  },
  computed: {
    visibleChildren() {
      return (this.item.children || []).filter(c => !c.hidden)
    },
    // 无子节点时当前项自身是叶子菜单；仅一个可见子节点且该子节点无子菜单时，提升为单级菜单
    singleChild() {
      const children = this.visibleChildren
      if (children.length === 0) return this.item
      if (children.length === 1 && !(children[0].children && children[0].children.some(c => !c.hidden))) {
        return children[0]
      }
      return null
    }
  },
  methods: {
    resolvePath(routePath) {
      if (routePath.startsWith('/')) return routePath
      if (!this.basePath) return routePath
      return `${this.basePath}/${routePath}`.replace(/\/+/g, '/')
    }
  }
}
</script>
