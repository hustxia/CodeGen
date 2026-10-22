<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">接口目录</span>
      <span class="page-desc">
        按表聚合已生成的接口，权限编码与菜单按钮一致；标「未生成」的表还只是配置。
      </span>
    </div>

    <el-form :inline="true" size="small" class="filter">
      <el-form-item label="关键字">
        <el-input v-model="keyword" placeholder="表名 / 注释 / 类名" clearable style="width: 240px" @keyup.enter.native="load" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="load">查询</el-button>
        <el-button icon="el-icon-refresh" @click="reset">重置</el-button>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="onlyGenerated">只看已生成</el-checkbox>
      </el-form-item>
    </el-form>

    <el-empty v-if="!loading && !shown.length" description="没有匹配的表配置" />

    <el-card v-for="t in shown" :key="t.tableId" shadow="never" class="api-card">
      <div slot="header" class="api-head">
        <span class="api-title">{{ t.tableComment }}</span>
        <span class="api-tbl">{{ t.tableName }}</span>
        <el-tag v-if="t.generated" size="mini" type="success">已生成</el-tag>
        <el-tag v-else size="mini" type="info">未生成</el-tag>
        <span class="api-base">{{ t.basePath }}</span>
      </div>

      <el-table :data="t.apis" size="mini" border>
        <el-table-column label="方法" width="80" align="center">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="methodType(row.method)">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="280" show-overflow-tooltip />
        <el-table-column prop="summary" label="说明" min-width="180" />
        <el-table-column prop="perms" label="权限编码" width="180">
          <template slot-scope="{ row }">
            <span v-if="row.perms" class="mono">{{ row.perms }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="frontendFn" label="前端方法" width="200">
          <template slot-scope="{ row }">
            <span v-if="row.frontendFn" class="mono">{{ row.frontendFn }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!t.apis.length" class="api-empty">
        该表未开启 Controller 或 Service 生成，暂无接口。
      </div>
    </el-card>
  </div>
</template>

<script>
import { getApiCatalog } from '@/api/gen'

export default {
  name: 'ApiCatalog',
  data() {
    return {
      loading: false,
      keyword: '',
      onlyGenerated: false,
      list: []
    }
  },
  computed: {
    shown() {
      return this.onlyGenerated ? this.list.filter(t => t.generated) : this.list
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await getApiCatalog(this.keyword)
        this.list = res.data || []
      } finally {
        this.loading = false
      }
    },
    reset() {
      this.keyword = ''
      this.load()
    },
    methodType(m) {
      return { GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger' }[m] || 'info'
    }
  }
}
</script>

<style scoped>
.page-head {
  margin-bottom: 12px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  margin-right: 10px;
}
.page-desc {
  font-size: 12px;
  color: var(--cg-text-secondary);
}
.filter {
  margin-bottom: 8px;
}
.api-card {
  margin-bottom: 12px;
}
.api-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.api-title {
  font-weight: 600;
}
.api-tbl,
.api-base {
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--cg-text-secondary);
}
.api-base {
  margin-left: auto;
}
.mono {
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
.muted {
  color: var(--cg-text-placeholder);
}
.api-empty {
  padding: 12px;
  font-size: 12px;
  color: var(--cg-text-secondary);
}
</style>
