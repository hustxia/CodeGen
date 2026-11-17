<template>
  <div class="multi-image-upload">
    <image-upload
      v-for="(url, idx) in items"
      :key="idx + '-' + url"
      :value="url"
      width="88px"
      height="88px"
      :max-size-mb="maxSizeMb"
      :disabled="disabled"
      @input="onItem(idx, $event)"
    />
    <image-upload
      v-if="items.length < limit && !disabled"
      value=""
      width="88px"
      height="88px"
      :max-size-mb="maxSizeMb"
      :disabled="disabled"
      @input="onAdd"
    />
  </div>
</template>

<script>
/**
 * 多图上传（V6.6 文件能力包扩展）：v-model 是 JSON 数组字符串（与 slider_images /
 * evidence_images 列的「URL JSON数组」语义对齐）。
 * 内部逐项复用 ImageUpload：删除某项时 ImageUpload 发空串 → 从数组里移除；
 * 末尾的空瓦片是"继续添加"入口，达到 limit 后隐藏。
 */
import ImageUpload from './ImageUpload.vue'

export default {
  name: 'MultiImageUpload',
  components: { ImageUpload },
  props: {
    value: { type: String, default: '' },
    maxSizeMb: { type: Number, default: 10 },
    limit: { type: Number, default: 9 },
    disabled: { type: Boolean, default: false }
  },
  data() {
    return {
      items: [],
      // 自己 emit 出去再回流时不再重复解析（防 watcher 回环）
      silent: false
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(v) {
        if (this.silent) {
          this.silent = false
          return
        }
        try {
          const arr = JSON.parse(v || '[]')
          this.items = Array.isArray(arr) ? arr.filter(x => typeof x === 'string' && x) : []
        } catch (e) {
          // 旧数据不是合法 JSON 数组时降级为空，不阻塞编辑
          this.items = []
        }
      }
    }
  },
  methods: {
    emitItems() {
      this.silent = true
      this.$emit('input', this.items.length ? JSON.stringify(this.items) : '')
    },
    onItem(idx, url) {
      if (!url) {
        this.items.splice(idx, 1)
      } else {
        this.items[idx] = url
      }
      this.emitItems()
    },
    onAdd(url) {
      if (!url) return
      this.items.push(url)
      this.emitItems()
    }
  }
}
</script>

<style scoped>
.multi-image-upload {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
