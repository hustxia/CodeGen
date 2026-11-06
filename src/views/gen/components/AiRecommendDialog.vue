<template>
  <el-dialog
    :title="title"
    :visible="visible"
    width="920px"
    top="5vh"
    append-to-body
    @close="close"
  >
    <div v-loading="loading">
      <el-alert v-if="!exists" type="info" :closable="false" :title="emptyText" />
      <template v-else>
        <el-alert v-if="drops.length" type="warning" :closable="false" style="margin-bottom: 8px">
          <template slot="title">AI 输出中的非法项已按白名单丢弃：{{ drops.slice(0, 6).join('；') }}{{ drops.length > 6 ? ' 等' + drops.length + ' 项' : '' }}</template>
        </el-alert>
        <div class="rec-tip">
          勾选要应用的项后点「应用勾选项」，未勾选的保持现值（规则推断默认）。
          带「已手改」标记的项默认不勾选，防止 AI 覆盖人工调整。
        </div>

        <el-tabs v-model="activeTab" size="small">
          <el-tab-pane label="表级 / 列级配置" name="cfg">
            <div v-for="t in tables" :key="t.tableId" class="rec-table">
              <div class="rec-table-head">
                <b>{{ t.tableComment || t.tableName }}</b>
                <span class="meta">{{ t.tableName }}</span>
              </div>
              <div v-if="t.tableFields && t.tableFields.length" class="rec-fields">
                <span class="rec-group">表级</span>
                <el-checkbox
                  v-for="f in t.tableFields"
                  :key="f.field"
                  v-model="checked['T' + t.tableId + '.' + f.field]"
                >
                  {{ FIELD_LABEL[f.field] || f.field }}：{{ fmtVal(f.current) }} → <b>{{ fmtVal(f.suggested) }}</b>
                  <el-tag v-if="f.manual" size="mini" type="info">已手改</el-tag>
                </el-checkbox>
              </div>
              <div v-for="c in t.columns" :key="c.columnName" class="rec-col">
                <span class="rec-colname">{{ c.columnComment || c.columnName }}</span>
                <el-checkbox
                  v-for="f in c.fields"
                  :key="t.tableId + '-' + c.columnName + '-' + f.field"
                  v-model="checked['C' + t.tableId + '.' + c.columnName + '.' + f.field]"
                >
                  {{ FIELD_LABEL[f.field] || f.field }}：{{ fmtVal(f.current) }} → <b>{{ fmtVal(f.suggested) }}</b>
                  <el-tag v-if="f.manual" size="mini" type="info">已手改</el-tag>
                </el-checkbox>
              </div>
            </div>
            <div v-if="!tables.length" class="meta" style="padding: 12px 0">
              无列级/表级差异建议（AI 建议与现值一致，或全部被白名单丢弃）
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="showMenus" :label="'菜单组织（' + menus.length + '）'" name="menu">
            <div class="rec-tip">勾选的模块会把其成员表的 module_name 归入该模块，作为菜单注册的分组依据；目录名/图标为参考。</div>
            <div class="rec-fields">
              <el-checkbox v-for="m in menus" :key="m.moduleName" v-model="checked['M.' + m.moduleName]">
                <b>{{ m.dirLabel }}</b>（{{ m.moduleName }}）
                <span v-if="m.icon" class="meta">图标 {{ m.icon }}</span>
              </el-checkbox>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
    <div slot="footer">
      <el-button size="small" @click="close">关 闭</el-button>
      <el-button
        size="small"
        type="primary"
        :disabled="!exists"
        :loading="applyLoading"
        @click="onApply"
      >应用勾选项</el-button>
    </div>
  </el-dialog>
</template>

<script>
/**
 * AI 推荐建议 diff 预览与勾选（V7.0 抽出，V7.3 表级入口复用）。
 *
 * 只负责「展示 + 勾选状态」，数据由父组件传入：
 * - 流水线入口：GET /gen/pipeline/{id}/ai-recommend/preview
 * - 表级入口：POST /gen/ai-recommend/tables/preview
 *
 * emit('apply', dto) 只带勾选清单，建议值一律由服务端按产物回填——
 * 客户端传值不可信，防止伪造建议写库。
 */
