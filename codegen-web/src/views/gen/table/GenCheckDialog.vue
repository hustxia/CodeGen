<template>
  <el-dialog
    title="生成自检"
    :visible="visible"
    width="920px"
    top="6vh"
    @update:visible="$emit('update:visible', $event)"
  >
    <div v-if="result">
      <el-alert
        :type="result.passed ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="`${result.passed ? '自检通过' : '自检未通过'}：文件 ${result.fileCount} 个（Java ${result.javaCount} 个），错误 ${result.errorCount}，警告 ${result.warningCount}，耗时 ${result.elapsedMs} ms`"
      />
      <div v-if="!result.compilerAvailable" class="tip">
        当前 JVM 没有编译器（仅 JRE），只做了结构检查。用 JDK 启动 admin 可启用完整编译自检。
      </div>
      <el-table v-if="result.issues && result.issues.length" :data="result.issues" size="mini" border max-height="420" class="mt12">
        <el-table-column prop="severity" label="级别" width="90">
          <template slot-scope="{ row }">
            <span class="pill" :class="row.severity === 'ERROR' ? 'err' : 'skip'">{{ row.severity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="file" label="文件" min-width="240" show-overflow-tooltip />
        <el-table-column prop="line" label="行" width="70" align="center" />
        <el-table-column prop="message" label="问题" min-width="340" show-overflow-tooltip />
      </el-table>
      <div v-else class="tip">没有问题项。</div>
    </div>
    <div slot="footer">
      <el-button @click="$emit('update:visible', false)">关 闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
/**
 * 生成自检结果弹窗（V2.0 从 gen.vue 拆出）
 *
 * 纯展示：自检请求仍由父页面发起（它要处理"未勾选表"的交互和按钮 loading），
 * 这里只负责把结果渲染得能看。
 */
export default {
  name: 'GenCheckDialog',
  props: {
    visible: { type: Boolean, default: false },
    result: { type: Object, default: null }
  }
}
</script>

<style scoped>
.tip {
  font-size: 12.5px;
  color: var(--cg-text-secondary);
  line-height: 1.6;
  margin-top: 8px;
}

.mt12 {
  margin-top: 12px;
}

.pill {
  display: inline-block;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11.5px;
  line-height: 18px;
}

.pill.err {
  background: var(--cg-danger-bg);
  color: var(--cg-danger);
}

.pill.skip {
  background: var(--cg-warning-bg);
  color: var(--cg-warning);
}
</style>
