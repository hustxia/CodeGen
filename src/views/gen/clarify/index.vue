<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">意图澄清与领域护栏</span>
      <span class="page-desc">
        一次只问一个关键问题；确定性规则先拦，命中时解释「为什么拦 + 怎么做才对」；确认后写入 SPEC 非目标章节。
      </span>
      <el-button size="small" type="primary" icon="el-icon-plus" class="head-btn" @click="openCreate">新建澄清</el-button>
    </div>

    <div class="layout">
      <!-- 左：会话列表 -->
      <el-card shadow="never" class="sessions">
        <div class="col-head"><b>澄清会话</b><span class="hint">{{ sessions.length }}</span></div>
        <div
          v-for="s in sessions"
          :key="s.id"
          class="sess"
          :class="{ active: session && session.id === s.id }"
          @click="pick(s)"
        >
          <div class="sess-name">{{ s.systemName }}</div>
          <div class="sess-meta">
            <el-tag size="mini" :type="statusTag(s.status)">{{ statusText(s.status) }}</el-tag>
            <span>{{ INDUSTRY[s.industry] || s.industry }}</span>
          </div>
        </div>
        <div v-if="!sessions.length" class="empty-small">暂无会话，点「新建澄清」开始</div>
      </el-card>

      <!-- 中：一次一问 -->
      <el-card shadow="never" class="chat-card">
        <div class="col-head">
          <b>澄清对话</b>
          <span class="hint">{{ answeredCount }} / {{ totalQuestions || 5 }} 问</span>
        </div>
        <div class="progress">
          <i v-for="n in (totalQuestions || 5)" :key="n" :class="{ done: n <= answeredCount, cur: n === answeredCount + 1 }" />
        </div>
        <div v-if="!session" class="empty">请从左侧选择会话，或新建一次澄清</div>
        <template v-else>
          <div class="chat-body">
            <div v-for="(m, i) in chat" :key="i" class="bubble" :class="m.role">
              <div class="btext">{{ m.text }}</div>
              <div v-if="m.options && m.options.length" class="opts">
                <span
                  v-for="(o, j) in m.options"
                  :key="j"
                  class="opt"
                  :class="{ sel: answerText === o }"
                  @click="answerText = o"
                >{{ o }}</span>
              </div>
            </div>
          </div>
          <div class="ask-bar">
            <el-input
              v-model="answerText"
              size="small"
              placeholder="填写答复；也可直接点上方选项"
              :disabled="done"
              @keyup.enter.native="submitAnswer"
            />
            <el-button size="small" type="primary" :disabled="done" :loading="answering" @click="submitAnswer">发送</el-button>
            <el-button size="small" :disabled="done" @click="submitAnswer(true)">跳过</el-button>
          </div>
        </template>
      </el-card>

      <!-- 右：拦截解释 -->
      <el-card shadow="never" class="guard-card">
        <div class="col-head">
          <b>护栏拦截记录</b>
          <span class="tags">
            <el-tag size="mini" type="danger">{{ evalResult.blockCount || 0 }} BLOCK</el-tag>
            <el-tag size="mini" type="warning">{{ evalResult.warnCount || 0 }} WARN</el-tag>
          </span>
        </div>
        <div v-if="!hits.length" class="empty-small">暂无命中；发送答复后自动重跑检测</div>
        <div v-for="h in hits" :key="h.ruleCode" class="guard" :class="{ warn: h.severity !== 'BLOCK' }">
          <div class="gt">
            <span>{{ h.severity === 'BLOCK' ? '⛔' : '⚠' }} {{ h.ruleCode }}</span>
            <span class="gc">{{ CATEGORY[h.category] || h.category }}</span>
          </div>
          <div class="gtitle">{{ h.title }}</div>
          <div class="why">{{ h.reason }}</div>
          <div class="fix">✓ {{ h.suggestion }}</div>
        </div>
        <div class="mode">
          <el-checkbox v-model="advancedMode" @change="onModeChange">高级模式：显式忽略 BLOCK（留痕）</el-checkbox>
        </div>
      </el-card>
    </div>

    <div class="footbar">
      <span class="foot-sum">
        <template v-if="session">
          行业 {{ INDUSTRY[session.industry] || session.industry }} · 已答 {{ answeredCount }}/{{ totalQuestions || 5 }}
          · 命中 {{ hits.length }} 条
          <span v-if="session.specId"> · 已生成 SPEC 草案（{{ session.specId }}）</span>
        </template>
        <template v-else>尚未开始澄清</template>
      </span>
      <span>
        <el-button size="small" :disabled="!session" :loading="checking" @click="recheck">重新检测</el-button>
        <el-button
          size="small"
          type="primary"
          :disabled="!session || session.status === 'CONFIRMED' || cannotConfirm"
          :loading="confirming"
          @click="doConfirm"
        >确认并写入 SPEC</el-button>
      </span>
    </div>

    <!-- 新建澄清 -->
    <el-dialog title="新建澄清" :visible.sync="createVisible" width="560px">
      <el-form label-width="96px" size="small">
        <el-form-item label="所属系统" required>
          <el-select v-model="form.projectId" filterable placeholder="选择系统" style="width:100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业" required>
          <el-select v-model="form.industry" placeholder="选择行业" style="width:100%">
            <el-option v-for="(label, key) in INDUSTRY" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统名称" required>
          <el-input v-model="form.systemName" placeholder="如：智慧种植 + 水肥一体化" />
        </el-form-item>
        <el-form-item label="业务描述">
          <el-input v-model="form.systemDesc" type="textarea" :rows="3" placeholder="写清业务对象与动作" />
        </el-form-item>
        <el-form-item label="候选模块">
          <el-input v-model="form.modules" placeholder="逗号分隔，如：地块与种植季,农事作业,水肥一体化" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="starting" @click="doStart">开始澄清</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listProject } from '@/api/gen'
