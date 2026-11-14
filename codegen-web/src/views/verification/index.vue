<template>
  <div class="verification-page">
    <el-alert type="info" :closable="false" show-icon class="scope-alert">
      <span slot="title">
        V9.2 验证中心：把平台自身的验证资产（312+ 单测、i18n 快照契约、206 语言键、生成物 JS、前端构建、空库冒烟）
        收拢成可追溯的运行记录。业务接口用例请走「测试中心」菜单（/test）。
      </span>
    </el-alert>

    <!-- 总览 -->
    <div class="metric-cards">
      <div class="mcard">
        <div class="label">最近一次运行</div>
        <div class="val">
          <el-tag v-if="lastRun" :type="tagType(lastRun.status)" size="small">{{ lastRun.status }}</el-tag>
          <span v-else class="muted">暂无记录</span>
        </div>
        <div class="sub">{{ lastRun ? lastRun.runNo : '点击「运行选中套件」开始' }}</div>
      </div>
      <div class="mcard">
        <div class="label">通过率</div>
        <div class="val">{{ overview.passRate == null ? '-' : overview.passRate }}<small>%</small></div>
        <el-progress :percentage="Number(overview.passRate || 0)" :show-text="false" :stroke-width="5" />
      </div>
      <div class="mcard">
        <div class="label">失败套件</div>
        <div class="val" :class="{ bad: (overview.failed || 0) > 0 }">{{ overview.failed == null ? '-' : overview.failed }}</div>
        <div class="sub">跳过 {{ lastRun ? lastRun.skipped : 0 }} 个</div>
      </div>
      <div class="mcard">
        <div class="label">总耗时</div>
        <div class="val">{{ fmtMs(overview.durationMs) }}<small>s</small></div>
        <div class="sub mono">commit {{ overview.gitCommit || '-' }}</div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="primary" size="small" icon="el-icon-video-play" :loading="starting" :disabled="busy" @click="runSelected">
        运行选中套件（{{ selected.length }}）
      </el-button>
      <el-button size="small" icon="el-icon-refresh-right" :disabled="!lastFailedRunId || busy" @click="runFailedOnly">仅失败项</el-button>
      <el-button size="small" icon="el-icon-switch-button" :disabled="!busy" @click="cancelCurrent">取消运行</el-button>
      <el-button size="small" icon="el-icon-download" :disabled="!lastRun" @click="exportReport('html')">导出 HTML</el-button>
      <el-button size="small" icon="el-icon-document" :disabled="!lastRun" @click="exportReport('json')">导出 JSON</el-button>
      <div class="spacer" />
      <el-tag v-if="busy" type="warning" size="mini" effect="dark"><i class="el-icon-loading" /> 运行中，2s 自动刷新</el-tag>
      <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="fetchAll">刷新</el-button>
    </div>

    <el-row :gutter="14">
      <!-- 套件列表 -->
      <el-col :span="9">
        <el-card shadow="never" class="panel">
          <div slot="header" class="card-head">
            <span>检查套件</span>
            <small>命令只来自服务端注册表</small>
          </div>
          <el-checkbox-group v-model="selected" class="suite-list">
            <div v-for="s in suites" :key="s.id" class="suite-row">
              <el-checkbox :label="s.id" :disabled="busy">
                <span class="suite-id">{{ s.id }}</span> {{ s.name }}
              </el-checkbox>
              <div class="suite-meta">
                <el-tag size="mini" :type="s.level === 'P0' ? 'success' : 'info'">{{ s.level }}</el-tag>
                <el-tag v-if="s.lastStatus" size="mini" :type="tagType(s.lastStatus)">{{ s.lastStatus }}</el-tag>
                <span class="muted">{{ s.timeoutMinutes }}min</span>
              </div>
              <div class="suite-baseline mono">{{ s.baseline }}</div>
              <div v-if="s.dependsOn && s.dependsOn.length" class="suite-dep">依赖 {{ s.dependsOn.join(', ') }}</div>
            </div>
          </el-checkbox-group>
          <div class="panel-foot muted">S8 / S9 为 P1 重项，默认不勾选（本机内存有限，套件严格串行执行）</div>
        </el-card>
      </el-col>

      <!-- 运行历史 -->
      <el-col :span="15">
        <el-card shadow="never" class="panel">
          <div slot="header" class="card-head">
            <span>运行历史</span>
            <small>共 {{ total }} 次</small>
            <div class="spacer" />
            <el-select v-model="statusFilter" size="mini" clearable placeholder="状态筛选" style="width: 130px" @change="fetchRuns">
              <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
            </el-select>
          </div>
          <el-table :data="runs" size="small" v-loading="loading" @row-click="openDetail" highlight-current-row>
            <el-table-column prop="runNo" label="运行编号" width="165" />
            <el-table-column prop="triggerType" label="触发" width="88" />
            <el-table-column label="状态" width="105">
              <template slot-scope="{ row }"><el-tag size="mini" :type="tagType(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="通过" width="110">
              <template slot-scope="{ row }">{{ row.passed }}/{{ row.total }} <span v-if="row.skipped" class="muted">(跳{{ row.skipped }})</span></template>
            </el-table-column>
            <el-table-column label="耗时" width="90">
              <template slot-scope="{ row }">{{ fmtMs(row.durationMs) }}s</template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" min-width="140" />
            <el-table-column label="操作" width="76" fixed="right">
              <template slot-scope="{ row }">
                <el-button type="text" size="mini" @click.stop="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            background
            layout="prev, pager, next"
            :page-size="pageSize"
            :current-page="pageNum"
            :total="total"
            @current-change="onPageChange"
          />
        </el-card>

        <el-card shadow="never" class="panel trend-panel">
          <div slot="header" class="card-head"><span>通过率 / 耗时趋势</span><small>最近 {{ trends.length }} 次</small></div>
          <div v-if="!trends.length" class="muted">暂无已完成运行</div>
          <div v-for="t in trends" :key="t.runNo" class="trend-row">
            <span class="mono trend-no">{{ t.runNo }}</span>
            <el-progress :percentage="Number(t.passRate || 0)" :stroke-width="8" :show-text="false" class="trend-bar" />
            <span class="trend-val">{{ t.passRate }}%</span>
            <span class="muted">{{ fmtMs(t.durationMs) }}s</span>
            <el-tag size="mini" :type="tagType(t.status)">{{ t.status }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情抽屉 -->
    <el-drawer :visible.sync="drawerVisible" size="62%" :with-header="true" title="运行详情" :before-close="closeDetail">
      <div v-if="detail.run" class="detail-body">
        <div class="detail-head">
          <el-tag :type="tagType(detail.run.status)">{{ detail.run.status }}</el-tag>
          <b class="mono">{{ detail.run.runNo }}</b>
          <span class="muted">{{ detail.run.suiteIds }}</span>
          <div class="spacer" />
          <el-button size="mini" icon="el-icon-refresh-right" :disabled="busy" @click="rerunFailedFromDetail">重跑失败项</el-button>
          <el-button size="mini" icon="el-icon-download" @click="exportReport('html', detail.run.id)">导出报告</el-button>
        </div>

        <h4>用例结果</h4>
        <el-table :data="detail.cases" size="mini" border>
          <el-table-column prop="suiteId" label="套件" width="80" />
          <el-table-column label="状态" width="100">
            <template slot-scope="{ row }"><el-tag size="mini" :type="tagType(row.status)">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="耗时" width="80"><template slot-scope="{ row }">{{ fmtMs(row.durationMs) }}s</template></el-table-column>
          <el-table-column label="指标" min-width="150">
            <template slot-scope="{ row }"><code class="mono">{{ row.metricsJson }}</code></template>
          </el-table-column>
          <el-table-column label="失败摘要" min-width="160">
            <template slot-scope="{ row }"><span class="err">{{ row.errorExcerpt || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template slot-scope="{ row }">
              <el-button type="text" size="mini" @click="loadLog(row)">日志</el-button>
              <el-button type="text" size="mini" :disabled="busy" @click="rerunOne(row)">重跑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4>实时日志 <small class="muted">{{ logCaseId ? '单套件' : '全部' }} · 运行中每 2s 刷新</small></h4>
        <el-input v-model="logText" type="textarea" :rows="12" readonly class="log-box" />

        <h4>产物</h4>
        <div v-if="!detail.artifacts.length" class="muted">暂无产物</div>
        <el-button v-for="a in detail.artifacts" :key="a.id" size="mini" class="artifact-btn" icon="el-icon-document" @click="downloadOne(a)">
          {{ a.artifactType }} · {{ a.fileName }}
        </el-button>

        <h4>JSON 摘要（codegen.testcenter/v1）</h4>
        <pre class="summary-box">{{ detail.run.summaryJson || '(运行中，尚未生成)' }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import {
  getSuites, getOverview, triggerRun, getRuns, getRun, getRunLog,
  cancelRun, rerunCase, rerunFailed, getReport, getTrends, downloadArtifact
} from '@/api/verification'
import { Message } from 'element-ui'

const TERMINAL = ['PASSED', 'FAILED', 'CANCELLED', 'TIMEOUT']

export default {
  name: 'VerificationCenter',
  data() {
    return {
      suites: [],
      selected: [],
      overview: {},
      runs: [],
      trends: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      statusFilter: '',
      statusOptions: ['PASSED', 'FAILED', 'CANCELLED', 'TIMEOUT', 'RUNNING'],
      loading: false,
      starting: false,
      busy: false,
      drawerVisible: false,
      detail: { run: null, cases: [], artifacts: [] },
      logText: '',
      logCaseId: null,
      timer: null
    }
  },
  computed: {
    lastRun() {
      return this.overview.lastRun || null
    },
    lastFailedRunId() {
      const hit = this.runs.find(r => (r.failed || 0) > 0 && TERMINAL.indexOf(r.status) >= 0)
      return hit ? hit.id : null
    }
  },
  created() {
    this.fetchAll()
    this.timer = setInterval(() => {
      if (this.busy || (this.drawerVisible && this.detail.run && !TERMINAL.includes(this.detail.run.status))) {
        this.fetchAll(true)
      }
    }, 2000)
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    async fetchAll(silent) {
      if (!silent) this.loading = true
      try {
        const [suites, overview, runs, trends] = await Promise.all([
          getSuites(), getOverview(), this.fetchRuns(true), getTrends(20)
        ])
        this.suites = suites || []
        if (!this.selected.length && this.suites.length) {
          this.selected = this.suites.filter(s => s.defaultSelected).map(s => s.id)
        }
        this.overview = overview || {}
        this.trends = trends || []
        this.busy = !!(overview && overview.busy)
        if (this.drawerVisible && this.detail.run) {
          const fresh = await getRun(this.detail.run.id)
          this.detail = fresh
          this.loadLog({ id: this.logCaseId })
        }
      } catch (e) {
        if (!silent) this.$message.error(e.message || '加载失败')
      } finally {
        if (!silent) this.loading = false
      }
    },
    async fetchRuns(silent) {
      const params = { pageNum: this.pageNum, pageSize: this.pageSize }
      if (this.statusFilter) params.status = this.statusFilter
      const page = await getRuns(params)
      this.runs = (page && page.records) || []
      this.total = (page && page.total) || 0
      return page
    },
    onPageChange(p) {
      this.pageNum = p
      this.fetchRuns()
    },
    async runSelected() {
      if (!this.selected.length) {
        this.$message.warning('请至少勾选一个套件')
        return
      }
      this.starting = true
      try {
        const run = await triggerRun({ suiteIds: this.selected, trigger: 'MANUAL', gitCommit: this.overview.gitCommit || '' })
        Message.success('已触发运行 ' + run.runNo)
        this.busy = true
        await this.fetchAll()
        this.openDetail(run)
      } catch (e) {
        Message.error(e.message || '触发失败')
      } finally {
        this.starting = false
      }
    },
    async runFailedOnly() {
      if (!this.lastFailedRunId) return
      try {
        const run = await rerunFailed(this.lastFailedRunId)
        Message.success('已重跑失败项 ' + run.runNo)
        this.busy = true
        this.openDetail(run)
      } catch (e) {
        Message.error(e.message || '重跑失败')
      }
    },
    async rerunFailedFromDetail() {
      try {
        const run = await rerunFailed(this.detail.run.id)
        this.busy = true
        this.openDetail(run)
      } catch (e) {
        Message.error(e.message || '重跑失败')
      }
    },
    async rerunOne(row) {
      try {
        const run = await rerunCase(row.id)
        this.busy = true
        this.openDetail(run)
      } catch (e) {
        Message.error(e.message || '重跑失败')
      }
    },
    async cancelCurrent() {
      // 后端下发字符串（19 位雪花 ID 用 JSON number 会丢精度）
      const id = this.overview.activeRunId
      if (!id || id === '-1' || Number(id) < 0) return
      try {
        await cancelRun(id)
        Message.info('已发送取消请求')
      } catch (e) {
        Message.error(e.message || '取消失败')
      }
    },
    async openDetail(row) {
      const detail = await getRun(row.id || row)
      this.detail = detail
      this.logCaseId = null
      this.drawerVisible = true
      this.loadLog({ id: null })
    },
    closeDetail(done) {
      this.drawerVisible = false
      this.logCaseId = null
      this.logText = ''
      if (done) done()
    },
    async loadLog(row) {
      if (!this.detail.run) return
      this.logCaseId = row && row.id ? row.id : null
      try {
        this.logText = await getRunLog(this.detail.run.id, this.logCaseId, 200)
      } catch (e) {
        this.logText = '日志读取失败：' + (e.message || e)
      }
    },
    async exportReport(format, runId) {
      const id = runId || (this.lastRun && this.lastRun.id)
      if (!id) return
      try {
        const content = await getReport(id, format)
        const blob = new Blob([content], { type: format === 'html' ? 'text/html;charset=utf-8' : 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `testcenter-${id}.${format}`
        a.click()
        URL.revokeObjectURL(url)
      } catch (e) {
        Message.error(e.message || '导出失败')
      }
    },
    async downloadOne(a) {
      try {
        const resp = await downloadArtifact(a.id)
        const blob = new Blob([resp.data])
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = a.fileName
        link.click()
        URL.revokeObjectURL(url)
      } catch (e) {
        Message.error(e.message || '下载失败')
      }
    },
    tagType(status) {
      return { PASSED: 'success', FAILED: 'danger', TIMEOUT: 'warning', CANCELLED: 'info', SKIPPED: 'info', RUNNING: 'warning', QUEUED: 'info' }[status] || 'info'
    },
    fmtMs(ms) {
      return ms == null ? '-' : (ms / 1000).toFixed(1)
    }
  }
}
</script>

<style scoped>
.verification-page { padding: 14px; }
.scope-alert { margin-bottom: 12px; }
.metric-cards { display: flex; gap: 12px; margin-bottom: 12px; }
.mcard { flex: 1; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 14px; background: #fff; min-width: 0; }
.mcard .label { color: #909399; font-size: 12px; }
.mcard .val { font-size: 24px; font-weight: 600; margin: 6px 0 4px; height: 32px; line-height: 32px; }
.mcard .val small { font-size: 12px; font-weight: 400; margin-left: 2px; }
.mcard .val.bad { color: #f56c6c; }
.mcard .sub { color: #909399; font-size: 12px; }
.action-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.spacer { flex: 1; }
.panel { margin-bottom: 14px; }
.card-head { display: flex; align-items: center; gap: 10px; }
.card-head small { color: #909399; font-weight: 400; }
.suite-list { width: 100%; }
.suite-row { border-bottom: 1px solid #f2f3f5; padding: 9px 4px; }
.suite-row:last-child { border-bottom: none; }
.suite-id { font-family: Consolas, monospace; color: #409eff; font-weight: 600; }
.suite-meta { margin: 4px 0 2px 24px; display: flex; gap: 6px; align-items: center; }
.suite-baseline { margin-left: 24px; color: #606266; font-size: 12px; }
.suite-dep { margin-left: 24px; color: #e6a23c; font-size: 12px; }
.panel-foot { margin-top: 10px; font-size: 12px; line-height: 1.6; }
.muted { color: #909399; }
.mono { font-family: Consolas, monospace; }
.pager { margin-top: 12px; text-align: right; }
.trend-row { display: flex; align-items: center; gap: 10px; padding: 4px 0; font-size: 12px; }
.trend-no { width: 145px; }
.trend-bar { flex: 1; }
.trend-val { width: 46px; text-align: right; }
.detail-body { padding: 0 18px 28px; }
.detail-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.detail-body h4 { margin: 18px 0 8px; font-size: 14px; }
.detail-body h4 small { font-weight: 400; margin-left: 8px; }
.log-box /deep/ textarea { font-family: Consolas, monospace; font-size: 12px; background: #1f2937; color: #e5e7eb; border: none; }
.summary-box { background: #f8fafc; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; max-height: 220px; overflow: auto; font-size: 12px; }
.err { color: #f56c6c; font-size: 12px; }
.artifact-btn { margin: 0 6px 6px 0; }
code { font-size: 11px; }
</style>
