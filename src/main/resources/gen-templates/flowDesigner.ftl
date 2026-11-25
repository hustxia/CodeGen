<template>
  <!--
    V9.17 简化版流程设计器（独立页版本）
    已接线：GenTemplateEngine.renderFlowShared 在开态（有表绑定 flow_code）时按本模板 pathPattern
    写盘为 src/views/flow/designer.vue，并在 flow-router-snippet.txt 注册 /flow/designer 路由。
    入口：flowTodo.vue「流程设计」tab 以 /flow/designer?flowCode=xxx 跳转，created() 读取 query 定位定义；
    无 flowCode 时保持空表单，可直接新建流程定义。
  -->
  <div class="flow-designer">
    <el-alert type="info" :closable="false" show-icon
              title="简化版设计器：不引入 bpmn-js，节点以卡片拖拽排序；审批人/条件分支以表单配置；JSON 实时预览 + 后端校验后保存。" />

    <el-form :inline="true" size="small" label-width="80px" class="designer-meta">
      <el-form-item label="流程编码"><el-input v-model="def.flowCode" placeholder="如 hr_leave_flow" /></el-form-item>
      <el-form-item label="流程名称"><el-input v-model="def.flowName" placeholder="如 请假审批" /></el-form-item>
      <el-form-item label="业务表"><el-input v-model="def.tableName" placeholder="如 hr_leave" /></el-form-item>
      <el-form-item label="状态列"><el-input v-model="def.statusCol" placeholder="status" /></el-form-item>
    </el-form>

    <div class="designer-toolbar">
      <el-button size="small" type="primary" plain @click="addNode">新增节点</el-button>
      <el-button size="small" @click="move(-1)">上移</el-button>
      <el-button size="small" @click="move(1)">下移</el-button>
      <el-button size="small" type="warning" @click="validate">校验定义</el-button>
      <el-button size="small" type="success" @click="save">保存定义</el-button>
      <span class="designer-tip">点选节点后可用上移/下移，或直接拖拽卡片排序</span>
    </div>

    <div class="designer-layout">
      <div class="designer-canvas">
        <div v-for="(node, i) in def.nodes" :key="'n' + i"
             class="node-card" :class="{ active: i === selected }"
             draggable="true"
             @dragstart="dragFrom = i" @dragover.prevent @drop="drop(i)"
             @click="selected = i">
          <div class="node-card-head">
            <span class="drag-dot" title="拖拽排序">⠿</span>
            <strong>节点 {{ i + 1 }}</strong>
            <el-button size="mini" type="danger" text @click.stop="remove(i)">删除</el-button>
          </div>
          <div class="node-grid">
            <el-input v-model="node.from" size="mini" placeholder="起始状态" />
            <el-input v-model="node.to" size="mini" placeholder="目标状态" />
            <el-input v-model="node.nodeName" size="mini" placeholder="节点名称" />
            <el-input v-model="node.roleCode" size="mini" placeholder="审批角色 roleCode" />
            <el-input v-model="node.rejectTo" size="mini" placeholder="驳回目标状态" />
            <el-select v-model="node.countersignMode" size="mini" placeholder="审批模式">
              <el-option label="普通审批(SINGLE)" value="SINGLE" />
              <el-option label="全部同意(ALL)" value="ALL" />
              <el-option label="一人同意(ANY)" value="ANY" />
              <el-option label="一票否决(VETO)" value="VETO" />
            </el-select>
            <el-input v-model="node.approvers" size="mini" placeholder="会签人ID，逗号分隔" />
            <el-input v-model="node.condition" size="mini" placeholder="条件分支表达式，如 amount>1000" />
          </div>
        </div>
        <div v-if="!def.nodes.length" class="designer-empty">尚无节点，点击「新增节点」开始设计</div>
      </div>

      <div class="designer-inspector">
        <div class="inspector-title">流程定义 JSON</div>
        <el-input v-model="defJson" type="textarea" :rows="20" spellcheck="false" />
        <div v-if="result" class="validate-box" :class="result.valid ? 'ok' : 'bad'">
          <div v-if="result.valid">校验通过，可保存</div>
          <div v-else>
            <div>校验失败：</div>
            <ul><li v-for="(e, i) in result.errors" :key="i">{{ e }}</li></ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getFlowProgress, flowValidateDef, flowSaveDef } from '@/api/flow'

function emptyNode(from) {
  return {
    from: from || '', to: '', nodeName: '', roleCode: '', rejectTo: '0',
    countersignMode: 'SINGLE', approvers: '', condition: ''
  }
}

