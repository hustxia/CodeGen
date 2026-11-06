<template>
  <view>
    <view class="card user-card">
      <view class="avatar">{{ initial }}</view>
      <view class="user-info">
        <view class="username">{{ userStore.username || '未登录' }}</view>
        <view class="perm-count">权限点 {{ userStore.perms.length }} 项</view>
      </view>
    </view>
    <view class="card">
      <view class="about-line">移动端工程：uni-app 单源（H5 + 微信小程序）</view>
      <view class="about-line">H5 调试：npm run dev:h5（/dev-api 代理后端）</view>
      <view class="about-line">小程序出包：npm run build:mp-weixin → 微信开发者工具导入 dist/build/mp-weixin</view>
    </view>
    <view class="logout-wrap">
      <button class="btn-danger logout-btn" @click="doLogout">退出登录</button>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const initial = computed(() => (userStore.username || '?').charAt(0).toUpperCase())

function doLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) userStore.logout()
    }
  })
}
</script>

<style scoped>
.user-card {
  display: flex;
  align-items: center;
}
.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #2979ff;
  color: #ffffff;
  font-size: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}
.username {
  font-size: 34rpx;
  font-weight: bold;
}
.perm-count {
  color: #909399;
  font-size: 24rpx;
  margin-top: 8rpx;
}
.about-line {
  color: #909399;
  font-size: 24rpx;
  padding: 8rpx 0;
}
.logout-wrap {
  padding: 48rpx;
}
.logout-btn {
  width: 100%;
  background: #fa3534;
  color: #ffffff;
  border-radius: 12rpx;
}
</style>
