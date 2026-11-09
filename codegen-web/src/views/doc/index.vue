<template>
  <div class="app-container">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item label="文档名称">
          <el-input
            v-model="query.name"
            placeholder="支持模糊搜索"
            clearable
            style="width: 220px"
            @keyup.enter.native="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.category" clearable placeholder="全部类型" style="width: 130px">
            <el-option label="PDF" value="pdf" />
            <el-option label="Word" value="word" />
            <el-option label="Excel" value="excel" />
            <el-option label="PPT" value="ppt" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div slot="header" class="card-header">
        <span>文档列表</span>
        <el-upload
          v-hasPermi="['doc:upload']"
          action="#"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
          :show-file-list="false"
          :http-request="doUpload"
          :before-upload="beforeUpload"
          :disabled="uploading"
        >
          <el-button type="primary" icon="el-icon-upload2" :loading="uploading">上传文档</el-button>
        </el-upload>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="文档名称" min-width="280">
          <template slot-scope="{ row }">
            <div class="fname">
              <span class="ficon" :class="'is-' + iconKind(row.ext)">{{ extLabel(row.ext) }}</span>
              <div class="fmain">
                <div class="n">{{ row.originalName }}</div>
                <div class="m">{{ row.contentType || '—' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template slot-scope="{ row }">{{ typeLabel(row.ext) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template slot-scope="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="预览方式" width="180">
          <template slot-scope="{ row }">
            <span class="tag" :class="modeBadge(row).cls">{{ modeBadge(row).text }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="110" />
        <el-table-column label="上传时间" width="170">
          <template slot-scope="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" :disabled="row.previewMode === 'unsupported'" @click="openPreview(row)">
              预览
            </el-button>
            <el-button type="text" @click="download(row)">下载</el-button>
            <el-button v-hasPermi="['doc:remove']" type="text" class="danger-btn" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        :current-page.sync="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />

      <div class="legend">
        <span><i class="dot is-pdf" />PDF 原生：浏览器 pdf.js 渲染，可翻页缩放</span>
        <span><i class="dot is-hi" />高保真：服务端转 PDF，版式与原文件一致</span>
        <span><i class="dot is-simple" />简易渲染：浏览器排版，复杂版式可能有差异</span>
        <span><i class="dot is-download" />仅下载：当前引擎不支持该格式</span>
      </div>
    </el-card>

    <DocPreviewDialog :visible.sync="previewVisible" :doc="currentDoc" />
  </div>
</template>

<script>
import { getDocPage, uploadDoc, deleteDoc, fetchDocDownload } from '@/api/doc'
import DocPreviewDialog from '@/components/DocPreviewDialog.vue'

const MAX_SIZE_MB = 30

const TYPE_LABEL = { pdf: 'PDF', doc: 'Word', docx: 'Word', xls: 'Excel', xlsx: 'Excel', ppt: 'PPT', pptx: 'PPT' }

export default {
  name: 'DocCenter',
  components: { DocPreviewDialog },
  data() {
    return {
      loading: false,
      uploading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, name: '', category: undefined },
      previewVisible: false,
      currentDoc: null
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    async fetchList() {
      this.loading = true
      try {
        const res = await getDocPage(this.query)
        this.list = res.data.list || []
        this.total = res.data.total || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, name: '', category: undefined }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    beforeUpload(file) {
      const ext = extOf(file.name)
      if (!TYPE_LABEL[ext]) {
        this.$message.warning('仅支持 pdf / doc / docx / xls / xlsx / ppt / pptx')
        return false
      }
      if (file.size > MAX_SIZE_MB * 1024 * 1024) {
        this.$message.warning(`单文件不能超过 ${MAX_SIZE_MB}MB`)
        return false
      }
      return true
    },
    async doUpload(options) {
      this.uploading = true
      try {
        const res = await uploadDoc(options.file, e => {
          if (options.onProgress && e.total) {
            options.onProgress({ percent: Math.round((e.loaded / e.total) * 100) })
          }
        })
        if (options.onSuccess) options.onSuccess(res)
        this.$message.success(`「${options.file.name}」上传成功`)
        this.query.page = 1
        this.fetchList()
      } catch (e) {
        if (options.onError) options.onError(e)
        /* 拦截器已提示 */
      } finally {
        this.uploading = false
      }
    },
    openPreview(row) {
      this.currentDoc = row
      this.previewVisible = true
    },
    download(row) {
      fetchDocDownload(row.id)
        .then(buf => {
          const blob = new Blob([buf], { type: row.contentType || 'application/octet-stream' })
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url
          a.download = row.originalName || 'document'
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          setTimeout(() => URL.revokeObjectURL(url), 2000)
        })
        .catch(() => { /* 拦截器已提示 */ })
    },
    handleDelete(row) {
      this.$confirm(`确定删除文档「${row.originalName}」吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteDoc(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    modeBadge(row) {
      if (row.previewMode === 'pdf') {
        return row.ext === 'pdf'
          ? { text: 'PDF 原生', cls: 'is-pdf' }
          : { text: '高保真 · LibreOffice', cls: 'is-hi' }
      }
      if (row.previewMode === 'browser-docx') {
        return { text: '简易渲染 · 浏览器', cls: 'is-simple' }
      }
      return { text: '仅支持下载', cls: 'is-download' }
    },
    iconKind(ext) {
      if (ext === 'pdf') return 'pdf'
      if (ext === 'doc' || ext === 'docx') return 'doc'
      if (ext === 'xls' || ext === 'xlsx') return 'xls'
      if (ext === 'ppt' || ext === 'pptx') return 'ppt'
      return 'other'
    },
    extLabel(ext) {
      return (ext || '?').toUpperCase().slice(0, 4)
    },
    typeLabel(ext) {
      return TYPE_LABEL[ext] || (ext ? ext.toUpperCase() : '—')
    },
    formatSize(size) {
      const n = Number(size) || 0
      if (n < 1024) return n + ' B'
      if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
      return (n / 1024 / 1024).toFixed(1) + ' MB'
    },
    formatTime(value) {
      if (!value) return '—'
      if (Array.isArray(value)) {
        const [y, mo, d, h, mi, s] = value
        return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
      }
      return String(value).replace('T', ' ').substring(0, 19)
    }
  }
}

function extOf(name) {
  const idx = (name || '').lastIndexOf('.')
  return idx === -1 ? '' : name.substring(idx + 1).toLowerCase()
}

function pad(n) {
  return String(n || 0).padStart(2, '0')
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  text-align: right;
}

.fname {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ficon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}

.ficon.is-pdf {
  background: var(--cg-danger-bg);
  color: var(--cg-danger);
}

.ficon.is-doc {
  background: var(--cg-primary-soft);
  color: var(--cg-primary);
}

.ficon.is-xls {
  background: var(--cg-success-bg);
  color: var(--cg-success);
}

.ficon.is-ppt {
  background: var(--cg-warning-bg);
  color: var(--cg-warning);
}

.ficon.is-other {
  background: var(--cg-info-bg);
  color: var(--cg-info);
}

.fmain {
  line-height: 1.35;
  min-width: 0;
}

.fmain .n {
  color: var(--cg-text);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fmain .m {
  margin-top: 2px;
  font-size: 11px;
  color: var(--cg-text-placeholder);
}

.tag {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
}

.tag.is-pdf {
  background: var(--cg-primary-soft);
  color: var(--cg-primary);
}

.tag.is-hi {
  background: var(--cg-success-bg);
  color: var(--cg-success);
}

.tag.is-simple {
  background: var(--cg-warning-bg);
  color: var(--cg-warning);
}

.tag.is-download {
  background: var(--cg-info-bg);
  color: var(--cg-info);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--cg-text-secondary);
}

.legend span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.dot.is-pdf {
  background: var(--cg-primary);
}

.dot.is-hi {
  background: var(--cg-success);
}

.dot.is-simple {
  background: var(--cg-warning);
}

.dot.is-download {
  background: var(--cg-text-placeholder);
}
</style>
