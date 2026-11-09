<template>
  <el-dialog
    title="字典自动识别"
    :visible.sync="visible"
    width="920px"
    append-to-body
    @open="load"
  >
    <div class="dict-detect-tip">
      从列类型 <code>enum(...)</code> 和列注释里的枚举列表（如
      <code>订单状态:待付款,已付款</code>）识别可用字典。勾选后落库，会同时回填字段配置的
      <code>dict_type</code>。
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      border
      size="small"
      max-height="440"
      @selection-change="onSelect"
    >
      <el-table-column type="selection" width="46" :selectable="rowSelectable" />
      <el-table-column prop="dictCode" label="字典编码" width="200" show-overflow-tooltip />
      <el-table-column prop="dictName" label="字典名称" width="130" show-overflow-tooltip />
      <el-table-column label="来源" width="80" align="center">
        <template slot-scope="{ row }">
          <el-tag size="mini" :type="row.source === 'enum' ? 'success' : 'info'">
            {{ row.source === 'enum' ? 'enum' : '注释' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="选项" min-width="260">
        <template slot-scope="{ row }">
          <div class="dict-opts">
            <el-tag
              v-for="o in row.options"
              :key="o.value"
              size="mini"
              type="info"
              class="dict-opt"
            >
              {{ o.label }}<span v-if="o.label !== o.value" class="dict-opt-v">={{ o.value }}</span>
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template slot-scope="{ row }">
          <el-tag v-if="row.bound" size="mini" type="success">已绑定</el-tag>
          <el-tag v-else-if="row.exists" size="mini" type="warning">已存在</el-tag>
          <el-tag v-else size="mini">待新建</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="columnComment" label="列注释" width="150" show-overflow-tooltip />
    </el-table>

    <el-empty v-if="!loading && !items.length" description="没识别到可转成字典的列" :image-size="70" />

    <template slot="footer">
      <span class="dict-detect-foot">
        已选 {{ selected.length }} 项；已存在的字典只补缺失项，不覆盖已改过的标签
      </span>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button
        size="small"
        type="primary"
        :loading="saving"
        :disabled="!selected.length"
        @click="doApply"
      >落库</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { detectGenDict, applyGenDict } from '@/api/gen'

export default {
  name: 'DictDetectDialog',
  data() {
    return {
      visible: false,
      loading: false,
      saving: false,
      tableId: null,
      items: [],
      selected: []
    }
  },
  methods: {
    open(tableId) {
      this.tableId = tableId
      this.items = []
      this.selected = []
      this.visible = true
    },
    async load() {
      if (!this.tableId) return
      this.loading = true
      try {
        const res = await detectGenDict(this.tableId)
        this.items = res.data || []
      } catch (e) {
        this.items = []
      } finally {
        this.loading = false
      }
    },
    // 已绑定的不用再选（重复落库没意义）
    rowSelectable(row) {
      return !row.bound
    },
    onSelect(rows) {
      this.selected = rows
    },
    async doApply() {
      const codes = this.selected.map(r => r.dictCode)
      this.saving = true
      try {
        const res = await applyGenDict(this.tableId, codes)
        this.$message.success(`已写入 ${res.data || 0} 个字典`)
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
.dict-detect-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: var(--cg-bg-page);
  border-left: 3px solid var(--cg-primary);
  font-size: 12px;
  line-height: 1.7;
  color: var(--cg-text-regular);
}
.dict-detect-tip code {
  background: var(--cg-bg-hover);
  padding: 0 4px;
  border-radius: 2px;
  color: var(--cg-primary);
}
.dict-opts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.dict-opt-v {
  opacity: 0.65;
  margin-left: 2px;
}
.dict-detect-foot {
  float: left;
  font-size: 12px;
  color: var(--cg-text-secondary);
  line-height: 32px;
}
</style>
