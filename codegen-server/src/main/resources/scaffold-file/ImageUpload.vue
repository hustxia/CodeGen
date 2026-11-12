<template>
  <div class="image-upload">
    <el-upload
      :action="action"
      :headers="headers"
      :show-file-list="false"
      :accept="accept"
      :disabled="disabled"
      :on-success="onSuccess"
      :on-error="onError"
      :before-upload="beforeUpload"
      :style="{width: width, height: height}"
      class="image-box">
      <img v-if="innerUrl" :src="resolvedUrl" class="preview" @error="onImgError">
      <el-icon v-else class="add-icon"><Plus /></el-icon>
      <div v-if="innerUrl" class="mask" @click.prevent>
        <el-icon title="预览" @click.stop="viewer = true"><ZoomIn /></el-icon>
        <el-icon title="移除" @click.stop="remove"><Delete /></el-icon>
      </div>
    </el-upload>
    <el-dialog v-model="viewer" append-to-body title="图片预览" width="720px">
      <img :src="resolvedUrl" style="width: 100%" alt="预览">
    </el-dialog>
  </div>
</template>

<script>
/**
 * 图片上传组件（V6.6 文件能力包）：单图回填 URL 字符串到 v-model；
 * multiple 由页面层用逗号分隔的多值实现（生成模板按列语义决定）。
 * URL 一律相对路径（/uploads/...），开发期由 devServer 代理 /uploads 到后端。
 */
import request from '@/utils/request'

export default {
  name: 'ImageUpload',
  props: {
    value: { type: String, default: '' },
    width: { type: String, default: '120px' },
    height: { type: String, default: '120px' },
    maxSizeMb: { type: Number, default: 10 },
    /** 字段联动禁用（模板主表单会给 link.disabled 传值；组件不声明则该属性无处生效） */
    disabled: { type: Boolean, default: false }
  },
  data() {
    return {
      action: process.env.VUE_APP_BASE_API + '/file/upload',
      viewer: false,
      imgBroken: false
    }
  },
  computed: {
    headers() {
      return { Authorization: 'Bearer ' + (localStorage.getItem('token') || '') }
    },
    accept() {
      // 不含 image/svg：后端白名单默认也排除 svg（/uploads 免鉴权直链 + SVG 脚本 = 存储型 XSS）
      return 'image/jpg,image/jpeg,image/png,image/gif,image/webp,image/bmp'
    },
    innerUrl() {
      return (this.value || '').split(',')[0]
    },
    resolvedUrl() {
      return this.innerUrl || ''
    }
  },
  methods: {
    beforeUpload(file) {
      if (file.size > this.maxSizeMb * 1024 * 1024) {
        this.$message.error('图片超过大小限制（' + this.maxSizeMb + 'MB）')
        return false
      }
      return true
    },
    onSuccess(res) {
      // el-upload 自带请求不经过拦截器，这里手动解析统一响应
      if (!res || (res.code !== 0 && res.code !== 200)) {
        this.$message.error((res && res.msg) || '上传失败')
        return
      }
      this.imgBroken = false
      this.$emit('input', res.data.url)
      this.$emit('uploaded', res.data)
    },
    onError() {
      this.$message.error('上传失败，请检查网络与登录状态')
    },
    onImgError() {
      this.imgBroken = true
    },
    remove() {
      if (this.disabled) return
      this.$emit('input', '')
    },
    /** 供生成页面的提交前校验复用 */
    request() {
      return request
    }
  }
}
</script>

<style scoped>
.image-box >>> .el-upload {
  width: 100%;
  height: 100%;
  border: 1px dashed #c0ccda;
  border-radius: 6px;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fbfdff;
}
.image-box >>> .el-upload:hover {
  border-color: #409eff;
}
.preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.add-icon {
  font-size: 26px;
  color: #8c939d;
}
.mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, .45);
  display: none;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #fff;
  font-size: 20px;
}
.image-box >>> .el-upload:hover .mask {
  display: flex;
}
</style>
