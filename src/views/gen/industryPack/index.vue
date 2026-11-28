<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">行业范式市场</span>
      <span class="page-desc">
        交付级范式包：经过工程化去重，含命中度与外接边界判断。一键安装到项目生成建模方案（plan），不直接建库。
      </span>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="industry" size="small" clearable placeholder="全部行业" class="cat-select">
          <el-option v-for="i in industries" :key="i" :label="i" :value="i" />
        </el-select>
        <el-input
          v-model="keyword"
          size="small"
          clearable
          placeholder="搜索范式包 / 业务主线"
          prefix-icon="el-icon-search"
          class="kw-input"
        />
        <div class="toolbar-right">
          <span class="stat">共 {{ paradigms.length }} 套范式包 · {{ totalTables }} 张直建表</span>
          <el-button size="small" type="primary" icon="el-icon-box" @click="goInstalled">我的安装</el-button>
        </div>
      </div>
    </el-card>

    <div v-loading="loading" class="card-area">
      <div class="card-grid">
        <el-card v-for="p in paradigms" :key="p.packKey" shadow="hover" class="para-card">
          <div class="card-top">
            <el-tag size="mini" effect="dark">{{ p.industry }}</el-tag>
            <el-tag size="mini" type="info">v{{ p.version }}</el-tag>
            <el-tag v-if="!p.templateAvailable" size="mini" type="warning">模板缺失</el-tag>
          </div>
          <div class="card-title">{{ p.name }}</div>
          <div class="card-key mono">{{ p.packKey }}</div>
          <div class="card-desc">{{ p.boundaryNote }}</div>
          <div class="metrics">
            <div><b>{{ p.hitRate }}%</b><span>命中度</span></div>
            <div><b>{{ p.directTables }}</b><span>直建表</span></div>
            <div><b :class="{ warn: p.externalSystems > 0 }">{{ p.externalSystems }}</b><span>外接系统</span></div>
          </div>
          <div class="hit-row">
            <span>生成器命中</span>
            <el-progress :percentage="p.hitRate" :show-text="false" :stroke-width="6" class="hit-bar" />
          </div>
          <div class="card-actions">
            <el-button type="text" size="mini" icon="el-icon-view" @click="openDetail(p)">查看详情</el-button>
            <el-button type="primary" size="mini" icon="el-icon-plus" @click="goInstall(p)">安装到项目</el-button>
          </div>
        </el-card>
      </div>
      <el-empty v-if="!loading && !paradigms.length" description="没有匹配的范式包" />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer :title="detail.name || '范式包详情'" :visible.sync="detailVisible" size="560px" direction="rtl">
      <div v-if="detail.packKey" class="drawer-body">
        <div class="chain-box">{{ detail.coreChain }}</div>
        <div class="section-title">模块与表（{{ detail.moduleCount }} 模块 / {{ detail.tableCount }} 表）</div>
        <el-table :data="detail.modules || []" size="mini" border>
          <el-table-column prop="name" label="模块" min-width="130" />
          <el-table-column label="代表表" min-width="120">
            <template slot-scope="s"><code>{{ s.row.tables && s.row.tables[0] ? s.row.tables[0].tableName : '—' }}</code></template>
          </el-table-column>
          <el-table-column label="表数" width="62" align="center">
            <template slot-scope="s">{{ s.row.tables ? s.row.tables.length : 0 }}</template>
          </el-table-column>
        </el-table>
        <div class="section-title">去重说明</div>
        <div class="note-box">{{ detail.dedupNote }}</div>
        <div class="section-title">不承诺边界</div>
        <div class="note-box danger">{{ detail.boundaryNote }}</div>
        <div class="drawer-actions">
          <el-button size="small" @click="detailVisible = false">关闭</el-button>
          <el-button size="small" type="primary" @click="goInstall(detail)">安装到项目</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { listParadigms, getParadigmDetail } from '@/api/industryPack'
import { getDefaultProject } from '@/api/gen'

export default {
  name: 'IndustryPackMarket',
  data() {
    return {
      loading: false,
      paradigms: [],
      industries: ['村务治理', '城市管理', '智慧种植', '智慧园区'],
      industry: undefined,
      keyword: '',
      detailVisible: false,
      detail: {}
    }
  },
  computed: {
    totalTables() {
      return this.paradigms.reduce((sum, p) => sum + (p.directTables || 0), 0)
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      listParadigms({ industry: this.industry || undefined, keyword: this.keyword || undefined })
        .then(res => {
          this.paradigms = res.data || []
        })
        .finally(() => { this.loading = false })
    },
    openDetail(p) {
      this.detail = {}
      this.detailVisible = true
      getParadigmDetail(p.packKey).then(res => { this.detail = res.data || {} })
    },
    goInstall(p) {
      this.$router.push({ path: '/gen/industry-pack/install', query: { packKey: p.packKey } })
    },
    goInstalled() {
      getDefaultProject().then(res => {
        const id = res && res.data && res.data.id
        this.$router.push({ path: '/gen/industry-pack/installed', query: id ? { projectId: String(id) } : {} })
      })
    }
  },
  watch: {
    industry() { this.load() },
    keyword() { this.load() }
  }
}
</script>

<style scoped>
.toolbar-card { margin-bottom: 16px; }
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cat-select { width: 150px; }
.kw-input { width: 230px; }
.toolbar-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.stat { color: #7c87a3; font-size: 12px; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; }
.para-card { border-radius: 6px; }
.card-top { display: flex; gap: 6px; margin-bottom: 10px; }
.card-title { font-size: 16px; font-weight: 600; color: #1f2d50; }
.card-key { font-size: 11px; color: #7c87a3; margin: 2px 0 8px; }
.card-desc { font-size: 12px; color: #7c87a3; min-height: 40px; line-height: 1.6; }
.metrics { display: flex; gap: 22px; margin: 12px 0 8px; }
.metrics div { display: flex; flex-direction: column; }
.metrics b { font-size: 18px; color: #1f2d50; }
.metrics b.warn { color: #e6a23c; }
.metrics span { font-size: 11px; color: #7c87a3; }
.hit-row { display: flex; align-items: center; gap: 10px; font-size: 11px; color: #7c87a3; }
.hit-bar { flex: 1; }
.card-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; border-top: 1px solid #e9eef8; padding-top: 8px; }
.mono { font-family: Consolas, Monaco, monospace; }
.drawer-body { padding: 0 20px 20px; }
.chain-box { background: #ecf0fe; border-radius: 4px; padding: 10px 12px; font-size: 12px; color: #3b4867; margin-bottom: 16px; }
.section-title { font-size: 13px; font-weight: 600; margin: 18px 0 9px; padding-left: 8px; border-left: 3px solid #3d6df5; }
.note-box { background: #f7f9fc; border: 1px solid #dde5f2; border-radius: 4px; padding: 10px 12px; font-size: 12px; color: #3b4867; line-height: 1.7; }
.note-box.danger { background: #fdf3f3; border-color: #f5c6c6; color: #8a3b3b; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
</style>
