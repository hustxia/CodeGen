<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="批量接管向导：选表 → 预览/差异 → 写盘策略 → 校验/菜单/台账。写盘后自动记账 gen_output（批次号 GEN+时间戳），覆盖前自动备份可回滚。菜单出口为 internal 时写入本平台侧边栏；为 artifact 时产出 menu.sql 到目标工程，需在目标库执行后目标系统才可见。"
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
          row-key="id"
          border
          stripe
          max-height="420"
          @selection-change="onSelectionChange"
        >
          <!-- reserve-selection：翻页/重新查询后按 row-key 恢复勾选，否则批量接管只能选当前页 -->
          <el-table-column type="selection" width="45" reserve-selection :selectable="() => true" />
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
        <div v-if="selectedTables.length" class="picked-bar">
          <span class="header-tip">已选 {{ selectedTables.length }} 张（跨页保留）：</span>
          <el-tag
            v-for="t in selectedTables"
            :key="t.id"
            size="mini"
            closable
            @close="removePicked(t.id)"
          >{{ t.tableName }}</el-tag>
        </div>
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
          <el-checkbox v-model="onlyChanged">仅看变更</el-checkbox>
          <span class="header-tip">
            已选 {{ selectedTables.length }} 张表 · 生成 {{ allRows.length }} 个文件 · 当前显示 {{ diffRows.length }} 个
          </span>
          <span v-if="skipCount" class="header-tip c-orange">
            {{ skipCount }} 个文件将被跳过{{ overwrite ? '（已被手改）' : '（同名已存在，切到「覆盖」可纳入）' }}
          </span>
        </div>
        <div class="diff-layout">
          <div class="file-tree">
            <div v-if="!diffGroups.length" class="tree-empty">
              <p>{{ onlyChanged ? '当前筛选下没有文件' : '没有生成文件' }}</p>
              <p v-if="onlyChanged && allRows.length">
                共 {{ allRows.length }} 个文件，内容均与磁盘一致。
                <el-button type="text" @click="onlyChanged = false">显示全部</el-button>
              </p>
              <p v-if="!overwrite && allRows.length">
                注意：当前是「同名跳过」策略，已存在文件不会写入，切到覆盖模式才看得到改动。
              </p>
            </div>
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
                <!-- 漂移是「磁盘 vs 上次生成」的事实，与 status（渲染结果 vs 磁盘）正交，所以单独一枚标签 -->
                <el-tag v-if="row.drift === 'DRIFTED'" type="danger" size="mini" effect="plain">已手改</el-tag>
                <el-tag :type="statusTagType(row.status)" size="mini">
                  {{ statusText(row.status, row) }}
                </el-tag>
              </div>
            </div>
          </div>
          <div class="diff-pane">
            <template v-if="activeFile">
              <div class="diff-head mono-text">
                {{ activeFile.path }}
                <el-tag :type="statusTagType(activeFile.status)" size="mini">{{ statusText(activeFile.status, activeFile) }}</el-tag>
                <el-tag v-if="activeFile.drift === 'DRIFTED'" type="danger" size="mini" effect="plain">
                  磁盘已被手改 · {{ activeFile.lastModified }}
                </el-tag>
                <el-tag v-else-if="activeFile.drift === 'MISSING'" type="info" size="mini" effect="plain">文件已不存在</el-tag>
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
      <div v-show="step === 2" v-loading="diffLoading">
        <div class="strategy-list">
          <div class="strategy-option" :class="{ active: strategyMode === 'skip' }" @click="strategyMode = 'skip'">
            <el-radio v-model="strategyMode" label="skip"><b>同名跳过（推荐）</b></el-radio>
            <p>磁盘已存在的文件不写入，记 SKIPPED；仅新增文件落盘，最安全。</p>
          </div>
          <div class="strategy-option" :class="{ active: strategyMode === 'safe' }" @click="strategyMode = 'safe'">
            <el-radio v-model="strategyMode" label="safe"><b>覆盖，但跳过已手改</b></el-radio>
            <p>
              已有文件先备份到 .gen-backup/&lt;批次号&gt;/，块内替换、块外手写代码保留；
              磁盘内容相对上次生成被改过的文件一律跳过，并说明「已手改」，不会覆盖掉手写逻辑。
            </p>
          </div>
          <div class="strategy-option" :class="{ active: strategyMode === 'overwrite' }" @click="strategyMode = 'overwrite'">
            <el-radio v-model="strategyMode" label="overwrite"><b>强制覆盖</b></el-radio>
            <p>不看漂移，全部按合并规则写入（仍会备份）。手改内容可能被块内替换掉，动手前请先确认差异页。</p>
          </div>
        </div>
        <div class="strategy-stats">
          <div class="stat"><b>{{ allRows.length }}</b><span>生成文件总数</span></div>
          <div class="stat"><b class="c-green">{{ statOf('NEW') }}</b><span>新增（会写入）</span></div>
          <div class="stat">
            <b class="c-orange">{{ overwrite ? statOf('MODIFIED') - (skipDrifted ? driftedOverwriteCount : 0) : skipCount }}</b>
            <span>{{ overwrite ? '覆盖 / 合并' : '同名跳过' }}</span>
          </div>
          <div class="stat">
            <b class="c-red">{{ driftCount }}</b>
            <span>已手改（漂移）{{ skipDrifted ? '· 将跳过' : '' }}</span>
          </div>
          <div class="stat"><b class="c-gray">{{ statOf('UNCHANGED') }}</b><span>内容无变化</span></div>
        </div>
        <div class="stat-note">
          {{ strategyNote }}
        </div>

        <el-divider content-position="left">写盘后动作</el-divider>
        <div class="menu-option">
          <el-checkbox v-model="registerMenu">注册菜单并重建前端路由（否则页面写进磁盘也没有入口）</el-checkbox>
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
            <span class="header-tip">幂等：已注册过的表会跳过，不会重复建菜单</span>
          </div>
        </div>
      </div>

      <!-- 第④步：校验报告 + 台账确认 -->
      <div v-show="step === 3" v-loading="verifyLoading">
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
            <el-tag v-if="skippedWriteCount" type="info" size="small">同名跳过 {{ skippedWriteCount }} 文件</el-tag>
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
            本次未注册菜单（第③步已关闭）。文件已落盘，但没有入口 ——
            <el-button type="text" :loading="menuLoading" @click="registerMenus">现在补注册</el-button>
          </div>
          <template v-else>
            <div v-if="menuLoading" class="final-tip">正在注册菜单并重建路由…</div>
            <template v-else-if="menuResult">
              <div class="verify-stats">
                <el-tag :type="menuFailCount ? 'danger' : 'success'" size="medium">
                  {{ menuSummary }}
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
                    <!-- 只有 internal 出口才写本平台 sys_menu，路径才点得动；artifact 指向的是目标系统 -->
                    <router-link v-if="isInternal && (row.status === 'REGISTERED' || row.status === 'SKIPPED')" :to="row.path" class="mono-text">
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

              <div v-if="isInternal" class="final-tip">
                刷新页面（F5）后侧边栏即出现上述菜单；想挂到别的目录下，重新执行注册即可（已注册的不会重复建）。
              </div>
              <div v-else class="final-tip">
                <b>当前菜单出口为 artifact</b>：不会写入本平台 sys_menu，所以本平台侧边栏不会出现这些菜单。
                菜单 SQL 已产出到目标工程
                <span class="mono-text">{{ menuResult.menuSqlFile || '（未落盘，见上方提示）' }}</span>，
                需在<b>目标系统自己的库</b>执行后，目标系统侧边栏才会出现对应入口。
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
      // allRows 恒为「当前策略下的全量结果」，diffRows 只是按「仅看变更」过滤后的展示视图。
      // 分开的原因：统计口径不能被展示过滤影响，否则「无变化/跳过」永远是 0。
      allRows: [],
      diffLoading: false,
      activeFile: null,
      // 三档策略：skip 同名跳过 / safe 覆盖但跳过已手改 / overwrite 强制覆盖
      // 用一档枚举而不是「overwrite + skipDrifted」两个布尔：两个布尔有四种组合，
      // 而其中一种是自相矛盾的（跳过 + 只写未漂移），枚举从类型上就排除了
      strategyMode: 'skip',
      writeLoading: false,
      writeDone: false,
      batchNo: '',
      writeResults: [],
      verifyResult: null,
      verifyLoading: false,
      // 写盘后动作：注册菜单（默认开，否则生成页没有入口）
      registerMenu: true,
      parentMenuId: 0,
      parentOptions: [],
      menuLoading: false,
      menuResult: null
    }
  },
  computed: {
    overwrite() {
      return this.strategyMode !== 'skip'
    },
    skipDrifted() {
      return this.strategyMode === 'safe'
    },
    /** 磁盘内容相对上次生成被改过的文件数（来自后端台账 md5 比对） */
    driftCount() {
      return this.allRows.filter(r => r.drift === 'DRIFTED').length
    },
    /** 保守覆盖下会被跳过的「已手改」文件数：只统计本来就要覆盖的（NEW 不存在，谈不上漂移） */
    driftedOverwriteCount() {
      return this.allRows.filter(r => r.status !== 'NEW' && r.drift === 'DRIFTED').length
    },
    strategyNote() {
      if (this.strategyMode === 'skip') {
        return '跳过模式：已存在文件一律不写，只有新增文件落盘（切「覆盖」可查看会改动哪些文件）。'
      }
      if (this.strategyMode === 'safe') {
        return this.driftCount
          ? `覆盖模式（保守）：已存在文件先备份再写入；其中 ${this.driftCount} 个文件相对上次生成被手改过，将按策略跳过并在结果里标注「已手改」。`
          : '覆盖模式（保守）：已存在文件先备份再写入；当前没有检测到手改文件，行为等同强制覆盖。'
      }
      return '强制覆盖：不看漂移，全部按合并规则写入，手改内容可能在块内被替换（备份仍保留，可回滚）。'
    },
    canNext() {
      if (this.step === 0) return this.selectedTables.length > 0
      return true
    },
    diffRows() {
      return this.onlyChanged ? this.allRows.filter(r => r.status !== 'UNCHANGED') : this.allRows
    },
    diffGroups() {
      // 归属由后端判定（与写盘同源），不再用 path.startsWith('src/') 猜——
      // 前端工程带 frontend-path 子目录时（demo-web/src/api/x.js）按前缀分会分错
      const backend = this.diffRows.filter(r => r.side === 'BACKEND')
      const frontend = this.diffRows.filter(r => r.side !== 'BACKEND')
      const groups = []
      if (backend.length) groups.push({ name: 'backend · 后端产物', rows: backend })
      if (frontend.length) groups.push({ name: 'frontend · 前端产物', rows: frontend })
      return groups
    },
    skipCount() {
      // 「跳过」是策略推导出来的，不是内容事实：跳过模式下所有已存在文件都不写；
      // 保守覆盖模式下只有「已手改」的会被跳过
      if (!this.overwrite) return this.allRows.filter(r => r.status !== 'NEW').length
      if (this.skipDrifted) {
        return this.allRows.filter(r => r.status !== 'NEW' && r.drift === 'DRIFTED').length
      }
      return 0
    },
    writtenCount() {
      return this.writeResults.filter(r => ['WRITTEN', 'OVERWRITTEN', 'MERGED'].includes(r.status)).length
    },
    skippedWriteCount() {
      return this.writeResults.filter(r => r.status === 'SKIPPED').length
    },
    verifySummary() {
      if (!this.verifyResult) return '校验未执行'
      return this.verifyResult.passed
        ? `校验通过（PASS ${this.verifyResult.pass} / WARN ${this.verifyResult.warn}）`
        : `存在 FAIL ${this.verifyResult.fail} 项，请处理`
    },
    isInternal() {
      return !!this.menuResult && this.menuResult.outlet === 'internal'
    },
    menuRows() {
      return (this.menuResult && this.menuResult.menus) || []
    },
    // artifact 出口每张表产出一份 SQL，状态是 GENERATED 而不是 REGISTERED，
    // 早先只数 REGISTERED/PLANNED，导致「菜单 0 新建」恒为假
    menuGeneratedCount() {
      return this.menuRows.filter(m => ['REGISTERED', 'GENERATED', 'PLANNED'].includes(m.status)).length
    },
    menuSkippedCount() {
      return this.menuRows.filter(m => m.status === 'SKIPPED').length
    },
    menuFailCount() {
      return this.menuRows.filter(m => m.status === 'ERROR').length
    },
    menuSummary() {
      if (this.isInternal) {
        return `菜单 ${this.menuGeneratedCount} 新建 / ${this.menuSkippedCount} 已存在 / ${this.menuFailCount} 失败`
      }
      // artifact：不写平台 sys_menu，只说产出了几份 SQL
      return `菜单 SQL ${this.menuGeneratedCount} 份已产出 / ${this.menuFailCount} 失败`
    }
  },
  // 差异是「渲染结果 vs 磁盘现状」的事实，切写盘策略不改变差异本身，
  // 只改变第③步的统计口径（由 computed 自动跟随），所以这里不需要监听 overwrite 重新拉取。
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
    removePicked(id) {
      const table = this.selectedTables.find(t => t.id === id)
      if (table && this.$refs.tableSelector) {
        this.$refs.tableSelector.toggleRowSelection(table, false)
      }
      // 兜底：跨页行若不在表格当前 data 里，toggle 可能无效，直接从选中列表剔除
      this.selectedTables = this.selectedTables.filter(t => t.id !== id)
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
      if (!this.selectedTables.length) {
        this.allRows = []
        return
      }
      this.diffLoading = true
      this.activeFile = null
      try {
        // 恒拉全量，展示层再做「仅看变更」过滤，保证第③步统计与策略一致
        const res = await diffGenCode(this.selectedIds(), this.overwrite, false)
        this.allRows = res.data || []
      } finally {
        this.diffLoading = false
      }
    },
    statOf(kind) {
      // 第③步统计的是「会怎么处理」，不是「内容变没变」：
      // 跳过模式下已存在文件（改动 + 无变化）全部不写，覆盖模式下才细分成覆盖/合并与无变化重写
      const count = s => this.allRows.filter(r => r.status === s).length
      if (kind === 'NEW') return count('NEW')
      if (!this.overwrite) return 0
      if (kind === 'MODIFIED') return count('MODIFIED')
      if (kind === 'UNCHANGED') return count('UNCHANGED')
      return 0
    },
    async doWrite() {
      this.writeLoading = true
      this.menuResult = null
      this.verifyResult = null
      try {
        const res = await writeGenCode(this.selectedIds(), this.overwrite, this.skipDrifted)
        this.writeResults = res.data || []
        this.batchNo = (this.writeResults[0] && this.writeResults[0].batchNo) || ''
        this.writeDone = true
        this.step = 3
        this.$message.success(`写盘完成：批次 ${this.batchNo}`)
        // 必须先注册菜单（会重建 gen-routes.js）再校验：
        // 反过来的话校验读到的是重建前的路由文件，必然误报「路由未注册」
        if (this.registerMenu) {
          await this.registerMenus()
        }
        await this.runVerify()
      } finally {
        this.writeLoading = false
      }
    },
    async runVerify() {
      this.verifyLoading = true
      try {
        const vr = await verifyGenOutput(this.selectedIds())
        this.verifyResult = vr.data
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.verifyLoading = false
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
        } else if (this.isInternal) {
          this.$message.success('菜单已注册，前端路由文件已重建，刷新页面即可在侧边栏看到')
        } else {
          // artifact 出口不写本平台菜单表，不能再说「刷新即可见」
          this.$message.success('路由文件已重建；菜单 SQL 已产出到目标工程，需在目标库执行后目标系统才可见')
        }
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.menuLoading = false
      }
    },
    menuTagType(status) {
      const map = { REGISTERED: 'success', GENERATED: 'success', PLANNED: 'success', SKIPPED: 'info', ERROR: 'danger' }
      return map[status] || 'info'
    },
    menuStatusText(status) {
      const map = {
        REGISTERED: '已注册',
        GENERATED: '已产出 SQL',
        PLANNED: '待注册',
        SKIPPED: '已存在',
        ERROR: '失败'
      }
      return map[status] || status
    },
    goLedger() {
      // 实际菜单路由是 /gen/output（sys_menu 1050）；原先写 /tool/genOutput 会直接 404
      this.$router.push({ path: '/gen/output', query: { batchNo: this.batchNo } })
    },
    shortPath(path) {
      const parts = String(path || '').split('/')
      return parts.length > 3 ? '…/' + parts.slice(-3).join('/') : path
    },
    statusTagType(status) {
      const map = { NEW: 'success', MODIFIED: 'warning', UNCHANGED: 'info' }
      return map[status] || 'info'
    },
    statusText(status, row) {
      const map = { NEW: '新增', MODIFIED: '改动', UNCHANGED: '无变化' }
      const label = map[status] || status
      return status === 'UNCHANGED' ? label : `${label} +${row.additions} -${row.deletions}`
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
.strategy-list { margin-bottom: 4px; }
.strategy-option { display: block; border: 1px solid var(--cg-border); border-radius: 4px; padding: 14px 16px; margin-bottom: 12px; cursor: pointer; }
.strategy-option.active { border-color: var(--cg-primary); background: var(--cg-primary-soft); }
.strategy-option .el-radio { margin-right: 0; }
.strategy-option .el-radio__label { font-size: 14px; }
.strategy-option p { color: var(--cg-text-secondary); font-size: 12px; margin: 6px 0 0 24px; }
.strategy-stats { display: flex; gap: 12px; margin-top: 16px; }
.stat-note { color: var(--cg-text-secondary); font-size: 12px; margin-top: 10px; }
.picked-bar { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.tree-empty { color: var(--cg-text-secondary); font-size: 12px; padding: 24px 8px; line-height: 1.9; }
.tree-empty p { margin: 0; }
.stat { flex: 1; border: 1px solid var(--cg-border-light); border-radius: 4px; padding: 12px 16px; background: var(--cg-bg-page); }
.stat b { display: block; font-size: 22px; }
.stat span { color: var(--cg-text-secondary); font-size: 12px; }
.c-green { color: var(--cg-success); } .c-orange { color: var(--cg-warning); } .c-gray { color: var(--cg-text-secondary); } .c-red { color: var(--cg-danger); }
.menu-option { padding: 2px 0 6px; }
.menu-parent { display: flex; align-items: center; gap: 10px; margin-top: 10px; flex-wrap: wrap; }
.menu-msg { margin-bottom: 10px; }
.verify-stats { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.final-tip { margin-top: 14px; color: var(--cg-text-regular); }
.wizard-foot { display: flex; margin-top: 18px; }
</style>
