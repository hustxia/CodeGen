<template>
  <div class="forbidden">
    <h1>403</h1>
    <p>当前账号没有访问该页面的权限</p>
    <p class="hint">如需开通，请联系管理员在「系统管理 → 角色管理 → 分配权限」中授权对应菜单。</p>
    <div class="actions">
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      <el-button @click="$router.push('/login')">切换账号</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ForbiddenPage'
}
</script>

<style scoped>
.forbidden {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--cg-bg-page);
}

.forbidden h1 {
  font-size: 80px;
  margin: 0;
  color: var(--cg-warning);
}

.forbidden p {
  margin: 6px 0;
  color: var(--cg-text);
}

.forbidden .hint {
  font-size: 13px;
  color: var(--cg-text-secondary);
  margin-bottom: 18px;
}
</style>
