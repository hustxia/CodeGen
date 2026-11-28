<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">行业护栏规则库</span>
      <span class="page-desc">
        M2.1：4 行业 × 7 条 = 28 条规则。规则存表可启停，命中时原样展示「为什么拦 + 怎么做才对」。
      </span>
    </div>

    <el-card shadow="never" class="filter-card">
      <span class="label">行业</span>
      <el-select v-model="industry" size="small" clearable placeholder="全部行业" class="sel">
        <el-option v-for="(label, key) in INDUSTRY" :key="key" :label="label" :value="key" />
      </el-select>
      <span class="label">类别</span>
      <el-select v-model="category" size="small" clearable placeholder="全部类别" class="sel">
        <el-option v-for="(label, key) in CATEGORY" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索编码 / 标题" class="kw" />
      <div class="count">
        规则总数 <b>{{ counts.total || 0 }}</b>
        <span class="hint">（村务 / 城管 / 种植 / 园区 各 7 条）</span>
      </div>
      <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
    </el-card>

    <el-card shadow="never">
      <el-table :data="filtered" size="small" v-loading="loading" border>
        <el-table-column prop="ruleCode" label="编码" width="100" />
        <el-table-column label="行业" width="100">
          <template slot-scope="s">{{ INDUSTRY[s.row.industry] || s.row.industry }}</template>
        </el-table-column>
        <el-table-column label="类别" width="104">
          <template slot-scope="s">
            <el-tag size="mini" :type="categoryTag(s.row.category)">{{ CATEGORY[s.row.category] || s.row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="76">
          <template slot-scope="s">
            <el-tag size="mini" :type="severityTag(s.row.severity)">{{ SEVERITY[s.row.severity] || s.row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发类型" width="92" />
        <el-table-column prop="triggerExpr" label="触发条件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="title" label="规则标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="怎么做才对" min-width="220" show-overflow-tooltip />
        <el-table-column label="启用" width="70">
          <template slot-scope="s">
            <el-tag size="mini" :type="s.row.enabled === 1 ? 'success' : 'info'">{{ s.row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template slot-scope="s">
            <el-button type="text" size="mini" @click="showDetail(s.row)">解释</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="规则解释" :visible.sync="detailVisible" width="560px">
      <template v-if="current">
        <p class="dtitle">{{ current.ruleCode }} · {{ current.title }}</p>
        <p class="dsec">为什么拦</p>
        <p class="dtext">{{ current.reason }}</p>
        <p class="dsec">怎么做才对</p>
        <p class="dtext ok">{{ current.suggestion }}</p>
        <p class="dsec">触发表达式</p>
        <p class="dtext mono">{{ current.triggerExpr || '（澄清类规则，不参与检测）' }}</p>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listRules, ruleCount } from '@/api/guardrail'

export default {
  name: 'GuardrailRules',
  data() {
    return {
      INDUSTRY: {
        gov_affairs: '村务治理', cm_city: '城市管理', agr_crop: '智慧种植', park_park: '智慧园区'
      },
      CATEGORY: {
        BLACKLIST: '黑名单', WHITELIST: '白名单提示', CONFLICT: '冲突检测',
        BOUNDARY: '边界提示', CLARIFY: '澄清问题'
      },
      SEVERITY: { BLOCK: '拦截', WARN: '提示', INFO: '信息' },
      industry: '',
      category: '',
      keyword: '',
      rules: [],
      counts: {},
      loading: false,
      detailVisible: false,
      current: null
    }
  },
  computed: {
    filtered() {
      const kw = (this.keyword || '').trim().toLowerCase()
      return this.rules.filter(r => {
        if (!kw) return true
        return ((r.ruleCode || '') + (r.title || '')).toLowerCase().includes(kw)
      })
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      Promise.all([listRules({ industry: this.industry, category: this.category }), ruleCount()])
        .then(([listRes, countRes]) => {
          this.rules = (listRes && listRes.data) || []
          this.counts = (countRes && countRes.data) || {}
        })
        .finally(() => { this.loading = false })
    },
    categoryTag(c) {
      if (c === 'BLACKLIST' || c === 'CONFLICT') return 'danger'
      if (c === 'WHITELIST' || c === 'BOUNDARY') return 'warning'
      return 'info'
    },
    severityTag(s) {
      if (s === 'BLOCK') return 'danger'
      if (s === 'WARN') return 'warning'
      return 'info'
    },
    showDetail(row) {
      this.current = row
      this.detailVisible = true
    }
  }
}
</script>

<style scoped>
.page-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.page-title { font-size: 17px; font-weight: 600; }
.page-desc { font-size: 12px; color: var(--cg-text-secondary); }
.filter-card >>> .el-card__body { padding: 12px 14px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.label { font-size: 12px; color: var(--cg-text-secondary); }
.sel { width: 150px; }
.kw { width: 220px; }
.count { margin-left: auto; font-size: 12px; color: var(--cg-text-secondary); }
.count b { font-size: 18px; color: var(--cg-primary); margin: 0 3px; }
.hint { font-size: 11px; }
.dtitle { font-weight: 600; font-size: 14px; margin-bottom: 14px; }
.dsec { font-size: 12px; color: var(--cg-text-secondary); margin: 12px 0 4px; }
.dtext { font-size: 13px; line-height: 1.7; }
.dtext.ok { color: var(--cg-success); }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }
</style>
