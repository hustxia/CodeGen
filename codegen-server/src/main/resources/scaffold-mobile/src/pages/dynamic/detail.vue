<template>
  <view>
    <view v-if="row" class="card">
      <view v-for="c in allCols" :key="c.field" class="detail-line">
        <text class="detail-label">{{ c.label }}</text>
        <text class="detail-value">{{ displayValue(c, row[c.field]) }}</text>
      </view>
    </view>
    <view v-else-if="loaded" class="card empty">数据不存在</view>

    <view class="action-bar">
      <button size="mini" class="btn-primary" @click="goEdit">编辑</button>
      <button size="mini" class="btn-danger" @click="doDelete">删除</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { get, del } from '@/utils/request'
import { findTable, displayValue } from '@/utils/tables'

let config = null
const row = ref(null)
const loaded = ref(false)
let id = null

onLoad((opt) => {
  config = findTable(opt.module, opt.table)
  id = opt.id
  uni.setNavigationBarTitle({ title: config ? config.title + '详情' : '详情' })
})

onShow(load)

const allCols = computed(() => {
  if (!config) return []
  const seen = new Set()
  const cols = []
  for (const c of [...(config.listCols || []), ...(config.formCols || [])]) {
    if (!seen.has(c.field)) {
      seen.add(c.field)
      cols.push(c)
    }
  }
  return cols
})

async function load() {
  if (!config || !id) return
  try {
    row.value = await get(`${config.api}/${id}`)
  } finally {
    loaded.value = true
  }
}

function goEdit() {
  uni.navigateTo({
    url: `/pages/dynamic/form?module=${config.module}&table=${config.table}&id=${id}`
  })
}

function doDelete() {
  uni.showModal({
    title: '确认删除',
    content: '删除后不可恢复，是否继续？',
    success: async (res) => {
      if (res.confirm) {
        await del(`${config.api}/${id}`)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 600)
      }
    }
  })
}
</script>

<style scoped>
.empty {
  color: #909399;
  text-align: center;
}
.detail-line {
  display: flex;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f2f3f5;
}
.detail-line:last-child {
  border-bottom: none;
}
.detail-label {
  width: 200rpx;
  color: #909399;
  flex-shrink: 0;
}
.detail-value {
  flex: 1;
  word-break: break-all;
}
.action-bar {
  display: flex;
  justify-content: center;
  gap: 32rpx;
  padding: 32rpx 0 64rpx;
}
.btn-danger {
  background: #fa3534;
  color: #ffffff;
  border-radius: 12rpx;
}
</style>
