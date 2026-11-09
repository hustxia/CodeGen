<template>
  <view>
    <view v-if="placeholder" class="card empty">
      待办接口暂不可用（需产物开启 V6.1 流程基座）；流程上线后本页自动生效
    </view>
    <view v-else-if="items.length === 0" class="card empty">暂无待办任务</view>

    <view v-for="(t, i) in items" :key="i" class="card todo-card">
      <view class="todo-title">{{ t.flowName || t.flowCode || '流程任务' }}</view>
      <view class="todo-line">业务：{{ t.tableName || t.bizTable || '-' }} #{{ t.bizId || '-' }}</view>
      <view class="todo-line">节点：{{ t.nodeName || t.fromNode || t.node || '-' }}</view>
      <view class="todo-line">发起：{{ t.createBy || t.create_by || '-' }} {{ t.createdAt || t.createTime || '' }}</view>
      <view class="todo-actions">
        <input v-model="comments[i]" class="comment-ipt" placeholder="审批意见（可空）" />
        <button size="mini" class="btn-primary" @click="act(t, i, 'approve')">通过</button>
        <button size="mini" class="btn-danger" @click="act(t, i, 'reject')">驳回</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { get, post } from '@/utils/request'

// V6.1 联动：GET /flow/todo（builtin/activiti 双引擎同契约）
// POST /flow/approve|reject {flowCode, bizId, comment}
const items = ref([])
const comments = ref([])
const placeholder = ref(false)

onShow(load)

async function load() {
  try {
    const data = await get('/flow/todo')
    items.value = Array.isArray(data) ? data : []
    comments.value = items.value.map(() => '')
    placeholder.value = false
  } catch (e) {
    placeholder.value = true
    items.value = []
  }
  uni.stopPullDownRefresh()
}

onPullDownRefresh(load)

async function act(task, i, action) {
  await post(`/flow/${action}`, {
    flowCode: task.flowCode,
    bizId: task.bizId,
    comment: comments.value[i] || ''
  })
  uni.showToast({ title: action === 'approve' ? '已通过' : '已驳回', icon: 'success' })
  load()
}
</script>

<style scoped>
.empty {
  color: #909399;
  text-align: center;
}
.todo-card {
  border-left: 6rpx solid #f90;
}
.todo-title {
  font-weight: bold;
  margin-bottom: 8rpx;
}
.todo-line {
  color: #606266;
  font-size: 24rpx;
  padding: 4rpx 0;
}
.todo-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}
.comment-ipt {
  flex: 1;
  border: 1rpx solid #e4e7ed;
  border-radius: 8rpx;
  padding: 8rpx 16rpx;
}
.btn-danger {
  background: #fa3534;
  color: #ffffff;
  border-radius: 12rpx;
}
</style>
