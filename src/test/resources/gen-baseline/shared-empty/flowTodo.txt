<template>
  <div class="flow-todo">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的待办" name="todo">
        <el-table :data="todoList" v-loading="loading" border size="small">
          <el-table-column prop="flowName" label="流程" width="180" />
          <el-table-column prop="nodeName" label="当前节点" width="140" />
          <el-table-column prop="bizTable" label="业务表" width="180" />
          <el-table-column prop="bizId" label="单据ID" width="180" />
          <el-table-column prop="createTime" label="到达时间" min-width="150" />
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="openApprove(row)">审批</el-button>
              <el-button type="warning" size="small" @click="openDelegate(row)">委派</el-button>
              <el-button size="small" @click="openProgress(row)">进度图</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="我的已办" name="done">
        <el-table :data="doneList" v-loading="loading" border size="small">
          <el-table-column prop="flowCode" label="流程" width="180" />
          <el-table-column prop="nodeName" label="处理节点" width="140" />
          <el-table-column prop="bizId" label="单据ID" width="180" />
          <el-table-column prop="operTime" label="处理时间" min-width="150" />
        </el-table>
      </el-tab-pane>

      <!-- V9.17 简化版流程设计器：节点拖拽排序 + 审批人配置 + 条件分支配置 -->
      <el-tab-pane label="流程设计" name="designer">
        <el-alert type="info" :closable="false" show-icon
                  title="简化版设计器：拖拽节点卡片排序，配置审批角色/会签人/条件分支；右侧 JSON 实时预览，校验通过后可保存为流程定义。" />
        <el-form :inline="true" size="small" class="designer-meta" label-width="80px">
          <el-form-item label="流程编码"><el-input v-model="defForm.flowCode" placeholder="如 hr_leave_flow" /></el-form-item>
          <el-form-item label="流程名称"><el-input v-model="defForm.flowName" placeholder="如 请假审批" /></el-form-item>
          <el-form-item label="业务表"><el-input v-model="defForm.tableName" placeholder="如 hr_leave" /></el-form-item>
          <el-form-item label="状态列"><el-input v-model="defForm.statusCol" placeholder="status" /></el-form-item>
        </el-form>
        <div class="designer-toolbar">
          <el-button size="small" type="primary" plain @click="addNode">新增节点</el-button>
          <el-button size="small" @click="moveNode(-1)">上移</el-button>
          <el-button size="small" @click="moveNode(1)">下移</el-button>
          <el-button size="small" type="warning" @click="validateDef">校验</el-button>
          <el-button size="small" type="success" @click="saveDef">保存</el-button>
          <span class="designer-tip">选中节点后可用上移/下移，或直接拖拽卡片</span>
        </div>
        <div class="designer-body">
          <div class="designer-nodes">
            <div v-for="(node, i) in defForm.nodes" :key="'node-' + i"
                 class="designer-node" :class="{ 'is-active': i === selectedNodeIndex }"
                 draggable="true"
                 @dragstart="onDragStart(i)" @dragover.prevent @drop="onDrop(i)"
                 @click="selectedNodeIndex = i">
              <div class="designer-node-head">
                <span class="drag-handle" title="拖拽排序">⠿</span>
                <b>节点 {{ i + 1 }}</b>
                <el-button size="mini" type="danger" text @click.stop="removeNode(i)">删除</el-button>
              </div>
              <div class="designer-node-grid">
                <el-input v-model="node.from" size="mini" placeholder="起始状态(如 0)" />
                <el-input v-model="node.to" size="mini" placeholder="目标状态(如 1)" />
                <el-input v-model="node.nodeName" size="mini" placeholder="节点名称(如 主管审批)" />
                <el-input v-model="node.roleCode" size="mini" placeholder="审批角色(如 manager)" />
                <el-input v-model="node.condition" size="mini" placeholder="条件分支表达式(可选，如 amount>1000)" />
                <el-input v-model="node.approvers" size="mini" placeholder="会签人ID，逗号分隔(可选)" />
                <el-select v-model="node.countersignMode" size="mini" placeholder="会签模式" style="width:100%">
                  <el-option label="普通审批(单机)" value="SINGLE" />
                  <el-option label="全部同意(ALL)" value="ALL" />
                  <el-option label="一人同意(ANY)" value="ANY" />
                  <el-option label="一票否决(VETO)" value="VETO" />
                </el-select>
                <el-input v-model="node.rejectTo" size="mini" placeholder="驳回目标状态(可选)" />
              </div>
            </div>
            <div v-if="!defForm.nodes.length" class="designer-empty">还没有节点，点「新增节点」开始</div>
          </div>
          <div class="designer-preview">
            <div class="designer-preview-title">流程定义 JSON（可直接编辑）</div>
            <el-input v-model="defJson" type="textarea" :rows="18" spellcheck="false" />
            <div v-if="validateResult" class="validate-result" :class="validateResult.valid ? 'ok' : 'bad'">
              <span v-if="validateResult.valid">校验通过，可保存</span>
              <span v-else>校验失败：{{ (validateResult.errors || []).join('；') }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 审批弹窗（含进度图 + 会签意见 + 会签投票） -->
    <el-dialog title="审批" v-model="dialogVisible" width="640px">
      <div v-if="current" style="margin-bottom: 12px; color: #909399; font-size: 13px;">
        流程：{{ current.flowName || current.flowCode }}　节点：{{ current.nodeName }}　单据：{{ current.bizId }}
      </div>

      <!-- V9.17 SVG 流程进度图：数据来自 /flow/state 的 nodeStatuses，无 bpmn-js -->
      <div class="progress-block">
        <div class="progress-title">流程进度</div>
        <div v-if="progressData && progressData.nodeStatuses && progressData.nodeStatuses.length" class="flow-progress">
          <svg :viewBox="'0 0 ' + progressWidth + ' 100'" class="flow-progress-svg" preserveAspectRatio="xMinYMid meet">
            <defs>
              <marker id="flowArrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
                <path d="M0,0 L0,6 L8,3 z" fill="#c0c4cc" />
              </marker>
            </defs>
            <template v-for="(n, i) in progressData.nodeStatuses" :key="'seg-' + i">
              <line v-if="i < progressData.nodeStatuses.length - 1"
                    :x1="60 + i * 150 + 36" y1="38"
                    :x2="60 + (i + 1) * 150 - 36" y2="38"
                    stroke="#c0c4cc" stroke-width="2" marker-end="url(#flowArrow)" />
            </template>
            <g v-for="(n, i) in progressData.nodeStatuses" :key="n.nodeKey || i"
               :transform="'translate(' + (60 + i * 150) + ',14)'">
              <rect x="-36" y="0" width="72" height="48" rx="6"
                    :fill="nodeColor(n.status)" />
              <text x="0" y="19" text-anchor="middle" font-size="12" fill="#ffffff">{{ shortName(n.nodeName) }}</text>
              <text x="0" y="37" text-anchor="middle" font-size="10" fill="#ffffff">{{ statusLabel(n.status) }}</text>
            </g>
          </svg>
          <div class="progress-legend">
            <span><i style="background:#67c23a"></i>已完成</span>
            <span><i style="background:#409eff"></i>进行中</span>
            <span><i style="background:#e6a23c"></i>已委派</span>
            <span><i style="background:#f56c6c"></i>已驳回</span>
            <span><i style="background:#c0c4cc"></i>未开始</span>
          </div>
        </div>
        <div v-else class="flow-empty">暂无节点进度数据</div>
      </div>

      <!-- V9.17 会签意见列表（与 approval_node 同构） -->
      <div class="progress-block">
        <div class="progress-title">会签意见</div>
        <el-table v-if="opinions.length" :data="opinions" size="mini" border>
          <el-table-column prop="assigneeName" label="审批人" width="110" />
          <el-table-column prop="result" label="结果" width="90" />
          <el-table-column prop="comment" label="意见" min-width="180" />
          <el-table-column prop="handleTime" label="时间" width="150" />
        </el-table>
        <div v-else class="flow-empty">暂无会签记录</div>
      </div>

      <el-input v-model="comment" type="textarea" :rows="3" placeholder="审批意见（可选）" />
      <div class="countersign-bar">
        <el-checkbox v-model="countersignOn">作为会签人投票</el-checkbox>
        <el-select v-if="countersignOn" v-model="countersignMode" size="small" style="width:170px;margin-left:8px">
          <el-option label="全部同意(ALL)" value="ALL" />
          <el-option label="一人同意(ANY)" value="ANY" />
          <el-option label="一票否决(VETO)" value="VETO" />
        </el-select>
      </div>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="warning" @click="doApprove(false)">驳回</el-button>
        <el-button size="small" type="primary" @click="doApprove(true)">通过</el-button>
      </template>
    </el-dialog>

    <!-- 只读进度图弹窗（待办行「进度图」按钮） -->
    <el-dialog title="流程进度" v-model="progressVisible" width="640px">
      <div v-if="progressData && progressData.nodeStatuses && progressData.nodeStatuses.length" class="flow-progress">
        <svg :viewBox="'0 0 ' + progressWidth + ' 100'" class="flow-progress-svg" preserveAspectRatio="xMinYMid meet">
          <template v-for="(n, i) in progressData.nodeStatuses" :key="'pseg-' + i">
            <line v-if="i < progressData.nodeStatuses.length - 1"
                  :x1="60 + i * 150 + 36" y1="38"
                  :x2="60 + (i + 1) * 150 - 36" y2="38"
                  stroke="#c0c4cc" stroke-width="2" marker-end="url(#flowArrow2)" />
          </template>
          <defs>
            <marker id="flowArrow2" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
              <path d="M0,0 L0,6 L8,3 z" fill="#c0c4cc" />
            </marker>
          </defs>
          <g v-for="(n, i) in progressData.nodeStatuses" :key="n.nodeKey || i"
             :transform="'translate(' + (60 + i * 150) + ',14)'">
            <rect x="-36" y="0" width="72" height="48" rx="6" :fill="nodeColor(n.status)" />
            <text x="0" y="19" text-anchor="middle" font-size="12" fill="#ffffff">{{ shortName(n.nodeName) }}</text>
            <text x="0" y="37" text-anchor="middle" font-size="10" fill="#ffffff">{{ statusLabel(n.status) }}</text>
          </g>
        </svg>
      </div>
      <div v-else class="flow-empty">暂无节点进度数据</div>
      <template #footer>
        <el-button size="small" @click="progressVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- V9.17 委派（转办）弹窗 -->
    <el-dialog title="委派（转办）" v-model="delegateVisible" width="480px">
      <div v-if="delegateTarget" style="margin-bottom: 12px; color: #909399; font-size: 13px;">
        单据：{{ delegateTarget.flowName || delegateTarget.flowCode }} / {{ delegateTarget.bizId }}
      </div>
      <el-form label-width="100px" size="small">
        <el-form-item label="目标审批人ID">
          <el-input-number v-model="delegateForm.toUserId" :min="1" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="姓名（选填）">
          <el-input v-model="delegateForm.toUserName" placeholder="便于转办记录阅读" />
        </el-form-item>
        <el-form-item label="转办原因">
          <el-input v-model="delegateForm.reason" type="textarea" :rows="2" placeholder="如：出差期间由张三代为审批" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="delegateVisible = false">取消</el-button>
        <el-button size="small" type="warning" @click="doDelegate">确认委派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  getFlowTodo, getFlowDone, flowApprove, flowReject, getFlowRecords,
  getFlowProgress, flowCountersign, getFlowCountersignOpinions,
  flowDelegate, flowValidateDef, flowSaveDef
} from '@/api/flow'

