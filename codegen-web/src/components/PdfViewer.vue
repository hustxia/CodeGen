<template>
  <div class="pdf-viewer">
    <div class="pv-bar">
      <el-button size="mini" icon="el-icon-arrow-left" :disabled="pageNum <= 1" @click="go(-1)" />
      <span class="pv-no">{{ pageNum }}</span>
      <span class="pv-total">/ {{ pageCount || '—' }}</span>
      <el-button size="mini" icon="el-icon-arrow-right" :disabled="pageNum >= pageCount" @click="go(1)" />

      <span class="pv-divider" />

      <el-button size="mini" icon="el-icon-minus" @click="zoom(-1)" />
      <span class="pv-scale">{{ Math.round(scale * 100) }}%</span>
      <el-button size="mini" icon="el-icon-plus" @click="zoom(1)" />
      <el-button size="mini" :type="fitWidth ? 'primary' : 'default'" @click="toggleFit">适宽</el-button>

      <span class="pv-spacer" />
      <span v-if="rendering" class="pv-tip">渲染中…</span>
    </div>

    <div ref="stage" class="pv-stage" @scroll="onScroll">
      <div v-if="loading" class="pv-hint">
        <i class="el-icon-loading" /> 正在解析 PDF…
      </div>
      <div v-else-if="error" class="pv-hint pv-err">
        <i class="el-icon-warning-outline" /> {{ error }}
      </div>
      <canvas v-show="!loading && !error" ref="canvas" class="pv-canvas" />
    </div>
  </div>
</template>

<script>
// worker 只取 URL 字符串（文件由 webpack 按 asset/resource 独立产出，见 vue.config.js），不阻塞首屏
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.js'

const MIN_SCALE = 0.5
const MAX_SCALE = 3

export default {
  name: 'PdfViewer',
  props: {
    // ArrayBuffer（axios arraybuffer 取流）
    data: { type: Object, required: true }
  },
  data() {
    return {
      loading: true,
      rendering: false,
      error: '',
      pdfDoc: null,
      pageNum: 1,
      pageCount: 0,
      scale: 1,
      fitWidth: true,
      renderTask: null
    }
  },
  mounted() {
    this.load()
    window.addEventListener('keydown', this.onKey)
  },
  beforeDestroy() {
    window.removeEventListener('keydown', this.onKey)
    if (this.pdfDoc) {
      this.pdfDoc.destroy()
      this.pdfDoc = null
    }
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        // legacy build 是 UMD，动态 import 的命名空间可能把导出挂在 default 上
        const mod = await import(/* webpackChunkName: "pdf-renderer" */ 'pdfjs-dist/legacy/build/pdf.min.js')
        const pdfjsLib = mod.default || mod
        pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl
        // getDocument 会转移 ArrayBuffer 所有权，只解析一次，后续缩放复用 pdfDoc
        this.pdfDoc = await pdfjsLib.getDocument({ data: this.data }).promise
        this.pageCount = this.pdfDoc.numPages
        // 首次直接按适宽渲染，避免先 100% 再重排的闪烁
        await this.fitToWidth()
      } catch (e) {
        this.error = 'PDF 解析失败：' + (e && e.message ? e.message : e)
      } finally {
        this.loading = false
      }
    },
    async renderPage() {
      if (!this.pdfDoc) return
      // 同一 canvas 不允许并发渲染：缩放/翻页连点时先取消上一个渲染任务
      if (this.renderTask) {
        try {
          this.renderTask.cancel()
        } catch (e) { /* 已结束的任务取消失败可忽略 */ }
        this.renderTask = null
      }
      this.rendering = true
      try {
        const page = await this.pdfDoc.getPage(this.pageNum)
        const viewport = page.getViewport({ scale: this.scale })
        const canvas = this.$refs.canvas
        const ctx = canvas.getContext('2d')
        const ratio = window.devicePixelRatio || 1
        canvas.width = Math.floor(viewport.width * ratio)
        canvas.height = Math.floor(viewport.height * ratio)
        canvas.style.width = Math.floor(viewport.width) + 'px'
        canvas.style.height = Math.floor(viewport.height) + 'px'
        this.renderTask = page.render({
          canvasContext: ctx,
          viewport,
          transform: ratio !== 1 ? [ratio, 0, 0, ratio, 0, 0] : null
        })
        await this.renderTask.promise
        this.renderTask = null
      } catch (e) {
        // 主动取消会抛出 RenderingCancelledException，不是真错误
        if (!e || e.name !== 'RenderingCancelledException') {
          this.error = '页面渲染失败：' + (e && e.message ? e.message : e)
        }
      } finally {
        this.rendering = false
      }
    },
    async fitToWidth() {
      if (!this.pdfDoc || !this.$refs.stage) return
      this.fitWidth = true
      const page = await this.pdfDoc.getPage(this.pageNum)
      const base = page.getViewport({ scale: 1 })
      const stageWidth = this.$refs.stage.clientWidth - 32
      const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, stageWidth / base.width))
      this.scale = scale
      await this.renderPage()
    },
    toggleFit() {
      if (this.fitWidth) {
        this.fitWidth = false
        return
      }
      this.fitToWidth()
    },
    zoom(delta) {
      this.fitWidth = false
      const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, this.scale + delta * 0.1))
      if (Math.abs(next - this.scale) < 0.001) return
      this.scale = next
      this.renderPage()
    },
    go(delta) {
      const next = this.pageNum + delta
      if (next < 1 || next > this.pageCount) return
      this.pageNum = next
      this.renderPage()
    },
    onKey(e) {
      if (e.key === 'PageDown' || e.key === 'ArrowDown') {
        e.preventDefault()
        this.go(1)
      } else if (e.key === 'PageUp' || e.key === 'ArrowUp') {
        e.preventDefault()
        this.go(-1)
      }
    },
    onScroll() {
      // 预留：跨页连续滚动不在本版范围，单页渲染已够用
    }
  }
}
</script>

<style scoped>
.pdf-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--cg-bg-page);
}

.pv-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--cg-bg-container);
  border-bottom: 1px solid var(--cg-border-light);
  flex: 0 0 auto;
}

.pv-no {
  min-width: 28px;
  text-align: center;
  font-size: 13px;
  color: var(--cg-text);
}

.pv-total,
.pv-scale,
.pv-tip {
  font-size: 12px;
  color: var(--cg-text-secondary);
}

.pv-scale {
  min-width: 42px;
  text-align: center;
}

.pv-divider {
  width: 1px;
  height: 18px;
  background: var(--cg-border-light);
  margin: 0 4px;
}

.pv-spacer {
  flex: 1;
}

.pv-stage {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 16px;
}

.pv-canvas {
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.12);
}

.pv-hint {
  margin: auto;
  font-size: 13px;
  color: var(--cg-text-secondary);
}

.pv-err {
  color: var(--cg-danger);
}
</style>
