<template>
  <view>
    <!-- 搜索区：searchCols 声明式渲染（枚举列出 picker，其余 input） -->
    <view class="card search-bar">
      <view v-for="c in config.searchCols" :key="c.field" class="search-row">
        <text class="search-label">{{ c.label }}</text>
        <picker
          v-if="c.ctrl === 'picker' && Array.isArray(c.enum)"
          :range="c.enum"
          range-key="label"
          @change="(e) => (query[c.field] = c.enum[e.detail.value].value)"
        >
          <view class="search-value">{{ pickerLabel(c, query[c.field]) }}</view>
        </picker>
        <input v-else v-model="query[c.field]" class="search-input" :placeholder="c.label" />
      </view>
      <button size="mini" class="btn-primary" @click="reload">查询</button>
    </view>

    <view v-if="items.length === 0 && !loading" class="card empty">暂无数据</view>

    <view v-for="row in items" :key="row.id" class="card row-card" @click="goDetail(row)">
      <view v-for="c in cardCols" :key="c.field" class="row-line">
        <text class="row-label">{{ c.label }}</text>
        <text class="row-value">{{ displayValue(c, row[c.field]) }}</text>
      </view>
    </view>

    <view class="load-more">{{ finished ? '没有更多了' : loading ? '加载中…' : '' }}</view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { post } from '@/utils/request'
import { findTable, displayValue } from '@/utils/tables'

let config = null
const items = ref([])
const page = ref(1)
const size = 10
const finished = ref(false)
const loading = ref(false)
const query = reactive({})

onLoad((opt) => {
  config = findTable(opt.module, opt.table)
  uni.setNavigationBarTitle({ title: config ? config.title : opt.table })
  if (config) {
    for (const c of config.searchCols || []) {
      query[c.field] = ''
    }
  }
  reload()
})

const cardCols = computed(() => (config ? (config.listCols || []).slice(0, 4) : []))

function pickerLabel(c, value) {
  if (Array.isArray(c.enum)) {
    const hit = c.enum.find((o) => String(o.value) === String(value || ''))
    return hit ? hit.label : '全部'
  }
  return '全部'
}

async function loadPage() {
  if (!config || loading.value || finished.value) return
  loading.value = true
  try {
    // 分页语义与 PC page 接口一致：POST {api}/page，pageNum/pageSize + 查询参数
    const payload = { pageNum: page.value, pageSize: size, ...cleanQuery() }
    const data = await post(config.api + '/page', payload)
    const list = data && Array.isArray(data.records) ? data.records : Array.isArray(data) ? data : []
    if (list.length < size) finished.value = true
    items.value = page.value === 1 ? list : items.value.concat(list)
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function cleanQuery() {
  const out = {}
  for (const [k, v] of Object.entries(query)) {
    if (v !== '' && v !== null && v !== undefined) out[k] = v
  }
  return out
}

function reload() {
  page.value = 1
  finished.value = false
  items.value = []
  loadPage()
}

onPullDownRefresh(reload)
onReachBottom(() => {
  if (!finished.value) {
    page.value += 1
    loadPage()
  }
})

function goDetail(row) {
  uni.navigateTo({ url: `/pages/dynamic/detail?module=${config.module}&table=${config.table}&id=${row.id}` })
}
</script>

<style scoped>
.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.search-row {
  display: flex;
  align-items: center;
  width: 100%;
  margin-bottom: 16rpx;
}
.search-label {
  width: 140rpx;
  color: #606266;
}
.search-input,
.search-value {
  flex: 1;
  border: 1rpx solid #e4e7ed;
  border-radius: 8rpx;
  padding: 12rpx 16rpx;
}
.empty {
  color: #909399;
  text-align: center;
}
.row-card {
  border-left: 6rpx solid #2979ff;
}
.row-line {
  display: flex;
  justify-content: space-between;
  padding: 6rpx 0;
}
.row-label {
  color: #909399;
}
.row-value {
  color: #303133;
  text-align: right;
  flex: 1;
  margin-left: 24rpx;
  word-break: break-all;
}
.load-more {
  text-align: center;
  color: #c0c4cc;
  padding: 24rpx 0 48rpx;
}
</style>
