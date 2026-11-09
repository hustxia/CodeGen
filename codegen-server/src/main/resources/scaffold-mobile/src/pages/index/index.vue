<template>
  <view>
    <view v-if="groups.length === 0" class="card empty">暂无移动端业务表：在平台开启 mobile_enabled 并重新生成即可</view>
    <view v-for="g in groups" :key="g.module" class="module-group">
      <view class="module-name">{{ g.module }}</view>
      <view class="grid">
        <view v-for="t in g.tables" :key="t.table" class="grid-item card" @click="goList(t)">
          <view class="grid-title">{{ t.title }}</view>
          <view class="grid-sub">{{ t.table }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { allTables } from '@/utils/tables'

// 业务入口宫格：按模块分组展示全部已生成表配置
const groups = computed(() => {
  const map = new Map()
  for (const t of allTables) {
    if (!map.has(t.module)) map.set(t.module, [])
    map.get(t.module).push(t)
  }
  return Array.from(map, ([module, tables]) => ({ module, tables }))
})

function goList(t) {
  uni.navigateTo({ url: `/pages/dynamic/list?module=${t.module}&table=${t.table}` })
}
</script>

<style scoped>
.empty {
  color: #909399;
  text-align: center;
}
.module-name {
  margin: 24rpx 24rpx 0;
  color: #909399;
  font-size: 24rpx;
}
.grid {
  display: flex;
  flex-wrap: wrap;
}
.grid-item {
  width: 300rpx;
  box-sizing: border-box;
}
.grid-title {
  font-size: 30rpx;
  font-weight: bold;
}
.grid-sub {
  color: #c0c4cc;
  font-size: 22rpx;
  margin-top: 8rpx;
}
</style>
