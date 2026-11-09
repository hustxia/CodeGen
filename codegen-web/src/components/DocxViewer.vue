<template>
  <div class="docx-viewer">
    <div class="dv-banner">
      <i class="el-icon-warning-outline" />
      当前为浏览器简易渲染，页眉页脚、表格合并、嵌入对象可能与原件有差异。需要高保真请由管理员安装 LibreOffice 并配置
      <code>doc.preview.engine=auto</code>。
    </div>
    <div ref="scroll" class="dv-scroll">
      <div v-if="loading" class="dv-hint"><i class="el-icon-loading" /> 正在渲染文档…</div>
      <div v-else-if="error" class="dv-hint dv-err"><i class="el-icon-warning-outline" /> {{ error }}</div>
      <div ref="body" v-show="!loading && !error" class="dv-body" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'DocxViewer',
  props: {
    // ArrayBuffer（/doc/raw 取回的原件）
    data: { type: Object, required: true }
  },
  data() {
    return {
      loading: true,
      error: ''
    }
  },
  mounted() {
    this.render()
  },
  methods: {
    async render() {
      this.loading = true
      this.error = ''
      try {
        // 预览器整体异步加载，不进首屏包
        const mod = await import(/* webpackChunkName: "docx-renderer" */ 'docx-preview')
        const renderAsync = mod.renderAsync || (mod.default && mod.default.renderAsync)
        if (!renderAsync) {
          throw new Error('docx-preview 未导出 renderAsync')
        }
        await renderAsync(new Blob([this.data]), this.$refs.body, this.$refs.body, {
          className: 'docx-page',
          inWrapper: true,
          breakPages: true,
          ignoreWidth: false,
          ignoreHeight: false,
          ignoreLastRenderedPageBreak: false,
          experimental: true,
          useBase64URL: true
        })
      } catch (e) {
        this.error = 'Word 渲染失败：' + (e && e.message ? e.message : e)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.docx-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--cg-bg-page);
}

.dv-banner {
  flex: 0 0 auto;
  padding: 9px 14px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--cg-warning);
  background: var(--cg-warning-bg);
  border-bottom: 1px solid var(--cg-border-light);
}

.dv-banner code {
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.6);
  font-family: Consolas, Monaco, monospace;
}

.dv-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.dv-body {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dv-hint {
  margin: 40px auto;
  font-size: 13px;
  text-align: center;
  color: var(--cg-text-secondary);
}

.dv-err {
  color: var(--cg-danger);
}
</style>
