<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="模板在线编辑，保存后立即生效（无需重启）。表级覆盖优先于全局模板；「恢复默认」即删除表级覆盖，回落到全局模板。"
    />

    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item label="模板编码">
          <el-input v-model="query.tplCode" placeholder="请输入模板编码" clearable @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="产物分组">
          <el-select v-model="query.fileGroup" placeholder="全部" clearable style="width: 140px">
            <el-option label="backend" value="backend" />
            <el-option label="frontend" value="frontend" />
            <el-option label="shared" value="shared" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属">
          <el-select v-model="query.scope" style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="全局模板" value="global" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div slot="header" class="card-header">
        <span>模板列表</span>
        <el-button type="primary" icon="el-icon-plus" @click="openEditDialog(null)">新增模板</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="tplCode" label="模板编码" width="110" />
        <el-table-column prop="tplName" label="模板名称" min-width="140" />
        <el-table-column label="产物分组" width="100" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="groupTagType(row.fileGroup)" size="small">{{ row.fileGroup }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pathPattern" label="输出路径模式" min-width="260" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono-text">{{ row.pathPattern }}</span></template>
        </el-table-column>
        <el-table-column label="归属" width="130" align="center">
          <template slot-scope="{ row }">
            <el-tag v-if="isGlobalTpl(row)" type="info" size="small">全局</el-tag>
            <el-tag v-else type="warning" size="small">表级 · {{ tableNameOf(row.tableId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="70" align="center">
          <template slot-scope="{ row }">
            <span class="mono-text">v{{ row.version || 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="290" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="text" @click="openHistoryDrawer(row)">历史</el-button>
            <el-button v-if="isGlobalTpl(row)" type="text" style="color: var(--cg-success)" @click="openCopyDialog(row)">复制为表级</el-button>
            <el-button v-else type="text" style="color: var(--cg-warning)" @click="handleRestore(row)">恢复默认</el-button>
            <el-button type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        :current-page.sync="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 弹窗：新增 / 编辑模板 -->
    <el-dialog
      :title="editTitle"
      :visible.sync="editVisible"
      width="960px"
      top="4vh"
      :close-on-click-modal="false"
    >
      <el-form ref="editForm" :model="form" :rules="formRules" label-width="90px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="模板编码" prop="tplCode">
              <el-select
                v-if="!form.id"
                v-model="form.tplCode"
                placeholder="请选择模板编码"
                style="width: 100%"
                @change="handleTplCodeChange"
              >
                <el-option v-for="d in tplDefs" :key="d.code" :label="`${d.code}（${d.name}）`" :value="d.code" />
              </el-select>
              <el-input v-else v-model="form.tplCode" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产物分组">
              <el-input v-model="form.fileGroup" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="模板名称" prop="tplName">
              <el-input v-model="form.tplName" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="路径模式" prop="pathPattern">
          <el-input v-model="form.pathPattern" class="mono" placeholder="输出相对路径，支持 ${var} 占位" />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="16"
            spellcheck="false"
            class="mono"
            placeholder="Freemarker 模板内容"
          />
          <div class="form-tip">
            Freemarker 语法，可用模型键：pkg / backendBase / className / varName / tableName / comment / moduleName /
            gatewayPrefix / author / apiBase / columns 等。保存后立即生效，无需重启。
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dlg-footer">
        <el-button icon="el-icon-circle-check" :loading="validating" @click="handleValidate">校 验</el-button>
        <span style="flex: 1"></span>
        <el-button @click="editVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：复制为表级模板 -->
    <el-dialog
      :title="`复制为表级模板 · ${copySource.tplCode || ''}`"
      :visible.sync="copyVisible"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item label="目标表" required>
          <el-select v-model="copyTableId" placeholder="请选择目标表" style="width: 100%" :loading="tablesLoading">
            <el-option
              v-for="t in tables"
              :key="t.id"
              :label="`${t.tableName}（${t.tableComment || t.className}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="form-tip">
        将把当前全局模板内容复制为所选表的专属模板；该表生成代码时优先使用表级模板，其他表不受影响。同表同编码已存在覆盖时需先删除旧覆盖。
      </div>
      <div slot="footer">
        <el-button @click="copyVisible = false">取 消</el-button>
        <el-button type="primary" :loading="copying" :disabled="!copyTableId" @click="handleCopy">复 制</el-button>
      </div>
    </el-dialog>

    <!-- 抽屉：版本历史（每次保存自动快照，可回滚） -->
    <el-drawer
      :title="`版本历史 · ${historyTpl.tplCode || ''}（${historyTpl.tplName || ''}）`"
      :visible.sync="historyVisible"
      size="620px"
      direction="rtl"
    >
      <div class="drawer-body">
        <div class="form-tip" style="margin-top: 0">
          每次保存都会把修改前的内容存成快照；回滚会把当前版本先存快照再写入所选版本，因此回滚本身也可以再回滚。
        </div>
        <el-table v-loading="historyLoading" :data="historyList" border size="mini" max-height="560">
          <el-table-column label="版本" width="70" align="center">
            <template slot-scope="{ row }"><span class="mono-text">v{{ row.version }}</span></template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="90" />
          <el-table-column prop="createTime" label="保存时间" min-width="150" />
          <el-table-column label="操作" width="130" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" @click="viewHistory(row)">查看</el-button>
              <el-button
                type="text"
                style="color: var(--cg-warning)"
                :disabled="row.version === (historyTpl.version || 1)"
                @click="handleRollback(row)"
              >回滚到此</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!historyLoading && !historyList.length" class="form-tip">
          还没有历史版本：这个模板保存过一次以上才会有快照。
        </div>
      </div>
    </el-drawer>

    <!-- 弹窗：查看历史版本内容 -->
    <el-dialog :title="`历史内容 · v${historyDetail.version || ''}`" :visible.sync="historyDetailVisible" width="900px" top="5vh">
      <pre class="history-code">{{ historyDetail.content || '' }}</pre>
      <div slot="footer">
        <el-button @click="historyDetailVisible = false">关 闭</el-button>
        <el-button type="warning" :loading="rollingBack" @click="handleRollback(historyDetail)">回滚到此版本</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  getGenTplPage, getGenTplDetail, createGenTpl, updateGenTpl,
  deleteGenTpl, copyGenTpl, validateGenTpl,
  getGenTplHistory, getGenTplHistoryDetail, rollbackGenTpl
} from '@/api/genTpl'
import { getGenTablePage } from '@/api/gen'

// 12 个内置模板编码（与后端 GenTemplateSeeder 种子一致）
const TPL_DEFS = [
  { code: 'entity', name: '实体类', group: 'backend', pathPattern: '${backendBase}/entity/${className}.java' },
  { code: 'mapper', name: 'Mapper 接口', group: 'backend', pathPattern: '${backendBase}/mapper/${className}Mapper.java' },
  { code: 'service', name: 'Service 接口', group: 'backend', pathPattern: '${backendBase}/service/${className}Service.java' },
  { code: 'serviceImpl', name: 'Service 实现', group: 'backend', pathPattern: '${backendBase}/service/impl/${className}ServiceImpl.java' },
  { code: 'pageDto', name: '分页查询 DTO', group: 'backend', pathPattern: '${backendBase}/dto/${className}PageDTO.java' },
  { code: 'saveDto', name: '保存 DTO', group: 'backend', pathPattern: '${backendBase}/dto/${className}SaveDTO.java' },
  { code: 'vo', name: '列表 VO', group: 'backend', pathPattern: '${backendBase}/vo/${className}VO.java' },
  { code: 'controller', name: 'Controller', group: 'backend', pathPattern: '${backendBase}/controller/${className}Controller.java' },
  { code: 'apiJs', name: '前端 api.js', group: 'frontend', pathPattern: 'src/api/${moduleName}.js' },
  { code: 'vuePage', name: '前端页面', group: 'frontend', pathPattern: 'src/views/${moduleName}/index.vue' },
  { code: 'routerSnippet', name: '路由片段', group: 'shared', pathPattern: 'router-snippet.txt' },
  { code: 'readme', name: '使用说明', group: 'shared', pathPattern: 'README-使用说明.txt' }
]

const defaultForm = () => ({
  id: undefined,
  tplCode: '',
  tplName: '',
  fileGroup: '',
  content: '',
  pathPattern: '',
  tableId: 0,
  status: 1,
  remark: ''
})

export default {
  name: 'CodeGenTpl',
  data() {
    return {
      loading: false,
      saving: false,
      validating: false,
      copying: false,
      tablesLoading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, tplCode: '', fileGroup: '', scope: '' },
      // 表id -> 表名（归属列展示用）
      tableMap: {},
      tplDefs: TPL_DEFS,
      // 新增 / 编辑
      editVisible: false,
      form: defaultForm(),
      formRules: {
        tplCode: [{ required: true, message: '请选择模板编码', trigger: 'change' }],
        tplName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
        pathPattern: [{ required: true, message: '请输入输出路径模式', trigger: 'blur' }],
        content: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
      },
      // 复制为表级
      copyVisible: false,
      copySource: {},
      copyTableId: undefined,
      tables: [],
      // V1.3-5 版本历史
      historyVisible: false,
      historyLoading: false,
      historyTpl: {},
      historyList: [],
      historyDetailVisible: false,
      historyDetail: {},
      rollingBack: false
    }
  },
  computed: {
    editTitle() {
      const base = this.form.id ? `编辑模板 · ${this.form.tplCode}` : '新增模板'
      return this.isGlobalTpl(this.form) ? base : `${base}（表级 · ${this.tableNameOf(this.form.tableId)}）`
    }
  },
  created() {
    this.fetchList()
    this.loadTables()
  },
  methods: {
    async fetchList() {
      this.loading = true
      try {
        const params = {
          page: this.query.page,
          size: this.query.size,
          tplCode: this.query.tplCode || undefined,
          fileGroup: this.query.fileGroup || undefined
        }
        if (this.query.scope === 'global') params.tableId = 0
        const res = await getGenTplPage(params)
        this.list = res.data.list || []
        this.total = res.data.total || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    async loadTables() {
      this.tablesLoading = true
      try {
        const res = await getGenTablePage({ page: 1, size: 500 })
        this.tables = res.data.list || []
        this.tableMap = this.tables.reduce((m, t) => ({ ...m, [t.id]: t.tableName }), {})
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.tablesLoading = false
      }
    },
    // 雪花 ID 以字符串下发，tableId 是 "0" 而不是 0，不能用 falsy 判断
    isGlobalTpl(row) {
      return !row || !row.tableId || String(row.tableId) === '0'
    },
    tableNameOf(tableId) {
      return this.tableMap[tableId] || `#${tableId}`
    },
    groupTagType(group) {
      if (group === 'frontend') return 'success'
      if (group === 'shared') return 'warning'
      return ''
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, tplCode: '', fileGroup: '', scope: '' }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },

    /* ---------- 新增 / 编辑 ---------- */
    async openEditDialog(row) {
      if (!row) {
        this.form = defaultForm()
        this.editVisible = true
        this.$nextTick(() => this.$refs.editForm && this.$refs.editForm.clearValidate())
        return
      }
      try {
        const res = await getGenTplDetail(row.id)
        this.form = { ...defaultForm(), ...(res.data || {}) }
        this.editVisible = true
        this.$nextTick(() => this.$refs.editForm && this.$refs.editForm.clearValidate())
      } catch (e) { /* 拦截器已提示 */ }
    },
    handleTplCodeChange(code) {
      const def = TPL_DEFS.find(d => d.code === code)
      if (def) {
        this.form.tplName = def.name
        this.form.fileGroup = def.group
        this.form.pathPattern = def.pathPattern
      }
    },
    async handleValidate() {
      if (!this.form.content) {
        this.$message.warning('请先输入模板内容')
        return
      }
      this.validating = true
      try {
        await validateGenTpl(this.form.content, this.form.tplCode)
        this.$message.success('校验通过：模板语法正确')
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.validating = false
      }
    },
    handleSave() {
      this.$refs.editForm.validate(async(valid) => {
        if (!valid) return
        this.saving = true
        try {
          if (this.form.id) {
            await updateGenTpl(this.form.id, this.form)
          } else {
            await createGenTpl(this.form)
          }
          this.$message.success('保存成功，已立即生效')
          this.editVisible = false
          this.fetchList()
        } catch (e) { /* 拦截器已提示 */ } finally {
          this.saving = false
        }
      })
    },

    /* ---------- 复制为表级 / 恢复默认 / 删除 ---------- */
    openCopyDialog(row) {
      this.copySource = row
      this.copyTableId = undefined
      this.copyVisible = true
      if (!this.tables.length) this.loadTables()
    },
    async handleCopy() {
      this.copying = true
      try {
        await copyGenTpl(this.copySource.id, this.copyTableId)
        this.$message.success(`已复制为「${this.tableNameOf(this.copyTableId)}」的表级模板`)
        this.copyVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.copying = false
      }
    },
    handleRestore(row) {
      this.$confirm(
        `恢复默认将删除「${this.tableNameOf(row.tableId)}」的 ${row.tplCode} 专属模板，该表生成代码时回落到全局模板。确定继续吗？`,
        '提示',
        { type: 'warning' }
      ).then(async() => {
        await deleteGenTpl(row.id)
        this.$message.success('已恢复默认（表级覆盖已删除）')
        if (this.list.length === 1 && this.query.page > 1) this.query.page--
        this.fetchList()
      }).catch(() => {})
    },
    handleDelete(row) {
      const target = this.isGlobalTpl(row) ? '全局模板' : `表级模板（${this.tableNameOf(row.tableId)}）`
      const extra = this.isGlobalTpl(row) ? '；删除后所有未覆盖的表将无法生成该产物' : ''
      this.$confirm(`确定删除 ${target}「${row.tplCode}（${row.tplName}）」吗${extra}？`, '提示', { type: 'warning' })
        .then(async() => {
          await deleteGenTpl(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },

    /* ---------- V1.3-5 版本历史 / 回滚 ---------- */
    async openHistoryDrawer(row) {
      this.historyTpl = { ...row }
      this.historyList = []
      this.historyVisible = true
      this.historyLoading = true
      try {
        const res = await getGenTplHistory(row.id)
        this.historyList = res.data || []
      } catch (e) {
        this.historyList = []
      } finally {
        this.historyLoading = false
      }
    },
    async viewHistory(row) {
      try {
        const res = await getGenTplHistoryDetail(this.historyTpl.id, row.version)
        this.historyDetail = { ...(res.data || row) }
        this.historyDetailVisible = true
      } catch (e) { /* 拦截器已提示 */ }
    },
    handleRollback(row) {
      const v = row.version
      if (!v) return
      this.$confirm(
        `确定把「${this.historyTpl.tplCode}」回滚到 v${v} 吗？当前版本会先存成快照，回滚后可再回滚回来。`,
        '回滚模板',
        { type: 'warning' }
      )
        .then(async() => {
          this.rollingBack = true
          try {
            await rollbackGenTpl(this.historyTpl.id, v)
            this.$message.success(`已回滚到 v${v}，模板立即生效`)
            this.historyDetailVisible = false
            this.historyVisible = false
            this.fetchList()
          } catch (e) { /* 拦截器已提示 */ } finally {
            this.rollingBack = false
          }
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  text-align: right;
}

.danger-btn {
  color: var(--cg-danger);
}

.tip-bar {
  margin-bottom: 16px;
}

.form-tip {
  color: var(--cg-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
}

.mono >>> input,
.mono >>> textarea {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.mono-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.dlg-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer-body {
  padding: 0 16px 16px;
}

.history-code {
  background: #282c34;
  color: #98c379;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 12px;
  margin: 0;
  border-radius: 4px;
  max-height: 62vh;
  overflow: auto;
  white-space: pre;
}
</style>
