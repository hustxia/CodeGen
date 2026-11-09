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
          <el-form-item v-if="form.mode === 'ai'" label="建模规模">
            <el-radio-group v-model="aiScale">
              <el-radio label="quick">精简（3-5 张，约 4 分钟）</el-radio>
              <el-radio label="full">完整系统（50 张+，逐模块生成约 40 分钟）</el-radio>
            </el-radio-group>
            <div v-if="aiScale === 'full' && aiLoading" style="font-size:12px;color:#409EFF;line-height:1.6;">
              {{ aiProgressText || '正在拆分业务模块...' }}
            </div>
          </el-form-item>
          <el-form-item v-if="form.mode === 'manual'" label="建表 DDL">
            <el-input
              v-model="form.ddlText"
              type="textarea"
              :rows="10"
              placeholder="粘贴 CREATE DATABASE / CREATE TABLE 语句。缺少审计列（id/create_time/update_time/create_by/update_by/deleted）时由服务端自动补齐：只增不改，补齐明细随后如实展示"
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
            <span>执行前将按 BaseEntity 约定检查审计列，缺列由服务端自动补齐（只增不改），补齐明细在下一步展示。</span>
            <el-button type="primary" size="small" :loading="dbLoading" @click="onPrepareDb">建库建表</el-button>
          </div>
        </template>

        <!-- V5.2 数据初始化（两种模式共用） -->
        <div class="seed-bar">
          <el-radio-group v-model="form.seedMode" size="small">
            <el-radio label="rule">规则生成示例数据（推荐）</el-radio>
            <el-radio label="ai">AI 增强（贴合业务）</el-radio>
            <el-radio label="off">跳过</el-radio>
          </el-radio-group>
          <div class="seed-tip">规则生成：建表后按外键依赖自动灌入中文示例数据（主数据 12 行、单据 8 行、明细跟随主表），枚举/唯一/必填约束全部满足，单次上限 2000 行。AI 增强：由 AI 依据系统名与表结构生成业务贴合的值池（商品名/公司名/备注等），约束仍由规则保证；AI 失败自动回落规则词库，需已配置 pipeline.ai。</div>
        </div>

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
              v-if="detail.status === 'success'"
              type="warning"
              size="small"
              plain
              icon="el-icon-magic-stick"
              :loading="recLoading"
              @click="openRecommend"
            >AI 推荐配置</el-button>
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

        <!-- V5.1：手动 DDL 补齐明细徽标（如实展示补了什么） -->
        <el-alert v-if="patchedReport.length" type="success" :closable="true" class="tip-bar">
          <template slot="title">
            建库时已自动补齐审计列（只增不改）：
            <el-tag
              v-for="p in patchedReport"
              :key="p.table"
              size="mini"
              type="success"
              style="margin: 0 6px 4px 0"
            >{{ p.table }} 补 {{ p.cols.join('/') }}</el-tag>
          </template>
        </el-alert>

        <!-- V5.1：审计列检查 + 一键补齐（构建前把缺列表修到合规） -->
        <div v-if="detail && ['await_confirm', 'failed'].includes(detail.status)" class="lint-card">
          <div class="lint-head">
            <span class="lint-title">审计列检查（目标库 {{ detail.dbName }}）</span>
            <div>
              <el-button size="mini" icon="el-icon-search" :loading="lintLoading" @click="onLint">检查</el-button>
              <el-button
                size="mini"
                type="primary"
                icon="el-icon-magic-stick"
                :disabled="fixDisabled"
                :loading="fixLoading"
                @click="onLintFix"
              >一键补齐</el-button>
            </div>
          </div>
          <el-table v-if="lintItems.length" :data="lintItems" size="mini" border>
            <el-table-column prop="table" label="表名" width="200" />
            <el-table-column label="缺失列" min-width="240">
              <template slot-scope="{ row }">
                <template v-if="row.missingCols && row.missingCols.length">
                  <el-tag v-for="c in row.missingCols" :key="c" size="mini" style="margin-right: 4px">{{ c }}</el-tag>
                </template>
                <span v-else class="meta">—</span>
              </template>
            </el-table-column>
            <el-table-column label="补齐结果" min-width="200">
              <template slot-scope="{ row }">
                <el-tag v-if="row.action === 'PATCHED'" size="mini" type="success">已补 {{ (row.patchedCols || []).length }} 列</el-tag>
                <el-tag v-else-if="row.action === 'SKIPPED'" size="mini" type="info">{{ row.error || '合规，跳过' }}</el-tag>
                <el-tag v-else-if="row.action === 'FAILED'" size="mini" type="danger" :title="row.error">失败：{{ row.error }}</el-tag>
                <el-tag v-else-if="row.missingCols && row.missingCols.length" size="mini" type="warning">待补齐</el-tag>
                <span v-else class="meta">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-else-if="lintChecked" class="audit-note">全部合规，可直接开始构建</div>
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

    <!-- V7.0 AI 推荐预览：diff + 勾选应用（应用前零写库）。V7.3 抽成公共组件，表配置页复用 -->
    <ai-recommend-dialog
      :visible.sync="recVisible"
      :loading="recLoading"
      :apply-loading="recApplyLoading"
      :exists="recExists"
      :tables="recData.tables"
      :menus="recData.menus"
      :drops="recData.drops"
      @apply="onApplyRecommend"
    />
  </div>
