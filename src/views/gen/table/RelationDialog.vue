<template>
  <el-dialog
    title="多对多关系识别"
    :visible.sync="visible"
    width="880px"
    append-to-body
    @open="load"
  >
    <div class="rel-tip">
      判定条件：表里恰好两个外键列且指向不同表、这两个外键有联合唯一键（或整表无其他业务列）、
      表名同时包含两侧表名词根。标记后中间表会被打上
      <code>relation_type=middle</code>，两个外键列自动变成关联下拉。
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      border
      size="small"
      max-height="420"
      @selection-change="onSelect"
    >
      <el-table-column type="selection" width="46" :selectable="row => !row.configured" />
      <el-table-column prop="middleTable" label="中间表" width="150" show-overflow-tooltip />
      <el-table-column prop="middleComment" label="说明" width="130" show-overflow-tooltip />
      <el-table-column label="关联两侧" min-width="240">
        <template slot-scope="{ row }">
          <span class="rel-side">{{ row.leftComment }}</span>
          <span class="rel-key">.{{ row.leftKey }}</span>
          <i class="el-icon-sort rel-icon" />
          <span class="rel-side">{{ row.rightComment }}</span>
          <span class="rel-key">.{{ row.rightKey }}</span>
        </template>
      </el-table-column>
      <el-table-column label="额外列" width="120">
        <template slot-scope="{ row }">
          <span v-if="row.extraColumns && row.extraColumns.length">{{ row.extraColumns.join(', ') }}</span>
          <span v-else class="muted">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="判定依据" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="{ row }">
          <el-tag v-if="row.configured" size="mini" type="success">已标记</el-tag>
          <el-tag v-else size="mini">待标记</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !items.length" description="没识别到中间表" :image-size="70" />

    <template slot="footer">
      <span class="rel-foot">
        已选 {{ selected.length }} 项；中间表需先导入为表配置才会被标记
      </span>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" :disabled="!selected.length" @click="doApply">
        标记
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
import { detectRelation, applyRelation } from '@/api/gen'

export default {
  name: 'RelationDialog',
  data() {
    return {
      visible: false,
      loading: false,
      saving: false,
      schema: 'shop',
      items: [],
      selected: []
    }
  },
  methods: {
    open(schema) {
      this.schema = schema || 'shop'
      this.items = []
      this.selected = []
      this.visible = true
    },
    async load() {
      this.loading = true
      try {
        const res = await detectRelation(this.schema)
        this.items = res.data || []
      } catch (e) {
        this.items = []
      } finally {
        this.loading = false
      }
    },
    onSelect(rows) {
      this.selected = rows
    },
    async doApply() {
      const names = this.selected.map(r => r.middleTable)
      this.saving = true
      try {
        const res = await applyRelation(this.schema, names)
        this.$message.success(`已标记 ${res.data || 0} 个中间表`)
        this.visible = false
        this.$emit('applied')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.rel-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: var(--cg-bg-page);
  border-left: 3px solid var(--cg-success);
  font-size: 12px;
  line-height: 1.7;
  color: var(--cg-text-regular);
}
.rel-tip code {
  background: var(--cg-bg-hover);
  padding: 0 4px;
  border-radius: 2px;
  color: var(--cg-primary);
}
.rel-side {
  font-weight: 500;
}
.rel-key {
  font-family: Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--cg-text-secondary);
}
.rel-icon {
  margin: 0 6px;
  color: var(--cg-primary);
}
.rel-foot {
  float: left;
  font-size: 12px;
  color: var(--cg-text-secondary);
  line-height: 32px;
}
.muted {
  color: var(--cg-text-placeholder);
}
</style>
