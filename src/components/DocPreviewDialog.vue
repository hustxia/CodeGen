<template>
  <el-dialog
    :visible="visible"
    :show-close="false"
    :close-on-click-modal="false"
    width="80vw"
    top="6vh"
    custom-class="doc-preview-dialog"
    @close="close"
  >
    <div slot="title" class="dp-head">
      <span class="dp-name">{{ doc ? doc.originalName : '文档预览' }}</span>
      <span v-if="badge" class="dp-badge" :class="badge.cls">{{ badge.text }}</span>
      <span class="dp-spacer" />
      <el-button type="text" icon="el-icon-download" :loading="downloading" @click="download">下载</el-button>
      <el-button type="text" icon="el-icon-close" class="dp-close" @click="close">关闭</el-button>
    </div>

    <div class="dp-body">
      <div v-if="state === 'loading'" class="dp-hint">
        <i class="el-icon-loading" /> {{ loadingText }}
      </div>

      <div v-else-if="state === 'unsupported'" class="dp-unsupported">
        <div class="dp-big">📄</div>
        <div class="dp-title">该文档暂不支持在线预览</div>
        <div class="dp-reason">{{ reason || '当前引擎不支持该格式，请下载查看。' }}</div>
        <el-button type="primary" icon="el-icon-download" :loading="downloading" @click="download">
          下载文档（{{ sizeText }}）
        </el-button>
      </div>

      <div v-else-if="state === 'error'" class="dp-unsupported">
        <div class="dp-big">⚠️</div>
        <div class="dp-title">预览失败</div>
        <div class="dp-reason">{{ reason }}</div>
        <el-button type="primary" icon="el-icon-download" :loading="downloading" @click="download">下载文档</el-button>
      </div>

      <PdfViewer v-else-if="state === 'pdf'" :key="viewerKey" :data="buffer" class="dp-viewer" />
      <DocxViewer v-else-if="state === 'docx'" :key="viewerKey" :data="buffer" class="dp-viewer" />
    </div>
  </el-dialog>
</template>

<script>
import { getDocMeta, fetchDocPreview, fetchDocRaw, fetchDocDownload } from '@/api/doc'

export default {
  name: 'DocPreviewDialog',
  components: {
    // 两个渲染器体积大（pdf.js / docx-preview），只在真正打开预览时才加载
    PdfViewer: () => import(/* webpackChunkName: "pdf-renderer" */ '@/components/PdfViewer.vue'),
    DocxViewer: () => import(/* webpackChunkName: "docx-renderer" */ '@/components/DocxViewer.vue')
  },
  props: {
    visible: { type: Boolean, default: false },
    doc: { type: Object, default: null }
  },
  data() {
    return {
      state: 'loading',
      loadingText: '正在准备预览…',
      reason: '',
      buffer: null,
      viewerKey: '',
      downloading: false,
      // meta.previewMode：当前引擎下文档实际会被如何渲染（与列表列同源）
      previewMode: '',
      engine: ''
    }
  },
  computed: {
    badge() {
      if (!this.doc || this.state === 'loading') return null
      if (this.previewMode === 'pdf') {
        return this.doc.ext === 'pdf'
          ? { text: 'PDF 原生', cls: 'is-pdf' }
          : { text: '高保真 · LibreOffice', cls: 'is-hi' }
      }
      if (this.previewMode === 'browser-docx') {
        return { text: '简易渲染 · 浏览器', cls: 'is-simple' }
      }
      return { text: '仅支持下载', cls: 'is-download' }
    },
    sizeText() {
      return formatSize(this.doc && this.doc.fileSize)
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.open()
      } else {
        this.reset()
      }
    }
  },
  methods: {
    async open() {
      if (!this.doc) return
      this.reset()
      this.state = 'loading'
      this.loadingText = '正在准备预览…'
      try {
        const res = await getDocMeta(this.doc.id)
        const meta = res.data || {}
        this.previewMode = meta.previewMode || ''
        this.engine = meta.engine || ''
        if (!meta.convertible) {
          this.state = 'unsupported'
          this.reason = meta.reason || ''
          return
        }
        await this.loadContent()
      } catch (e) {
        this.state = 'error'
        this.reason = (e && e.message) || '预览准备失败'
      }
    },
    async loadContent() {
      const id = this.doc.id
      this.state = 'loading'
      if (this.previewMode === 'browser-docx') {
        this.loadingText = '正在渲染 Word 文档…'
        this.buffer = await fetchDocRaw(id)
        this.state = 'docx'
        this.viewerKey = 'docx-' + id
        return
      }
      // pdf：原件 PDF 直接流；非 PDF 由服务端转换，首次可能等几秒
      this.loadingText = this.doc.ext === 'pdf' ? '正在加载 PDF…' : '首次转换约需 3-8 秒，请稍候…'
      try {
        this.buffer = await fetchDocPreview(id)
        this.previewMode = 'pdf'
        this.state = 'pdf'
        this.viewerKey = 'pdf-' + id
      } catch (e) {
        // 服务端转换失败时，docx 仍可在浏览器渲染：自动回退并只提示一次
        if (this.doc.ext === 'docx') {
          this.loadingText = '转换失败，改用浏览器渲染…'
          this.buffer = await fetchDocRaw(id)
          this.previewMode = 'browser-docx'
          this.state = 'docx'
          this.viewerKey = 'docx-' + id
          this.$message.warning('服务端转换失败，已改用浏览器简易渲染')
          return
        }
        this.state = 'error'
        this.reason = (e && e.message) || '文档转换失败'
      }
    },
    download() {
      if (!this.doc) return
      this.downloading = true
      fetchDocDownload(this.doc.id)
        .then(buf => {
          const blob = new Blob([buf], { type: this.doc.contentType || 'application/octet-stream' })
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url
          a.download = this.doc.originalName || 'document'
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          setTimeout(() => URL.revokeObjectURL(url), 2000)
        })
        .catch(() => { /* 拦截器已提示 */ })
        .finally(() => {
          this.downloading = false
        })
    },
    close() {
      this.$emit('update:visible', false)
    },
    reset() {
      this.state = 'loading'
      this.reason = ''
      this.buffer = null
      this.viewerKey = ''
      this.previewMode = ''
      this.engine = ''
    }
  }
}

function formatSize(size) {
  const n = Number(size) || 0
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.dp-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 8px;
}

.dp-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--cg-text);
  max-width: 50vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dp-badge {
  padding: 1px 9px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
}

.dp-badge.is-pdf {
  background: var(--cg-primary-soft);
  color: var(--cg-primary);
}

.dp-badge.is-hi {
  background: var(--cg-success-bg);
  color: var(--cg-success);
}

.dp-badge.is-simple {
  background: var(--cg-warning-bg);
  color: var(--cg-warning);
}

.dp-badge.is-download {
  background: var(--cg-info-bg);
  color: var(--cg-info);
}

.dp-spacer {
  flex: 1;
}

.dp-close {
  color: var(--cg-text-secondary);
}

.dp-body {
  height: 72vh;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dp-viewer {
  flex: 1;
  min-height: 0;
}

.dp-hint {
  margin: auto;
  font-size: 13px;
  color: var(--cg-text-secondary);
}

.dp-unsupported {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
  max-width: 520px;
}

.dp-big {
  font-size: 40px;
  line-height: 1;
}

.dp-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--cg-text);
}

.dp-reason {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--cg-text-secondary);
}
</style>
