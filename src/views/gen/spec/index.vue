<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">规格工件链</span>
      <span class="page-desc">
        SDD 规格先行：constitution → spec → plan → tables → tasks，保存即版本快照；spec 确认（CONFIRMED）后才能作为对账依据。
      </span>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <span class="toolbar-label">所属系统</span>
      <el-select v-model="projectId" size="small" filterable placeholder="选择系统" class="proj-select" @change="loadAll">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <div class="toolbar-right">
        <el-button size="small" icon="el-icon-document" :disabled="!projectId" :loading="initing" @click="doInitConstitution">
          初始化基本法
        </el-button>
        <el-button size="small" icon="el-icon-refresh" :disabled="!projectId" @click="loadTree">刷新</el-button>
      </div>
    </el-card>

    <div v-if="!projectId" class="empty-hint">
      <i class="el-icon-folder-opened" />
      <p>请先选择一个系统</p>
    </div>

    <div v-else class="spec-layout">
      <!-- 左栏：工件树（五类各最新版本） -->
      <el-card shadow="never" class="tree-card" v-loading="loading">
        <div class="tree-head">
          <b>工件树</b>
          <span class="hint">各类型最新版本</span>
        </div>
        <div
          v-for="t in TYPE_ORDER"
          :key="t.type"
          class="artifact-item"
          :class="{ active: current && current.artifactType === t.type }"
          @click="pickType(t.type)"
        >
          <template v-if="latestByType[t.type]">
            <div class="row1">
              <span class="type">{{ t.label }}</span>
              <el-tag size="mini" :type="statusTag(latestByType[t.type].status)">{{ statusText(latestByType[t.type].status) }}</el-tag>
            </div>
            <div class="title">{{ latestByType[t.type].title || '（无标题）' }}</div>
            <div class="row1">
              <span class="ver">v{{ latestByType[t.type].version }} · {{ latestByType[t.type].createTime || '—' }}</span>
              <el-tag v-if="t.type === 'SPEC' && latestByType[t.type].status === 'CONFIRMED'" size="mini" type="success">对账依据</el-tag>
            </div>
          </template>
          <template v-else>
            <div class="row1">
              <span class="type">{{ t.label }}</span>
              <span class="hint">未创建</span>
            </div>
          </template>
        </div>
        <div class="chain-tip">
          对账只认已生效（CONFIRMED）的最新 SPEC；草稿评审确认后自动成为新对账依据。
        </div>
      </el-card>

      <!-- 右栏：编辑 / 历史 -->
      <el-card shadow="never" class="edit-card">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="编辑" name="edit">
            <div v-if="!current" class="empty-hint small">
              <p>从左侧选择工件类型开始</p>
            </div>
            <template v-else>
              <div class="current-meta">
                <span class="el-tag" size="mini" :type="statusTag(current.status)">{{ statusText(current.status) }}</span>
                <span class="ver">v{{ current.version }}</span>
                <span v-if="viewingHistory" class="hint">
                  正在查看历史版本（保存将基于该内容生成新版本）
                  <el-button type="text" size="mini" @click="backToLatest">回到最新</el-button>
                </span>
              </div>
              <el-form label-position="top" size="small">
                <el-form-item label="标题">
                  <el-input v-model="form.title" placeholder="工件标题" />
                </el-form-item>
                <el-form-item label="Markdown 正文（人与 Agent 共读）">
                  <el-input
                    v-model="form.contentMd"
                    type="textarea"
                    :rows="14"
                    placeholder="# 规格 Markdown 正文"
                    class="mono"
                  />
                </el-form-item>
                <el-form-item label="结构化元数据 JSON（对应 Markdown front matter）">
                  <el-input
                    v-model="form.yamlMeta"
                    type="textarea"
                    :rows="4"
                    placeholder='{"domain":"...","module":"..."}'
                    class="mono"
                  />
                </el-form-item>
                <el-form-item
                  v-if="current.artifactType === 'SPEC'"
                  label="期望清单 JSON（converge 比对数据源：tables / enums）"
                >
                  <el-input
                    v-model="form.structuredJson"
                    type="textarea"
                    :rows="6"
                    placeholder='{"tables":[{"name":"...","fields":["..."]}],"enums":["..."]}'
                    class="mono"
                  />
                </el-form-item>
              </el-form>
              <div class="edit-foot">
                <span class="hint">保存即新增版本快照（历史不可覆盖）</span>
                <span>
                  <el-button size="small" :loading="saving" @click="doSave">保存为新版本</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="current.status !== 'DRAFT' || viewingHistory"
                    :loading="confirming"
                    @click="doConfirm"
                  >
                    确认生效
                  </el-button>
                </span>
              </div>
            </template>
          </el-tab-pane>

          <el-tab-pane :label="`历史版本（${history.length}）`" name="history">
            <el-table :data="history" size="mini" v-loading="historyLoading">
              <el-table-column label="版本" width="70">
                <template slot-scope="s">v{{ s.row.version }}</template>
              </el-table-column>
              <el-table-column prop="title" label="标题" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template slot-scope="s">
                  <el-tag size="mini" :type="statusTag(s.row.status)">{{ statusText(s.row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="updateTime" label="更新时间" width="160" />
              <el-table-column label="操作" width="80">
                <template slot-scope="s">
                  <el-button type="text" size="mini" @click="viewVersion(s.row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script>
import { listProjectOptions, getDefaultProject } from '@/api/gen'
import { specTree, specDetail, specHistory, saveSpec, confirmSpec, initConstitution } from '@/api/spec'

// 工件类型展示顺序（与实体常量一致：CONSTITUTION/SPEC/PLAN/TABLES/TASKS）
const TYPE_ORDER = [
  { type: 'CONSTITUTION', label: '① 基本法 CONSTITUTION' },
  { type: 'SPEC', label: '② 规格 SPEC' },
  { type: 'PLAN', label: '③ 技术方案 PLAN' },
  { type: 'TABLES', label: '④ 表设计 TABLES' },
  { type: 'TASKS', label: '⑤ 任务 TASKS' }
]

// 状态枚举穷举：DRAFT/CONFIRMED
const STATUS_MAP = {
  DRAFT: { text: '草稿', tag: 'info' },
  CONFIRMED: { text: '已生效', tag: 'success' }
}

const emptyForm = () => ({ title: '', contentMd: '', yamlMeta: '', structuredJson: '' })

export default {
  name: 'GenSpec',
  data() {
    return {
      TYPE_ORDER,
      projects: [],
      projectId: '',
      loading: false,
      initing: false,
      saving: false,
      confirming: false,
      latestByType: {},
      current: null,
      viewingHistory: false,
      form: emptyForm(),
      activeTab: 'edit',
      history: [],
      historyLoading: false
    }
  },
  created() {
    this.loadProjects()
  },
  methods: {
    statusText(s) {
      return (STATUS_MAP[s] || {}).text || s || '—'
    },
    statusTag(s) {
      return (STATUS_MAP[s] || {}).tag || 'info'
    },
    loadProjects() {
      listProjectOptions().then(res => {
        const d = res.data || []
        this.projects = Array.isArray(d) ? d : (d.records || [])
        if (this.projects.length) {
          // 预选默认系统（沿用平台约定：新建表也预选默认系统），取不到再回落第一个
          getDefaultProject().then(r => {
            const def = r.data
            this.projectId = def && this.projects.some(p => p.id === def.id) ? def.id : this.projects[0].id
            this.loadAll()
          }).catch(() => {
            this.projectId = this.projects[0].id
            this.loadAll()
          })
        }
      })
    },
    loadAll() {
      this.current = null
      this.viewingHistory = false
      this.history = []
      this.loadTree()
    },
    loadTree() {
      if (!this.projectId) return
      this.loading = true
      specTree(this.projectId).then(res => {
        const list = res.data || []
        const byType = {}
        list.forEach(a => { byType[a.artifactType] = a })
        this.latestByType = byType
        // 默认选中 SPEC，其次第一个存在的类型
        const preferred = ['SPEC', 'CONSTITUTION', 'PLAN', 'TABLES', 'TASKS']
          .find(t => byType[t]) || 'SPEC'
        this.pickType(preferred)
      }).finally(() => { this.loading = false })
    },
    pickType(type) {
      const latest = this.latestByType[type]
      if (latest) {
        this.loadArtifact(latest.id)
      } else {
        // 类型不存在：给空白表单，artifactType 预置
        this.current = { artifactType: type, status: 'NEW', version: null, id: null }
        this.form = emptyForm()
        this.viewingHistory = false
        this.loadHistory(type)
      }
    },
    loadArtifact(id) {
      specDetail(id).then(res => {
        this.current = res.data
        this.form = {
          title: res.data.title || '',
          contentMd: res.data.contentMd || '',
          yamlMeta: res.data.yamlMeta || '',
          structuredJson: res.data.structuredJson || ''
        }
        this.viewingHistory = false
        this.loadHistory(res.data.artifactType)
      })
    },
    loadHistory(type) {
      if (!this.projectId || !type) return
      this.historyLoading = true
      specHistory(this.projectId, type).then(res => {
        this.history = res.data || []
      }).finally(() => { this.historyLoading = false })
    },
    viewVersion(row) {
      this.loadArtifact(row.id)
      this.viewingHistory = row.id !== (this.latestByType[row.artifactType] || {}).id
      this.activeTab = 'edit'
    },
    backToLatest() {
      const latest = this.latestByType[this.current.artifactType]
      if (latest) this.loadArtifact(latest.id)
    },
    doInitConstitution() {
      this.initing = true
      initConstitution(this.projectId).then(res => {
        this.$message.success('基本法已就绪（已有则返回最新，幂等）')
        this.loadTree()
        if (res.data) this.loadArtifact(res.data.id)
      }).finally(() => { this.initing = false })
    },
    doSave() {
      if (!this.form.title && !this.form.contentMd) {
        this.$message.warning('标题与正文至少填一项')
        return
      }
      this.saving = true
      saveSpec({
        projectId: this.projectId,
        artifactType: this.current.artifactType,
        title: this.form.title,
        contentMd: this.form.contentMd,
        yamlMeta: this.form.yamlMeta,
        structuredJson: this.form.structuredJson
      }).then(res => {
        this.$message.success(`已保存为 v${res.data.version}（草稿）`)
        this.loadTree()
        this.loadArtifact(res.data.id)
      }).finally(() => { this.saving = false })
    },
    doConfirm() {
      if (!this.current || !this.current.id) return
      this.confirming = true
      confirmSpec(this.current.id).then(res => {
        this.$message.success('已确认生效，可执行对账')
        this.current = res.data
        this.loadTree()
      }).finally(() => { this.confirming = false })
    }
  }
}
</script>

<style scoped>
.toolbar-card >>> .el-card__body { padding: 12px 14px; display: flex; align-items: center; }
.toolbar-label { font-size: 13px; color: var(--cg-text-secondary); margin-right: 8px; }
.proj-select { width: 240px; }
.toolbar-right { margin-left: auto; }
.spec-layout { display: grid; grid-template-columns: 340px 1fr; gap: 14px; align-items: start; }
.tree-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.tree-head b { font-size: 14px; }
.hint { font-size: 12px; color: var(--cg-text-secondary); }
.artifact-item { border: 1px solid var(--cg-border); border-radius: 6px; padding: 10px 12px; margin-bottom: 8px; cursor: pointer; transition: border-color .2s, background .2s; }
.artifact-item:hover { border-color: var(--cg-primary-light-5); }
.artifact-item.active { border-color: var(--cg-primary); background: var(--cg-primary-soft); }
.artifact-item .row1 { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }
.artifact-item .type { font-weight: 600; font-size: 13px; }
.artifact-item .title { font-size: 12.5px; color: var(--cg-text-secondary); margin-bottom: 5px; }
.ver { font-size: 12px; color: var(--cg-primary); }
.chain-tip { margin-top: 10px; font-size: 12px; color: var(--cg-text-secondary); border-left: 3px solid var(--cg-primary-light-5); padding-left: 8px; line-height: 1.7; }
.current-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.mono >>> textarea { font-family: Consolas, Monaco, monospace; font-size: 12.5px; }
.edit-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.empty-hint { text-align: center; color: var(--cg-text-secondary); padding: 60px 0; font-size: 13px; }
.empty-hint i { font-size: 40px; color: var(--cg-border); margin-bottom: 12px; }
.empty-hint.small { padding: 30px 0; }
</style>
