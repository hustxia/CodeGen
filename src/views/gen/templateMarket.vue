<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">模板市场</span>
      <span class="page-desc">
        预置行业模板随平台发布；这里可导出为 JSON / ZIP，也可导入自定义包。同名 key 默认拒绝，勾选「覆盖同名模板」后才覆盖。
      </span>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="category" size="small" clearable placeholder="全部分类" class="cat-select">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input
          v-model="keyword"
          size="small"
          clearable
          placeholder="搜索名称 / key / 说明"
          prefix-icon="el-icon-search"
          class="kw-input"
        />
        <div class="toolbar-right">
          <el-checkbox v-model="overwrite" class="overwrite">覆盖同名模板</el-checkbox>
          <el-button
            size="small"
            icon="el-icon-download"
            :disabled="!selectedKeys.length"
            @click="exportSelected"
          >
            导出所选 ZIP（{{ selectedKeys.length }}）
          </el-button>
        </div>
      </div>

      <el-upload
        drag
        action="#"
        accept=".json,.zip"
        :show-file-list="false"
        :disabled="importing"
        :http-request="doImport"
        class="drop-zone"
      >
        <i class="el-icon-upload" />
        <div class="el-upload__text">
          拖拽 <em>.json / .zip</em> 到这里，或点击选择
        </div>
        <div slot="tip" class="el-upload__tip">
          单包 ≤ 5MB、表 ≤ 200 张；文件名只取基名，禁止 ../ 路径片段。校验失败会一次性列出全部问题。
        </div>
      </el-upload>
    </el-card>

    <div v-loading="loading" class="card-area">
      <el-checkbox-group v-model="selectedKeys" class="card-grid">
        <el-card v-for="t in shown" :key="t.key" shadow="hover" class="tpl-card">
          <div class="card-top">
            <el-checkbox :label="t.key" class="pick" @click.native.stop />
            <el-tag size="mini" :type="t.source === 'user' ? 'warning' : 'info'">
              {{ t.source === 'user' ? '用户包' : '预置' }}
            </el-tag>
            <el-tag size="mini" type="success" class="cat-tag">{{ t.category }}</el-tag>
          </div>
          <div class="card-title" :title="t.name">{{ t.name }}</div>
          <div class="card-key mono" :title="t.key">{{ t.key }}</div>
          <div class="card-desc" :title="t.desc">{{ t.desc || '—' }}</div>
          <div class="card-meta">{{ t.moduleCount }} 个模块 · {{ t.tableCount }} 张表</div>
          <div class="card-actions">
            <el-button type="text" size="mini" icon="el-icon-download" @click="exportOne(t)">导出</el-button>
            <el-button
              v-if="t.source === 'user'"
              type="text"
              size="mini"
              class="danger"
              icon="el-icon-delete"
              @click="removeOne(t)"
            >
              删除
            </el-button>
          </div>
        </el-card>
      </el-checkbox-group>

      <el-empty v-if="!loading && !shown.length" description="没有匹配的模板" />
    </div>

    <el-dialog title="导入校验失败" :visible.sync="errorVisible" width="640px">
      <div class="error-summary">以下问题导致本次导入未落盘，请逐条修正后重试：</div>
      <ul class="error-list">
        <li v-for="(e, i) in errors" :key="i">{{ e }}</li>
      </ul>
      <span slot="footer">
        <el-button type="primary" size="small" @click="errorVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { Message } from 'element-ui'
import {
  listTemplatePacks,
  exportTemplatePack,
  exportTemplatePackZip,
  importTemplatePack,
  deleteTemplatePack
} from '@/api/genTemplatePack'

function filenameOf(res, fallback) {
  const headers = (res && res.headers) || {}
  const disposition = headers['content-disposition'] || headers['Content-Disposition'] || ''
  let match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (match) {
    try {
      return decodeURIComponent(match[1])
    } catch (e) {
      return match[1]
    }
  }
  match = /filename="?([^";]+)"?/i.exec(disposition)
  return match ? match[1] : fallback
}

