<template>
  <div class="test-center">
    <el-tabs v-model="tab">
      <!-- ==================== 用例库 ==================== -->
      <el-tab-pane label="用例库" name="cases">
        <div class="layout">
          <aside class="suites">
            <div class="aside-head">
              <span>套件</span>
              <el-button type="text" size="mini" @click="openSuiteDialog">新建</el-button>
            </div>
            <div
              v-for="s in suites"
              :key="s.id"
              :class="['suite', { active: s.id === suiteId }]"
              @click="selectSuite(s)"
            >
              <span class="suite-name">{{ s.name }}</span>
              <span class="suite-count">{{ s.caseCount || 0 }}</span>
            </div>
            <div v-if="!suites.length" class="empty">暂无套件，先新建一个</div>
          </aside>

          <section class="main">
            <div class="toolbar">
              <el-select v-model="envId" size="small" placeholder="执行环境" style="width: 190px">
                <el-option v-for="e in envs" :key="e.id" :label="e.name" :value="e.id" />
              </el-select>
              <el-button
                size="small"
                type="primary"
                :loading="running"
                :disabled="!suiteId || !envId"
                @click="runSuite"
              >
                运行本套件
              </el-button>
              <el-button size="small" :disabled="!suiteId" @click="openCaseDialog()">新建用例</el-button>
              <span v-if="!envs.length" class="hint">未配置 test 环境，请先到「环境配置」添加</span>
            </div>

            <el-table v-loading="loadingCases" :data="cases" size="mini" border>
              <el-table-column prop="name" label="用例" min-width="200" show-overflow-tooltip />
              <el-table-column label="来源" width="80" align="center">
                <template slot-scope="{ row }">
                  <el-tag size="mini" :type="row.source === 'generated' ? '' : 'info'">
                    {{ row.source === 'generated' ? '生成' : '手工' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="method" label="方法" width="72" align="center" />
              <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
              <el-table-column label="断言" width="60" align="center">
                <template slot-scope="{ row }">{{ assertCount(row.assertsJson) }}</template>
              </el-table-column>
              <el-table-column label="最近结果" width="90" align="center">
                <template slot-scope="{ row }">
                  <el-tag v-if="row.lastResult" size="mini" :type="resultType(row.lastResult)">
                    {{ row.lastResult }}
                  </el-tag>
                  <span v-else class="muted">未运行</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" align="center">
                <template slot-scope="{ row }">
                  <el-button type="text" size="mini" @click="openCaseDialog(row)">编辑</el-button>
                  <el-button type="text" size="mini" @click="quickTry(row)">试运行</el-button>
                  <el-button type="text" size="mini" class="danger" @click="removeCase(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </div>
      </el-tab-pane>

      <!-- ==================== 执行结果 ==================== -->
      <el-tab-pane label="执行结果" name="runs">
        <el-table v-loading="loadingRuns" :data="runs" size="mini" border>
          <el-table-column prop="id" label="批次" width="90" />
          <el-table-column label="状态" width="90" align="center">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="runType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="通过 / 失败 / 跳过" width="150" align="center">
            <template slot-scope="{ row }">
              <span class="ok">{{ row.passed }}</span> /
              <span class="bad">{{ row.failed }}</span> /
              <span class="muted">{{ row.skipped }}</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template slot-scope="{ row }">{{ row.durationMs }}ms</template>
          </el-table-column>
          <el-table-column prop="startedAt" label="开始时间" width="180" />
          <el-table-column label="操作" width="140" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="mini" @click="openRun(row.id)">查看报告</el-button>
              <el-button v-if="row.status === 'running'" type="text" size="mini" @click="doAbort(row.id)">
                中断
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 环境配置 ==================== -->
      <el-tab-pane label="环境配置" name="envs">
        <el-button size="small" type="primary" style="margin-bottom: 10px" @click="openEnvDialog()">
          新增环境
        </el-button>
        <el-table v-loading="loadingEnvs" :data="envs" size="mini" border>
          <el-table-column prop="name" label="环境名" width="140" />
          <el-table-column prop="baseUrl" label="baseUrl" min-width="220" show-overflow-tooltip />
          <el-table-column label="生产" width="80" align="center">
            <template slot-scope="{ row }">
              <el-tag v-if="row.isProd === 1" size="mini" type="danger">只读</el-tag>
              <span v-else class="muted">否</span>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="70" align="center">
            <template slot-scope="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="mini" @click="doVerify(row)">探活</el-button>
              <el-button type="text" size="mini" @click="openEnvDialog(row)">编辑</el-button>
              <el-button type="text" size="mini" class="danger" @click="removeEnv(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 用例编辑 -->
    <el-dialog :title="caseForm.id ? '编辑用例' : '新建用例'" :visible.sync="caseDialog" width="820px">
      <div class="editor">
        <div class="editor-form">
          <el-form label-width="76px" size="small">
            <el-form-item label="用例名">
              <el-input v-model="caseForm.name" placeholder="如：新增订单 · 必填校验" />
            </el-form-item>
            <el-form-item label="方法">
              <el-select v-model="caseForm.method" style="width: 120px">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="路径">
              <el-input v-model="caseForm.path" placeholder="/order/save" />
            </el-form-item>
            <el-form-item label="请求头">
              <el-input
                v-model="caseForm.headersJson"
                type="textarea"
                :rows="2"
                placeholder='{"Authorization":"Bearer ${case.login.token}"}'
              />
            </el-form-item>
            <el-form-item label="请求体">
              <el-input v-model="caseForm.bodyJson" type="textarea" :rows="3" placeholder='{"amount":100}' />
            </el-form-item>
            <el-form-item label="断言">
              <el-input
                v-model="caseForm.assertsJson"
                type="textarea"
                :rows="4"
                placeholder='[{"type":"status","expect":200}]'
              />
            </el-form-item>
            <el-form-item label="提取变量">
              <el-input
                v-model="caseForm.extractJson"
                type="textarea"
                :rows="2"
                placeholder='{"orderId":"$.data.id"}'
              />
            </el-form-item>
            <el-form-item label="顺序">
              <el-input-number v-model="caseForm.orderNo" :min="0" size="small" />
              <el-checkbox v-model="caseForm.enabledBool" style="margin-left: 12px">启用</el-checkbox>
            </el-form-item>
          </el-form>
        </div>

        <div class="editor-result">
          <div class="result-head">
            <span>试运行</span>
            <el-button size="mini" :disabled="!caseForm.id" :loading="trying" @click="doTry">运行</el-button>
          </div>
          <div v-if="!tryResult" class="result-empty">
            保存后可试运行；结果不写入执行记录
          </div>
          <div v-else>
            <div class="result-line">
              <el-tag size="mini" :type="tryResult.pass ? 'success' : 'danger'">
                {{ tryResult.pass ? '通过' : '失败' }}
              </el-tag>
              <span class="mono">{{ tryResult.status }}</span>
              <span class="muted">{{ tryResult.durationMs }}ms</span>
            </div>
            <div v-if="tryResult.error" class="result-error">{{ tryResult.error }}</div>
            <div v-for="(a, i) in tryResult.asserts" :key="i" class="assert-row">
              <el-tag size="mini" :type="a.pass ? 'success' : 'danger'">{{ a.pass ? '✓' : '✕' }}</el-tag>
              <span class="mono">{{ a.type }}</span>
              <span class="assert-target">{{ a.target }}</span>
              <span class="muted">{{ a.message }}</span>
            </div>
          </div>
        </div>
      </div>
      <span slot="footer">
        <el-button size="small" @click="caseDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="savingCase" @click="submitCase">保存</el-button>
      </span>
    </el-dialog>

    <!-- 执行报告 -->
    <el-dialog title="执行报告" :visible.sync="runDialog" width="880px">
      <template v-if="runDetail">
        <div class="run-head">
          <el-tag size="small" :type="runType(runDetail.run.status)">{{ runDetail.run.status }}</el-tag>
          <span class="ok">通过 {{ runDetail.run.passed }}</span>
          <span class="bad">失败 {{ runDetail.run.failed }}</span>
          <span class="muted">跳过 {{ runDetail.run.skipped }}</span>
          <span class="muted">{{ runDetail.run.durationMs }}ms</span>
        </div>
        <el-table :data="runDetail.results" size="mini" border max-height="420">
          <el-table-column type="expand">
            <template slot-scope="{ row }">
              <div class="snapshot">
                <div><b>请求</b><pre>{{ row.requestSnapshot }}</pre></div>
                <div v-if="row.responseSnapshot"><b>响应</b><pre>{{ row.responseSnapshot }}</pre></div>
                <div v-if="row.failedAssertJson"><b>失败断言</b><pre>{{ row.failedAssertJson }}</pre></div>
                <div v-if="row.errorMsg"><b>错误</b><pre>{{ row.errorMsg }}</pre></div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="caseName" label="用例" min-width="220" show-overflow-tooltip />
          <el-table-column label="结果" width="80" align="center">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="resultType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template slot-scope="{ row }">{{ row.durationMs }}ms</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 套件 / 环境 -->
    <el-dialog title="新建套件" :visible.sync="suiteDialog" width="420px">
      <el-form label-width="70px" size="small">
        <el-form-item label="名称">
          <el-input v-model="suiteForm.name" placeholder="如：订单管理" />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="suiteForm.moduleName" placeholder="如：order" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="suiteDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitSuite">保存</el-button>
      </span>
    </el-dialog>

    <el-dialog :title="envForm.id ? '编辑环境' : '新增环境'" :visible.sync="envDialog" width="560px">
      <el-form label-width="90px" size="small">
        <el-form-item label="环境名">
          <el-input v-model="envForm.name" placeholder="test / 预发" />
        </el-form-item>
        <el-form-item label="baseUrl">
          <el-input v-model="envForm.baseUrl" placeholder="http://127.0.0.1:8140" />
        </el-form-item>
        <el-form-item label="公共请求头">
          <el-input v-model="envForm.headersJson" type="textarea" :rows="2" placeholder='{"Content-Type":"application/json"}' />
        </el-form-item>
        <el-form-item label="生产环境">
          <el-switch v-model="envForm.isProdBool" />
          <span class="hint">开启后写操作会被硬拦截</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="envForm.enabledBool" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="envDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitEnv">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import {
  getEnvPage,
  saveEnv,
  deleteEnv,
  verifyEnv,
  getSuitePage,
  saveSuite,
  getCasePage,
  getCaseDetail,
  saveCase,
  deleteCase,
  tryRunCase,
  startRun,
  getRunPage,
  getRunDetail,
  abortRun
} from '@/api/test'

const DEFAULT_ASSERTS = JSON.stringify(
  [
    { type: 'status', expect: 200 },
    { type: 'jsonpath', path: '$.code', op: 'eq', expect: 200 }
  ],
  null,
  2
)

export default {
  name: 'TestCenter',
  data() {
    return {
      tab: 'cases',
      envs: [],
      suites: [],
      cases: [],
      runs: [],
      envId: null,
      suiteId: null,
      loadingEnvs: false,
      loadingCases: false,
      loadingRuns: false,
      running: false,
      savingCase: false,
      trying: false,
      caseDialog: false,
      runDialog: false,
      suiteDialog: false,
      envDialog: false,
      tryResult: null,
      runDetail: null,
      caseForm: this.emptyCase(),
      suiteForm: { name: '', moduleName: '' },
      envForm: this.emptyEnv()
    }
  },
  created() {
    this.loadEnvs()
    this.loadSuites()
    this.loadRuns()
  },
  methods: {
    emptyCase() {
      return {
        id: null,
        suiteId: null,
        name: '',
        method: 'POST',
        path: '',
        headersJson: '',
        bodyJson: '',
        assertsJson: DEFAULT_ASSERTS,
        extractJson: '',
        orderNo: 0,
        enabledBool: true
      }
    },
    emptyEnv() {
      return { id: null, name: '', baseUrl: '', headersJson: '', isProdBool: false, enabledBool: true }
    },
    async loadEnvs() {
      this.loadingEnvs = true
      try {
        const res = await getEnvPage({ page: 1, size: 100 })
        this.envs = res.data.list || []
        if (!this.envId && this.envs.length) {
          this.envId = this.envs[0].id
        }
      } finally {
        this.loadingEnvs = false
      }
    },
    async loadSuites() {
      const res = await getSuitePage({ page: 1, size: 200 })
      this.suites = res.data.list || []
      if (!this.suiteId && this.suites.length) {
        this.selectSuite(this.suites[0])
      }
    },
    async loadCases() {
      if (!this.suiteId) {
        this.cases = []
        return
      }
      this.loadingCases = true
      try {
        const res = await getCasePage({ page: 1, size: 500, suiteId: this.suiteId })
        this.cases = res.data.list || []
      } finally {
        this.loadingCases = false
      }
    },
    async loadRuns() {
      this.loadingRuns = true
      try {
        const res = await getRunPage({ page: 1, size: 50 })
        this.runs = res.data.list || []
      } finally {
        this.loadingRuns = false
      }
    },
    selectSuite(s) {
      this.suiteId = s.id
      this.loadCases()
    },
    openSuiteDialog() {
      this.suiteForm = { name: '', moduleName: '' }
      this.suiteDialog = true
    },
    async submitSuite() {
      if (!this.suiteForm.name) {
        this.$message.warning('请填写套件名')
        return
      }
      await saveSuite({ ...this.suiteForm, type: 'api', source: 'manual', enabled: 1 })
      this.suiteDialog = false
      await this.loadSuites()
    },
    openCaseDialog(row) {
      this.tryResult = null
      if (row) {
        getCaseDetail(row.id).then(res => {
          const d = res.data || {}
          this.caseForm = {
            ...d,
            enabledBool: d.enabled === 1
          }
          this.caseDialog = true
        })
      } else {
        this.caseForm = { ...this.emptyCase(), suiteId: this.suiteId }
        this.caseDialog = true
      }
    },
    async submitCase() {
      if (!this.caseForm.name || !this.caseForm.path) {
        this.$message.warning('用例名与路径不能为空')
        return
      }
      this.savingCase = true
      try {
        const payload = { ...this.caseForm, enabled: this.caseForm.enabledBool ? 1 : 0 }
        delete payload.enabledBool
        await saveCase(payload)
        this.$message.success('已保存')
        await this.loadCases()
        if (!this.caseForm.id) {
          // 新建后刷新拿回自增 ID 才能试运行
          const res = await getCasePage({ page: 1, size: 500, suiteId: this.suiteId })
          const created = (res.data.list || []).find(c => c.name === this.caseForm.name)
          if (created) {
            this.caseForm.id = created.id
          }
        }
      } finally {
        this.savingCase = false
      }
    },
    async doTry() {
      if (!this.envId) {
        this.$message.warning('请先选择执行环境')
        return
      }
      this.trying = true
      try {
        const res = await tryRunCase(this.caseForm.id, this.envId)
        this.tryResult = res.data
      } finally {
        this.trying = false
      }
    },
    quickTry(row) {
      this.openCaseDialog(row)
      this.$nextTick(() => this.doTry())
    },
    removeCase(row) {
      this.$confirm(`删除用例「${row.name}」？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteCase(row.id)
          await this.loadCases()
        })
        .catch(() => {})
    },
    async runSuite() {
      this.running = true
      try {
        const res = await startRun(this.envId, [this.suiteId])
        this.runDetail = res.data
        this.runDialog = true
        await this.loadRuns()
      } finally {
        this.running = false
      }
    },
    async openRun(id) {
      const res = await getRunDetail(id)
      this.runDetail = res.data
      this.runDialog = true
    },
    async doAbort(id) {
      await abortRun(id)
      await this.loadRuns()
    },
    openEnvDialog(row) {
      if (row) {
        this.envForm = {
          ...row,
          isProdBool: row.isProd === 1,
          enabledBool: row.enabled === 1
        }
      } else {
        this.envForm = this.emptyEnv()
      }
      this.envDialog = true
    },
    async submitEnv() {
      const payload = {
        ...this.envForm,
        isProd: this.envForm.isProdBool ? 1 : 0,
        enabled: this.envForm.enabledBool ? 1 : 0
      }
      delete payload.isProdBool
      delete payload.enabledBool
      await saveEnv(payload)
      this.envDialog = false
      await this.loadEnvs()
    },
    async doVerify(row) {
      const res = await verifyEnv(row.id)
      const r = res.data
      this.$message[r.reachable ? 'success' : 'error'](
        r.reachable ? `可达 HTTP ${r.status}（${r.durationMs}ms）` : `不可达：${r.message}`
      )
    },
    removeEnv(row) {
      this.$confirm(`删除环境「${row.name}」？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteEnv(row.id)
          await this.loadEnvs()
        })
        .catch(() => {})
    },
    assertCount(json) {
      try {
        return JSON.parse(json || '[]').length
      } catch (e) {
        return 0
      }
    },
    resultType(s) {
      return s === 'pass' ? 'success' : s === 'fail' ? 'danger' : 'info'
    },
    runType(s) {
      if (s === 'success') return 'success'
      if (s === 'failed') return 'danger'
      if (s === 'running') return 'warning'
      return 'info'
    }
  }
}
</script>

<style scoped>
.test-center {
  padding: 12px;
}
.layout {
  display: flex;
  gap: 12px;
}
.suites {
  width: 200px;
  flex: 0 0 200px;
  border-right: 1px solid #ebeef5;
  padding-right: 10px;
}
.aside-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}
.suite {
  display: flex;
  justify-content: space-between;
  padding: 7px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.suite:hover {
  background: #f5f7fa;
}
.suite.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}
.suite-count {
  color: #909399;
  font-family: monospace;
}
.main {
  flex: 1;
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.editor {
  display: flex;
  gap: 14px;
}
.editor-form {
  flex: 1.4;
  min-width: 0;
}
.editor-result {
  flex: 1;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
}
.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
  margin-bottom: 8px;
}
.result-empty {
  color: #909399;
  font-size: 12px;
}
.result-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.result-error {
  color: #f56c6c;
  font-size: 12px;
  margin-bottom: 8px;
  word-break: break-all;
}
.assert-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  border-top: 1px solid #f2f6fc;
  font-size: 12px;
  flex-wrap: wrap;
}
.assert-target {
  font-family: monospace;
  color: #606266;
}
.run-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
}
.snapshot {
  padding: 8px 14px;
  font-size: 12px;
}
.snapshot pre {
  margin: 4px 0 10px;
  white-space: pre-wrap;
  word-break: break-all;
  color: #606266;
}
.mono {
  font-family: monospace;
}
.muted {
  color: #909399;
}
.ok {
  color: #67c23a;
}
.bad {
  color: #f56c6c;
}
.danger {
  color: #f56c6c;
}
.hint {
  color: #e6a23c;
  font-size: 12px;
  margin-left: 6px;
}
.empty {
  color: #c0c4cc;
  font-size: 12px;
  padding: 8px;
}
</style>
