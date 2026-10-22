<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="批量接管向导：选表 → 预览/差异 → 写盘策略 → 校验/菜单/台账。写盘后自动记账 gen_output（批次号 GEN+时间戳），覆盖前自动备份可回滚；默认同时注册菜单并重建前端路由，生成页可直接在侧边栏打开。"
    />

    <el-card shadow="never">
      <el-steps :active="step" finish-status="success" align-center class="steps">
        <el-step title="① 勾选多张表" />
        <el-step title="② 预览 / 差异" />
        <el-step title="③ 写盘策略" />
        <el-step title="④ 校验 / 菜单 / 台账" />
      </el-steps>

      <!-- 第①步：选表 -->
      <div v-show="step === 0">
        <el-form :inline="true" @submit.native.prevent>
          <el-form-item label="表名">
            <el-input v-model="tableQuery.tableName" placeholder="表名（模糊）" clearable style="width: 180px" @keyup.enter.native="fetchTables" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="fetchTables">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table
          ref="tableSelector"
          v-loading="tablesLoading"
          :data="tables"
          border
          stripe
          max-height="420"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="45" :selectable="() => true" />
          <el-table-column prop="tableName" label="表名" width="170" />
          <el-table-column prop="tableComment" label="功能名" min-width="150" />
          <el-table-column prop="moduleName" label="前端模块" width="140" />
          <el-table-column prop="targetModule" label="目标模块" width="120">
            <template slot-scope="{ row }"><span class="mono-text">{{ row.targetModule || '默认' }}</span></template>
          </el-table-column>
          <el-table-column prop="lastGenTime" label="上次生成时间" width="160">
            <template slot-scope="{ row }">{{ row.lastGenTime || '从未' }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pagination"
          background
          layout="total, prev, pager, next"
          :total="tableTotal"
          :page-size="tableQuery.size"
          :current-page.sync="tableQuery.page"
          @current-change="fetchTables"
        />
      </div>

      <!-- 第②步：预览/差异 -->
      <div v-show="step === 1" v-loading="diffLoading">
        <div class="step-toolbar">
          <el-checkbox v-model="onlyChanged" @change="loadDiff">仅看变更</el-checkbox>
          <span class="header-tip">已选 {{ selectedTables.length }} 张表，共 {{ diffRows.length }} 个文件</span>
        </div>
        <div class="diff-layout">
          <div class="file-tree">
            <div v-for="group in diffGroups" :key="group.name" class="tree-group">
              <div class="tree-group-title">{{ group.name }}（{{ group.rows.length }}）</div>
              <div
                v-for="row in group.rows"
                :key="row.path"
                class="tree-node"
                :class="{ active: activeFile && activeFile.path === row.path }"
                @click="activeFile = row"
              >
                <span class="mono-text tree-path">{{ shortPath(row.path) }}</span>
                <el-tag :type="statusTagType(row.status)" size="mini">
                  {{ row.status === 'UNCHANGED' ? 'UNCHANGED' : `+${row.additions} -${row.deletions}` }}
                </el-tag>
              </div>
            </div>
          </div>
          <div class="diff-pane">
            <template v-if="activeFile">
              <div class="diff-head mono-text">
                {{ activeFile.path }}
                <el-tag :type="statusTagType(activeFile.status)" size="mini">{{ activeFile.status }}</el-tag>
                <span>+{{ activeFile.additions }} -{{ activeFile.deletions }}</span>
              </div>
              <div class="diff-body">
                <div v-for="(line, i) in activeFile.lines" :key="i" class="diff-line mono-text" :class="'dl-' + line.type">
                  {{ line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' ' }} {{ line.text }}
                </div>
              </div>
            </template>
            <el-empty v-else description="左侧选择文件查看行级差异" />
          </div>
        </div>
      </div>

      <!-- 第③步：写盘策略 -->
      <div v-show="step === 2">
        <el-radio-group v-model="overwrite">
          <div class="strategy-option" :class="{ active: !overwrite }" @click="overwrite = false">
            <b>⭕ 同名跳过（推荐）</b>
            <p>磁盘已存在的文件不写入，记 SKIPPED；仅新增文件落盘，最安全。</p>
          </div>
          <div class="strategy-option" :class="{ active: overwrite }" @click="overwrite = true">
            <b>⭕ 覆盖（保留手写代码）</b>
            <p>已有文件先备份到 .gen-backup/&lt;批次号&gt;/，标记块内替换、块外手写代码原样保留（MERGED），记 OVERWRITTEN。</p>
          </div>
        </el-radio-group>
        <div class="strategy-stats">
          <div class="stat"><b>{{ diffRows.length }}</b><span>将处理文件</span></div>
          <div class="stat"><b class="c-green">{{ statOf('NEW') }}</b><span>预计新增</span></div>
          <div class="stat"><b class="c-orange">{{ statOf('MODIFIED') }}</b><span>预计覆盖/合并</span></div>
          <div class="stat"><b class="c-gray">{{ statOf('UNCHANGED') }}</b><span>无变化</span></div>
        </div>

        <el-divider content-position="left">写盘后动作</el-divider>
        <div class="menu-option">
          <el-checkbox v-model="registerMenu">注册菜单并重建前端路由（否则页面写进磁盘也无法从侧边栏打开）</el-checkbox>
          <div v-if="registerMenu" class="menu-parent">
            <span class="header-tip">挂载到：</span>
            <el-select v-model="parentMenuId" size="small" style="width: 260px" placeholder="顶级菜单">
              <el-option label="（顶级菜单）" :value="0" />
              <el-option
                v-for="m in parentOptions"
                :key="m.id"
                :label="m.label"
                :value="m.id"
              />
            </el-select>
            <span class="header-tip">幂等：已注册过的表会跳过，不会重复建菜单；按钮权限自动授权给管理员角色</span>
          </div>
        </div>
      </div>

      <!-- 第④步：校验报告 + 台账确认 -->
      <div v-show="step === 3">
        <el-alert
          v-if="!writeDone"
          type="warning"
          :closable="false"
          title="尚未执行写盘，请回到第③步执行。"
        />
        <template v-else>
          <div class="verify-stats">
            <el-tag :type="verifyResult && verifyResult.passed ? 'success' : 'danger'" size="medium">
              {{ verifySummary }}
            </el-tag>
            <el-tag size="medium">批次：<span class="mono-text">{{ batchNo }}</span></el-tag>
            <el-tag type="success" size="small">写盘成功 {{ writtenCount }} 文件</el-tag>
          </div>
          <el-table v-if="verifyResult" :data="verifyResult.items" border stripe size="small" max-height="360">
            <el-table-column prop="file" label="校验对象" min-width="300" show-overflow-tooltip>
              <template slot-scope="{ row }"><span class="mono-text">{{ row.file }}</span></template>
            </el-table-column>
            <el-table-column label="结果" width="90" align="center">
              <template slot-scope="{ row }">
                <el-tag :type="severityTagType(row.severity)" size="small">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="360" show-overflow-tooltip />
          </el-table>
          <div class="final-tip">
            台账已记录本批次明细；可前往
            <el-button type="text" @click="goLedger">生成台账（携带批次号）</el-button>
          </div>

          <el-divider content-position="left">菜单与前端路由</el-divider>
          <div v-if="!registerMenu" class="final-tip">
            本次未注册菜单（第③步已关闭）。文件已落盘，但侧边栏没有入口 ——
            <el-button type="text" :loading="menuLoading" @click="registerMenus">现在补注册</el-button>
          </div>
          <template v-else>
            <div v-if="menuLoading" class="final-tip">正在注册菜单并重建路由…</div>
            <template v-else-if="menuResult">
              <div class="verify-stats">
                <el-tag :type="menuFailCount ? 'danger' : 'success'" size="medium">
                  菜单 {{ menuRegisteredCount }} 新建 / {{ menuSkippedCount }} 已存在 / {{ menuFailCount }} 失败
                </el-tag>
                <el-tag :type="menuResult.routeSynced ? 'success' : 'warning'" size="small">
                  路由文件 {{ menuResult.routeSynced ? '已重建' : '未写入' }}
                </el-tag>
                <span class="mono-text">{{ menuResult.routeFile }}</span>
              </div>
              <el-alert
                v-if="menuResult.message"
                type="warning"
                :closable="false"
                :title="menuResult.message"
                class="menu-msg"
              />
              <el-table :data="menuResult.menus || []" border stripe size="small" max-height="240">
                <el-table-column prop="tableName" label="表名" width="180">
                  <template slot-scope="{ row }"><span class="mono-text">{{ row.tableName }}</span></template>
                </el-table-column>
                <el-table-column prop="name" label="菜单名" min-width="140" />
                <el-table-column prop="path" label="访问路径" min-width="180">
                  <template slot-scope="{ row }">
                    <router-link v-if="row.status === 'REGISTERED' || row.status === 'SKIPPED'" :to="row.path" class="mono-text">
                      {{ row.path }}
                    </router-link>
                    <span v-else class="mono-text">{{ row.path }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="结果" width="110" align="center">
                  <template slot-scope="{ row }">
                    <el-tag :type="menuTagType(row.status)" size="small">{{ menuStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="message" label="说明" min-width="260" show-overflow-tooltip />
              </el-table>
              <div class="final-tip">
                刷新页面（F5）后侧边栏即出现上述菜单；想挂到别的目录下，重新执行第③步前的注册即可（已注册的不会重复建）。
              </div>
            </template>
          </template>
        </template>
      </div>

      <!-- 底部导航 -->
      <div class="wizard-foot">
        <el-button v-if="step > 0" icon="el-icon-back" @click="step--">上一步</el-button>
        <span style="flex: 1" />
        <el-button v-if="step === 2" type="primary" icon="el-icon-upload2" :loading="writeLoading" @click="doWrite">
          执行写盘并进入第④步
        </el-button>
        <el-button v-else-if="step < 3" type="primary" :disabled="!canNext" @click="nextStep">
          下一步 →
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getGenTablePage, previewGenCode, diffGenCode, writeGenCode, registerGenMenu } from '@/api/gen'
import { verifyGenOutput } from '@/api/genOutput'
import { getEnabledMenuTree } from '@/api/system'

export default {
  name: 'GenWizard',
  data() {
    return {
      step: 0,
      tableQuery: { page: 1, size: 10, tableName: '' },
      tables: [],
      tableTotal: 0,
      tablesLoading: false,
      selectedTables: [],
      onlyChanged: true,
      diffRows: [],
      diffLoading: false,
      activeFile: null,
      overwrite: false,
      writeLoading: false,
      writeDone: false,
      batchNo: '',
      writeResults: [],
      verifyResult: null,
      verifyLoading: false,
      // 写盘后动作：注册菜单（默认开，否则页面写进磁盘也没入口）
      registerMenu: true,
      parentMenuId: 0,
      parentOptions: [],
      menuLoading: false,
      menuResult: null
    }
  },
  computed: {
    canNext() {
      if (this.step === 0) return this.selectedTables.length > 0
      return true
    },
    diffGroups() {
      const backend = this.diffRows.filter(r => !r.path.startsWith('src/'))
      const frontend = this.diffRows.filter(r => r.path.startsWith('src/'))
      const groups = []
      if (backend.length) groups.push({ name: 'backend · 后端产物', rows: backend })
      if (frontend.length) groups.push({ name: 'frontend · 前端产物', rows: frontend })
      return groups
    },
    writtenCount() {
      return this.writeResults.filter(r => ['WRITTEN', 'OVERWRITTEN', 'MERGED'].includes(r.status)).length
    },
    verifySummary() {
      if (!this.verifyResult) return '校验未执行'
      return this.verifyResult.passed
        ? `校验通过（PASS ${this.verifyResult.pass} / WARN ${this.verifyResult.warn}）`
        : `存在 FAIL ${this.verifyResult.fail} 项，请处理`
    },
    menuRows() {
      return (this.menuResult && this.menuResult.menus) || []
    },
    menuRegisteredCount() {
      return this.menuRows.filter(m => m.status === 'REGISTERED' || m.status === 'PLANNED').length
    },
    menuSkippedCount() {
      return this.menuRows.filter(m => m.status === 'SKIPPED').length
    },
    menuFailCount() {
      return this.menuRows.filter(m => m.status === 'ERROR').length
    }
  },
  created() {
    this.fetchTables()
    this.loadParentOptions()
  },
  methods: {
    async fetchTables() {
      this.tablesLoading = true
      try {
        const res = await getGenTablePage(this.tableQuery)
        this.tables = res.data.list
        this.tableTotal = res.data.total
      } finally {
        this.tablesLoading = false
      }
    },
    onSelectionChange(rows) {
      this.selectedTables = rows
    },
    async nextStep() {
      if (this.step === 0 && !this.selectedTables.length) {
        this.$message.warning('请先勾选要接管的表')
        return
      }
      this.step++
      if (this.step === 1) {
        await this.loadDiff()
      }
    },
    selectedIds() {
      return this.selectedTables.map(t => t.id)
    },
    async loadDiff() {
      this.diffLoading = true
      this.activeFile = null
      try {
        const res = await diffGenCode(this.selectedIds(), this.overwrite, this.onlyChanged)
        this.diffRows = res.data || []
      } finally {
        this.diffLoading = false
      }
    },
    statOf(status) {
      return this.diffRows.filter(r => r.status === status).length
    },
    async doWrite() {
      this.writeLoading = true
      this.menuResult = null
      try {
        const res = await writeGenCode(this.selectedIds(), this.overwrite)
        this.writeResults = res.data || []
        this.batchNo = (this.writeResults[0] && this.writeResults[0].batchNo) || ''
        this.writeDone = true
        this.step = 3
        this.$message.success(`写盘完成：批次 ${this.batchNo}`)
        // 自动触发真校验
        this.verifyLoading = true
        try {
          const vr = await verifyGenOutput(this.selectedIds())
          this.verifyResult = vr.data
        } finally {
          this.verifyLoading = false
        }
        // 页面已落盘，再注册菜单：否则生成页没有侧边栏入口
        if (this.registerMenu) {
          await this.registerMenus()
        }
      } finally {
        this.writeLoading = false
      }
    },
    // 目录/菜单候选（按钮不参与挂载）
    async loadParentOptions() {
      try {
        const res = await getEnabledMenuTree()
        const out = []
        const walk = (nodes, depth) => {
          (nodes || []).forEach(n => {
            if (n.type === 3) return
            out.push({ id: n.id, label: `${'　'.repeat(depth)}${n.title || n.name}` })
            if (n.children && n.children.length) walk(n.children, depth + 1)
          })
        }
        walk(res.data || [], 0)
        this.parentOptions = out
      } catch (e) {
        this.parentOptions = []
      }
    },
    async registerMenus() {
      if (!this.selectedTables.length) return
      this.menuLoading = true
      try {
        const res = await registerGenMenu({
          ids: this.selectedIds(),
          parentId: this.parentMenuId || 0,
          syncRoute: true
        })
        this.menuResult = res.data || null
        this.registerMenu = true
        const fail = this.menuFailCount
        if (fail) {
          this.$message.warning(`菜单注册完成，${fail} 张表失败（见第④步说明）`)
        } else {
          this.$message.success('菜单已注册，前端路由文件已重建，刷新页面即可在侧边栏看到')
        }
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.menuLoading = false
      }
    },
    menuTagType(status) {
      const map = { REGISTERED: 'success', PLANNED: 'success', SKIPPED: 'info', ERROR: 'danger' }
      return map[status] || 'info'
    },
    menuStatusText(status) {
      const map = { REGISTERED: '已注册', PLANNED: '待注册', SKIPPED: '已存在', ERROR: '失败' }
      return map[status] || status
    },
    goLedger() {
      this.$router.push({ path: '/tool/genOutput', query: { batchNo: this.batchNo } })
    },
    shortPath(path) {
      const parts = String(path || '').split('/')
      return parts.length > 3 ? '…/' + parts.slice(-3).join('/') : path
    },
    statusTagType(status) {
      const map = { NEW: 'success', MODIFIED: 'warning', UNCHANGED: 'info' }
      return map[status] || 'info'
    },
    severityTagType(severity) {
      const map = { PASS: 'success', WARN: 'warning', FAIL: 'danger' }
      return map[severity] || 'info'
    }
  }
}
</script>

<style scoped>
.tip-bar { margin-bottom: 12px; }
.steps { margin-bottom: 20px; }
.step-toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 10px; }
.header-tip { color: var(--cg-text-secondary); font-size: 12px; }
.mono-text { font-family: Consolas, Monaco, monospace; font-size: 12px; }
.pagination { margin-top: 14px; text-align: right; }
.diff-layout { display: flex; gap: 12px; min-height: 420px; }
.file-tree { width: 330px; flex-shrink: 0; border: 1px solid var(--cg-border-light); border-radius: 4px; padding: 10px; background: var(--cg-bg-page); overflow: auto; max-height: 480px; }
.tree-group-title { color: var(--cg-text-secondary); font-size: 12px; font-weight: 600; margin: 8px 0 4px; }
.tree-node { display: flex; justify-content: space-between; align-items: center; gap: 6px; padding: 4px 8px; border-radius: 3px; cursor: pointer; }
.tree-node:hover { background: var(--cg-primary-soft); }
.tree-node.active { background: var(--cg-primary-soft); }
.tree-path { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.diff-pane { flex: 1; border: 1px solid var(--cg-border-light); border-radius: 4px; overflow: hidden; display: flex; flex-direction: column; }
.diff-head { background: var(--cg-bg-hover); padding: 8px 12px; border-bottom: 1px solid var(--cg-border-light); }
.diff-body { overflow: auto; max-height: 440px; }
.diff-line { padding: 1px 12px; white-space: pre-wrap; }
.dl-add { background: var(--cg-success-bg); color: var(--cg-success); }
.dl-del { background: var(--cg-danger-bg); color: var(--cg-danger); text-decoration: line-through; }
.dl-ctx { color: var(--cg-text-regular); }
.strategy-option { display: block; border: 1px solid var(--cg-border); border-radius: 4px; padding: 14px 16px; margin-bottom: 12px; cursor: pointer; }
.strategy-option.active { border-color: var(--cg-primary); background: var(--cg-primary-soft); }
.strategy-option p { color: var(--cg-text-secondary); font-size: 12px; margin: 6px 0 0; }
.strategy-stats { display: flex; gap: 12px; margin-top: 16px; }
.stat { flex: 1; border: 1px solid var(--cg-border-light); border-radius: 4px; padding: 12px 16px; background: var(--cg-bg-page); }
.stat b { display: block; font-size: 22px; }
.stat span { color: var(--cg-text-secondary); font-size: 12px; }
.c-green { color: var(--cg-success); } .c-orange { color: var(--cg-warning); } .c-gray { color: var(--cg-text-secondary); }
.menu-option { padding: 2px 0 6px; }
.menu-parent { display: flex; align-items: center; gap: 10px; margin-top: 10px; flex-wrap: wrap; }
.menu-msg { margin-bottom: 10px; }
.verify-stats { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.final-tip { margin-top: 14px; color: var(--cg-text-regular); }
.wizard-foot { display: flex; margin-top: 18px; }
</style>
