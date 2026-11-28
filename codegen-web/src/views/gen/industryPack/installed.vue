<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">我的范式安装</span>
      <span class="page-desc">项目已安装的行业范式包与版本、模块；移除只删台账，不删除已生成的表</span>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" size="small" filterable placeholder="选择项目" style="width: 260px">
          <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-refresh" @click="load">刷新</el-button>
        <el-button size="small" icon="el-icon-shopping-bag-1" @click="$router.push('/gen/industry-pack')">去市场</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="records" v-loading="loading" size="small" border>
        <el-table-column prop="packKey" label="范式包" min-width="150" />
        <el-table-column prop="packVersion" label="版本" width="80" align="center">
          <template slot-scope="s">v{{ s.row.packVersion }}</template>
        </el-table-column>
        <el-table-column prop="scope" label="范围" width="100" align="center">
          <template slot-scope="s">
            <el-tag size="mini" :type="s.row.scope === 'supplement' ? 'warning' : 'info'">
              {{ s.row.scope === 'supplement' ? '补表' : '新建' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模块" min-width="200">
          <template slot-scope="s">{{ parseNames(s.row.moduleNames).join('、') }}</template>
        </el-table-column>
        <el-table-column prop="tableCount" label="表数" width="70" align="center" />
        <el-table-column prop="installTime" label="安装时间" width="160" />
        <el-table-column label="操作" width="90" align="center">
          <template slot-scope="s">
            <el-button type="text" size="mini" class="danger" @click="removeOne(s.row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !records.length" description="该项目尚未安装范式包" />
    </el-card>
  </div>
</template>

<script>
import { listInstalled, removeInstall } from '@/api/industryPack'
import { listProjectOptions, getDefaultProject } from '@/api/gen'
import { Message, MessageBox } from 'element-ui'

export default {
  name: 'IndustryPackInstalled',
  data() {
    return {
      loading: false,
      projectId: null,
      projects: [],
      records: []
    }
  },
  created() {
    listProjectOptions().then(res => {
      const d = res.data || []
      this.projects = Array.isArray(d) ? d : (d.records || [])
      const qid = this.$route.query.projectId
      if (qid) {
        this.projectId = Number(qid)
      } else {
        getDefaultProject().then(r => { this.projectId = r && r.data && r.data.id })
      }
    })
  },
  methods: {
    load() {
      if (!this.projectId) return
      this.loading = true
      listInstalled(this.projectId).then(res => { this.records = res.data || [] })
        .finally(() => { this.loading = false })
    },
    parseNames(json) {
      try { return JSON.parse(json || '[]') } catch (e) { return [] }
    },
    removeOne(row) {
      MessageBox.confirm(`确认移除「${row.packKey} v${row.packVersion}」的安装记录？已生成的表不会被删除。`, '提示', { type: 'warning' })
        .then(() => removeInstall(row.id).then(() => { Message.success('已移除'); this.load() }))
        .catch(() => {})
    }
  },
  watch: {
    projectId() { this.load() }
  }
}
</script>

<style scoped>
.toolbar-card { margin-bottom: 14px; }
.toolbar { display: flex; gap: 10px; }
.danger { color: #e05252; }
</style>