import {
  startClarify, answerClarify, clarifySessions, clarifyStep,
  evaluateSession, confirmClarify
} from '@/api/guardrail'

export default {
  name: 'ClarifyWizard',
  data() {
    return {
      INDUSTRY: {
        gov_affairs: '村务治理', cm_city: '城市管理', agr_crop: '智慧种植', park_park: '智慧园区'
      },
      CATEGORY: {
        BLACKLIST: '黑名单', WHITELIST: '白名单', CONFLICT: '冲突',
        BOUNDARY: '边界', CLARIFY: '澄清'
      },
      projects: [],
      sessions: [],
      session: null,
      question: null,
      done: false,
      answeredCount: 0,
      totalQuestions: 5,
      chat: [],
      answerText: '',
      hits: [],
      evalResult: {},
      advancedMode: false,
      overriddenCodes: [],
      checking: false,
      answering: false,
      confirming: false,
      starting: false,
      createVisible: false,
      form: { projectId: null, industry: '', systemName: '', systemDesc: '', modules: '' }
    }
  },
  computed: {
    cannotConfirm() {
      if (!this.evalResult.blocked) return false
      if (!this.advancedMode) return true
      const codes = (this.hits || []).filter(h => h.severity === 'BLOCK').map(h => h.ruleCode)
      return codes.some(c => !this.overriddenCodes.includes(c))
    }
  },
  created() {
    this.loadProjects()
    this.loadSessions()
  },
  methods: {
    loadProjects() {
      listProject().then(res => { this.projects = (res && res.data) || [] })
    },
    loadSessions() {
      clarifySessions(null).then(res => { this.sessions = (res && res.data) || [] })
    },
    openCreate() {
      this.form = { projectId: this.projects[0] ? this.projects[0].id : null, industry: '', systemName: '', systemDesc: '', modules: '' }
      this.createVisible = true
    },
    doStart() {
      if (!this.form.projectId || !this.form.industry || !this.form.systemName) {
        this.$message.warning('所属系统、行业与系统名称必填')
        return
      }
      this.starting = true
      const modules = (this.form.modules || '').split(/[,，]/).map(s => s.trim()).filter(Boolean)
      startClarify({
        projectId: this.form.projectId,
        industry: this.form.industry,
        systemName: this.form.systemName,
        systemDesc: this.form.systemDesc,
        moduleNames: modules
      }).then(res => {
        const data = res.data || {}
        this.applyStep(data)
        this.chat = [
          { role: 'ai', text: '检测到你选择了「' + this.session.systemName + '」，先确认几个会影响生成结构的问题。' },
          this.bubbleOf(data.question)
        ]
        this.createVisible = false
        this.loadSessions()
        this.recheck()
      }).finally(() => { this.starting = false })
    },
    pick(row) {
      clarifyStep(row.id).then(res => {
        this.applyStep(res.data || {})
        this.advancedMode = !!(this.session && this.session.advancedMode === 1)
        this.rebuildChat()
        this.recheck()
      })
    },
    applyStep(data) {
      this.session = data.session || this.session
      this.question = data.question || null
      this.done = !!data.done
      this.answeredCount = data.answeredCount || 0
      this.totalQuestions = data.totalQuestions || 5
      this.answerText = ''
    },
    bubbleOf(q) {
      if (!q) return { role: 'ai', text: '信息已足够，可以确认并写入 SPEC。' }
      let options = []
      try { options = JSON.parse(q.optionsJson || '[]') } catch (e) { options = [] }
      return { role: 'ai', text: q.question, options }
    },
    rebuildChat() {
      let answers = {}
      try { answers = JSON.parse((this.session && this.session.answersJson) || '{}') } catch (e) { answers = {} }
      const chat = [{ role: 'ai', text: '检测到你选择了「' + this.session.systemName + '」，先确认几个会影响生成结构的问题。' }]
      Object.keys(answers).forEach(k => {
        chat.push({ role: 'ai', text: '请确认：' + k })
        chat.push({ role: 'me', text: answers[k] || '（已跳过）' })
      })
      chat.push(this.bubbleOf(this.question))
      this.chat = chat
    },
    submitAnswer(skip) {
      if (!this.question) return
      const answer = skip ? '已跳过' : (this.answerText || '').trim()
      if (!skip && !answer) {
        this.$message.warning('请填写答复，或点「跳过」')
        return
      }
      this.answering = true
      answerClarify({
        sessionId: this.session.id,
        questionCode: this.question.questionCode,
        answer
      }).then(res => {
        this.chat.push({ role: 'me', text: answer })
        this.applyStep(res.data || {})
        this.chat.push(this.bubbleOf(this.question))
        this.recheck()
      }).finally(() => { this.answering = false })
    },
    recheck() {
      if (!this.session) return
      this.checking = true
      evaluateSession(this.session.id).then(res => {
        const data = res.data || {}
        this.evalResult = data
        this.hits = data.hits || []
      }).finally(() => { this.checking = false })
    },
    onModeChange() {
      const blocks = (this.hits || []).filter(h => h.severity === 'BLOCK').map(h => h.ruleCode)
      this.overriddenCodes = this.advancedMode ? blocks : []
    },
    doConfirm() {
      this.confirming = true
      confirmClarify(this.session.id, {
        advancedMode: this.advancedMode,
        overriddenCodes: this.advancedMode ? this.overriddenCodes : []
      }).then(res => {
        const data = res.data || {}
        this.$message.success('已生成 SPEC 草案 v' + data.specVersion + '，请在规格工件页复核确认')
        this.session.status = 'CONFIRMED'
        this.session.specId = data.specId
        this.loadSessions()
      }).finally(() => { this.confirming = false })
    },
    statusTag(s) {
      if (s === 'CONFIRMED') return 'success'
      if (s === 'READY') return 'warning'
      if (s === 'ABANDONED') return 'info'
      return 'primary'
    },
    statusText(s) {
      return { CLARIFYING: '澄清中', READY: '待确认', CONFIRMED: '已确认', ABANDONED: '已放弃' }[s] || s
    }
  }
}
</script>

