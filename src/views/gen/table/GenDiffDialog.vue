<template>
  <el-dialog
    title="差异预览"
    :visible="visible"
    width="1150px"
    top="3vh"
    @update:visible="$emit('update:visible', $event)"
    @open="load"
  >
    <div class="diff-bar">
      <span class="write-tip">只显示有变化的文件</span>
      <el-switch v-model="onlyChanged" @change="load" />
      <span class="inline-tip">共 {{ list.length }} 个文件，其中 {{ changedCount }} 个会发生变化</span>
      <span style="flex: 1"></span>
      <el-button size="mini" icon="el-icon-refresh" :loading="loading" @click="load">重新对比</el-button>
    </div>
    <div v-loading="loading" class="preview">
      <div class="preview-tree diff-tree">
        <div
          v-for="d in list"
          :key="d.tableName + d.path"
          class="diff-item"
          :class="{ active: active && active.path === d.path && active.tableName === d.tableName }"
          @click="active = d"
        >
          <el-tag :type="statusType(d.status)" size="mini" disable-transitions>{{ statusText(d.status) }}</el-tag>
          <span class="diff-path">{{ d.path }}</span>
          <span class="diff-stat">
            <span v-if="d.additions" class="diff-add-num">+{{ d.additions }}</span>
            <span v-if="d.deletions" class="diff-del-num">-{{ d.deletions }}</span>
          </span>
        </div>
        <div v-if="!loading && !list.length" class="muted" style="padding: 12px">
          没有变化：磁盘上的文件与本次生成结果一致。
        </div>
      </div>
      <div class="preview-code">
        <div class="code-head">{{ active ? `${active.tableName} / ${active.path}` : '点击左侧文件查看差异' }}</div>
        <div class="diff-body">
          <div v-for="(line, idx) in activeLines" :key="idx" class="diff-line" :class="lineClass(line)">
            <span class="diff-gutter">{{ line.oldLine || '' }}</span>
            <span class="diff-gutter">{{ line.newLine || '' }}</span>
            <span class="diff-sign">{{ lineSign(line) }}</span>
            <span class="diff-text">{{ line.text }}</span>
          </div>
        </div>
      </div>
    </div>
    <div slot="footer">
      <el-button @click="$emit('update:visible', false)">关 闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { diffGenCode } from '@/api/gen'

/**
 * 差异预览弹窗（V1.3 diff 的展示层，V2.0 从 gen.vue 拆出）
 *
 * 只负责"拉 diff + 展示"：父组件传要对比的表 ID，打开时自己取数据，
 * 这样父页面不用再为它维护 5 个状态和 6 个方法。
 */
export default {
  name: 'GenDiffDialog',
  props: {
    visible: { type: Boolean, default: false },
    tableIds: { type: Array, default: () => [] }
  },
  data() {
    return {
      loading: false,
      onlyChanged: true,
      list: [],
      active: null
    }
  },
  computed: {
    changedCount() {
      return this.list.filter(d => d.status === 'MODIFIED' || d.status === 'NEW').length
    },
    activeLines() {
      return (this.active && this.active.lines) || []
    }
  },
  methods: {
    async load() {
      if (!this.tableIds.length) {
        this.list = []
        this.active = null
        return
      }
      this.loading = true
      try {
        const res = await diffGenCode(this.tableIds, true, this.onlyChanged)
        this.list = res.data || []
        this.active = this.list.find(d => d.status === 'MODIFIED') || this.list[0] || null
      } catch (e) {
        this.list = []
        this.active = null
      } finally {
        this.loading = false
      }
    },
    statusText(status) {
      if (status === 'NEW') return '新文件'
      if (status === 'MODIFIED') return '有变化'
      return '无变化'
    },
    statusType(status) {
      if (status === 'NEW') return 'success'
      if (status === 'MODIFIED') return 'warning'
      return 'info'
    },
    lineClass(line) {
      if (line.type === 'ADD') return 'diff-add'
      if (line.type === 'DEL') return 'diff-del'
      return 'diff-equal'
    },
    lineSign(line) {
      if (line.type === 'ADD') return '+'
      if (line.type === 'DEL') return '-'
      return ' '
    }
  }
}
</script>

<style scoped>
.diff-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.write-tip {
  font-size: 13px;
  color: var(--cg-text-regular);
}

.inline-tip {
  font-size: 12px;
  color: var(--cg-text-secondary);
}

.muted {
  color: var(--cg-text-secondary);
  font-size: 12.5px;
}

.preview {
  display: flex;
  gap: 14px;
  height: 520px;
}

.preview-tree {
  width: 44%;
  border: 1px solid var(--cg-border-light);
  border-radius: 4px;
  overflow: auto;
  padding: 8px;
}

.preview-code {
  flex: 1;
  border: 1px solid var(--cg-border-light);
  border-radius: 4px;
  background: #282c34;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.code-head {
  color: #abb2bf;
  font-size: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid #3e4451;
  font-family: Consolas, Monaco, monospace;
}

.diff-tree {
  width: 38%;
  padding: 4px;
}

.diff-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12.5px;
}

.diff-item:hover {
  background: var(--cg-bg-page);
}

.diff-item.active {
  background: var(--cg-primary-soft);
}

.diff-path {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: Consolas, Monaco, monospace;
}

.diff-stat {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.diff-add-num {
  color: var(--cg-success);
  margin-left: 6px;
}

.diff-del-num {
  color: var(--cg-danger);
  margin-left: 6px;
}

.diff-body {
  flex: 1;
  overflow: auto;
  padding: 6px 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.diff-line {
  display: flex;
  gap: 8px;
  padding: 0 10px;
  white-space: pre;
}

.diff-gutter {
  width: 34px;
  text-align: right;
  color: #5c6370;
  flex: none;
}

.diff-sign {
  width: 10px;
  flex: none;
}

.diff-text {
  flex: 1;
}

.diff-add {
  background: #1e3a24;
  color: #98c379;
}

.diff-del {
  background: #3d2222;
  color: #e06c75;
}

.diff-equal {
  color: #abb2bf;
}
</style>