const FIELD_LABEL = {
  isQuery: '查询',
  queryType: '查询方式',
  isList: '列表显示',
  isForm: '表单',
  htmlType: '控件',
  dictType: '字典',
  sort: '排序',
  moduleName: '模块',
  treeParentColumn: '树形父列',
  treeLabelColumn: '树形显示列',
  masterTable: '主表',
  relationType: '关系'
}

export default {
  name: 'AiRecommendDialog',
  props: {
    visible: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    applyLoading: { type: Boolean, default: false },
    /** 产物是否存在；false 时只显示兜底提示 */
    exists: { type: Boolean, default: false },
    /** GenAiRecommendPreviewVO.tables */
    tables: { type: Array, default: () => [] },
    /** GenAiRecommendPreviewVO.menus */
    menus: { type: Array, default: () => [] },
    /** GenAiRecommendPreviewVO.drops */
    drops: { type: Array, default: () => [] },
    title: { type: String, default: 'AI 推荐配置预览' },
    emptyText: {
      type: String,
      default: '无 AI 推荐产物（ai_recommend 步骤未成功或被降级），本次生成走的是规则默认配置'
    },
    /** 表级入口没有菜单组织建议（只看一部分表归纳不出可靠分组） */
    showMenus: { type: Boolean, default: true }
  },
  data() {
    return {
      checked: {},
      activeTab: 'cfg',
      FIELD_LABEL
    }
  },
  watch: {
    // 每次拿到新数据重建勾选态：manual（已手改）项默认不勾，其余默认勾
    tables() { this.initChecked() },
    menus() { this.initChecked() },
    visible(v) { if (v) this.activeTab = 'cfg' }
  },
  methods: {
    initChecked() {
      const checked = {}
      for (const t of this.tables) {
        for (const f of t.tableFields || []) {
          checked[`T${t.tableId}.${f.field}`] = !f.manual
        }
        for (const c of t.columns || []) {
          for (const f of c.fields || []) {
            checked[`C${t.tableId}.${c.columnName}.${f.field}`] = !f.manual
          }
        }
      }
      for (const m of this.menus) {
        checked[`M.${m.moduleName}`] = true
      }
      // Vue2 深层 v-model 需要初始响应式键，整体替换触发重渲染
      this.checked = checked
    },

    /** 勾选状态 → ApplyDTO（只传「选了哪些」） */
    buildApplyDto() {
      const tables = []
      for (const t of this.tables) {
        const fields = []
        const columns = []
        for (const f of t.tableFields || []) {
          if (this.checked[`T${t.tableId}.${f.field}`]) fields.push(f.field)
        }
        for (const c of t.columns || []) {
          const cf = []
          for (const f of c.fields || []) {
            if (this.checked[`C${t.tableId}.${c.columnName}.${f.field}`]) cf.push(f.field)
          }
          if (cf.length) columns.push({ columnName: c.columnName, fields: cf })
        }
        if (fields.length || columns.length) {
          tables.push({ tableName: t.tableName, fields, columns })
        }
      }
      const menus = this.menus
        .filter(m => this.checked[`M.${m.moduleName}`])
        .map(m => m.moduleName)
      return { tables, menus }
    },

    onApply() {
      const dto = this.buildApplyDto()
      if (!dto.tables.length && !dto.menus.length) {
        return this.$message.warning('尚未勾选任何项')
      }
      this.$emit('apply', dto)
    },

    fmtVal(v) {
      if (v === null || v === undefined || v === '') return '—'
      if (typeof v === 'boolean') return v ? '是' : '否'
      return String(v)
    },

    close() {
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>
.rec-tip { font-size: 12px; color: #909399; margin-bottom: 10px; line-height: 1.6; }
.rec-table { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px 12px; margin-bottom: 10px; }
.rec-table-head { margin-bottom: 8px; }
.rec-table-head .meta { color: #909399; font-size: 12px; margin-left: 8px; }
.rec-fields .el-checkbox { margin-right: 16px; }
.rec-group { font-size: 12px; color: #909399; margin-right: 6px; }
.rec-col { display: flex; align-items: center; flex-wrap: wrap; padding: 3px 0 3px 14px; }
.rec-colname { display: inline-block; min-width: 120px; font-size: 13px; color: #606266; }
.rec-col .el-checkbox { margin-right: 16px; }
.meta { color: #909399; font-size: 12px; }
</style>
