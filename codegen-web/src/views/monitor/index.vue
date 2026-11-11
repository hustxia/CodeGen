<template>
  <div class="monitor-page">
    <!-- 采样停滞（F-16）：超过 3 个采样周期没有新样本才出现 -->
    <el-alert v-if="stalled" type="error" :closable="false" show-icon class="stall-alert">
      <span slot="title">
        采样停滞：最近一次采样在 {{ lastSampleText }}，已超过 15 秒未更新。请检查后端调度线程或查看服务日志。
      </span>
    </el-alert>

    <!-- 状态条 -->
    <div class="status-bar">
      <i class="pulse" :class="{ off: !overview.sampling }" />
      <span class="st-text">
        <b>{{ overview.sampling ? '采样正常' : '采样停滞' }}</b>
        <em>·</em> 每 {{ intervalText }}一次
        <em>·</em> 最近采样 <span class="mono">{{ lastSampleText }}</span>
      </span>
      <el-tag size="mini" type="info">已采样 {{ overview.sampledPoints }} / {{ overview.bufferSize }} 点</el-tag>
      <el-tag size="mini" type="warning">仅进程内 30 分钟 · 重启归零 · 多实例不共享</el-tag>
      <div class="spacer" />
      <span class="uptime">JVM 当前进程 · UP {{ uptimeText }}</span>
      <el-button size="mini" icon="el-icon-refresh" :loading="loading" @click="fetchAll">立即刷新</el-button>
    </div>

    <!-- 4 张指标卡 -->
    <div class="metric-cards">
      <div class="mcard">
        <div class="label"><span class="ico blue">堆</span>堆内存使用率</div>
        <div class="val">{{ fmtPct(overview.jvm.heapUsedRatio) }}<small>%</small></div>
        <div class="sub">已用 {{ fmtMb(overview.jvm.heapUsedMb) }} / 上限 {{ fmtMb(overview.jvm.heapMaxMb) }}</div>
        <el-progress :percentage="heapPercent" :show-text="false" :stroke-width="5" :color="heapColor" />
      </div>
      <div class="mcard">
        <div class="label"><span class="ico green">线</span>活跃线程</div>
        <div class="val">{{ overview.jvm.threadsLive }}<small>个</small></div>
        <div class="sub">GC {{ overview.jvm.gcCount }} 次 · 累计暂停 {{ fmtMs(overview.jvm.gcPauseMs) }}</div>
        <div class="foot">CPU {{ fmtPct(overview.jvm.cpuUsage / 100) }}% · {{ overview.jvm.cpuCount }} 核</div>
      </div>
      <div class="mcard">
        <div class="label"><span class="ico orange">流</span>区间 QPS</div>
        <div class="val">{{ overview.http.qps.toFixed(1) }}<small>req/s</small></div>
        <div class="sub">最近区间 {{ overview.http.requests }} 次请求</div>
        <div class="foot">近 5 分钟非 heap 内存 {{ fmtMb(overview.jvm.nonHeapUsedMb) }}</div>
      </div>
      <div class="mcard">
        <div class="label"><span class="ico red">耗</span>平均响应耗时</div>
        <div class="val">{{ fmtNum(overview.http.avgMs) }}<small>ms</small></div>
        <div class="sub">P95 {{ fmtMs(overview.http.p95Ms) }} · P99 {{ fmtMs(overview.http.p99Ms) }}</div>
        <div class="foot">
          <span :class="{ bad: overview.http.errors5xx > 0 }">4xx {{ overview.http.errors4xx }} · 5xx {{ overview.http.errors5xx }}</span>
        </div>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="chart-grid">
      <el-card shadow="never" class="chart-card">
        <div slot="header" class="card-head">
          <span>JVM 趋势</span>
          <small>近 30 分钟 · {{ intervalText }}采样</small>
          <div class="legend">
            <span><i class="dot blue" />堆内存 (MB)</span>
            <span><i class="dot green" />活跃线程</span>
          </div>
        </div>
        <div v-if="chartError" class="chart-fallback">图表组件加载失败，指标卡与表格仍可用</div>
        <div v-else ref="jvmChart" class="chart" />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <div slot="header" class="card-head">
          <span>HTTP 趋势</span>
          <small>近 30 分钟</small>
          <div class="legend">
            <span><i class="dot orange" />QPS</span>
            <span><i class="dot red" />P95 (ms)</span>
          </div>
        </div>
        <div v-if="chartError" class="chart-fallback">图表组件加载失败，指标卡与表格仍可用</div>
        <div v-else ref="httpChart" class="chart" />
      </el-card>
    </div>

    <!-- 慢接口 -->
    <el-card shadow="never" class="slow-card">
      <div slot="header" class="card-head">
        <span>慢接口 Top 10</span>
        <small>按区间平均耗时排序 · URI 为 Micrometer 模板</small>
        <div class="spacer" />
        <el-tag v-if="http.warmup" size="mini" type="info">数据积累中</el-tag>
        <el-tag size="mini" :type="http.errors5xx > 0 ? 'danger' : 'success'">
          区间 4xx {{ http.errors4xx }} · 5xx {{ http.errors5xx }}
        </el-tag>
      </div>
      <el-table v-loading="loading" :data="http.topSlow" size="small" border>
        <el-table-column prop="uri" label="URI" min-width="300">
          <template slot-scope="{ row }"><span class="mono">{{ row.uri }}</span></template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="90">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="methodType(row.method)">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="次数" width="90" />
        <el-table-column label="平均耗时" width="130">
          <template slot-scope="{ row }">
            <span class="num" :class="slowClass(row.avgMs)">{{ fmtMs(row.avgMs) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最大耗时" width="130">
          <template slot-scope="{ row }"><span class="num">{{ fmtMs(row.maxMs) }}</span></template>
        </el-table-column>
        <template slot="empty">当前窗口还没有请求记录</template>
      </el-table>
    </el-card>

    <!-- 业务指标 -->
    <el-card shadow="never" class="biz-card">
      <div slot="header" class="card-head">
        <span>业务指标</span>
        <small>近 30 分钟 · 仅统计本进程</small>
        <div class="spacer" />
        <el-tag v-if="business.warmup" size="mini" type="info">数据积累中</el-tag>
      </div>
      <div class="biz-grid">
        <div class="bcard">
          <div class="t">生成批次</div>
          <div class="donut" :style="donutStyle">
            <div class="inner"><b>{{ fmtPct(business.gen.successRate / 100) }}%</b><span>成功率</span></div>
          </div>
          <div class="kv"><span>批次总数</span><b>{{ business.gen.batches }}</b></div>
          <div class="kv"><span>成功 / 失败</span><b>{{ business.gen.success }} / <span class="bad">{{ business.gen.failed }}</span></b></div>
          <div class="kv"><span>平均耗时</span><b>{{ fmtMs(business.gen.avgMs) }}</b></div>
        </div>

        <div class="bcard">
          <div class="t">写盘文件数</div>
          <div class="big">{{ business.gen.writeFileTotal }} <small>个文件</small></div>
          <div class="stack">
            <i
              v-for="item in writeFileEntries"
              :key="item.status"
              :style="{ width: item.percent + '%', background: item.color }"
            />
          </div>
          <div v-for="item in writeFileEntries" :key="'kv-' + item.status" class="kv">
            <span>{{ item.status }}</span><b>{{ item.value }}</b>
          </div>
          <div v-if="!writeFileEntries.length" class="empty">暂无写盘记录</div>
        </div>

        <div class="bcard">
          <div class="t">流水线状态分布</div>
          <div class="big">{{ business.pipeline.runs }} <small>次运行</small></div>
          <div class="stack">
            <i :style="{ width: pipelinePercent('success') + '%', background: '#0f9d58' }" />
            <i :style="{ width: pipelinePercent('failed') + '%', background: '#e5484d' }" />
            <i :style="{ width: pipelinePercent('stopped') + '%', background: '#9fa8bd' }" />
          </div>
          <div class="kv"><span>成功</span><b>{{ business.pipeline.success }}</b></div>
          <div class="kv"><span>失败</span><b>{{ business.pipeline.failed }}</b></div>
          <div class="kv"><span>已停止</span><b>{{ business.pipeline.stopped }}</b></div>
          <div class="steps">
            <div v-for="step in topSteps" :key="step.step">
              <span class="name" :title="step.step">{{ step.step }}</span>
              <span class="track"><i :style="{ width: stepWidth(step) }" /></span>
              <span class="ms">{{ fmtMs(step.avgMs) }}</span>
            </div>
          </div>
        </div>

        <div class="bcard">
          <div class="t">文档转换（LibreOffice）</div>
          <div class="big">{{ business.doc.hit + business.doc.converted + business.doc.failed }} <small>次</small></div>
          <div class="kv" style="margin-top: 8px"><span>缓存命中 hit</span><b>{{ business.doc.hit }}</b></div>
          <div class="kv"><span>实际转换 converted</span><b>{{ business.doc.converted }}</b></div>
          <div class="kv"><span>失败 failed</span><b class="bad">{{ business.doc.failed }}</b></div>
          <div class="kv"><span>平均转换耗时</span><b>{{ fmtMs(business.doc.avgMs) }}</b></div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getMonitorOverview, getMonitorSeries, getMonitorHttp, getMonitorBusiness } from '@/api/monitor'

const POLL_MS = 5000
const STALL_MS = 15000

// ECharts 按需引入 + 异步 chunk：不进主包，加载失败只降级趋势区
let echartsLoader = null
function loadEcharts() {
  if (!echartsLoader) {
    echartsLoader = Promise.all([
      import(/* webpackChunkName: "echarts" */ 'echarts/core'),
      import(/* webpackChunkName: "echarts" */ 'echarts/charts'),
      import(/* webpackChunkName: "echarts" */ 'echarts/components'),
      import(/* webpackChunkName: "echarts" */ 'echarts/renderers')
    ]).then(([core, charts, components, renderers]) => {
      const echarts = core.default || core
      echarts.use([
        charts.LineChart,
        components.GridComponent,
        components.TooltipComponent,
        components.LegendComponent,
        renderers.CanvasRenderer
      ])
      return echarts
    }).catch(err => {
      echartsLoader = null
      throw err
    })
  }
  return echartsLoader
}

function emptyOverview() {
  return {
    sampling: false,
    lastSampleAt: null,
    intervalMs: 5000,
    bufferSize: 360,
    sampledPoints: 0,
    jvm: {
      heapUsedMb: 0, heapMaxMb: 0, heapUsedRatio: 0, nonHeapUsedMb: 0, threadsLive: 0,
      gcCount: 0, gcPauseMs: 0, cpuUsage: 0, cpuCount: 0, uptimeSeconds: 0
    },
    http: { qps: 0, avgMs: 0, p95Ms: 0, p99Ms: 0, requests: 0, errors4xx: 0, errors5xx: 0 }
  }
}

function emptyHttp() {
  return { warmup: false, totalRequests: 0, qps: 0, avgMs: 0, p95Ms: 0, p99Ms: 0, errors4xx: 0, errors5xx: 0, topSlow: [] }
}

function emptyBusiness() {
  return {
    warmup: false,
    gen: { batches: 0, success: 0, failed: 0, successRate: 0, avgMs: 0, writeFileTotal: 0, writeFiles: {} },
    pipeline: { runs: 0, success: 0, failed: 0, stopped: 0, steps: [] },
    doc: { hit: 0, converted: 0, failed: 0, avgMs: 0 }
  }
}

const WRITE_COLORS = { WRITTEN: '#0f9d58', MERGED: '#3d6df5', SKIPPED: '#d97706', ERROR: '#e5484d' }

export default {
  name: 'SystemMonitor',
  data() {
    return {
      loading: false,
      stalled: false,
      chartError: false,
      timer: null,
      echarts: null,
      charts: { jvm: null, http: null },
      overview: emptyOverview(),
      http: emptyHttp(),
      business: emptyBusiness(),
      series: { heap: [], threads: [], qps: [], p95: [] }
    }
  },
  computed: {
    intervalText() {
      return Math.round((this.overview.intervalMs || 5000) / 1000) + ' 秒'
    },
    lastSampleText() {
      const ts = this.overview.lastSampleAt
      return ts ? this.fmtClock(ts) : '尚未采样'
    },
    uptimeText() {
      return this.fmtUptime(this.overview.jvm.uptimeSeconds)
    },
    heapPercent() {
      return Math.min(100, Math.round(this.overview.jvm.heapUsedRatio * 1000) / 10)
    },
    heapColor() {
      if (this.heapPercent >= 90) return '#e5484d'
      if (this.heapPercent >= 75) return '#d97706'
      return '#3d6df5'
    },
    donutStyle() {
      const rate = Math.max(0, Math.min(100, this.business.gen.successRate || 0))
      return { background: `conic-gradient(#0f9d58 0 ${rate}%, #f2f4f9 ${rate}% 100%)` }
    },
    writeFileEntries() {
      const files = this.business.gen.writeFiles || {}
      const total = this.business.gen.writeFileTotal || 0
      return Object.keys(files).map(status => {
        const value = files[status] || 0
        return {
          status,
          value,
          percent: total > 0 ? Math.max(1, Math.round(value * 100 / total)) : 0,
          color: WRITE_COLORS[status] || '#9fa8bd'
        }
      })
    },
    topSteps() {
      return (this.business.pipeline.steps || []).slice(0, 4)
    },
    stepMax() {
      return Math.max(1, ...this.topSteps.map(s => s.avgMs || 0))
    }
  },
  mounted() {
    document.addEventListener('visibilitychange', this.handleVisibility)
    window.addEventListener('resize', this.handleResize)
    loadEcharts()
      .then(echarts => {
        this.echarts = echarts
        this.$nextTick(() => {
          this.initCharts()
          this.renderCharts()
        })
      })
      .catch(() => {
        this.chartError = true
      })
    this.fetchAll()
    this.startTimer()
  },
  beforeDestroy() {
    this.stopTimer()
    document.removeEventListener('visibilitychange', this.handleVisibility)
    window.removeEventListener('resize', this.handleResize)
    Object.keys(this.charts).forEach(key => {
      if (this.charts[key]) {
        this.charts[key].dispose()
        this.charts[key] = null
      }
    })
  },
  methods: {
    startTimer() {
      this.stopTimer()
      this.timer = setInterval(this.fetchAll, POLL_MS)
    },
    stopTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    handleVisibility() {
      if (document.hidden) {
        this.stopTimer()
      } else {
        this.fetchAll()
        this.startTimer()
      }
    },
    handleResize() {
      Object.keys(this.charts).forEach(key => {
        if (this.charts[key]) {
          this.charts[key].resize()
        }
      })
    },
    async fetchAll() {
      if (this.loading) return
      this.loading = true
      try {
        const [overview, heap, threads, qps, p95, http, business] = await Promise.all([
          getMonitorOverview().catch(() => null),
          getMonitorSeries('jvm.heap').catch(() => null),
          getMonitorSeries('jvm.threads').catch(() => null),
          getMonitorSeries('http.qps').catch(() => null),
          getMonitorSeries('http.p95').catch(() => null),
          getMonitorHttp(30, 10).catch(() => null),
          getMonitorBusiness(30).catch(() => null)
        ])
        if (overview) {
          this.overview = overview
        }
        if (heap) this.series.heap = heap.points || []
        if (threads) this.series.threads = threads.points || []
        if (qps) this.series.qps = qps.points || []
        if (p95) this.series.p95 = p95.points || []
        if (http) this.http = http
        if (business) this.business = business

        // 刚启动 30s 内本来就没有样本，不算停滞
        this.stalled = !this.overview.sampling &&
          (this.overview.sampledPoints > 0 || this.overview.jvm.uptimeSeconds > 30)
        this.renderCharts()
      } finally {
        this.loading = false
      }
    },
    initCharts() {
      if (!this.echarts) return
      if (!this.charts.jvm && this.$refs.jvmChart) {
        this.charts.jvm = this.echarts.init(this.$refs.jvmChart)
      }
      if (!this.charts.http && this.$refs.httpChart) {
        this.charts.http = this.echarts.init(this.$refs.httpChart)
      }
    },
    renderCharts() {
      if (!this.echarts) return
      this.initCharts()
      const timeAxis = {
        type: 'time',
        axisLine: { lineStyle: { color: '#e5e9f2' } },
        axisLabel: { color: '#9fa8bd', fontSize: 10 }
      }

      if (this.charts.jvm) {
        this.charts.jvm.setOption({
          tooltip: { trigger: 'axis' },
          grid: { left: 44, right: 44, top: 18, bottom: 28 },
          xAxis: timeAxis,
          yAxis: [
            { type: 'value', name: 'MB', nameTextStyle: { color: '#9fa8bd', fontSize: 10 }, axisLabel: { color: '#9fa8bd', fontSize: 10 }, splitLine: { lineStyle: { color: '#f2f4f9' } } },
            { type: 'value', name: '线程', nameTextStyle: { color: '#9fa8bd', fontSize: 10 }, axisLabel: { color: '#9fa8bd', fontSize: 10 }, splitLine: { show: false } }
          ],
          series: [
            { name: '堆内存 (MB)', type: 'line', showSymbol: false, yAxisIndex: 0, itemStyle: { color: '#3d6df5' }, data: this.toPoints(this.series.heap) },
            { name: '活跃线程', type: 'line', showSymbol: false, yAxisIndex: 1, itemStyle: { color: '#0f9d58' }, data: this.toPoints(this.series.threads) }
          ]
        }, true)
      }

      if (this.charts.http) {
        this.charts.http.setOption({
          tooltip: { trigger: 'axis' },
          grid: { left: 44, right: 44, top: 18, bottom: 28 },
          xAxis: timeAxis,
          yAxis: [
            { type: 'value', name: 'req/s', nameTextStyle: { color: '#9fa8bd', fontSize: 10 }, axisLabel: { color: '#9fa8bd', fontSize: 10 }, splitLine: { lineStyle: { color: '#f2f4f9' } } },
            { type: 'value', name: 'ms', nameTextStyle: { color: '#9fa8bd', fontSize: 10 }, axisLabel: { color: '#9fa8bd', fontSize: 10 }, splitLine: { show: false } }
          ],
          series: [
            { name: 'QPS', type: 'line', showSymbol: false, yAxisIndex: 0, itemStyle: { color: '#d97706' }, data: this.toPoints(this.series.qps) },
            { name: 'P95 (ms)', type: 'line', showSymbol: false, yAxisIndex: 1, itemStyle: { color: '#e5484d' }, data: this.toPoints(this.series.p95) }
          ]
        }, true)
      }
    },
    toPoints(points) {
      return (points || []).map(p => [p.t, p.v])
    },
    pipelinePercent(key) {
      const runs = this.business.pipeline.runs || 0
      if (!runs) return 0
      return Math.round((this.business.pipeline[key] || 0) * 100 / runs)
    },
    stepWidth(step) {
      return Math.max(4, Math.round((step.avgMs || 0) * 100 / this.stepMax)) + '%'
    },
    methodType(method) {
      if (method === 'POST') return 'primary'
      if (method === 'PUT' || method === 'PATCH') return 'warning'
      if (method === 'DELETE') return 'danger'
      return 'success'
    },
    slowClass(ms) {
      if (ms >= 3000) return 'bad'
      if (ms >= 1000) return 'warn'
      return ''
    },
    fmtPct(ratio) {
      const v = (Number(ratio) || 0) * 100
      return v.toFixed(1)
    },
    fmtNum(v) {
      return Math.round(Number(v) || 0)
    },
    fmtMb(v) {
      return Math.round(Number(v) || 0) + ' MB'
    },
    fmtMs(ms) {
      const v = Number(ms) || 0
      if (v >= 1000) return (v / 1000).toFixed(2) + ' s'
      return Math.round(v) + ' ms'
    },
    fmtClock(ts) {
      const d = new Date(ts)
      const p = n => (n < 10 ? '0' + n : '' + n)
      return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
    },
    fmtUptime(seconds) {
      const total = Math.max(0, Math.round(Number(seconds) || 0))
      const days = Math.floor(total / 86400)
      const hours = Math.floor((total % 86400) / 3600)
      const minutes = Math.floor((total % 3600) / 60)
      const p = n => (n < 10 ? '0' + n : '' + n)
      return (days > 0 ? days + ' 天 ' : '') + p(hours) + ':' + p(minutes)
    }
  }
}
</script>

<style scoped>
.monitor-page {
  padding: 16px 20px 24px;
  background: #f3f5fa;
  min-height: calc(100vh - 84px);
}

.stall-alert {
  margin-bottom: 12px;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  padding: 10px 16px;
  margin-bottom: 14px;
  font-size: 13px;
  color: #5a6478;
}

.status-bar .pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0f9d58;
  box-shadow: 0 0 0 4px rgba(15, 157, 88, 0.14);
}

