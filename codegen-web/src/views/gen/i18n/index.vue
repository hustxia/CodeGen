<template>
  <div class="i18n-page">
    <div class="page-head">
      <div>
        <h2>文案翻译</h2>
        <p class="page-desc">
          为生成的前端工程逐语言补译文。留空即回落到默认语言，无需一次翻完；
          保存后重新生成工程即可生效。
        </p>
      </div>
      <div class="head-tag">V8.0 · 产物国际化</div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form inline size="small" @submit.native.prevent>
        <el-form-item label="所属系统">
          <el-select v-model="projectId" style="width: 200px" @change="onProjectChange">
            <el-option
              v-for="p in projects"
              :key="String(p.id)"
              :label="p.projectName"
              :value="String(p.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="语言">
          <el-select v-model="locale" style="width: 220px" @change="onLocaleChange">
            <el-option
              v-for="l in locales"
              :key="l.locale"
              :label="l.label + ' · ' + l.locale"
              :value="l.locale"
            >
              <span>{{ l.label }}</span>
              <span class="opt-native">{{ l.nameNative }}<b v-if="l.dir === 'rtl'"> RTL</b></span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            clearable
            placeholder="键名 / 中文原文 / 分类"
            style="width: 220px"
            @keyup.enter.native="loadPage(1)"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="loadPage(1)">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="coverage">
        <span class="coverage-label">译文覆盖率</span>
        <el-progress
          :percentage="coveragePercent"
          :stroke-width="10"
          style="width: 260px"
        />
        <span class="coverage-text">{{ coverageText }}</span>
        <span class="coverage-hint">
          内置精译 {{ builtinLocales.length }} 种 · 词库 {{ totalKeys }} 键 · 其余语言为占位包
        </span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <span class="card-title">{{ currentLocaleLabel }} 文案（{{ total }}）</span>
        <div>
          <el-button size="mini" icon="el-icon-document" @click="exportJson">导出 JSON</el-button>
          <el-button size="mini" icon="el-icon-upload2" @click="openImport">导入 JSON</el-button>
          <el-button size="mini" icon="el-icon-refresh-left" :disabled="!dirtyCount" @click="resetSelected">恢复内置</el-button>
          <el-button
            size="mini"
            type="primary"
            icon="el-icon-check"
            :loading="saving"
            :disabled="!dirtyCount"
            @click="save"
          >保存{{ dirtyCount ? '（' + dirtyCount + '）' : '' }}</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        size="small"
        height="520"
        row-key="key"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="42" reserve-selection />
        <el-table-column prop="key" label="文案键" width="210" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="80" align="center" />
        <el-table-column prop="sourceText" label="中文原文" min-width="190" show-overflow-tooltip />
        <el-table-column prop="builtinValue" label="内置译文" min-width="180" show-overflow-tooltip>
          <template slot-scope="s">
            <span :class="{ muted: !s.row.builtinValue }">{{ s.row.builtinValue || '—（占位）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前译文" min-width="240">
          <template slot-scope="s">
            <el-input
              v-model="s.row.value"
              size="mini"
              :placeholder="s.row.builtinValue || '留空则回落默认语言'"
              @input="markDirty(s.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template slot-scope="s">
            <el-tag v-if="s.row.overridden" type="success" size="mini">已覆盖</el-tag>
            <el-tag v-else-if="s.row.builtinValue" type="info" size="mini">内置</el-tag>
            <el-tag v-else type="warning" size="mini">待补译</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        @current-change="loadPage"
        @size-change="onSizeChange"
      />
    </el-card>

    <el-dialog title="导入译文 JSON" :visible.sync="importVisible" width="560px">
      <p class="dialog-tip">
        粘贴本页导出的 JSON（含 <code>items: [{ key, value }]</code>），
        键不在词库内的条目会自动跳过。
      </p>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="10"
        placeholder='{"locale":"en-US","items":[{"key":"common.add","value":"Add"}]}'
      />
      <el-checkbox v-model="overwrite" style="margin-top: 10px">覆盖已有译文</el-checkbox>
      <div slot="footer">
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listI18nLocales,
  listI18nKeys,
  i18nCatalog,
  pageI18nMessages,
  saveI18nMessages,
  exportI18nMessages,
  importI18nMessages,
  listProjects
} from '@/api/i18n'

export default {
  name: 'GenI18n',
  data() {
    return {
      projects: [],
      locales: [],
      keys: [],
      rows: [],
      selected: [],
      projectId: '',
      locale: '',
      keyword: '',
      pageNum: 1,
      pageSize: 20,
      total: 0,
      totalKeys: 0,
      builtinLocales: [],
      loading: false,
      saving: false,
      importing: false,
      importVisible: false,
      importText: '',
      overwrite: true
    }
  },
  computed: {
    currentLocaleLabel() {
      const hit = this.locales.find(l => l.locale === this.locale)
      return hit ? hit.label + ' · ' + hit.locale : '译文'
    },
    coveragePercent() {
      const hit = this.locales.find(l => l.locale === this.locale)
      if (!hit || !hit.total) return 0
      return Math.round(hit.translated * 100 / hit.total)
    },
    coverageText() {
      const hit = this.locales.find(l => l.locale === this.locale)
      return hit ? hit.translated + ' / ' + hit.total : '0 / 0'
    },
    dirtyCount() {
      return this.rows.filter(r => r.dirty).length
    }
  },
  created() {
    this.init()
  },
  methods: {
    async init() {
      try {
        const [pRes, cRes, kRes] = await Promise.all([
          listProjects(), i18nCatalog(), listI18nKeys()
        ])
        this.projects = (pRes.data || []).filter(p => p.enabled !== 0)
        const def = this.projects.find(p => p.isDefault === 1) || this.projects[0]
        this.projectId = def ? String(def.id) : ''
        this.totalKeys = (cRes.data && cRes.data.totalKeys) || 0
        this.builtinLocales = (cRes.data && cRes.data.builtinLocales) || []
        this.keys = kRes.data || []
        await this.loadLocales()
      } catch (e) {
        /* 拦截器已提示 */
      }
    },
    async loadLocales() {
      if (!this.projectId) return
      try {
        const res = await listI18nLocales(this.projectId)
        this.locales = res.data || []
        this.locale = this.locales.length ? this.locales[0].locale : ''
        await this.loadPage(1)
      } catch (e) {
        /* 拦截器已提示 */
      }
    },
    async loadPage(page) {
      if (!this.projectId || !this.locale) return
      this.pageNum = page || this.pageNum
      this.loading = true
      try {
        const res = await pageI18nMessages({
          projectId: this.projectId,
          locale: this.locale,
          keyword: this.keyword,
          pageNum: this.pageNum,
          pageSize: this.pageSize
        })
        const data = res.data || {}
        this.rows = (data.records || []).map(r => ({ ...r, dirty: false }))
        this.total = data.total || 0
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.loading = false
      }
    },
    onProjectChange() {
      this.pageNum = 1
      this.loadLocales()
    },
    onLocaleChange() {
      this.loadPage(1)
    },
    onSizeChange(size) {
      this.pageSize = size
      this.loadPage(1)
    },
    resetFilter() {
      this.keyword = ''
      this.loadPage(1)
    },
    markDirty(row) {
      this.$set(row, 'dirty', true)
    },
    onSelectionChange(rows) {
      this.selected = rows
    },
    async save() {
      const items = this.rows.filter(r => r.dirty).map(r => ({ key: r.key, value: r.value }))
      if (!items.length) return
      this.saving = true
      try {
        await saveI18nMessages({ projectId: this.projectId, locale: this.locale, items })
        this.$message.success('保存成功')
        await this.loadLocales()
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.saving = false
      }
    },
    async resetSelected() {
      const targets = this.selected.length ? this.selected : this.rows.filter(r => r.dirty)
      if (!targets.length) return
      const items = targets.map(r => ({ key: r.key, value: '' }))
      this.saving = true
      try {
        await saveI18nMessages({ projectId: this.projectId, locale: this.locale, items })
        this.$message.success('已恢复内置回落')
        await this.loadLocales()
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.saving = false
      }
    },
    async exportJson() {
      try {
        const res = await exportI18nMessages(this.projectId, this.locale)
        const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'i18n-' + this.locale + '.json'
        a.click()
        URL.revokeObjectURL(url)
        this.$message.success('已导出 ' + (res.data.items || []).length + ' 条')
      } catch (e) {
        /* 拦截器已提示 */
      }
    },
    async openImport() {
      try {
        const res = await exportI18nMessages(this.projectId, this.locale)
        this.importText = JSON.stringify(res.data, null, 2)
      } catch (e) {
        this.importText = ''
      }
      this.importVisible = true
    },
    async doImport() {
      let parsed
      try {
        parsed = JSON.parse(this.importText)
      } catch (e) {
        this.$message.error('JSON 解析失败：' + e.message)
        return
      }
      const items = Array.isArray(parsed) ? parsed : (parsed && parsed.items)
      if (!Array.isArray(items)) {
        this.$message.error('缺少 items 数组')
        return
      }
      this.importing = true
      try {
        const res = await importI18nMessages({
          projectId: this.projectId,
          locale: this.locale,
          items,
          overwrite: this.overwrite
        })
        const d = res.data || {}
        this.$message.success('导入 ' + (d.imported || 0) + ' 条，跳过 ' + (d.skipped || 0) + ' 条')
        this.importVisible = false
        await this.loadLocales()
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.importing = false
      }
    }
  }
}
</script>

<style scoped>
.i18n-page { padding: 18px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 14px; }
.page-head h2 { margin: 0 0 4px; font-size: 20px; }
.page-desc { margin: 0; color: #8492a6; font-size: 13px; }
.head-tag { color: #409EFF; background: #ecf5ff; border: 1px solid #d9ecff; padding: 5px 10px; border-radius: 4px; font-size: 12px; }
.filter-card, .table-card { margin-bottom: 14px; }
.coverage { display: flex; align-items: center; gap: 12px; padding: 4px 0 0; font-size: 13px; }
.coverage-label { color: #606266; }
.coverage-text { color: #409EFF; font-weight: 600; }
.coverage-hint { color: #a0a6b0; font-size: 12px; }
.opt-native { float: right; color: #8492a6; font-size: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-title { font-weight: 600; }
.muted { color: #c0c4cc; }
.dialog-tip { margin: 0 0 10px; color: #606266; font-size: 13px; line-height: 1.6; }
.pager { margin-top: 12px; text-align: right; }
</style>