export default {
  name: 'FlowTodo',
  data() {
    return {
      activeTab: 'todo',
      loading: false,
      todoList: [],
      doneList: [],
      dialogVisible: false,
      current: null,
      comment: '',
      // V9.17 进度图
      progressData: null,
      progressVisible: false,
      opinions: [],
      countersignOn: false,
      countersignMode: 'ALL',
      // V9.17 委派
      delegateVisible: false,
      delegateTarget: null,
      delegateForm: { toUserId: null, toUserName: '', reason: '' },
      // V9.17 设计器
      defForm: {
        flowCode: '', flowName: '', tableName: '', statusCol: 'status', nodes: []
      },
      selectedNodeIndex: -1,
      dragIndex: -1,
      validateResult: null
    }
  },
  computed: {
    // 节点横向排布：每个节点占 150px，首尾各留 60px
    progressWidth() {
      const n = (this.progressData && this.progressData.nodeStatuses || []).length
      return Math.max(360, 120 + n * 150)
    },
    // 设计器 JSON 双向绑定：左侧表单 <-> 右侧 JSON
    defJson: {
      get() {
        const def = {
          flowCode: this.defForm.flowCode,
          flowName: this.defForm.flowName,
          tableName: this.defForm.tableName,
          statusCol: this.defForm.statusCol || 'status',
          nodes: (this.defForm.nodes || []).map(function (n) {
            return {
              from: n.from, to: n.to, nodeName: n.nodeName, roleCode: n.roleCode,
              rejectTo: n.rejectTo || undefined,
              condition: n.condition || undefined,
              approvers: (n.approvers || '').split(',').map(function (s) { return s.trim() }).filter(Boolean),
              countersignMode: n.countersignMode || 'SINGLE'
            }
          })
        }
        return JSON.stringify(def, null, 2)
      },
      set(v) {
        try {
          const o = JSON.parse(v)
          if (!o || typeof o !== 'object') return
          this.defForm.flowCode = o.flowCode || ''
          this.defForm.flowName = o.flowName || ''
          this.defForm.tableName = o.tableName || ''
          this.defForm.statusCol = o.statusCol || 'status'
          if (Array.isArray(o.nodes)) {
            this.defForm.nodes = o.nodes.map(function (n) {
              const a = Array.isArray(n.approvers) ? n.approvers : []
              return {
                from: n.from || '', to: n.to || '', nodeName: n.nodeName || '',
                roleCode: n.roleCode || '', rejectTo: n.rejectTo || '',
                condition: n.condition || '', approvers: a.join(','),
                countersignMode: n.countersignMode || 'SINGLE'
              }
            })
          }
        } catch (e) {
          // JSON 编辑中允许临时不合法，不打断输入
        }
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const [todo, done] = await Promise.all([getFlowTodo(), getFlowDone()])
        this.todoList = (todo && todo.data) || []
        this.doneList = (done && done.data) || []
      } finally {
        this.loading = false
      }
    },
    // ---------- 进度图 ----------
    async loadProgress(flowCode, bizId) {
      try {
        const res = await getFlowProgress(flowCode, bizId)
        this.progressData = (res && res.data) || null
      } catch (e) {
        this.progressData = null
      }
    },
    async loadOpinions(flowCode, bizId, nodeName) {
      try {
        const res = await getFlowCountersignOpinions(flowCode, bizId, nodeName)
        this.opinions = (res && res.data) || []
      } catch (e) {
        this.opinions = []
      }
    },
    nodeColor(status) {
      if (status === 'DONE') return '#67c23a'
      if (status === 'CURRENT') return '#409eff'
      if (status === 'DELEGATED') return '#e6a23c'
      if (status === 'REJECTED') return '#f56c6c'
      return '#c0c4cc'
    },
    statusLabel(status) {
      const map = { DONE: '已完成', CURRENT: '进行中', DELEGATED: '已委派', REJECTED: '已驳回', PENDING: '未开始' }
      return map[status] || '未开始'
    },
    shortName(name) {
      const s = String(name || '节点')
      return s.length > 6 ? s.slice(0, 6) + '…' : s
    },
    openProgress(row) {
      this.loadProgress(row.flowCode, row.bizId)
      this.progressVisible = true
    },
    // ---------- 审批 / 会签 ----------
    async openApprove(row) {
      this.current = row
      this.comment = ''
      this.countersignOn = false
      this.countersignMode = 'ALL'
      this.opinions = []
      this.progressData = null
      await Promise.all([
        this.loadProgress(row.flowCode, row.bizId),
        this.loadOpinions(row.flowCode, row.bizId, row.nodeName)
      ])
      this.dialogVisible = true
    },
    async doApprove(approved) {
      const row = this.current
      const flowCode = row.flowCode
      const bizId = row.bizId
      try {
        if (this.countersignOn) {
          // 会签票：approvers 传空数组，后端取当前登录人作为审批人
          await flowCountersign(flowCode, bizId, [], this.countersignMode, this.comment, approved)
          this.$message.success(approved ? '会签同意票已记录' : '会签反对票已记录')
        } else if (approved) {
          await flowApprove(flowCode, bizId, this.comment)
          this.$message.success('已通过')
        } else {
          await flowReject(flowCode, bizId, this.comment)
          this.$message.success('已驳回')
        }
        this.dialogVisible = false
        this.load()
      } catch (e) {
        // request.js 统一弹出错误，这里不再重复提示
      }
    },
    // ---------- 委派 ----------
    openDelegate(row) {
      this.delegateTarget = row
      this.delegateForm = { toUserId: null, toUserName: '', reason: '' }
      this.delegateVisible = true
    },
    async doDelegate() {
      if (!this.delegateForm.toUserId) {
        this.$message.warning('请填写目标审批人ID')
        return
      }
      try {
        await flowDelegate(
          this.delegateTarget.flowCode, this.delegateTarget.bizId,
          this.delegateForm.toUserId, this.delegateForm.toUserName, this.delegateForm.reason
        )
        this.$message.success('已委派，转办记录已留痕')
        this.delegateVisible = false
        this.load()
      } catch (e) {
        // request.js 统一弹出错误
      }
    },
    // ---------- 设计器 ----------
    addNode() {
      const last = this.defForm.nodes[this.defForm.nodes.length - 1]
      this.defForm.nodes.push({
        from: last ? last.to : '0', to: '', nodeName: '', roleCode: '',
        rejectTo: '0', condition: '', approvers: '', countersignMode: 'SINGLE'
      })
      this.selectedNodeIndex = this.defForm.nodes.length - 1
      this.validateResult = null
    },
    removeNode(i) {
      this.defForm.nodes.splice(i, 1)
      this.selectedNodeIndex = -1
      this.validateResult = null
    },
    onDragStart(i) {
      this.dragIndex = i
    },
    onDrop(i) {
      const from = this.dragIndex
      if (from < 0 || from === i) return
      const node = this.defForm.nodes.splice(from, 1)[0]
      this.defForm.nodes.splice(i, 0, node)
      this.selectedNodeIndex = i
      this.dragIndex = -1
      this.validateResult = null
    },
    moveNode(delta) {
      const i = this.selectedNodeIndex
      const j = i + delta
      if (i < 0 || j < 0 || j >= this.defForm.nodes.length) return
      const node = this.defForm.nodes.splice(i, 1)[0]
      this.defForm.nodes.splice(j, 0, node)
      this.selectedNodeIndex = j
      this.validateResult = null
    },
    async validateDef() {
      try {
        const res = await flowValidateDef(this.defJson)
        this.validateResult = (res && res.data) || { valid: false, errors: ['校验接口无返回'] }
      } catch (e) {
        this.validateResult = { valid: false, errors: ['校验接口调用失败'] }
      }
    },
    async saveDef() {
      await this.validateDef()
      if (!this.validateResult || !this.validateResult.valid) {
        this.$message.warning('请先修正流程定义中的错误')
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
.flow-todo { padding: 16px; }
.progress-block { margin: 12px 0; }
.progress-title { font-size: 13px; color: #606266; margin-bottom: 6px; }
.flow-progress { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; overflow-x: auto; }
.flow-progress-svg { width: 100%; min-width: 400px; height: 100px; }
.progress-legend { font-size: 12px; color: #909399; display: flex; gap: 14px; margin-top: 4px; }
.progress-legend i { display: inline-block; width: 9px; height: 9px; border-radius: 2px; margin-right: 4px; }
.flow-empty { color: #c0c4cc; font-size: 12px; padding: 8px 0; }
.countersign-bar { margin-top: 10px; }
.designer-meta { margin-top: 12px; }
.designer-toolbar { display: flex; align-items: center; gap: 8px; margin: 8px 0 12px; }
.designer-tip { color: #909399; font-size: 12px; margin-left: 8px; }
.designer-body { display: flex; gap: 12px; align-items: flex-start; }
.designer-nodes { flex: 1; min-width: 0; }
.designer-node { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; margin-bottom: 8px; background: #fff; }
.designer-node.is-active { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,.12); }
.designer-node-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 13px; }
.drag-handle { cursor: move; color: #909399; font-size: 16px; }
.designer-node-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.designer-empty { color: #c0c4cc; font-size: 13px; padding: 24px; text-align: center; border: 1px dashed #ebeef5; }
.designer-preview { width: 380px; flex: none; }
.designer-preview-title { font-size: 13px; color: #606266; margin-bottom: 6px; }
.validate-result { margin-top: 8px; font-size: 12px; padding: 6px 8px; border-radius: 4px; }
.validate-result.ok { color: #67c23a; background: #f0f9eb; }
.validate-result.bad { color: #f56c6c; background: #fef0f0; }
</style>
