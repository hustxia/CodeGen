<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">规格对账</span>
      <span class="page-desc">
        以已生效 SPEC 的期望清单为依据，核对实际产物（表 / 字段 / 枚举三类）；CONVERGED 后才提示可进入验收。
      </span>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <span class="toolbar-label">所属系统</span>
      <el-select v-model="projectId" size="small" filterable placeholder="选择系统" class="proj-select" @change="loadReports">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-button
        size="small"
        type="primary"
        icon="el-icon-cpu"
        class="run-btn"
        :disabled="!projectId"
        :loading="running"
        @click="doRun"
      >
        执行对账
      </el-button>
    </el-card>

    <!-- 最新报告汇总 -->
    <div v-if="latest" class="stat-strip">
      <div class="stat ok">
        <span class="num">{{ latest.okCount }}</span>
        <span class="lbl">已实现<br>OK</span>
      </div>
      <div class="stat miss">
        <span class="num">{{ latest.missingCount }}</span>
        <span class="lbl">缺失<br>MISSING</span>
      </div>
      <div class="stat extra">
        <span class="num">{{ latest.extraCount }}</span>
        <span class="lbl">多余<br>EXTRA</span>
      </div>
      <div class="stat conv">
        <span class="num">{{ convergeText }}</span>
        <span class="lbl">收敛进度<br>missing=0 且 extra=0 → CONVERGED</span>
      </div>
    </div>

    <div v-if="latest && latest.status === 'UNCONVERGED'" class="callout warn">
      <i class="el-icon-warning" />
      存在缺口（缺失 {{ latest.missingCount }}、多余 {{ latest.extraCount }}），处理建议见明细；全部收敛后才可进入截图验收。
    </div>
    <div v-if="latest && latest.status === 'CONVERGED'" class="callout ok">
      <i class="el-icon-success" />
      规格已收敛（CONVERGED），可进入截图验收环节。
    </div>

    <el-card shadow="never" class="table-card">
      <div class="block-title">对账报告列表</div>
      <el-table
        :data="reports"
        size="mini"
        v-loading="loading"
        highlight-current-row
        @current-change="pickReport"
      >
        <el-table-column label="对账依据" width="110">
          <template slot-scope="s">SPEC v{{ s.row.specVersion }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template slot-scope="s">
            <el-tag size="mini" :type="reportTag(s.row.status)">{{ reportText(s.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="okCount" label="已实现" width="80" />
        <el-table-column label="缺失" width="80">
          <template slot-scope="s">
            <span :class="{ 'num-danger': s.row.missingCount > 0 }">{{ s.row.missingCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="多余" width="80">
          <template slot-scope="s">
            <span :class="{ 'num-warning': s.row.extraCount > 0 }">{{ s.row.extraCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="对账时间" min-width="160" />
        <el-table-column label="操作" width="100">
          <template slot-scope="s">
            <el-button type="text" size="mini" @click="showDetail(s.row)">查看明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="detail.items.length" class="detail-wrap">
        <div class="block-title">报告明细（#{{ detail.reportId }} · 排序：MISSING → EXTRA → OK）</div>
        <el-table :data="detail.items" size="mini" v-loading="detailLoading" max-height="420">
          <el-table-column label="类型" width="90">
            <template slot-scope="s">
              <el-tag size="mini" type="info">{{ typeText(s.row.itemType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="itemKey" label="项键" min-width="180" show-overflow-tooltip>
            <template slot-scope="s">
              <span class="mono">{{ s.row.itemKey }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结论" width="110">
            <template slot-scope="s">
              <el-tag size="mini" :type="kindTag(s.row.kind)">{{ kindText(s.row.kind) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourceRef" label="规格来源" min-width="130" show-overflow-tooltip />
          <el-table-column prop="suggestion" label="处理建议" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script>
import { listProjectOptions, getDefaultProject } from '@/api/gen'
import { runConverge, convergeReports, convergeReport } from '@/api/spec'

// 状态映射穷举
const REPORT_STATUS_MAP = {
  CONVERGED: { text: 'CONVERGED', tag: 'success' },
  UNCONVERGED: { text: 'UNCONVERGED', tag: 'warning' }
}
const KIND_MAP = {
  OK: { text: 'OK', tag: 'success' },
  MISSING: { text: 'MISSING', tag: 'danger' },
  EXTRA: { text: 'EXTRA', tag: 'warning' }
}
const ITEM_TYPE_MAP = {
  TABLE: '表',
  FIELD: '字段',
  ENUM: '枚举'
}

export default {
  name: 'GenConverge',
  data() {
    return {
      projects: [],
      projectId: '',
      running: false,
      loading: false,
      detailLoading: false,
      reports: [],
      latest: null,
      detail: { reportId: null, items: [] }
    }
  },
  computed: {
    convergeText() {
      if (!this.latest) return '—'
      const done = this.latest.okCount
      const total = this.latest.okCount + this.latest.missingCount
      return `${done} / ${total}`
    }
  },
  created() {
    this.loadProjects()
  },
  methods: {
    reportText(s) { return (REPORT_STATUS_MAP[s] || {}).text || s || '—' },
    reportTag(s) { return (REPORT_STATUS_MAP[s] || {}).tag || 'info' },
    kindText(k) { return (KIND_MAP[k] || {}).text || k || '—' },
    kindTag(k) { return (KIND_MAP[k] || {}).tag || 'info' },
    typeText(t) { return ITEM_TYPE_MAP[t] || t || '—' },
    loadProjects() {
      listProjectOptions().then(res => {
        const d = res.data || []
        this.projects = Array.isArray(d) ? d : (d.records || [])
        if (this.projects.length) {
          // 预选默认系统（沿用平台约定），取不到再回落第一个
          getDefaultProject().then(r => {
            const def = r.data
            this.projectId = def && this.projects.some(p => p.id === def.id) ? def.id : this.projects[0].id
            this.loadReports()
          }).catch(() => {
            this.projectId = this.projects[0].id
            this.loadReports()
          })
        }
      })
    },
    loadReports() {
      if (!this.projectId) return
      this.loading = true
      this.detail = { reportId: null, items: [] }
      convergeReports(this.projectId).then(res => {
        this.reports = res.data || []
        this.latest = this.reports.length ? this.reports[0] : null
      }).finally(() => { this.loading = false })
    },
    doRun() {
      this.running = true
      runConverge(this.projectId).then(res => {
        const d = res.data || {}
        this.$notify({
          title: '对账完成',
          message: `SPEC v${d.specVersion}：已实现 ${d.okCount} / 缺失 ${d.missingCount} / 多余 ${d.extraCount}，结论 ${d.status}`,
          type: d.status === 'CONVERGED' ? 'success' : 'warning',
          duration: 5000
        })
        this.loadReports()
      }).finally(() => { this.running = false })
    },
    pickReport(row) {
      if (row) this.showDetail(row)
    },
    showDetail(row) {
      this.detailLoading = true
      convergeReport(row.id).then(res => {
        const d = res.data || {}
        this.detail = { reportId: row.id, items: d.items || [] }
      }).finally(() => { this.detailLoading = false })
    }
  }
}
</script>

<style scoped>
.toolbar-card >>> .el-card__body { padding: 12px 14px; display: flex; align-items: center; }
.toolbar-label { font-size: 13px; color: var(--cg-text-secondary); margin-right: 8px; }
.proj-select { width: 240px; }
.run-btn { margin-left: auto; }
.stat-strip { display: flex; gap: 12px; margin-bottom: 14px; }
.stat { flex: 1; background: #fff; border: 1px solid var(--cg-border); border-radius: 6px; padding: 12px 16px; display: flex; align-items: center; gap: 12px; }
.stat .num { font-size: 24px; font-weight: 700; }
.stat .lbl { font-size: 12.5px; color: var(--cg-text-secondary); line-height: 1.6; }
.stat.ok .num { color: var(--cg-success); }
.stat.miss .num { color: var(--cg-danger); }
.stat.extra .num { color: var(--cg-warning); }
.stat.conv .num { color: var(--cg-primary); }
.table-card >>> .el-card__body { padding: 14px; }
.block-title { font-size: 13.5px; font-weight: 600; margin-bottom: 8px; }
.detail-wrap { margin-top: 16px; }
.num-danger { color: var(--cg-danger); font-weight: 600; }
.num-warning { color: var(--cg-warning); font-weight: 600; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12.5px; }
.callout { border-radius: 0 4px 4px 0; padding: 10px 14px; font-size: 12.5px; margin-bottom: 14px; }
.callout i { margin-right: 6px; }
.callout.warn { border-left: 3px solid var(--cg-warning); background: var(--cg-warning-bg); color: #6b4a15; }
.callout.ok { border-left: 3px solid var(--cg-success); background: var(--cg-success-bg); color: #1d5c3d; }
</style>
