<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="写盘文件级台账：/gen/write、一键 apply、批量接管向导三个入口的每次写盘逐文件记账；ZIP 下载不记账。"
    />

    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item label="表名">
          <el-input v-model="query.tableName" placeholder="请输入表名（模糊）" clearable style="width: 170px" @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="query.batchNo" placeholder="GEN+yyyyMMddHHmmss" clearable style="width: 200px" @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="query.action" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="a in actions" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最近批次">
          <el-select v-model="batchFilter" placeholder="全部" clearable style="width: 220px" @change="applyBatchFilter">
            <el-option v-for="b in batches" :key="b.batchNo" :label="`${b.batchNo}（${b.files} 文件）`" :value="b.batchNo" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right">
          <el-button icon="el-icon-data-analysis" @click="openBatches">批次摘要</el-button>
          <el-button type="warning" icon="el-icon-finished" @click="openVerify">真校验（勾选表）</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 台账列表 -->
    <el-card shadow="never">
      <div slot="header" class="card-header">
        <span>写盘明细</span>
        <span class="header-tip">动作：WRITTEN 新建 / OVERWRITTEN 覆盖 / MERGED 合并块 / SKIPPED 跳过 / ERROR 失败</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="batchNo" label="批次号" width="175">
          <template slot-scope="{ row }"><span class="mono-text">{{ row.batchNo }}</span></template>
        </el-table-column>
        <el-table-column prop="tableName" label="表名" width="130" />
        <el-table-column label="分组" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.fileGroup === 'backend' ? 'primary' : 'success'" size="small">{{ row.fileGroup }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="relPath" label="相对路径" min-width="300" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono-text">{{ row.relPath }}</span></template>
        </el-table-column>
        <el-table-column label="动作" width="130" align="center">
          <template slot-scope="{ row }">
            <el-tooltip :disabled="!row.message" :content="row.message" placement="top">
              <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="字节数" width="90" align="right">
          <template slot-scope="{ row }">{{ row.fileSize || '—' }}</template>
        </el-table-column>
        <el-table-column label="MD5" width="140" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono-text">{{ row.md5 || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="createTime" label="写盘时间" width="160" />
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

    <!-- 批次摘要 -->
    <el-dialog title="批次摘要（按批次汇总写盘台账）" :visible.sync="batchesVisible" width="1080px" top="5vh">
      <el-table v-loading="batchesLoading" :data="batches" border stripe size="small">
        <el-table-column prop="batchNo" label="批次号" width="175">
          <template slot-scope="{ row }"><span class="mono-text">{{ row.batchNo }}</span></template>
        </el-table-column>
        <el-table-column prop="tables" label="表数" width="60" align="center" />
        <el-table-column prop="files" label="文件数" width="70" align="center" />
        <el-table-column prop="written" label="WRITTEN" width="90" align="center" />
        <el-table-column prop="overwritten" label="OVERWRITTEN" width="110" align="center" />
        <el-table-column prop="merged" label="MERGED" width="80" align="center" />
        <el-table-column prop="skipped" label="SKIPPED" width="80" align="center" />
        <el-table-column label="ERROR" width="70" align="center">
          <template slot-scope="{ row }">
            <span :style="row.errors > 0 ? 'color:var(--cg-danger);font-weight:600' : ''">{{ row.errors }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalBytes" label="总字节" width="90" align="right" />
        <el-table-column prop="operator" label="操作人" width="80" />
        <el-table-column prop="lastTime" label="最后写盘时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openManifest(row)">查看清单</el-button>
            <el-button type="text" @click="applyBatchFilter(row.batchNo, true)">回填过滤</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="dlg-tip">摘要与 ZIP manifest.txt / 批次清单同构同源（路径+字节数+MD5）；ERROR&gt;0 的批次建议先看明细再决定是否回滚。</div>
    </el-dialog>

    <!-- 批次清单（与 ZIP manifest 同构） -->
    <el-dialog :title="`批次清单 · ${manifestBatch.batchNo}（与 ZIP manifest.txt 同构）`" :visible.sync="manifestVisible" width="900px" top="5vh">
      <el-table v-loading="manifestLoading" :data="manifestRows" border stripe size="small">
        <el-table-column prop="relPath" label="相对路径" min-width="360" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono-text">{{ row.relPath }}</span></template>
        </el-table-column>
        <el-table-column prop="fileSize" label="字节数" width="90" align="right" />
        <el-table-column prop="md5" label="MD5" width="260" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono-text">{{ row.md5 }}</span></template>
        </el-table-column>
        <el-table-column label="动作" width="120" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="dlg-tip">仅列实际写盘文件（WRITTEN/OVERWRITTEN/MERGED）；线下解压 ZIP 的 manifest.txt 逐行格式与此一致。</div>
    </el-dialog>

    <!-- 真校验 -->
    <el-dialog title="前端产物真校验（读磁盘实际文件静态解析）" :visible.sync="verifyVisible" width="960px" top="4vh" :close-on-click-modal="false">
      <el-form :inline="true" @submit.native.prevent>
        <el-form-item label="校验对象">
          <el-select v-model="verifyIds" multiple filterable placeholder="选择要校验的表配置" style="width: 520px">
            <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName}（${t.tableComment || t.className}）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-finished" :loading="verifyLoading" @click="runVerify">开始校验</el-button>
        </el-form-item>
      </el-form>

      <template v-if="verifyResult">
        <div class="verify-stats">
          <el-tag :type="verifyResult.passed ? 'success' : 'danger'" size="medium">
            {{ verifyResult.passed ? '通过（无 FAIL 项）' : '存在 FAIL 项' }}
          </el-tag>
          <el-tag type="success" size="small">PASS {{ verifyResult.pass }}</el-tag>
          <el-tag type="warning" size="small">WARN {{ verifyResult.warn }}</el-tag>
          <el-tag type="danger" size="small">FAIL {{ verifyResult.fail }}</el-tag>
          <span class="header-tip">共 {{ verifyResult.total }} 项</span>
        </div>
        <el-table :data="verifyResult.items" border stripe size="small" max-height="420">
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
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { getGenOutputPage, getGenOutputBatches, verifyGenOutput } from '@/api/genOutput'
import { getGenTablePage } from '@/api/gen'

