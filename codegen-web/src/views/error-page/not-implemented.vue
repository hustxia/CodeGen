<template>
  <div class="not-implemented">
    <i class="el-icon-document-add" />
    <h2>{{ title }}</h2>
    <p>该菜单已注册，但还没有绑定可用的前端页面。</p>
    <p class="hint">
      处理方式：在「系统管理 → 菜单管理」里把「组件路径」填成 <code>src/views</code> 下的真实路径
      （如 <code>goods/list</code>），或先用代码生成器生成页面。
    </p>
    <el-button type="primary" plain @click="$router.push('/tool/gen')">去代码生成器</el-button>
  </div>
</template>

<script>
export default {
  name: 'NotImplementedPage',
  computed: {
    title() {
      return (this.$route.meta && this.$route.meta.title) || '页面未实现'
    }
  }
}
</script>

<style scoped>
.not-implemented {
  padding: 80px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--cg-text);
  background: #fff;
  border-radius: 6px;
}

.not-implemented i {
  font-size: 56px;
  color: var(--cg-text-placeholder);
  margin-bottom: 12px;
}

.not-implemented h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.not-implemented p {
  margin: 4px 0;
  color: var(--cg-text-regular);
}

.not-implemented .hint {
  font-size: 13px;
  color: var(--cg-text-secondary);
  max-width: 560px;
  text-align: center;
  margin-bottom: 18px;
}

.not-implemented code {
  background: var(--cg-bg-page);
  padding: 1px 5px;
  border-radius: 3px;
}
</style>