<style scoped>
.page-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.page-title { font-size: 17px; font-weight: 600; }
.page-desc { font-size: 12px; color: var(--cg-text-secondary); }
.head-btn { margin-left: auto; }
.layout { display: grid; grid-template-columns: 230px 1fr 320px; gap: 12px; align-items: start; }
.col-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 13px; }
.hint, .empty-small { font-size: 12px; color: var(--cg-text-secondary); }
.sess { border: 1px solid var(--cg-border); border-radius: 6px; padding: 8px 10px; margin-bottom: 8px; cursor: pointer; }
.sess:hover { border-color: var(--cg-primary-light-5); }
.sess.active { border-color: var(--cg-primary); background: var(--cg-primary-soft); }
.sess-name { font-size: 12.5px; font-weight: 600; margin-bottom: 5px; }
.sess-meta { display: flex; gap: 8px; align-items: center; font-size: 11px; color: var(--cg-text-secondary); }
.progress { display: flex; gap: 5px; margin-bottom: 10px; }
.progress i { flex: 1; height: 4px; border-radius: 2px; background: var(--cg-border-light); }
.progress i.done { background: var(--cg-primary); }
.progress i.cur { background: var(--cg-primary-light-5); }
.chat-body { height: 400px; overflow-y: auto; padding-right: 4px; }
.bubble { max-width: 88%; border-radius: 8px; padding: 9px 11px; margin-bottom: 10px; font-size: 12px; line-height: 1.7; }
.bubble.ai { background: var(--cg-bg-page); color: var(--cg-text-regular); }
.bubble.me { background: var(--cg-primary); color: #fff; margin-left: auto; }
.opts { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; }
.opt { border: 1px solid var(--cg-border); border-radius: 12px; padding: 3px 9px; font-size: 11px; cursor: pointer; background: var(--cg-bg-container); }
.opt.sel { border-color: var(--cg-primary); color: var(--cg-primary); background: var(--cg-primary-soft); }
.ask-bar { display: flex; gap: 8px; margin-top: 10px; }
.ask-bar .el-input { flex: 1; }
.empty { text-align: center; color: var(--cg-text-secondary); padding: 120px 0; font-size: 13px; }
.guard { border-left: 3px solid var(--cg-danger); background: #fef6f6; border-radius: 0 5px 5px 0; padding: 8px 10px; margin-bottom: 9px; font-size: 11.5px; }
.guard.warn { border-left-color: var(--cg-warning); background: #fdf9f1; }
.gt { display: flex; justify-content: space-between; font-weight: 600; color: var(--cg-danger); }
.guard.warn .gt { color: #a37018; }
.gc { font-size: 10px; font-weight: 400; }
.gtitle { margin: 3px 0 5px; font-weight: 600; }
.why { color: var(--cg-text-regular); line-height: 1.6; margin-bottom: 4px; }
.fix { color: var(--cg-success); line-height: 1.6; }
.mode { margin-top: 12px; font-size: 11.5px; color: var(--cg-text-secondary); }
.footbar { display: flex; justify-content: space-between; align-items: center; margin-top: 14px; background: var(--cg-bg-container); border: 1px solid var(--cg-border-light); border-radius: 6px; padding: 10px 14px; }
.foot-sum { font-size: 12px; color: var(--cg-text-secondary); }
.tags { display: flex; gap: 5px; }
</style>