</template>

<script>
import { aiModel, aiModules, createPipeline, preparePipelineDb, runPipeline, getPipeline, getPipelinePage, stopPipeline, getConfig, lintDb, fixPipelineLint, aiRecommendPreview, aiRecommendApply } from '@/api/pipeline'
import { getToken } from '@/utils/auth'
import AiRecommendDialog from '../components/AiRecommendDialog.vue'

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

/* V7.0 建议字段中文名 */

export default {
  name: 'GenPipeline',
  components: { AiRecommendDialog },
  data() {
    return {
      step: 0,
      aiEnabled: false,
      aiScale: 'quick',
      aiProgressText: '',
      form: { systemName: '', systemDesc: '', dbName: '', mode: 'ai', ddlText: '', seedMode: 'rule' },
      tables: [],
      aiLoading: false,
      dbLoading: false,
      runLoading: false,
      detail: null,
      pageData: [],
      page: { page: 1, size: 10, total: 0 },
      es: null,
      prepareResult: null,
      lintChecked: false,
      lintLoading: false,
      fixLoading: false,
      lintItems: [],
      recVisible: false,
      recLoading: false,
      recApplyLoading: false,
      recExists: false,
      recData: { tables: [], menus: [], drops: [] },
      STATUS_TEXT,
      STATUS_TYPE,
      STEP_ICON,
      TYPE_OPTIONS: ['bigint', 'int', 'varchar(32)', 'varchar(64)', 'varchar(128)', 'varchar(255)', 'decimal(18,2)', 'text', 'datetime', 'date', 'tinyint']
    }
  },
  computed: {
    /* V5.1：建库时被补齐的表（PATCHED），供结果区徽标展示 */
    patchedReport() {
      const list = (this.prepareResult && this.prepareResult.auditReport) || []
      return list.filter(i => i.action === 'PATCHED').map(i => ({ table: i.table, cols: i.patchedCols || [] }))
    },
    /* V5.1：没有待补/失败的表时禁用一键补齐（含未检查状态） */
    fixDisabled() {
      if (!this.lintChecked) return true
      return !this.lintItems.some(i => (i.action ? i.action === 'FAILED' : (i.missingCols || []).length > 0))
    },
    /* V7.0：生成期被白名单丢弃的 AI 建议项（提示用户哪些建议没进来） */
    recDrops() {
      return this.recData.drops || []
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
      this.aiProgressText = ''
      try {
        if (this.aiScale === 'full') {
          await this.buildFullSystem()
        } else {
          const res = await aiModel({ systemName: this.form.systemName, systemDesc: this.form.systemDesc })
          this.tables = this.normalizeTables((res.data.tables || []))
          this.step = 1
        }
      } finally {
        this.aiLoading = false
        this.aiProgressText = ''
      }
    },

    /** 列结构统一成编辑器需要的形状 */
    normalizeTables(list) {
      return (list || []).map(t => ({
        tableName: t.tableName,
        tableComment: t.tableComment,
        columns: (t.columns || []).map(c => ({ name: c.name, type: c.type, comment: c.comment }))
      }))
    },

    /**
     * 完整系统模式：AI 先拆业务模块，再逐模块生成表结构，前端按表名合并去重。
     * 单模块失败跳过不中断（小模型偶发超时属正常，凑够总量即可）。
     */
    async buildFullSystem() {
      const base = { systemName: this.form.systemName, systemDesc: this.form.systemDesc }
      const modRes = await aiModules(base)
      const modules = (modRes.data && modRes.data.modules) || []
      if (!modules.length) throw new Error('AI 未能拆分出业务模块，请重试或改用精简模式')
      const merged = []
      const seen = new Set()
      let failed = 0
      for (let i = 0; i < modules.length; i++) {
        const m = modules[i]
        this.aiProgressText = `正在生成模块 ${i + 1}/${modules.length}：${m.name}${m.desc ? '（' + m.desc + '）' : ''} —— 已合并 ${merged.length} 张表`
        try {
          const r = await aiModel({ ...base, moduleName: m.name })
          for (const t of this.normalizeTables(r.data.tables || [])) {
            const key = (t.tableName || '').trim().toLowerCase()
            if (key && !seen.has(key)) {
              seen.add(key)
              merged.push(t)
            }
          }
        } catch (e) {
          failed++
          console.warn('模块生成失败，已跳过:', m.name, e)
        }
      }
      if (!merged.length) throw new Error('所有模块均生成失败，请检查模型服务后重试')
      if (failed > 0) {
        this.$message.warning(`${failed} 个模块生成失败已跳过，其余模块合并出 ${merged.length} 张表`)
      }
      this.tables = merged
      this.step = 1
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
        const prep = await preparePipelineDb(pipelineId, {
          tables: this.form.mode === 'ai' ? this.tables : null,
          ddlText: this.form.mode === 'manual' ? this.form.ddlText : null
        })
        this.prepareResult = prep.data || null
        this.resetLint()
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
      this.prepareResult = null
      this.resetLint()
      this.loadDetail(row.id)
      this.step = 2
    },

    /* ---------- V5.1 审计列检查/一键补齐 ---------- */
    resetLint() {
      this.lintChecked = false
      this.lintItems = []
    },
    async onLint() {
      if (!this.detail) return
      this.lintLoading = true
      try {
        const res = await lintDb(this.detail.dbName)
        this.lintItems = (res.data && res.data.items) || []
        this.lintChecked = true
      } finally {
        this.lintLoading = false
      }
    },
    async onLintFix() {
      if (!this.detail) return
      this.fixLoading = true
      try {
        const res = await fixPipelineLint(this.detail.id)
        this.lintItems = (res.data && res.data.items) || []
        this.lintChecked = true
        if (res.data && res.data.ok) {
          this.$message.success('补齐完成，复检全部合规')
        } else {
          this.$message.warning('部分表补齐失败，详见补齐结果列')
        }
      } finally {
        this.fixLoading = false
      }
    },

    async refreshDetail() {
      if (this.detail) await this.loadDetail(this.detail.id)
    },

    /* ---------- V7.0 AI 推荐预览与应用 ---------- */

    async openRecommend() {
      if (!this.detail) return
      this.recVisible = true
      this.recLoading = true
      this.recExists = false
      this.recData = { tables: [], menus: [], drops: [] }
      try {
        const res = await aiRecommendPreview(this.detail.id)
        const vo = res.data || {}
        this.recExists = !!vo.exists
        this.recData = {
          tables: vo.tables || [],
          menus: vo.menus || [],
          drops: vo.drops || []
        }
        // 勾选态由 AiRecommendDialog 内部按 manual 标记初始化，这里只管数据
      } finally {
        this.recLoading = false
      }
    },

    async onApplyRecommend(dto) {
      this.recApplyLoading = true
      try {
        const res = await aiRecommendApply(this.detail.id, dto)
        const n = res.data || 0
        this.$message.success(`已应用 ${n} 个字段，重新生成即可生效`)
        this.recVisible = false
        await this.refreshDetail()
      } finally {
        this.recApplyLoading = false
      }
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
.seed-bar {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  background: #ecf5ff;
}
.seed-tip { color: #909399; font-size: 12px; margin-top: 8px; line-height: 1.6; }
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
.lint-card { border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; margin-bottom: 14px; }
.lint-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.lint-title { font-size: 14px; font-weight: 600; }

/* V7.0 AI 推荐预览 */
.rec-tip { font-size: 12px; color: #909399; margin-bottom: 10px; line-height: 1.6; }
.rec-table { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px 12px; margin-bottom: 10px; }
.rec-table-head { margin-bottom: 8px; }
.rec-table-head .meta { margin-left: 8px; }
.rec-fields .el-checkbox { margin-right: 16px; }
.rec-group { font-size: 12px; color: #909399; margin-right: 6px; }
.rec-col { display: flex; align-items: center; flex-wrap: wrap; padding: 3px 0 3px 14px; }
.rec-colname { display: inline-block; min-width: 120px; font-size: 13px; color: #606266; }
.rec-col .el-checkbox { margin-right: 16px; }
</style>
