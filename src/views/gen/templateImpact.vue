<template>
  <!--
    模板影响面预览（只读）。

    菜单说明：本项目业务菜单由后端 sys_menu 下发（/auth/routers），正式环境需补一条
    path=/gen/template-impact、component=gen/templateImpact 的菜单记录；当前由
    src/store/modules/permission.js 的静态入口注入，不直接改库。
  -->
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">模板影响面预览</span>
      <span class="page-desc">改 .ftl 前先看会影响哪些表、产出哪些文件；只读预览，不写盘、不改库。</span>
    </div>

    <div class="impact-body">
      <el-card class="tpl-panel" shadow="never">
        <div slot="header" class="panel-head">
          <span>模板列表</span>
          <span class="muted">{{ templates.length }} 个</span>
        </div>
        <el-input
          v-model="keyword"
          size="small"
          clearable
          placeholder="搜索模板路径"
          prefix-icon="el-icon-search"
        />
        <div class="tpl-item all" :class="{ active: !selected }" @click="selectTemplate('')">
          <div class="tpl-name">全部模板（聚合视图）</div>
          <div class="tpl-meta">一次算出所有模板的影响面</div>
        </div>
        <div class="tpl-scroll">
          <div
            v-for="t in shownTemplates"
            :key="t.template"
            class="tpl-item"
            :class="{ active: selected === t.template }"
            @click="selectTemplate(t.template)"
          >
            <div class="tpl-name">{{ t.name }}</div>
            <div class="tpl-meta">
              <span class="mono tpl-dir">{{ t.directory }}</span>
              <el-tag size="mini" type="info">引用 {{ t.refCount }}</el-tag>
            </div>
          </div>
          <div v-if="!shownTemplates.length" class="muted empty">没有匹配的模板</div>
        </div>
      </el-card>

      <el-card class="impact-panel" shadow="never">
        <div slot="header" class="panel-head">
          <span class="mono">{{ selected || '全部模板' }}</span>
          <span class="summary">
            影响 {{ impact.affectedTables.length }} 张表 · 产物 {{ impact.outputCount }} 个
          </span>
        </div>

        <el-table v-loading="loading" :data="impact.affectedTables" size="mini" border>
          <el-table-column type="expand">
            <template slot-scope="{ row }">
              <div class="outputs">
                <div v-for="p in row.outputs" :key="p" class="output-line mono">{{ p }}</div>
                <div v-if="row.error" class="error">渲染失败：{{ row.error }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="表名" min-width="160">
            <template slot-scope="{ row }">
              <span class="mono">{{ row.tableName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="tableComment" label="注释" min-width="150" show-overflow-tooltip />
          <el-table-column label="形态" width="230">
            <template slot-scope="{ row }">
              <el-tag v-for="h in row.modeHints" :key="h" size="mini" class="hint">{{ h }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="产物数" width="90" align="center">
            <template slot-scope="{ row }">{{ row.outputs.length }}</template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!loading && !impact.affectedTables.length"
          description="没有表会产出该模板的文件"
        />
      </el-card>
    </div>
  </div>
</template>

<script>
import { getTemplateFiles, getTemplateImpact } from '@/api/genTemplateImpact'

export default {
  name: 'TemplateImpact',
  data() {
    return {
      keyword: '',
      templates: [],
      selected: '',
      loading: false,
      impact: { template: '', affectedTables: [], outputCount: 0 }
    }
  },
  computed: {
    shownTemplates() {
      const kw = this.keyword.trim().toLowerCase()
      if (!kw) {
        return this.templates
      }
      return this.templates.filter(t => t.template.toLowerCase().indexOf(kw) !== -1)
    }
  },
  created() {
    this.loadTemplates()
    this.loadImpact('')
  },
  methods: {
    async loadTemplates() {
      const res = await getTemplateFiles()
      this.templates = res.data || []
    },
    selectTemplate(template) {
      if (this.selected === template) {
        return
      }
      this.selected = template
      this.loadImpact(template)
    },
    async loadImpact(template) {
      this.loading = true
      try {
        const res = await getTemplateImpact(template)
        this.impact = res.data || { template: template || 'ALL', affectedTables: [], outputCount: 0 }
      } finally {
        this.loading = false
      }
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
.impact-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.tpl-panel {
  flex: 0 0 300px;
  width: 300px;
}
.impact-panel {
  flex: 1;
  min-width: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 600;
}
.summary {
  font-size: 12px;
  font-weight: 400;
  color: var(--cg-text-secondary);
}
.tpl-scroll {
  margin-top: 8px;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}
.tpl-item {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
}
.tpl-item:hover {
  background: var(--cg-bg-hover, #f5f7fa);
}
.tpl-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.tpl-item.all {
  margin-top: 8px;
}
.tpl-name {
  font-size: 13px;
  font-weight: 500;
}
.tpl-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--cg-text-secondary);
}
.tpl-dir {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.outputs {
  padding: 4px 12px 8px 12px;
}
.output-line {
  line-height: 20px;
  color: var(--cg-text-regular, #303133);
}
.error {
  margin-top: 4px;
  font-size: 12px;
  color: #f56c6c;
}
.hint {
  margin-right: 4px;
}
.mono {
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
.muted {
  color: var(--cg-text-placeholder, #909399);
}
.empty {
  padding: 12px;
  font-size: 12px;
}
</style>
