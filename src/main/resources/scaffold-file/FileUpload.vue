<template>
  <div class="file-upload">
    <el-upload
      :action="action"
      :headers="headers"
      :show-file-list="false"
      :disabled="disabled"
      :before-upload="beforeUpload"
      :on-success="onSuccess"
      :on-error="onError">
      <el-button size="small" type="primary" plain icon="UploadFilled">上传文件</el-button>
    </el-upload>
    <div v-if="fileName" class="file-row">
      <el-icon><Document /></el-icon>
      <a class="file-name" :title="fileName" @click="download">{{ fileName }}</a>
      <el-icon class="remove" title="移除" @click="remove"><Close /></el-icon>
    </div>
  </div>
</template>

<script>
/**
 * 文件上传组件（V6.6 文件能力包）：v-model 回填文件 URL；
 * 展示原始文件名 + 点击下载（经 /file/download/{id} 带 Content-Disposition 原名）。
 * 生成页面的 file 列存的是「url」，文件名从上传响应记忆在组件内（刷新后回退显示 url 尾段）。
 */
import request from '@/utils/request'

export default {
  name: 'FileUpload',
  props: {
    value: { type: String, default: '' },
    maxSizeMb: { type: Number, default: 10 },
    /** 字段联动禁用（模板主表单会给 link.disabled 传值；组件不声明则该属性无处生效） */
    disabled: { type: Boolean, default: false }
  },
  data() {
    return {
      action: process.env.VUE_APP_BASE_API + '/file/upload',
      fileId: null,
      nameMap: {}
    }
  },
  computed: {
    headers() {
      return { Authorization: 'Bearer ' + (localStorage.getItem('token') || '') }
    },
    innerUrl() {
      return this.value || ''
    },
    fileName() {
      if (!this.innerUrl) return ''
      if (this.nameMap[this.innerUrl]) return this.nameMap[this.innerUrl]
      // 刷新后无名可查：显示 URL 尾段兜底
      const tail = this.innerUrl.split('/').pop()
      return decodeURIComponent(tail || this.innerUrl)
    }
  },
  methods: {
    beforeUpload(file) {
      if (file.size > this.maxSizeMb * 1024 * 1024) {
        this.$message.error('文件超过大小限制（' + this.maxSizeMb + 'MB）')
        return false
      }
      return true
    },
    onSuccess(res) {
      if (!res || (res.code !== 0 && res.code !== 200)) {
        this.$message.error((res && res.msg) || '上传失败')
        return
      }
      this.$set(this.nameMap, res.data.url, res.data.name)
      this.fileId = res.data.id
      this.$emit('input', res.data.url)
      this.$emit('uploaded', res.data)
    },
    onError() {
      this.$message.error('上传失败，请检查网络与登录状态')
    },
    /**
     * 下载必须走 request：浏览器新窗口直开不带 Authorization 头，鉴权过滤器会直接 401。
     * responseType=blob 时 request.js 原样返回 axios response，这里转 objectURL 触发保存。
     */
    download() {
      if (!this.innerUrl) return
      // 有 id 走原名下载接口；刷新后无名无 id，退回静态直链（/uploads 免鉴权）
      const url = this.fileId ? '/file/download/' + this.fileId : this.innerUrl
      request({ url, method: 'get', responseType: 'blob' }).then(resp => {
        const link = document.createElement('a')
        link.href = window.URL.createObjectURL(resp.data)
        link.download = this.fileName
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(link.href)
      }).catch(() => {
        // 失败提示由 request.js 响应拦截器统一弹出
      })
    },
    remove() {
      if (this.disabled) return
      this.$emit('input', '')
    },
    request() {
      return request
    }
  }
}
</script>

<style scoped>
.file-row {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.file-name {
  color: #409eff;
  cursor: pointer;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.remove {
  cursor: pointer;
  color: #909399;
}
.remove:hover {
  color: #f56c6c;
}
</style>
