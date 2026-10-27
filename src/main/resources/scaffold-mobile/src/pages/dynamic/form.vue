<template>
  <view>
    <view class="card">
      <view v-for="c in formCols" :key="c.field" class="form-line">
        <view class="form-label">
          <text v-if="c.required" class="required">*</text>{{ c.label }}
        </view>
        <view class="form-control">
          <picker
            v-if="c.ctrl === 'picker' && Array.isArray(c.enum)"
            :range="c.enum"
            range-key="label"
            @change="(e) => (model[c.field] = c.enum[e.detail.value].value)"
          >
            <view class="picker-value">{{ pickerLabel(c, model[c.field]) || '请选择' }}</view>
          </picker>
          <picker v-else-if="c.ctrl === 'date'" mode="date" :value="model[c.field]" @change="(e) => (model[c.field] = e.detail.value)">
            <view class="picker-value">{{ model[c.field] || '请选择日期' }}</view>
          </picker>
          <switch
            v-else-if="c.ctrl === 'switch'"
            :checked="String(model[c.field]) === '1'"
            @change="(e) => (model[c.field] = e.detail.value ? 1 : 0)"
          />
          <textarea
            v-else-if="c.ctrl === 'textarea'"
            v-model="model[c.field]"
            class="form-textarea"
            :placeholder="'请输入' + c.label"
          />
          <input
            v-else
            v-model="model[c.field]"
            class="form-input"
            :type="c.ctrl === 'number' ? 'digit' : 'text'"
            :placeholder="'请输入' + c.label"
          />
        </view>
      </view>
    </view>
    <view class="action-bar">
      <button class="btn-primary save-btn" :loading="saving" @click="save">保 存</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, post, put } from '@/utils/request'
import { findTable } from '@/utils/tables'

// 列类型→控件映射在生成侧已完成（config.ctrl），这里只做形态分派：
// picker（枚举/字典）/date/switch/textarea/number/input，与 PC 表单语义一一对应
let config = null
let id = null
const saving = ref(false)
const model = reactive({})

onLoad(async (opt) => {
  config = findTable(opt.module, opt.table)
  id = opt.id || null
  uni.setNavigationBarTitle({ title: config ? (id ? '编辑' : '新增') + config.title : '表单' })
  if (!config) return
  for (const c of config.formCols || []) {
    model[c.field] = c.ctrl === 'switch' ? 0 : ''
  }
  if (id) {
    const data = await get(`${config.api}/${id}`)
    for (const c of config.formCols || []) {
      model[c.field] = data && data[c.field] !== null && data[c.field] !== undefined ? data[c.field] : ''
    }
  }
})

function pickerLabel(c, value) {
  if (Array.isArray(c.enum)) {
    const hit = c.enum.find((o) => String(o.value) === String(value || ''))
    return hit ? hit.label : ''
  }
  return value || ''
}

async function save() {
  if (!config) return
  for (const c of config.formCols || []) {
    if (c.required && (model[c.field] === '' || model[c.field] === null || model[c.field] === undefined)) {
      uni.showToast({ title: c.label + '不能为空', icon: 'none' })
      return
    }
  }
  saving.value = true
  try {
    if (id) {
      model.id = id
      await put(config.api, model)
    } else {
      await post(config.api, model)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-line {
  display: flex;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f3f5;
}
.form-line:last-child {
  border-bottom: none;
}
.form-label {
  width: 200rpx;
  color: #606266;
  flex-shrink: 0;
  padding-top: 12rpx;
}
.required {
  color: #fa3534;
  margin-right: 4rpx;
}
.form-control {
  flex: 1;
}
.form-input,
.form-textarea {
  width: 100%;
  border: 1rpx solid #e4e7ed;
  border-radius: 8rpx;
  padding: 12rpx 16rpx;
  box-sizing: border-box;
}
.form-textarea {
  height: 160rpx;
}
.picker-value {
  border: 1rpx solid #e4e7ed;
  border-radius: 8rpx;
  padding: 12rpx 16rpx;
  color: #303133;
}
.action-bar {
  padding: 32rpx 48rpx 64rpx;
}
.save-btn {
  width: 100%;
}
</style>