async function saveBlob(res, fallbackName, mime) {
  const blob = res.data
  const type = ((res.headers && res.headers['content-type']) || (blob && blob.type) || '').toLowerCase()
  // 后端业务错误仍是 JSON 体（HTTP 200 + Result），不能把它当文件存下来
  if (type.indexOf('application/json') !== -1) {
    let msg = '导出失败'
    try {
      const text = await blob.text()
      msg = JSON.parse(text).msg || msg
    } catch (e) {
      // 保留兜底文案
    }
    Message.error(msg)
    throw new Error(msg)
  }
  const url = window.URL.createObjectURL(new Blob([blob], { type: mime }))
  const link = document.createElement('a')
  link.href = url
  link.download = filenameOf(res, fallbackName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

function today() {
  const d = new Date()
  const pad = n => (n < 10 ? '0' + n : '' + n)
  return '' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate())
}

export default {
  name: 'TemplateMarket',
  data() {
    return {
      loading: false,
      importing: false,
      templates: [],
      category: '',
      keyword: '',
      overwrite: false,
      selectedKeys: [],
      errors: [],
      errorVisible: false
    }
  },
  computed: {
    categories() {
      const set = []
      this.templates.forEach(t => {
        if (t.category && set.indexOf(t.category) === -1) {
          set.push(t.category)
        }
      })
      return set.sort()
    },
    shown() {
      const kw = this.keyword.trim().toLowerCase()
      return this.templates.filter(t => {
        if (this.category && t.category !== this.category) {
          return false
        }
        if (!kw) {
          return true
        }
        return [t.name, t.key, t.desc].some(v => (v || '').toLowerCase().indexOf(kw) !== -1)
      })
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await listTemplatePacks()
        this.templates = res.data || []
        const valid = this.templates.map(t => t.key)
        this.selectedKeys = this.selectedKeys.filter(k => valid.indexOf(k) !== -1)
      } finally {
        this.loading = false
      }
    },
    async exportOne(template) {
      const res = await exportTemplatePack(template.key)
      await saveBlob(res, 'template-' + template.key + '-' + today() + '.json', 'application/json')
    },
    async exportSelected() {
      if (!this.selectedKeys.length) {
        Message.warning('请先勾选要导出的模板')
        return
      }
      const res = await exportTemplatePackZip(this.selectedKeys)
      await saveBlob(res, 'industry-templates-' + today() + '.zip', 'application/zip')
    },
    async doImport(options) {
      this.importing = true
      try {
        const res = await importTemplatePack(options.file, this.overwrite)
        const result = res.data || {}
        if (result.success) {
          const extra = result.overwritten ? '（覆盖 ' + result.overwritten + ' 套）' : ''
          Message.success('导入成功：' + result.imported + ' 套' + extra)
          this.overwrite = false
          await this.load()
        } else {
          this.errors = result.errors || ['导入失败']
          this.errorVisible = true
        }
        if (options.onSuccess) {
          options.onSuccess(result)
        }
      } catch (e) {
        if (options.onError) {
          options.onError(e)
        }
      } finally {
        this.importing = false
      }
    },
    async removeOne(template) {
      try {
        await this.$confirm(
          '确定删除用户模板包「' + template.name + '」（' + template.key + '）？删除后同名预置模板会重新出现。',
          '删除确认',
          { type: 'warning' }
        )
      } catch (e) {
        return
      }
      await deleteTemplatePack(template.key)
      Message.success('已删除用户模板包：' + template.key)
      this.selectedKeys = this.selectedKeys.filter(k => k !== template.key)
      await this.load()
    }
  }
}
</script>

<style scoped>
.page-head {
  margin-bottom: 12px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  margin-right: 10px;
}
.page-desc {
  font-size: 12px;
  color: var(--cg-text-secondary);
}
.toolbar-card {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.cat-select {
  width: 170px;
}
.kw-input {
  width: 260px;
}
.toolbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.overwrite {
  margin-right: 4px;
}
.drop-zone {
  margin-top: 12px;
}
.drop-zone ::v-deep .el-upload,
.drop-zone ::v-deep .el-upload-dragger {
  width: 100%;
}
.drop-zone ::v-deep .el-upload-dragger {
  padding: 14px 10px;
}
.drop-zone ::v-deep .el-icon-upload {
  margin: 0 6px 0 0;
  font-size: 24px;
  line-height: 1;
  vertical-align: middle;
}
.drop-zone ::v-deep .el-upload__text {
  display: inline-block;
  line-height: 24px;
}
.card-area {
  min-height: 120px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.tpl-card ::v-deep .el-card__body {
  padding: 12px;
}
.card-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pick {
  margin-right: 2px;
}
.cat-tag {
  margin-left: auto;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-title {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-key {
  margin-top: 2px;
  font-size: 12px;
  color: var(--cg-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--cg-text-regular, #606266);
  line-height: 18px;
  height: 36px;
  overflow: hidden;
}
.card-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--cg-text-placeholder, #909399);
}
.card-actions {
  margin-top: 4px;
  border-top: 1px solid var(--cg-border-color, #ebeef5);
  padding-top: 4px;
  display: flex;
  justify-content: flex-end;
}
.danger {
  color: #f56c6c;
}
.mono {
  font-family: Menlo, Consolas, monospace;
}
.error-summary {
  margin-bottom: 8px;
  color: #f56c6c;
}
.error-list {
  max-height: 340px;
  overflow-y: auto;
  padding-left: 18px;
  font-size: 12px;
  line-height: 20px;
}
</style>
