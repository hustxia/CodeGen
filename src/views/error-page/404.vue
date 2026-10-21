<template>
  <div class="not-found">
    <h1>404</h1>
    <p>抱歉，你访问的页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script>
export default {
  name: 'NotFoundPage'
}
</script>

<style scoped>
.not-found {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.not-found h1 {
  font-size: 80px;
  margin: 0;
  color: #409eff;
}
</style>
