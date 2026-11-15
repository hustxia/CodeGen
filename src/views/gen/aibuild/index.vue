<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="V9.5 AI 建表推荐（独立模块）：AI 仅推荐表结构，不建表；人工在编辑器增删改并确认后，再落库/建库。生成的系统支持微服务拆分、单表与关联表分离（主从模板）、页面隐藏 id/外键显名称、图片上传与富文本。"
    />

    <el-card shadow="never" class="main-card">
      <el-steps :active="step" finish-status="success" align-center class="steps">
        <el-step title="① 录入需求" />
        <el-step title="② 表设计编辑器" />
        <el-step title="③ 落库 / 建库" />
      </el-steps>

      <!-- ============ 第①步：录入需求 ============ -->
      <div v-show="step === 0">
        <el-form label-width="120px" style="max-width: 760px; margin-top: 8px" @submit.native.prevent>
          <el-form-item label="系统名称" required>
            <el-input v-model="form.systemName" placeholder="如：设备资产管理系统" maxlength="64" />
          </el-form-item>
          <el-form-item label="一句话描述">
            <el-input
              v-model="form.systemDesc"
              type="textarea"
              :rows="2"
              placeholder="写清业务对象与动作，AI 拆模块/建模才贴合该系统"
              maxlength="512"
            />
          </el-form-item>
          <el-form-item label="数据库名" required>
            <el-input v-model="form.dbName" placeholder="小写字母开头 snake_case，如 ams_new" maxlength="63" />
          </el-form-item>
          <el-form-item label="范围">
            <el-radio-group v-model="form.scope">
              <el-radio label="new">新建系统</el-radio>
              <el-radio label="supplement">为已有系统补表</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.scope === 'supplement'" label="目标系统" required>
            <el-select
              v-model="form.projectId"
              filterable
              placeholder="选择要补表的系统（其已有表会被自动排除）"
              style="width: 100%"
            >
              <el-option
                v-for="p in projects"
                :key="p.id"
                :label="p.projectName + (p.projectCode ? '（' + p.projectCode + '）' : '')"
                :value="p.id"
              />
            </el-select>
            <div class="muted">补表只推荐该系统缺失的表：已有表写进 prompt 让模型避让，服务端再做一次差集兜底。</div>
          </el-form-item>
          <el-form-item label="规模">
            <el-radio-group v-model="form.scale">
              <el-radio label="small">精简（&lt;50 表）</el-radio>
              <el-radio label="large">大型（&lt;500 表）</el-radio>
              <el-radio label="huge">超大型（几千张）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="部署形态">
            <el-radio-group v-model="form.deploy">
              <el-radio label="mono">单体工程</el-radio>
              <el-radio label="micro">微服务拆分</el-radio>
            </el-radio-group>
            <div class="muted">超大型默认微服务：按领域模块拆成 N 个独立服务工程 + 网关，每服务自有 schema。</div>
          </el-form-item>
          <el-alert
            v-if="!aiEnabled"
            type="warning"
            :closable="false"
            title="AI 建模未启用（未配置 LLM）。请在后端开启后重试，或使用「一句话建系统」向导的手动 DDL。"
            style="margin-bottom: 12px"
          />
          <el-form-item>
            <el-button
              type="primary"
              icon="el-icon-magic-stick"
              :loading="planLoading"
              :disabled="!aiEnabled"
              @click="doPlan"
            >🤖 AI 推荐（仅推荐，不会建表）</el-button>
            <span class="muted" style="margin-left: 10px">大模型本地推理一次约 3-5 分钟，请耐心等待。</span>
          </el-form-item>
        </el-form>
      </div>

      <!-- ============ 第②步：表设计编辑器 ============ -->
      <div v-show="step === 1">
        <el-alert
          v-if="plan && plan.relevanceWarning"
          type="warning"
          :closable="false"
          :title="'AI 拆模块与系统关联度偏低：' + (plan.relevanceTip || '建议补充更具体的业务描述后重试')"
          style="margin-bottom: 10px"
        />
        <el-alert
          v-if="plan && plan.excludedCount > 0"
          type="success"
          :closable="false"
          style="margin-bottom: 10px"
          :title="'已排除 ' + plan.excludedCount + ' 张重复表（目标系统已有表 ' + plan.existingTableCount + ' 张）'"
        >
          <div class="muted">{{ (plan.excludedTables || []).join('；') }}</div>
        </el-alert>
        <div class="editor-layout">
          <!-- 左：模块树 -->
          <div class="tree-pane">
            <div class="pane-title">模块 / 表（{{ plan ? plan.tableCount : 0 }} 张）</div>
            <el-input v-model="treeFilter" placeholder="过滤表名/注释" size="small" clearable style="margin-bottom:6px" />
            <el-tree
              :data="treeData"
              :props="{ label: 'label', children: 'children' }"
              node-key="key"
              default-expand-all
              highlight-current
              @node-click="onTreeNodeClick"
            />
          </div>
          <!-- 右：表清单 + 生成页预览 -->
          <div class="table-pane">
            <div class="pane-title">
              表清单
              <el-button size="mini" type="danger" plain :disabled="!selectedTables.length" @click="removeSelected">删除选中（{{ selectedTables.length }}）</el-button>
            </div>
            <el-table :data="visibleTables" border height="240" @selection-change="onSelect">
              <el-table-column type="selection" width="40" />
              <el-table-column prop="tableName" label="表名" width="200" />
              <el-table-column prop="tableComment" label="注释" />
              <el-table-column label="角色" width="90">
                <template slot-scope="{ row }">
                  <el-tag size="mini" :type="roleType(roleOf(row.tableName))">{{ roleLabel(roleOf(row.tableName)) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="列数" width="64">
                <template slot-scope="{ row }">{{ row.columns.length }}</template>
              </el-table-column>
            </el-table>

            <el-tabs v-model="genTab" style="margin-top: 10px">
              <el-tab-pane label="生成页预览（§4.6/§4.8）" name="gen">
                <div class="muted" style="margin-bottom:6px">
                  生成页面规范：列表/表单不显示主键 id；外键列显示关联表名称；单表与关联表分离（主从模板）。下方按所选场景渲染示例页。
                </div>
                <el-radio-group v-model="genScenario" size="small" style="margin-bottom:8px">
                  <el-radio-button label="master">主从</el-radio-button>
                  <el-radio-button label="single">单表</el-radio-button>
                  <el-radio-button label="detail">从表视角</el-radio-button>
                  <el-radio-button label="tree">树形</el-radio-button>
                </el-radio-group>
                <div v-if="genTable" class="gen-preview">
                  <div class="gen-badge">角色：{{ roleLabel(roleOf(genTable.tableName)) }}</div>
                  <div class="muted">{{ genTable.tableName }}（{{ genTable.tableComment }}）</div>
                  <!-- 列表 -->
                  <el-table :data="genRows" border size="mini" style="margin-top:6px">
                    <el-table-column
                      v-for="c in genVisibleCols(genTable)"
                      :key="c.name"
                      :label="isFk(c) ? friendlyName(c) + '（名称）' : friendlyName(c)"
                    >
                      <template slot-scope="{ row }">
                        <span v-if="isFk(c)">{{ fkSample(c) }}<el-tag size="mini" type="info" style="margin-left:4px">FK</el-tag></span>
                        <span v-else>{{ row[c.name] }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="120">
                      <template>
                        <el-button type="text" size="mini">编辑</el-button>
                        <el-button type="text" size="mini" class="del-text">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <!-- 主从：内嵌明细 -->
                  <div v-if="genScenario === 'master'" class="master-detail">
                    <div class="muted" style="margin:8px 0 4px">主表详情 → 内嵌明细（从表不挂独立菜单，随主表级联增删）</div>
                    <el-tabs>
                      <el-tab-pane v-for="dn in roleDetails(genTable.tableName)" :key="dn" :label="dn + '（从表）'" :name="dn">
                        <el-table :data="detailRows(dn)" border size="mini">
                          <el-table-column
                            v-for="c in detailVisibleCols(dn)"
                            :key="c.name"
                            :label="isFk(c) ? friendlyName(c) + '（名称）' : friendlyName(c)"
                          >
                            <template slot-scope="{ row }">
                              <span v-if="isFk(c)">{{ fkSample(c) }}<el-tag size="mini" type="info" style="margin-left:4px">FK</el-tag></span>
                              <span v-else>{{ row[c.name] }}</span>
                            </template>
                          </el-table-column>
                          <el-table-column label="操作" width="100">
                            <template>
                              <el-button type="text" size="mini">改</el-button>
                              <el-button type="text" size="mini" class="del-text">删</el-button>
                            </template>
                          </el-table-column>
                        </el-table>
                        <el-button type="primary" size="mini" style="margin-top:6px">+ 新增明细行</el-button>
                      </el-tab-pane>
                    </el-tabs>
                  </div>
                  <div v-if="genScenario === 'detail'" class="muted" style="margin-top:8px">
                    从表视角：归属主表 <b>{{ roleMaster(genTable.tableName) }}</b>，不挂独立菜单，作为主表详情内嵌明细子组件生成；跨服务时降级为远程关联逻辑主从。
                  </div>
                  <div v-if="genScenario === 'tree'" class="muted" style="margin-top:8px">
                    树形模板：自关联 parent_id，走树列表 + 层级 CRUD，每行可「加子级」递归展开。
                  </div>
                </div>
                <el-empty v-else description="当前结果无示例表，可调整规模或重跑 AI" :image-size="60" />
              </el-tab-pane>
              <el-tab-pane label="同源 DDL 预览" name="ddl">
                <el-button size="mini" type="primary" :loading="ddlLoading" @click="doDdlPreview">生成 DDL 预览</el-button>
                <pre v-if="ddlText" class="ddl-box">{{ ddlText }}</pre>
                <div v-else class="muted">点击上方按钮，按当前表结构生成与建库执行同源的只读 SQL。</div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </div>
      </div>

      <!-- ============ 第③步：落库 / 建库 ============ -->
      <div v-show="step === 2">
        <el-form label-width="120px" style="max-width: 760px" @submit.native.prevent>
          <el-alert type="success" :closable="false" class="mb12" :title="summaryText" />
          <el-form-item label="执行模式">
            <el-radio-group v-model="confirmMode">
              <el-radio label="draft">落为草稿（仅创建任务，不建库、不导表）</el-radio>
              <el-radio label="build">确认建库（建库建表，进 await_confirm；构建由「构建」按钮触发）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.deploy === 'micro'" label="微服务概览">
            <el-card shadow="never" class="micro-card">
              <div>将生成 <b>{{ microModules.length }}</b> 个独立服务工程 + 网关；每服务自有 schema：</div>
              <el-tag v-for="m in microModules.slice(0, 12)" :key="m.name" size="mini" class="micro-tag">{{ m.name }} → {{ m.db }}</el-tag>
              <span v-if="microModules.length > 12" class="muted"> …等 {{ microModules.length }} 个</span>
            </el-card>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="confirmLoading" @click="doConfirm">{{ confirmMode === 'draft' ? '落为草稿' : '确认建库' }}</el-button>
            <el-button @click="step = 1">返回编辑</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="confirmResult" type="success" :closable="false" class="mt12">
          <div>执行完成：模式 {{ confirmResult.mode }}，形态 {{ confirmResult.deploy }}，服务数 {{ confirmResult.serviceCount }}，表数 {{ confirmResult.tableCount }}。</div>
          <div v-if="confirmResult.skippedCount > 0" class="del-text">
            已跳过 {{ confirmResult.skippedCount }} 张与目标系统重复的表：{{ (confirmResult.skippedTables || []).join('；') }}
          </div>
          <div v-if="confirmResult.pipelineId">单体流水线 ID：{{ confirmResult.pipelineId }}（请到「一句话建系统」点「构建」启动）。</div>
          <div v-if="confirmResult.services && confirmResult.services.length">
            <div class="mt8">微服务逐服务结果：</div>
            <div v-for="s in confirmResult.services" :key="s.dbName" class="mt4">
              · {{ s.moduleName }} → {{ s.dbName }}（ID {{ s.pipelineId }}）<el-tag size="mini" :type="s.status === 'ok' ? 'success' : 'danger'">{{ s.status }}</el-tag>
              <span v-if="s.failReason" class="del-text"> {{ s.failReason }}</span>
            </div>
          </div>
        </el-alert>
      </div>

      <!-- 底部导航 -->
      <div class="foot-nav">
        <el-button v-if="step > 0" @click="step--">← 上一步</el-button>
        <el-button v-if="step < 2" type="primary" :disabled="!plan" @click="step++">下一步 →</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import {
  aiBuildConfig,
  aiBuildPlan,
  aiBuildDdlPreview,
  aiBuildConfirm
} from '@/api/pipeline'
import { listProjectOptions } from '@/api/gen'

const FK_SET = ['org', 'user', 'dept', 'type', 'category', 'parent', 'creator', 'owner']

export default {
  name: 'GenAiBuild',
  data() {
    return {
      step: 0,
      aiEnabled: false,
      form: {
        systemName: '',
        systemDesc: '',
        dbName: '',
        scope: 'new',
        projectId: null,
        scale: 'huge',
        deploy: 'micro'
      },
      projects: [],
      planLoading: false,
      plan: null,
      treeFilter: '',
      activeModule: null,
      selectedTables: [],
      genTab: 'gen',
      genScenario: 'master',
      ddlLoading: false,
      ddlText: '',
      confirmMode: 'build',
      confirmLoading: false,
      confirmResult: null
    }
  },
  computed: {
    relationMap() {
      const m = {}
      if (this.plan && this.plan.relations) {
        this.plan.relations.forEach(r => { m[r.name] = r })
      }
      return m
    },
    treeData() {
      if (!this.plan) return []
      const list = []
      const mods = this.plan.moduleTableNames || {}
      const byModule = {}
      this.plan.tables.forEach(t => { (byModule[t._mod] = byModule[t._mod] || []).push(t) })
      Object.keys(mods).forEach(mod => {
        const children = (mods[mod] || []).map(name => {
          const t = this.plan.tables.find(x => x.tableName === name)
          return { key: 't:' + name, label: name + '（' + (t ? t.tableComment || '' : '') + '）', table: name, isTable: true }
        })
        list.push({ key: 'm:' + mod, label: mod + '（' + (mods[mod] || []).length + '）', children })
      })
      // 无模块（精简）时直接列平表
      if (list.length === 0) {
        this.plan.tables.forEach(t => list.push({ key: 't:' + t.tableName, label: t.tableName, table: t.tableName, isTable: true }))
      }
      return list
    },
    visibleTables() {
      if (!this.plan) return []
      let arr = this.plan.tables
      if (this.activeModule) {
        const names = (this.plan.moduleTableNames[this.activeModule] || [])
        arr = arr.filter(t => names.includes(t.tableName))
      }
      if (this.treeFilter) {
        const f = this.treeFilter.toLowerCase()
        arr = arr.filter(t => t.tableName.toLowerCase().includes(f) || (t.tableComment || '').toLowerCase().includes(f))
      }
      return arr
    },
    genTable() {
      if (!this.plan || !this.plan.tables.length) return null
      const pick = role => this.plan.tables.find(t => this.roleOf(t.tableName) === role)
      return pick(this.genScenario) || this.plan.tables[0]
    },
    microModules() {
      if (!this.plan || !this.plan.moduleTableNames) return []
      let i = 0
      return Object.keys(this.plan.moduleTableNames).filter(m => {
        const names = this.plan.moduleTableNames[m] || []
        return this.plan.tables.some(t => names.includes(t.tableName))
      }).map(m => {
        const slug = m.toLowerCase().replace(/[^a-z0-9]/g, '') || ('m' + i)
        let db = (this.form.dbName + '_' + slug)
        if (db.length > 63) db = db.substring(0, 63)
        while (db.endsWith('_')) db = db.substring(0, db.length - 1)
        if (!/^[a-z][a-z0-9_]{0,62}$/.test(db)) db = this.form.dbName + '_m' + i
        i++
        return { name: m, db }
      })
    },
    summaryText() {
      if (!this.plan) return ''
      return `共 ${this.plan.tableCount} 张表：主表 ${this.plan.masterCount} / 从表 ${this.plan.detailCount} / 单表 ${this.plan.singleCount} / 树形 ${this.plan.treeCount}；模块 ${this.plan.moduleCount}；形态 ${this.form.deploy === 'micro' ? '微服务拆分' : '单体工程'}。`
    },
    genRows() { return this.sampleRows(this.genTable) },
    detailTables() {
      const map = {}
      if (this.plan) this.plan.tables.forEach(t => { map[t.tableName] = t })
      return map
    }
  },
  created() {
    aiBuildConfig().then(r => { this.aiEnabled = !!(r.data && r.data.aiEnabled) }).catch(() => { this.aiEnabled = false })
    listProjectOptions()
      .then(r => { this.projects = r.data || [] })
      .catch(() => { this.projects = [] })
  },
  methods: {
    doPlan() {
      if (!this.form.systemName || !this.form.dbName) {
        this.$message.warning('请先填写系统名称与数据库名')
        return
      }
      if (this.form.scope === 'supplement' && !this.form.projectId) {
        this.$message.warning('补表必须选择目标系统，否则无法排除已有表')
        return
      }
      this.planLoading = true
      const payload = { ...this.form }
      if (this.form.scope !== 'supplement') {
        payload.projectId = null
      }
      aiBuildPlan(payload).then(r => {
        const vo = r.data
        // 给每张表打 _mod 标记，便于模块树分组
        const mods = vo.moduleTableNames || {}
        const modOf = {}
        Object.keys(mods).forEach(m => (mods[m] || []).forEach(n => { modOf[n] = m }))
        vo.tables.forEach(t => { t._mod = modOf[t.tableName] || '' })
        this.plan = vo
        this.ddlText = ''
        this.confirmResult = null
        this.genScenario = vo.masterCount > 0 ? 'master' : 'single'
        this.step = 1
        this.$message.success('AI 推荐完成，请人工确认表设计')
      }).catch(e => {
        this.$message.error('AI 推荐失败：' + (e.message || e))
      }).finally(() => { this.planLoading = false })
    },
    onTreeNodeClick(node) {
      if (node.isTable) {
        // 点表：跳到该表所在模块并定位（这里仅聚焦模块过滤）
        const t = node.table
        const mod = this.plan.tables.find(x => x.tableName === t)
        this.activeModule = mod && mod._mod ? mod._mod : null
      } else {
        this.activeModule = node.label.replace(/\s*（.*$/, '')
      }
    },
    onSelect(rows) { this.selectedTables = rows },
    removeSelected() {
      const names = this.selectedTables.map(t => t.tableName)
      this.plan.tables = this.plan.tables.filter(t => !names.includes(t.tableName))
      this.plan.tableCount = this.plan.tables.length
      this.selectedTables = []
      this.$message.success('已删除 ' + names.length + ' 张表')
    },
    doDdlPreview() {
      if (!this.form.dbName) { this.$message.warning('请先填写数据库名'); return }
      this.ddlLoading = true
      aiBuildDdlPreview({ dbName: this.form.dbName, tables: this.plan.tables }).then(r => {
        this.ddlText = (r.data && r.data.ddl) || ''
        this.$message.success('DDL 预览已生成（与建库执行同源）')
      }).catch(e => this.$message.error('预览失败：' + (e.message || e))).finally(() => { this.ddlLoading = false })
    },
    doConfirm() {
      this.confirmLoading = true
      const payload = {
        systemName: this.form.systemName,
        systemDesc: this.form.systemDesc,
        dbName: this.form.dbName,
        scope: this.form.scope,
        projectId: this.form.scope === 'supplement' ? this.form.projectId : null,
        deploy: this.form.deploy,
        mode: this.confirmMode,
        tables: this.plan.tables,
        moduleTableNames: this.plan.moduleTableNames
      }
      aiBuildConfirm(payload).then(r => {
        this.confirmResult = r.data
        this.$message.success(this.confirmMode === 'draft' ? '已落为草稿' : '已确认建库')
      }).catch(e => this.$message.error('确认失败：' + (e.message || e))).finally(() => { this.confirmLoading = false })
    },
    // ---- 关系视图辅助 ----
    roleOf(name) { return (this.relationMap[name] && this.relationMap[name].role) || 'single' },
    roleLabel(role) { return { master: '主表', detail: '从表', single: '单表', tree: '树形' }[role] || '单表' },
    roleType(role) { return { master: 'warning', detail: 'danger', single: '', tree: 'success' }[role] || '' },
    roleDetails(name) { return (this.relationMap[name] && this.relationMap[name].details) || [] },
    roleMaster(name) { return (this.relationMap[name] && this.relationMap[name].master) || '' },
    // ---- 列辅助 ----
    isFk(col) { return col.name.endsWith('_id') || FK_SET.includes(col.name) },
    friendlyName(col) {
      if (col.comment) return col.comment
      if (col.name === 'name') return '名称'
      if (col.name === 'code') return '编码'
      if (col.name === 'status') return '状态'
      return col.name
    },
    fkSample(col) {
      const base = { org: '组织', user: '用户', dept: '部门', type: '类型', category: '分类', parent: '上级', creator: '创建人', owner: '负责人' }[col.name] || '关联'
      return base + 'A'
    },
    valSample(col) {
      if (col.name === 'name') return '示例名称'
      if (col.name === 'code') return 'CODE-001'
      if (col.name === 'status') return '启用'
      if (col.type && col.type.includes('datetime')) return '2026-11-15 10:00'
      if (col.type === 'date') return '2026-11-15'
      if (col.type && col.type.startsWith('decimal')) return '123.45'
      if (['tinyint', 'int', 'bigint'].includes(col.type)) return '1'
      if (col.type === 'text') return '—'
      return '示例值'
    },
    genVisibleCols(t) { return t.columns.filter(c => !c.pk && c.name !== 'id') },
    detailVisibleCols(dn) {
      const t = this.detailTables[dn]
      if (!t) return []
      return t.columns.filter(c => !c.pk && c.name !== 'id' && c.name !== (this.genTable.tableName.replace(/^.*_/, '') + '_id'))
    },
    sampleRows(t) {
      if (!t) return []
      const rows = []
      for (let i = 0; i < 3; i++) {
        const row = {}
        t.columns.filter(c => !c.pk && c.name !== 'id').forEach(c => { row[c.name] = this.isFk(c) ? this.fkSample(c) : this.valSample(c) })
        rows.push(row)
      }
      return rows
    },
    detailRows(dn) {
      const t = this.detailTables[dn]
      if (!t) return []
      const rows = []
      for (let i = 0; i < 3; i++) {
        const row = {}
        t.columns.filter(c => !c.pk && c.name !== 'id').forEach(c => { row[c.name] = this.isFk(c) ? this.fkSample(c) : this.valSample(c) })
        rows.push(row)
      }
      return rows
    }
  }
}
</script>

<style scoped>
.tip-bar { margin-bottom: 12px; }
.main-card { padding: 8px 4px; }
.steps { margin: 8px 0 20px; }
.editor-layout { display: flex; gap: 14px; }
.tree-pane { width: 280px; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; height: 420px; overflow: auto; }
.table-pane { flex: 1; min-width: 0; }
.pane-title { font-weight: 600; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
.gen-preview { border: 1px dashed #dcdfe6; border-radius: 4px; padding: 10px; }
.gen-badge { display: inline-block; background: #ecf5ff; color: #409eff; border-radius: 4px; padding: 2px 8px; font-size: 12px; margin-bottom: 6px; }
.master-detail { border-top: 1px solid #ebeef5; margin-top: 8px; padding-top: 6px; }
.micro-card { background: #fafafa; }
.micro-tag { margin: 4px 4px 0 0; }
.ddl-box { background: #1e1e1e; color: #d4d4d4; padding: 10px; border-radius: 4px; max-height: 320px; overflow: auto; font-size: 12px; white-space: pre-wrap; }
.del-text { color: #f56c6c; }
.muted { color: #909399; font-size: 12px; }
.mb12 { margin-bottom: 12px; }
.mt12 { margin-top: 12px; }
.mt8 { margin-top: 8px; }
.mt4 { margin-top: 4px; }
.foot-nav { margin-top: 18px; display: flex; justify-content: space-between; }
</style>