const ACTION_OPTIONS = [
  { value: 'WRITTEN', label: 'WRITTEN（新建）' },
  { value: 'OVERWRITTEN', label: 'OVERWRITTEN（覆盖）' },
  { value: 'MERGED', label: 'MERGED（合并块）' },
  { value: 'SKIPPED', label: 'SKIPPED（跳过）' },
  { value: 'ERROR', label: 'ERROR（失败）' }
]

export default {
  name: 'GenOutput',
  data() {
    return {
      actions: ACTION_OPTIONS,
      query: { page: 1, size: 10, tableName: '', batchNo: '', action: '' },
      batchFilter: '',
      list: [],
      total: 0,
      loading: false,
      batches: [],
      batchesVisible: false,
      batchesLoading: false,
      manifestBatch: { batchNo: '' },
      manifestVisible: false,
      manifestLoading: false,
      manifestRows: [],
      verifyVisible: false,
      verifyLoading: false,
      verifyIds: [],
      verifyResult: null,
      tables: []
    }
  },
  created() {
    // 批量接管向导「前往台账」携带批次号
    if (this.$route.query.batchNo) {
      this.query.batchNo = this.$route.query.batchNo
    }
    this.fetchList()
    this.fetchBatches()
  },
  methods: {
    async fetchList() {
      this.loading = true
      try {
        const res = await getGenOutputPage(this.query)
        this.list = res.data.list
        this.total = res.data.total
      } finally {
        this.loading = false
      }
    },
    async fetchBatches() {
      const res = await getGenOutputBatches(20)
      this.batches = res.data || []
    },
    handleSearch() {
      this.batchFilter = ''
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: 10, tableName: '', batchNo: '', action: '' }
      this.batchFilter = ''
      this.fetchList()
    },
    handleSizeChange() {
      this.query.page = 1
      this.fetchList()
    },
    applyBatchFilter(batchNo, closeBatches) {
      this.query.batchNo = batchNo
      this.batchFilter = batchNo
      this.query.page = 1
      if (closeBatches) {
        this.batchesVisible = false
      }
      this.fetchList()
    },
    async openBatches() {
      this.batchesVisible = true
      this.batchesLoading = true
      try {
        await this.fetchBatches()
      } finally {
        this.batchesLoading = false
      }
    },
    async openManifest(batch) {
      this.manifestBatch = batch
      this.manifestVisible = true
      this.manifestLoading = true
      try {
        // 仅实际写盘文件（与 ZIP manifest 一致）
        const res = await getGenOutputPage({ page: 1, size: 100, batchNo: batch.batchNo })
        this.manifestRows = res.data.list.filter(r => ['WRITTEN', 'OVERWRITTEN', 'MERGED'].includes(r.action))
      } finally {
        this.manifestLoading = false
      }
    },
    async openVerify() {
      this.verifyVisible = true
      this.verifyResult = null
      this.verifyIds = []
      if (!this.tables.length) {
        const res = await getGenTablePage({ page: 1, size: 200 })
        this.tables = res.data.list
      }
    },
    async runVerify() {
      if (!this.verifyIds.length) {
        this.$message.warning('请先选择要校验的表')
        return
      }
      this.verifyLoading = true
      try {
        const res = await verifyGenOutput(this.verifyIds)
        this.verifyResult = res.data
      } finally {
        this.verifyLoading = false
      }
    },
    actionTagType(action) {
      const map = { WRITTEN: 'success', OVERWRITTEN: '', MERGED: 'warning', SKIPPED: 'info', ERROR: 'danger' }
      return map[action] || 'info'
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
.card-header { display: flex; align-items: center; justify-content: space-between; }
.header-tip { color: var(--cg-text-secondary); font-size: 12px; }
.mono-text { font-family: Consolas, Monaco, monospace; font-size: 12px; }
.pagination { margin-top: 14px; text-align: right; }
.dlg-tip { color: var(--cg-text-secondary); font-size: 12px; margin-top: 10px; }
.verify-stats { display: flex; gap: 10px; align-items: center; margin: 8px 0 12px; }
</style>
