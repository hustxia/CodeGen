<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">安装范式包到项目</span>
      <span class="page-desc">克隆表结构生成建模方案（plan），不直接建库 — Step2 建库仍需人工二次确认</span>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <el-steps :active="step" finish-status="success" simple>
        <el-step title="选择范式包" />
        <el-step title="选项目与模块" />
        <el-step title="确认安装" />
      </el-steps>
    </el-card>

    <!-- Step 1 -->
    <el-card v-if="step === 0" shadow="never" class="form-card">
      <div class="section-title">选择要安装的范式包</div>
      <el-radio-group v-model="packKey" class="pack-list">
        <el-radio v-for="p in paradigms" :key="p.packKey" :label="p.packKey" border class="pack-item">
          {{ p.name }} <span class="mono sub">{{ p.packKey }}</span>
          <span class="sub">命中 {{ p.hitRate }}% · 直建 {{ p.directTables }} 表</span>
        </el-radio>
      </el-radio-group>
      <div class="form-actions">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :disabled="!packKey" @click="step = 1">下一步</el-button>
      </div>
    </el-card>

    <!-- Step 2 -->
    <el-card v-if="step === 1" shadow="never" class="form-card">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="范式包">
          <span>{{ currentPack.name || form.packKey }} <span class="mono sub">v{{ currentPack.version }}</span></span>
        </el-form-item>
        <el-form-item label="目标项目" required>
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 340px">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统名称">
          <el-input v-model="form.systemName" placeholder="中文系统名，如：某某城管平台" style="width: 340px" />
        </el-form-item>
        <el-form-item label="安装范围">
          <el-radio-group v-model="form.scope">
            <el-radio label="new">新建系统</el-radio>
            <el-radio label="supplement">为已有系统补表</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择模块" required>
          <el-checkbox-group v-model="form.moduleNames" class="mod-grid">
            <el-checkbox v-for="m in currentPack.modules || []" :key="m.name" :label="m.name" border>
              {{ m.name }}（{{ m.tables.length }} 表）
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <div class="form-actions">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :disabled="!form.projectId || !form.moduleNames.length" @click="submit">生成安装预览</el-button>
      </div>
    </el-card>

    <!-- Step 3 -->
    <el-card v-if="step === 2" shadow="never" class="form-card">
      <div class="section-title">安装预览（plan 摘要 · 由克隆链路实时生成）</div>
      <div class="summary">
        <div><b>{{ result.tableCount }}</b><span>规划表数</span></div>
        <div><b>{{ result.moduleCount }}</b><span>模块数</span></div>
        <div><b>{{ result.excludedCount }}</b><span>排除表数</span></div>
        <div><b>{{ result.planSource }}</b><span>plan 来源</span></div>
      </div>
      <div class="warn-box">
        安装只生成建模方案与安装台账，<b>不会自动建库、不会自动生成代码</b>。下一步在「规格工件链」确认 TABLES 工件后，走流水线 Step2 二次确认建库。
      </div>
      <div class="form-actions">
        <el-button @click="step = 1">返回修改</el-button>
        <el-button type="primary" @click="finish">完成</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { listParadigms, getParadigmDetail, installParadigm } from '@/api/industryPack'
import { listProjectOptions } from '@/api/gen'
import { Message } from 'element-ui'

export default {
  name: 'IndustryPackInstall',
  data() {
    return {
      step: 0,
      paradigms: [],
      packKey: this.$route.query.packKey || '',
      currentPack: {},
      projects: [],
      form: {
        packKey: '',
        projectId: null,
        moduleNames: [],
        scope: 'new',
        systemName: '',
        scale: 'small',
        deploy: 'mono'
      },
      result: {}
    }
  },
  created() {
    listParadigms().then(res => { this.paradigms = res.data || [] })
    listProjectOptions().then(res => {
      const d = res.data || []
      this.projects = Array.isArray(d) ? d : (d.records || [])
    })
    if (this.packKey) this.loadDetail(this.packKey)
  },
  methods: {
    loadDetail(key) {
      getParadigmDetail(key).then(res => {
        this.currentPack = res.data || {}
        this.form.moduleNames = (this.currentPack.modules || []).map(m => m.name)
      })
    },
    submit() {
      this.form.packKey = this.packKey
      this.form.systemName = this.form.systemName || (this.currentPack.name || '新系统')
      installParadigm(this.form).then(res => {
        this.result = res.data || {}
        this.step = 2
      })
    },
    finish() {
      Message.success('安装完成：已生成建模方案，台账已记录')
      this.$router.push({ path: '/gen/industry-pack/installed', query: { projectId: String(this.form.projectId) } })
    }
  },
  watch: {
    packKey(key) { if (key) this.loadDetail(key) }
  }
}
</script>

<style scoped>
.form-card { margin-top: 14px; padding: 18px; }
.section-title { font-size: 13px; font-weight: 600; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #3d6df5; }
.pack-list { display: flex; flex-wrap: wrap; gap: 10px; }
.pack-item { margin: 0; }
.sub { color: #7c87a3; font-size: 11px; margin-left: 8px; }
.mono { font-family: Consolas, Monaco, monospace; }
.mod-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 14px 0; }
.summary div { background: #ecf0fe; border-radius: 6px; padding: 14px; text-align: center; }
.summary b { display: block; font-size: 22px; color: #3d6df5; }
.summary span { font-size: 11px; color: #7c87a3; }
.warn-box { background: #f6f8fd; border: 1px dashed #dde5f2; border-radius: 6px; padding: 12px 14px; font-size: 12px; color: #7c87a3; line-height: 1.8; }
</style>