.status-bar .pulse.off {
  background: #9fa8bd;
  box-shadow: 0 0 0 4px rgba(159, 168, 189, 0.16);
}

.status-bar .st-text em {
  font-style: normal;
  color: #c3cad8;
  margin: 0 2px;
}

.status-bar .uptime {
  color: #9fa8bd;
  font-size: 12px;
}

.spacer {
  flex: 1;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.metric-cards .mcard {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  padding: 14px 16px 12px;
}

.metric-cards .label {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: #7c87a3;
}

.metric-cards .ico {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.metric-cards .ico.blue { background: #e8effd; color: #3d6df5; }
.metric-cards .ico.green { background: #e8f7ef; color: #0f9d58; }
.metric-cards .ico.orange { background: #fff4e6; color: #d97706; }
.metric-cards .ico.red { background: #fdecec; color: #e5484d; }

.metric-cards .val {
  font-size: 25px;
  font-weight: 700;
  margin: 8px 0 2px;
  color: #1f2d50;
}

.metric-cards .val small {
  font-size: 12px;
  font-weight: 500;
  color: #9fa8bd;
  margin-left: 3px;
}

.metric-cards .sub {
  font-size: 11.5px;
  color: #9fa8bd;
  margin-bottom: 8px;
  min-height: 16px;
}

.metric-cards .foot {
  font-size: 11.5px;
  color: #9fa8bd;
  margin-top: 8px;
}

.metric-cards .foot .bad {
  color: #e5484d;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d50;
}

.card-head small {
  font-size: 11.5px;
  font-weight: 400;
  color: #9fa8bd;
}

.card-head .legend {
  margin-left: auto;
  display: flex;
  gap: 14px;
  font-size: 11.5px;
  font-weight: 400;
  color: #7c87a3;
}

.card-head .legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.card-head .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.card-head .dot.blue { background: #3d6df5; }
.card-head .dot.green { background: #0f9d58; }
.card-head .dot.orange { background: #d97706; }
.card-head .dot.red { background: #e5484d; }

.chart {
  height: 220px;
}

.chart-fallback {
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9fa8bd;
  font-size: 13px;
  background: #fafbfe;
  border-radius: 6px;
}

.slow-card,
.biz-card {
  margin-bottom: 14px;
}

.num {
  font-family: Consolas, Monaco, monospace;
}

.num.warn { color: #d97706; }
.num.bad { color: #e5484d; }

.biz-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.bcard {
  border: 1px solid #eef1f7;
  border-radius: 8px;
  padding: 12px 14px;
  min-height: 190px;
}

.bcard .t {
  font-size: 12.5px;
  color: #7c87a3;
  margin-bottom: 10px;
}

.bcard .big {
  font-size: 21px;
  font-weight: 700;
  color: #1f2d50;
  margin-bottom: 8px;
}

.bcard .big small {
  font-size: 11px;
  color: #9fa8bd;
  font-weight: 500;
}

.bcard .kv {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #5a6478;
  padding: 3px 0;
}

.bcard .kv b {
  color: #1f2d50;
  font-weight: 600;
}

.bcard .kv b.bad {
  color: #e5484d;
}

.bcard .empty {
  font-size: 12px;
  color: #9fa8bd;
}

.donut {
  width: 86px;
  height: 86px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 2px auto 8px;
}

.donut .inner {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.donut .inner b {
  font-size: 16px;
  color: #1f2d50;
}

.donut .inner span {
  font-size: 10px;
  color: #9fa8bd;
}

.stack {
  display: flex;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  margin: 6px 0 8px;
  background: #f2f4f9;
}

.stack i {
  display: block;
  height: 100%;
}

.steps {
  margin-top: 8px;
  border-top: 1px dashed #eef1f7;
  padding-top: 8px;
}

.steps > div {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: #5a6478;
  margin-bottom: 5px;
}

.steps .name {
  width: 104px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.steps .track {
  flex: 1;
  height: 6px;
  background: #eef1f7;
  border-radius: 3px;
  overflow: hidden;
}

.steps .track i {
  display: block;
  height: 100%;
  background: #3d6df5;
  border-radius: 3px;
}

.steps .ms {
  width: 48px;
  text-align: right;
  font-family: Consolas, Monaco, monospace;
  color: #7c87a3;
}

@media (max-width: 1400px) {
  .metric-cards,
  .biz-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
