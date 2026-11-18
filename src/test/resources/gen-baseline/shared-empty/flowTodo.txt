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
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="openApprove(row)">审批</el-button>
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
    </el-tabs>

    <el-dialog title="审批" v-model="dialogVisible" width="460px">
      <div v-if="current" style="margin-bottom: 12px; color: #909399; font-size: 13px;">
        流程：{{ current.flowName || current.flowCode }}　节点：{{ current.nodeName }}　单据：{{ current.bizId }}
      </div>
      <el-input v-model="comment" type="textarea" :rows="3" placeholder="审批意见（可选）" />
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="danger" @click="doApprove(false)">驳 回</el-button>
        <el-button size="small" type="primary" @click="doApprove(true)">通 过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  getFlowTodo, getFlowDone, flowApprove, flowReject, getFlowRecords
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
      comment: ''
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
    openApprove(row) {
      this.current = row
      this.comment = ''
      this.dialogVisible = true
    },
    async doApprove(approved) {
      const row = this.current
      const flowCode = row.flowCode
      const bizId = row.bizId
      try {
        if (approved) {
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
    }
  }
}
</script>

<style scoped>
.flow-todo { padding: 16px; }
</style>
