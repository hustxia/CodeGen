<template>
  <div class="rich-text-editor" :class="{ 'is-disabled': disabled }">
    <div class="rte-toolbar" v-if="!disabled">
      <button type="button" class="rte-btn" title="加粗" @click="exec('bold')"><b>B</b></button>
      <button type="button" class="rte-btn" title="斜体" @click="exec('italic')"><i>I</i></button>
      <button type="button" class="rte-btn" title="下划线" @click="exec('underline')"><u>U</u></button>
      <button type="button" class="rte-btn" title="无序列表" @click="exec('insertUnorderedList')">• 列表</button>
      <button type="button" class="rte-btn" title="有序列表" @click="exec('insertOrderedList')">1. 列表</button>
      <button type="button" class="rte-btn" title="插入链接" @click="insertLink">🔗 链接</button>
      <button type="button" class="rte-btn" title="清除格式" @click="exec('removeFormat')">清除</button>
    </div>
    <div
      class="rte-content"
      :contenteditable="disabled ? 'false' : 'true'"
      ref="editor"
      @input="onInput"
      @blur="onInput"
      :placeholder="placeholder"
    ></div>
  </div>
</template>

<script>
/**
 * V9.5 §4.7 富文本编辑器：轻量、零外部依赖（不引 TinyMCE/Quill，避免给每个生成工程加 npm 包）。
 * 基于 contenteditable + execCommand（生成的后台管理页面向 Chromium 内核，execCommand 稳定可用）。
 * v-model 值为 HTML 字符串，后端按 text/longtext 列原样存储；列表页取纯文本摘要、详情页渲染已净化 HTML。
 */
export default {
  name: 'RichTextEditor',
  props: {
    value: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    placeholder: { type: String, default: '请输入正文内容…' }
  },
  watch: {
    value(val) {
      // 仅当外部值与编辑器内容不一致时才回写，避免输入时光标跳动
      const cur = this.$refs.editor ? this.$refs.editor.innerHTML : ''
      if (val !== cur) {
        this.$refs.editor.innerHTML = val || ''
      }
    }
  },
  mounted() {
    if (this.$refs.editor) {
      this.$refs.editor.innerHTML = this.value || ''
    }
  },
  methods: {
    exec(command) {
      if (this.disabled) return
      this.$refs.editor.focus()
      document.execCommand(command, false, null)
      this.onInput()
    },
    insertLink() {
      if (this.disabled) return
      const url = window.prompt('请输入链接地址（http/https）', 'https://')
      if (!url) return
      this.$refs.editor.focus()
      document.execCommand('createLink', false, url)
      this.onInput()
    },
    onInput() {
      if (this.disabled) return
      const html = this.$refs.editor ? this.$refs.editor.innerHTML : ''
      this.$emit('input', html)
    }
  }
}
</script>

<style scoped>
.rich-text-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
}
.rich-text-editor.is-disabled {
  background: #f5f7fa;
  cursor: not-allowed;
}
.rte-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}
.rte-btn {
  min-width: 28px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}
.rte-btn:hover {
  border-color: #409eff;
  color: #409eff;
}
.rte-content {
  min-height: 140px;
  max-height: 420px;
  overflow-y: auto;
  padding: 10px 12px;
  outline: none;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
}
.rte-content:empty::before {
  content: attr(placeholder);
  color: #c0c4cc;
}
.rte-content img {
  max-width: 100%;
}
</style>
