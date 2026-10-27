<template>
  <view class="login-page">
    <view class="login-title">{{ title }}</view>
    <view class="card">
      <input v-model="form.username" class="ipt" placeholder="用户名" />
      <input v-model="form.password" class="ipt" password placeholder="密码" />
      <view class="captcha-row">
        <input v-model="form.captchaAnswer" class="ipt captcha-ipt" placeholder="计算结果" />
        <image v-if="captchaImage" :src="captchaImage" class="captcha-img" mode="aspectFit" @click="loadCaptcha" />
      </view>
      <button class="btn-primary login-btn" :loading="loading" @click="doLogin">登 录</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { get, post } from '@/utils/request'
import { useUserStore } from '@/store/user'

const title = '__PROJECT__'
const userStore = useUserStore()
const loading = ref(false)
const captchaImage = ref('')
const form = reactive({ username: '', password: '', captchaId: '', captchaAnswer: '' })

// V6.4 联动：/auth/captcha 存在才出验证码（老产物基座无此端点时静默降级）
async function loadCaptcha() {
  try {
    const data = await get('/auth/captcha')
    if (data && data.captchaId) {
      form.captchaId = data.captchaId
      captchaImage.value = data.image
    }
  } catch (e) {
    captchaImage.value = ''
  }
}
loadCaptcha()

async function doLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const data = await post('/auth/login', {
      username: form.username,
      password: form.password,
      captchaId: form.captchaId,
      captchaAnswer: form.captchaAnswer
    })
    userStore.setAuth(data)
    uni.reLaunch({ url: '/pages/index/index' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  padding: 160rpx 48rpx 0;
}
.login-title {
  font-size: 44rpx;
  font-weight: bold;
  text-align: center;
  margin-bottom: 48rpx;
}
.ipt {
  border: 1rpx solid #e4e7ed;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 24rpx;
}
.captcha-row {
  display: flex;
  align-items: center;
}
.captcha-ipt {
  flex: 1;
  margin-right: 16rpx;
}
.captcha-img {
  width: 220rpx;
  height: 80rpx;
  border-radius: 8rpx;
  background: #f5f6fa;
}
.login-btn {
  margin-top: 16rpx;
}
</style>
