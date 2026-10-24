<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="一句话建系统：录入名称与描述 → AI 建模（或手动粘贴 DDL）→ 确认表结构 → 一键构建 → 部署完成后直接打开系统。全程不碰数据库、不开 curl。"
    />

    <el-card shadow="never" class="main-card">
      <el-steps :active="step" finish-status="success" align-center class="steps">
        <el-step title="① 录入与建模" />
        <el-step title="② 表结构确认" />
        <el-step title="③ 构建进度" />
      </el-steps>

      <!-- ============ 第①步：录入与建模 ============ -->
      <div v-show="step === 0">
        <el-form label-width="120px" style="max-width: 720px; margin-top: 8px" @submit.native.prevent>
          <el-form-item label="系统名称" required>
            <el-input v-model="form.systemName" placeholder="如：仓库管理系统" maxlength="64" />
          </el-form-item>
          <el-form-item label="一句话描述">
            <el-input
              v-model="form.systemDesc"
              type="textarea"
              :rows="2"
              placeholder="如：管理入库、出库、库存盘点"
              maxlength="512"
            />
          </el-form-item>
          <el-form-item label="数据库名" required>
            <el-input v-model="form.dbName" placeholder="小写字母开头的 snake_case，如 wms_new" maxlength="63" />
          </el-form-item>
          <el-form-item label="建模方式">
            <el-radio-group v-model="form.mode">
              <el-radio label="ai" :disabled="!aiEnabled">AI 生成表结构</el-radio>
              <el-radio label="manual">手动粘贴 DDL</el-radio>
            </el-radio-group>
            <div v-if="!aiEnabled" style="font-size:12px;color:#909399;line-height:1.6;">
              AI 建模未启用（服务端未配置 LLM key），请在「手动粘贴 DDL」中完成建表
            </div>
          </el-form-item>
          <el-form-item v-if="form.mode === 'manual'" label="建表 DDL">
            <el-input
              v-model="form.ddlText"
              type="textarea"
              :rows="10"
              placeholder="粘贴 CREATE DATABASE / CREATE TABLE 语句（每张表需包含 id/create_time/update_time/create_by/update_by/deleted 审计列，缺失会被 lint 阻断并给出补齐 SQL）"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="aiLoading" @click="onAiModel">
              {{ form.mode === 'ai' ? 'AI 生成表结构' : '检查 DDL' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- ============ 第②步：表结构确认 ============ -->
      <div v-show="step === 1">
        <!-- AI 模式：可编辑表结构 -->
        <template v-if="form.mode === 'ai'">
          <div class="confirm-bar">
            <span>共 {{ tables.length }} 张表。列可增删改，确认后执行建库建表（CREATE DATABASE IF NOT EXISTS，幂等可重试）。</span>
            <div>
              <el-button size="small" icon="el-icon-plus" @click="addTable">新增表</el-button>
              <el-button type="primary" size="small" :loading="dbLoading" @click="onPrepareDb">建库建表</el-button>
            </div>
          </div>
          <div v-for="(t, ti) in tables" :key="ti" class="table-block">
            <div class="table-head">
              <el-input v-model="t.tableName" size="small" style="width: 240px" placeholder="表名 snake_case">
                <template slot="prepend">表名</template>
              </el-input>
              <el-input v-model="t.tableComment" size="small" style="width: 220px" placeholder="表注释">
                <template slot="prepend">注释</template>
              </el-input>
              <el-button size="small" icon="el-icon-plus" @click="addColumn(t)">加列</el-button>
              <el-button size="small" type="danger" plain icon="el-icon-delete" @click="removeTable(ti)">删表</el-button>
            </div>
            <el-table :data="t.columns" size="mini" border>
              <el-table-column label="列名" width="220">
                <template slot-scope="{ row }"><el-input v-model="row.name" size="mini" placeholder="列名" /></template>
              </el-table-column>
              <el-table-column label="类型" width="180">
                <template slot-scope="{ row }">
                  <el-select v-model="row.type" size="mini" filterable allow-create>
                    <el-option v-for="opt in TYPE_OPTIONS" :key="opt" :label="opt" :value="opt" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="注释" min-width="200">
                <template slot-scope="{ row }"><el-input v-model="row.comment" size="mini" /></template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template slot-scope="{$index}">
                  <el-button type="text" class="danger-text" @click="t.columns.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="audit-note">建库时自动补齐：id（主键）、create_time、update_time、create_by、update_by、deleted</div>
          </div>
        </template>

        <!-- 手动模式：DDL 确认 -->
        <template v-else>
          <el-input v-model="form.ddlText" type="textarea" :rows="14" />
          <div class="confirm-bar">
            <span>执行前将按 BaseEntity 约定检查审计列，缺列会被阻断并给出补齐 SQL。</span>
            <el-button type="primary" size="small" :loading="dbLoading" @click="onPrepareDb">建库建表</el-button>
          </div>
        </template>

        <div style="margin-top: 12px">
          <el-button size="small" icon="el-icon-back" @click="step = 0">上一步</el-button>
        </div>
      </div>

      <!-- ============ 第③步：构建进度 ============ -->
      <div v-show="step === 2">
        <div v-if="detail" class="progress-head">
          <div>
            <span class="sys-name">{{ detail.systemName }}</span>
            <el-tag size="mini" :type="STATUS_TYPE[detail.status]">{{ STATUS_TEXT[detail.status] || detail.status }}</el-tag>
            <span class="meta">{{ detail.pipelineNo }} · 库 {{ detail.dbName }} · 后端 :{{ detail.backendPort || '—' }} · 前端 :{{ detail.frontendPort || '—' }}</span>
          </div>
          <div>
            <el-button
              v-if="detail.status === 'await_confirm' || detail.status === 'failed'"
              type="primary"
              size="small"
              icon="el-icon-video-play"
              :loading="runLoading"
              @click="onRun"
            >开始构建</el-button>
            <el-button
              v-if="detail.status === 'success'"
              type="success"
              size="small"
              icon="el-icon-link"
              @click="openSystem"
            >打开系统</el-button>
            <el-button
              v-if="detail.status === 'running' || detail.status === 'success'"
              size="small"
              type="danger"
              plain
              icon="el-icon-switch-button"
              @click="onStop"
            >停止</el-button>
            <el-button size="small" icon="el-icon-refresh" @click="refreshDetail">刷新</el-button>
          </div>
        </div>

        <!-- 三轨步骤：全局轨 / 后端轨 / 前端轨 -->
        <template v-if="detail">
          <div class="track">
            <div class="track-title">主流程</div>
            <div class="track-steps">
              <div v-for="s in stepsOf('pipeline')" :key="s.id" class="step-chip" :class="'st-' + s.status">
                <i :class="STEP_ICON[s.status] || 'el-icon-more'" />
                <span class="step-name">{{ s.stepName }}</span>
                <span v-if="s.message" class="step-msg">{{ s.message }}</span>
              </div>
            </div>
          </div>
          <div class="track">
            <div class="track-title">后端轨</div>
            <div class="track-steps">
              <div v-for="s in stepsOf('backend')" :key="s.id" class="step-chip" :class="'st-' + s.status">
                <i :class="STEP_ICON[s.status] || 'el-icon-more'" />
                <span class="step-name">{{ s.stepName }}</span>
                <span v-if="s.message" class="step-msg">{{ s.message }}</span>
              </div>
            </div>
          </div>
          <div class="track">
            <div class="track-title">前端轨</div>
            <div class="track-steps">
              <div v-for="s in stepsOf('frontend')" :key="s.id" class="step-chip" :class="'st-' + s.status">
                <i :class="STEP_ICON[s.status] || 'el-icon-more'" />
                <span class="step-name">{{ s.stepName }}</span>
                <span v-if="s.message" class="step-msg">{{ s.message }}</span>
              </div>
            </div>
          </div>

          <!-- 完成卡片 -->
          <el-result v-if="detail.status === 'success'" icon="success" title="部署完成" class="done-card">
            <template slot="extra">
              <div class="done-url">
                前端访问地址：
                <el-link type="primary" :underline="false" @click="openSystem">{{ detail.frontendUrl }}</el-link>
                <span class="meta">（默认账号 admin / admin123）</span>
              </div>
            </template>
          </el-result>
          <el-alert
            v-if="detail.status === 'failed'"
            type="error"
            :closable="false"
            :title="'失败：' + (detail.failReason || '详见步骤日志')"
            class="fail-bar"
          />
        </template>
      </div>
    </el-card>

    <!-- 历史任务 -->
    <el-card shadow="never" class="history-card">
      <div slot="header">
        <span>建系统任务</span>
        <el-button size="mini" type="text" icon="el-icon-refresh" style="float: right" @click="fetchPage">刷新</el-button>
      </div>
      <el-table :data="pageData" size="mini" border stripe>
        <el-table-column prop="pipelineNo" label="流水线号" width="170" />
        <el-table-column prop="systemName" label="系统名称" min-width="140" />
        <el-table-column prop="dbName" label="目标库" width="140" />
        <el-table-column prop="mode" label="方式" width="70">
          <template slot-scope="{ row }">{{ row.mode === 'ai' ? 'AI' : '手动' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="STATUS_TYPE[row.status]">{{ STATUS_TEXT[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="180">
          <template slot-scope="{ row }">
            <el-button type="text" size="mini" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'success'"
              type="text"
              size="mini"
              @click="openUrl(row.frontendUrl)"
            >打开系统</el-button>
            <el-button
              v-if="row.status === 'running'"
              type="text"
              size="mini"
              class="danger-text"
              @click="onStop(row)"
            >停止</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        :current-page.sync="page.page"
        :page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next"
        style="margin-top: 10px"
        @current-change="fetchPage"
      />
    </el-card>
  </div>
</template>

<script>
import { aiModel, createPipeline, preparePipelineDb, runPipeline, getPipeline, getPipelinePage, stopPipeline, getConfig } from '@/api/pipeline'
import { getToken } from '@/utils/auth'

const STATUS_TEXT = {
  draft: '待建库',
  await_confirm: '待构建',
  running: '运行中',
  success: '成功',
  failed: '失败',
  stopped: '已停止'
}
const STATUS_TYPE = {
  draft: 'info',
  await_confirm: 'warning',
  running: 'primary',
  success: 'success',
  failed: 'danger',
  stopped: 'info'
}
const STEP_ICON = {
  pending: 'el-icon-time',
  running: 'el-icon-loading',
  success: 'el-icon-success',
  failed: 'el-icon-error',
  skipped: 'el-icon-remove-outline'
}

export default {
  name: 'GenPipeline',
  data() {
    return {
      step: 0,
      aiEnabled: false,
      form: { systemName: '', systemDesc: '', dbName: '', mode: 'ai', ddlText: '' },
      tables: [],
      aiLoading: false,
      dbLoading: false,
      runLoading: false,
      detail: null,
      pageData: [],
      page: { page: 1, size: 10, total: 0 },
      es: null,
      STATUS_TEXT,
      STATUS_TYPE,
      STEP_ICON,
      TYPE_OPTIONS: ['bigint', 'int', 'varchar(32)', 'varchar(64)', 'varchar(128)', 'varchar(255)', 'decimal(18,2)', 'text', 'datetime', 'date', 'tinyint']
    }
  },
  created() {
    this.fetchPage()
    // AI 可用性前置下发：未配 LLM key 时直接禁用 AI 选项，不让用户点了才报错
    getConfig().then(res => {
      this.aiEnabled = !!(res.data && res.data.aiEnabled)
      if (!this.aiEnabled && this.form.mode === 'ai') {
        this.form.mode = 'manual'
      }
    }).catch(() => {})
  },
  beforeDestroy() {
    this.closeSse()
  },
  methods: {
    /* ---------- 第①步 ---------- */
    async onAiModel() {
      if (!this.form.systemName.trim()) return this.$message.warning('请输入系统名称')
      if (this.form.mode === 'ai' && !this.aiEnabled) {
        return this.$message.warning('AI 建模未启用（服务端未配置 LLM key），请使用手动粘贴 DDL')
      }
      if (this.form.mode === 'manual') {
        if (!this.form.ddlText.trim()) return this.$message.warning('请粘贴 DDL')
        this.step = 1
        return
      }
      this.aiLoading = true
      try {
        const res = await aiModel({ systemName: this.form.systemName, systemDesc: this.form.systemDesc })
        this.tables = (res.data.tables || []).map(t => ({
          tableName: t.tableName,
          tableComment: t.tableComment,
          columns: (t.columns || []).map(c => ({ name: c.name, type: c.type, comment: c.comment }))
        }))
        this.step = 1
      } finally {
        this.aiLoading = false
      }
    },

    /* ---------- 第②步 ---------- */
    addTable() {
      this.tables.push({ tableName: '', tableComment: '', columns: [{ name: '', type: 'varchar(64)', comment: '' }] })
    },
    removeTable(i) {
      this.tables.splice(i, 1)
    },
    addColumn(t) {
      t.columns.push({ name: '', type: 'varchar(255)', comment: '' })
    },
    async onPrepareDb() {
      if (!this.form.dbName.trim()) return this.$message.warning('请填写数据库名')
      if (this.form.mode === 'ai') {
        for (const t of this.tables) {
          if (!t.tableName.trim()) return this.$message.warning('存在未命名的表')
        }
      }
      await this.$confirm(
        `将在数据库 ${this.form.dbName} 中建表${this.form.mode === 'ai' ? `（共 ${this.tables.length} 张）` : ''}，已存在的同名库/表不会覆盖。确认执行？`,
        '建库建表',
        { type: 'warning' }
      )
      this.dbLoading = true
      try {
        // 任务先落库（建模结果一并带上），prepare-db 只带确认后的表结构
        const created = await createPipeline({ ...this.form, tables: this.tables })
        const pipelineId = created.data.id
        await preparePipelineDb(pipelineId, {
          tables: this.form.mode === 'ai' ? this.tables : null,
          ddlText: this.form.mode === 'manual' ? this.form.ddlText : null
        })
        await this.loadDetail(pipelineId)
        this.step = 2
        this.fetchPage()
      } finally {
        this.dbLoading = false
      }
    },

    /* ---------- 第③步 ---------- */
    async onRun() {
      this.runLoading = true
      try {
        await runPipeline(this.detail.id)
        await this.loadDetail(this.detail.id)
        this.subscribeSse(this.detail.id)
      } finally {
        this.runLoading = false
      }
    },
    async onStop(row) {
      const id = row && row.id ? row.id : this.detail.id
      await this.$confirm('将停止后端与前端进程并置为已停止，确认？', '停止系统', { type: 'warning' })
      await stopPipeline(id)
      this.closeSse()
      if (this.detail && this.detail.id === id) {
        await this.loadDetail(id)
      }
      this.fetchPage()
    },
    openSystem() {
      this.openUrl(this.detail.frontendUrl)
    },
    openUrl(url) {
      if (url) window.open(url, '_blank')
    },
    stepsOf(side) {
      return (this.detail && this.detail.steps || []).filter(s => s.side === side)
    },
    async loadDetail(id) {
      const res = await getPipeline(id)
      this.detail = res.data
      if (this.detail.status === 'running') {
        this.subscribeSse(id)
      }
    },
    openDetail(row) {
      this.loadDetail(row.id)
      this.step = 2
    },
    async refreshDetail() {
      if (this.detail) await this.loadDetail(this.detail.id)
    },

    /* ---------- SSE ---------- */
    subscribeSse(id) {
      this.closeSse()
      const base = process.env.VUE_APP_BASE_API || ''
      const url = `${base}/gen/pipeline/${id}/stream?access_token=${getToken()}`
      this.es = new EventSource(url)
      this.es.addEventListener('progress', e => {
        try {
          this.detail = JSON.parse(e.data)
        } catch (err) {
          /* 忽略半包 */
        }
        if (this.detail && ['success', 'failed', 'stopped'].includes(this.detail.status)) {
          this.closeSse()
          this.fetchPage()
        }
      })
      this.es.onerror = () => {
        // 断流不等于失败：终态前的断线由刷新兜底，避免误报
      }
    },
    closeSse() {
      if (this.es) {
        this.es.close()
        this.es = null
      }
    },

    /* ---------- 任务分页 ---------- */
    async fetchPage() {
      const res = await getPipelinePage({ page: this.page.page, size: this.page.size })
      this.pageData = res.data.list
      this.page.total = res.data.total
    }
  }
}
</script>

<style scoped>
.tip-bar { margin-bottom: 12px; }
.main-card { margin-bottom: 12px; }
.steps { margin-bottom: 18px; }
.confirm-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin: 8px 0 12px; color: #606266; font-size: 13px;
}
.table-block { margin-bottom: 16px; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; }
.table-head { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.audit-note { color: #909399; font-size: 12px; margin-top: 6px; }
.progress-head {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;
}
.sys-name { font-size: 16px; font-weight: 600; margin-right: 8px; }
.meta { color: #909399; font-size: 12px; margin-left: 8px; }
.track { margin-bottom: 12px; }
.track-title { font-size: 13px; color: #303133; font-weight: 600; margin-bottom: 6px; }
.track-steps { display: flex; flex-wrap: wrap; gap: 8px; }
.step-chip {
  display: inline-flex; align-items: center; gap: 6px;
  border: 1px solid #ebeef5; border-radius: 16px; padding: 4px 12px; font-size: 13px;
  background: #fafafa;
}
.step-chip i { font-size: 14px; }
.st-success { border-color: #b3e19d; background: #f0f9eb; color: #67c23a; }
.st-running { border-color: #a0cfff; background: #ecf5ff; color: #409eff; }
.st-failed { border-color: #fab6b6; background: #fef0f0; color: #f56c6c; }
.st-pending { color: #909399; }
.step-msg { color: #909399; font-size: 12px; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.done-card { margin-top: 10px; }
.done-url { font-size: 14px; }
.fail-bar { margin-top: 10px; }
.history-card { margin-top: 12px; }
.danger-text { color: #f56c6c; }
</style>
