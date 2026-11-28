<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">MCP Server</span>
      <span class="page-desc">通过 Streamable HTTP 向 MCP 客户端开放 SDD、Converge 与代码生成流水线能力。</span>
    </div>

    <el-alert type="warning" :closable="false" show-icon class="security-alert">
      <span slot="title">写操作必须在 arguments 中显式传入 confirmed=true；所有 tools/call 调用均记录审计日志。</span>
    </el-alert>

    <div class="config-grid">
      <el-card shadow="never" v-loading="metaLoading">
        <div slot="header" class="card-header">
          <span>连接配置</span>
          <el-button type="text" icon="el-icon-refresh" @click="refresh">刷新</el-button>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="服务名称">{{ config.serverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ config.serverVersion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="协议">{{ config.protocol || '—' }}</el-descriptions-item>
          <el-descriptions-item label="端点"><span class="mono">{{ endpointUrl }}</span></el-descriptions-item>
          <el-descriptions-item label="认证"><span class="mono">{{ config.authentication || '—' }}</span></el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <div slot="header" class="card-header">
          <span>MCP Client 配置示例</span>
          <small>替换实际服务地址和 token</small>
        </div>
        <pre class="client-config">{{ clientConfig }}</pre>
      </el-card>
    </div>

    <el-card shadow="never" class="tools-card">
      <div slot="header" class="card-header">
        <span>Tools 清单</span>
        <small>共 {{ tools.length }} 个工具</small>
      </div>
      <el-table :data="tools" size="small" border v-loading="metaLoading">
        <el-table-column prop="name" label="工具名称" min-width="220">
          <template slot-scope="{ row }"><span class="mono">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.mutating ? 'danger' : 'success'">{{ row.mutating ? '写操作' : '只读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="inputSchema" label="Input Schema" min-width="320" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono">{{ row.inputSchema }}</span></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="logs-card">
      <div slot="header" class="card-header">
        <span>调用记录</span>
        <small>仅记录 tools/call，按调用时间倒序</small>
      </div>
      <div class="toolbar">
        <el-select v-model="query.toolName" size="small" clearable filterable placeholder="全部工具" class="tool-filter">
          <el-option v-for="tool in tools" :key="tool.name" :label="tool.name" :value="tool.name" />
        </el-select>
        <el-select v-model="query.status" size="small" clearable placeholder="全部状态" class="status-filter">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="search">查询</el-button>
        <el-button size="small" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
      </div>

      <el-table :data="logs" size="small" border v-loading="logsLoading">
        <el-table-column prop="createTime" label="调用时间" width="170" />
        <el-table-column prop="toolName" label="工具" min-width="210" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono">{{ row.toolName }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时 (ms)" width="100" align="right" />
        <el-table-column prop="argumentsJson" label="调用参数" min-width="230" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono">{{ row.argumentsJson || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="结果 / 错误" min-width="260" show-overflow-tooltip>
          <template slot-scope="{ row }"><span class="mono">{{ row.errorMessage || row.resultSummary || '—' }}</span></template>
        </el-table-column>
        <template slot="empty">暂无 MCP 工具调用记录</template>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script>
import { getMcpConfig, getMcpTools, getMcpLogs } from '@/api/mcp'

export default {
  name: 'GenMcp',
  data() {
    return {
      config: {},
      tools: [],
      logs: [],
      total: 0,
      metaLoading: false,
      logsLoading: false,
      query: { pageNum: 1, pageSize: 10, toolName: '', status: '' }
    }
  },
  computed: {
    endpointUrl() {
      return `http://localhost:8120${this.config.endpoint || '/mcp'}`
    },
    clientConfig() {
      return JSON.stringify({
        mcpServers: {
          [this.config.serverName || 'codegen-server']: {
            url: this.endpointUrl,
            headers: { Authorization: 'Bearer <token>' }
          }
        }
      }, null, 2)
    }
  },
  created() {
    this.loadMeta()
    this.loadLogs()
  },
  methods: {
    loadMeta() {
      this.metaLoading = true
      Promise.all([getMcpConfig(), getMcpTools()]).then(([configRes, toolsRes]) => {
        this.config = configRes.data || {}
        this.tools = toolsRes.data || []
      }).finally(() => { this.metaLoading = false })
    },
    loadLogs() {
      this.logsLoading = true
      getMcpLogs(this.query).then(res => {
        const page = res.data || {}
        this.logs = page.records || []
        this.total = page.total || 0
      }).finally(() => { this.logsLoading = false })
    },
    refresh() {
      this.loadMeta()
      this.loadLogs()
    },
    search() {
      this.query.pageNum = 1
      this.loadLogs()
    },
    resetQuery() {
      this.query = { pageNum: 1, pageSize: 10, toolName: '', status: '' }
      this.loadLogs()
    },
    handleSizeChange(size) {
      this.query.pageSize = size
      this.query.pageNum = 1
      this.loadLogs()
    },
    handlePageChange(page) {
      this.query.pageNum = page
      this.loadLogs()
    }
  }
}
</script>

<style scoped>
.security-alert { margin-bottom: 14px; }
.config-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); gap: 14px; margin-bottom: 14px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-header small { color: var(--cg-text-secondary); font-weight: 400; }
.client-config { margin: 0; padding: 12px; min-height: 190px; overflow: auto; background: #f6f8fa; color: #263445; font: 12.5px/1.6 Consolas, Monaco, monospace; }
.tools-card { margin-bottom: 14px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.tool-filter { width: 260px; }
.status-filter { width: 140px; }
.pager { margin-top: 14px; text-align: right; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }
@media (max-width: 1100px) {
  .config-grid { grid-template-columns: 1fr; }
}
</style>