export default {
  name: 'FlowDesigner',
  data() {
    return {
      def: { flowCode: '', flowName: '', tableName: '', statusCol: 'status', nodes: [] },
      selected: -1,
      dragFrom: -1,
      result: null
    }
  },
  created() {
    // V9.17 独立路由入口：/flow/designer?flowCode=xxx 定位到对应流程定义
    const code = String(this.$route.query.flowCode || '').trim()
    if (code) {
      this.def.flowCode = code
      this.loadDef(code)
    }
  },
  computed: {
    defJson: {
      get() {
        const payload = {
          flowCode: this.def.flowCode,
          flowName: this.def.flowName,
          tableName: this.def.tableName,
          statusCol: this.def.statusCol || 'status',
          nodes: this.def.nodes.map(function (n) {
            return {
              from: n.from, to: n.to, nodeName: n.nodeName, roleCode: n.roleCode,
              rejectTo: n.rejectTo || '0',
              countersignMode: n.countersignMode || 'SINGLE',
              approvers: String(n.approvers || '').split(',').map(function (s) { return s.trim() }).filter(Boolean),
              condition: n.condition || ''
            }
          })
        }
        return JSON.stringify(payload, null, 2)
      },
      set(v) {
        try {
          const o = JSON.parse(v)
          this.def.flowCode = o.flowCode || ''
          this.def.flowName = o.flowName || ''
          this.def.tableName = o.tableName || ''
          this.def.statusCol = o.statusCol || 'status'
          if (Array.isArray(o.nodes)) {
            this.def.nodes = o.nodes.map(function (n) {
              return {
                from: n.from || '', to: n.to || '', nodeName: n.nodeName || '',
                roleCode: n.roleCode || '', rejectTo: n.rejectTo || '0',
                countersignMode: n.countersignMode || 'SINGLE',
                approvers: Array.isArray(n.approvers) ? n.approvers.join(',') : '',
                condition: n.condition || ''
              }
            })
          }
          this.result = null
        } catch (e) {
          // 编辑中允许临时非法 JSON
        }
      }
    }
  },
  methods: {
    // 从 /flow/state 取回既有定义并回填表单；bizId 传 null（草稿预览，不画图只取 nodes）
    async loadDef(flowCode) {
      try {
        const res = await getFlowProgress(flowCode, null)
        const data = (res && res.data) || {}
        if (data.flowName) this.def.flowName = data.flowName
        if (Array.isArray(data.nodes)) {
          this.def.nodes = data.nodes.map(function (n) {
            return {
              from: n.from || '', to: n.to || '', nodeName: n.nodeName || '',
              roleCode: n.roleCode || '', rejectTo: n.rejectTo || '0',
              countersignMode: n.countersignMode || 'SINGLE',
              approvers: Array.isArray(n.approvers) ? n.approvers.join(',') : '',
              condition: n.condition || ''
            }
          })
        }
      } catch (e) {
        // request.js 统一弹出错误；无定义时保留空表单，可新建
      }
    },
    addNode() {
      const last = this.def.nodes[this.def.nodes.length - 1]
      this.def.nodes.push(emptyNode(last ? last.to : '0'))
      this.selected = this.def.nodes.length - 1
      this.result = null
    },
    remove(i) {
      this.def.nodes.splice(i, 1)
      this.selected = -1
      this.result = null
    },
    drop(i) {
      const from = this.dragFrom
      if (from < 0 || from === i) return
      const node = this.def.nodes.splice(from, 1)[0]
      this.def.nodes.splice(i, 0, node)
      this.selected = i
      this.dragFrom = -1
      this.result = null
    },
    move(delta) {
      const i = this.selected
      const j = i + delta
      if (i < 0 || j < 0 || j >= this.def.nodes.length) return
      const node = this.def.nodes.splice(i, 1)[0]
      this.def.nodes.splice(j, 0, node)
      this.selected = j
      this.result = null
    },
    async validate() {
      try {
        const res = await flowValidateDef(this.defJson)
        this.result = (res && res.data) || { valid: false, errors: ['校验接口无返回'] }
      } catch (e) {
        this.result = { valid: false, errors: ['校验接口调用失败'] }
      }
      return this.result
    },
    async save() {
      const r = await this.validate()
      if (!r.valid) {
        this.$message.warning('请先修正定义中的错误')
        return
      }
      try {
        await flowSaveDef(this.defJson)
        this.$message.success('流程定义已保存')
      } catch (e) {
        // request.js 统一弹出错误
      }
    }
  }
}
</script>

<style scoped>
.flow-designer { padding: 16px; }
.designer-meta { margin-top: 12px; }
.designer-toolbar { display: flex; align-items: center; gap: 8px; margin: 8px 0 12px; }
.designer-tip { color: #909399; font-size: 12px; margin-left: 8px; }
.designer-layout { display: flex; gap: 12px; align-items: flex-start; }
.designer-canvas { flex: 1; min-width: 0; }
.designer-inspector { width: 400px; flex: none; }
.inspector-title { font-size: 13px; color: #606266; margin-bottom: 6px; }
.node-card { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; margin-bottom: 8px; background: #fff; }
.node-card.active { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64, 158, 255, .12); }
.node-card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.drag-dot { cursor: move; color: #909399; font-size: 16px; }
.node-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.designer-empty { padding: 24px; text-align: center; color: #c0c4cc; border: 1px dashed #ebeef5; }
.validate-box { margin-top: 8px; padding: 8px; border-radius: 4px; font-size: 12px; }
.validate-box.ok { color: #67c23a; background: #f0f9eb; }
.validate-box.bad { color: #f56c6c; background: #fef0f0; }
.validate-box ul { margin: 4px 0 0 18px; padding: 0; }
</style>
