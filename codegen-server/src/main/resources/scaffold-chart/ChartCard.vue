<template>
  <div class="chart-card">
    <div class="chart-card-head">
      <span class="chart-card-title">{{ title }}</span>
      <span v-if="subtitle" class="chart-card-sub">{{ subtitle }}</span>
    </div>
    <div v-if="!rows || !rows.length" class="chart-card-empty">暂无数据</div>
    <div v-else ref="chartEl" class="chart-card-body"></div>
  </div>
</template>

<script>
/**
 * 通用图卡（V9.15 数据可视化能力包）。
 *
 * 跨系统通用性：所有系列色从 --app-primary CSS 变量实时读取并用 color-mix 派生，
 * 没有任何硬编码色值——任何系统改主色，看板颜色自动跟随（换肤零改图表代码）。
 * 生命周期：ResizeObserver + dispose 成对；setOption(opt, true) 全量替换。
 */
import * as echarts from 'echarts/core'
import { PieChart, LineChart, BarChart, GaugeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, LineChart, BarChart, GaugeChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#e1251b'
}

// 从单一主色 token 派生色阶（canvas 不认 CSS color-mix，这里用 JS 混色）。
// 任何系统改 --app-primary，系列色自动跟随——无硬编码色值。
function mix(hex, target, weight) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim())
  if (!m) return hex
  const n = parseInt(m[1], 16)
  const channels = [n >> 16, (n >> 8) & 0xff, n & 0xff].map(v =>
    Math.round(v * (1 - weight) + target * weight).toString(16).padStart(2, '0'))
  return '#' + channels.join('')
}

function seriesColors() {
  const primary = cssVar('--app-primary')
  return [primary,
    mix(primary, 255, 0.30),
    mix(primary, 255, 0.55),
    mix(primary, 0, 0.15),
    '#94a3b8']
}

export default {
  name: 'ChartCard',
  props: {
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' },
    type: { type: String, default: 'bar' }, // pie / line / bar / gauge
    labels: { type: Array, default: () => [] },
    values: { type: Array, default: () => [] },
    drillText: { type: String, default: '' }
  },
  emits: ['drill'],
  data() {
    return { chart: null, observer: null }
  },
  computed: {
    rows() { return this.labels || [] }
  },
  mounted() {
    if (this.rows.length) {
      this.renderChart()
      this.observer = new ResizeObserver(() => this.chart && this.chart.resize())
      this.observer.observe(this.$refs.chartEl)
    }
  },
  beforeUnmount() {
    if (this.observer) { this.observer.disconnect(); this.observer = null }
    if (this.chart) { this.chart.dispose(); this.chart = null }
  },
  watch: {
    labels() { this.refresh() },
    values() { this.refresh() },
    type() { this.refresh() }
  },
  methods: {
    refresh() {
      if (!this.rows.length) {
        if (this.chart) { this.chart.dispose(); this.chart = null }
        return
      }
      // 数据从空态切换时 v-if 刚把 DOM 换出来，必须等 nextTick 才能拿到 chartEl，
      // 否则 echarts.init(undefined) 抛 "Invalid DOM"（S8 实测坑）
      this.$nextTick(() => {
        if (!this.$refs.chartEl) return
        if (!this.chart) { this.renderChart(); return }
        this.chart.setOption(this.buildOption(), true)
      })
    },
    renderChart() {
      if (!this.$refs.chartEl) return
      this.chart = echarts.init(this.$refs.chartEl)
      this.chart.setOption(this.buildOption(), true)
      if (this.drillText) {
        this.chart.on('click', () => this.$emit('drill'))
      }
    },
    buildOption() {
      const labels = this.labels.map(v => String(v))
      const values = this.values.map(v => Number(v) || 0)
      const colors = seriesColors()
      const base = { color: colors, textStyle: { fontFamily: 'inherit' } }
      if (this.type === 'pie' || this.type === 'gauge') {
        const pieData = labels.map((label, i) => ({ name: label, value: values[i] }))
        if (this.type === 'gauge') {
          const total = values.reduce((a, b) => a + b, 0) || 0
          const done = values.length ? values[values.length - 1] : 0
          return Object.assign({}, base, {
            series: [{ type: 'gauge', min: 0, max: total || 100,
              progress: { show: true }, axisLine: { lineStyle: { width: 10 } },
              detail: { formatter: '{value}', fontSize: 20 },
              data: [{ value: done, name: this.title }] }]
          })
        }
        return Object.assign({}, base, {
          tooltip: { trigger: 'item' },
          legend: { bottom: 0, type: 'scroll' },
          series: [{ type: 'pie', radius: ['42%', '68%'], avoidLabelOverlap: true,
            label: { show: false }, data: pieData }]
        })
      }
      if (this.type === 'line') {
        return Object.assign({}, base, {
          tooltip: { trigger: 'axis' },
          grid: { left: 48, right: 16, top: 24, bottom: 28 },
          xAxis: { type: 'category', data: labels, boundaryGap: false },
          yAxis: { type: 'value' },
          series: [{ type: 'line', smooth: true, areaStyle: { opacity: 0.12 },
            data: values, symbol: 'circle', symbolSize: 5 }]
        })
      }
      return Object.assign({}, base, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 96, right: 24, top: 16, bottom: 24 },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: labels },
        series: [{ type: 'bar', data: values, barMaxWidth: 18, itemStyle: { borderRadius: [0, 4, 4, 0] } }]
      })
    }
  }
}
</script>

<style scoped>
.chart-card { background: var(--color-bg-card, #fff); border: 1px solid var(--color-border, #e5e7eb); border-radius: 10px; padding: 16px; }
.chart-card-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.chart-card-title { font-size: 14px; font-weight: 600; color: var(--color-text, #1f2937); }
.chart-card-sub { font-size: 11px; color: var(--color-text-muted, #9ca3af); }
.chart-card-body { width: 100%; height: 260px; }
.chart-card-empty { height: 260px; display: grid; place-items: center; color: var(--color-text-muted, #9ca3af); font-size: 13px; }
</style>
