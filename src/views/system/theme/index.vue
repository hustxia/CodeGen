<template>
  <div class="app-container theme-setting">
    <el-row :gutter="16">
      <!-- ================= 左侧：三组选择器 ================= -->
      <el-col :span="16">
        <el-card shadow="never" class="sec-card">
          <div slot="header" class="sec-head">
            <span>主色相</span>
            <span class="sec-sub">决定按钮、菜单高亮、链接的品牌色（24 种）</span>
          </div>
          <div class="hue-grid">
            <div
              v-for="h in hues"
              :key="h.id"
              class="hue-item"
              :class="{ active: draft.hue === h.id }"
              :title="h.name"
              @click="pick('hue', h.id)"
            >
              <i class="hue-dot" :style="{ background: h.color }"></i>
              <em>{{ h.name }}</em>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="sec-card">
          <div slot="header" class="sec-head">
            <span>明暗模式</span>
            <span class="sec-sub">浅色适合白天办公，深色适合长时间盯屏</span>
          </div>
          <div class="mode-row">
            <div
              v-for="m in modes"
              :key="m.id"
              class="mode-item"
              :class="{ active: draft.mode === m.id }"
              @click="pick('mode', m.id)"
            >
              <i class="mode-swatch" :style="swatchOf(m.id)"></i>
              <div class="mode-txt">
                <b>{{ m.name }}</b>
                <span>{{ m.desc }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="sec-card">
          <div slot="header" class="sec-head">
            <span>界面形态</span>
            <span class="sec-sub">控制圆角、控件高度与阴影强度</span>
          </div>
          <div class="shape-row">
            <div
              v-for="s in shapeList"
              :key="s.id"
              class="shape-item"
              :class="{ active: draft.shape === s.id }"
              @click="pick('shape', s.id)"
            >
              <div class="shape-demo">
                <i :style="{ borderRadius: Math.min(s.radiusBtn, 10) + 'px' }"></i>
                <i :style="{ borderRadius: Math.min(s.radiusBtn, 10) + 'px' }"></i>
              </div>
              <b>{{ s.name }}</b>
              <span>{{ s.desc }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- ================= 右侧：预览 + 保存 ================= -->
      <el-col :span="8">
        <el-card shadow="never" class="preview-card">
          <div slot="header" class="sec-head">
            <span>实时预览</span>
            <span class="sec-sub">仅影响本卡片，保存前不影响线上</span>
          </div>

          <!-- 变量挂在这一层，子元素全部用 var(--cg-*) 取色，
               因此只是「局部换了套皮肤」，不会污染全局 -->
          <div class="pv-wrap" :style="previewStyle">
            <div class="pv-login">
              <div class="pv-glow g1"></div>
              <div class="pv-glow g2"></div>
              <div class="pv-card">
                <i class="pv-badge"></i>
                <i class="pv-line w60"></i>
                <i class="pv-line w40"></i>
                <i class="pv-input"></i>
                <i class="pv-input"></i>
                <i class="pv-btn"></i>
              </div>
            </div>

            <div class="pv-dash">
              <div class="pv-aside">
                <i class="pv-logo"></i>
                <i class="pv-nav on"></i>
                <i class="pv-nav"></i>
                <i class="pv-nav"></i>
                <i class="pv-nav"></i>
              </div>
              <div class="pv-main">
                <div class="pv-topbar"></div>
                <div class="pv-body">
                  <i class="pv-block"></i>
                  <i class="pv-block"></i>
                  <i class="pv-block"></i>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="save-card">
          <div class="state-row">
            <span>当前生效</span>
            <b>{{ labelOf(effectiveId) }}</b>
          </div>
          <div class="state-row">
            <span>已保存到账号</span>
            <b>{{ labelOf(savedPrefId) }}</b>
          </div>
          <div class="state-row" :class="{ dirty: isDirty }">
            <span>待保存</span>
            <b>{{ labelOf(draftId) }}</b>
          </div>

          <el-button class="reset-btn" size="small" @click="resetDraft">恢复默认</el-button>

          <el-button
            type="primary"
            class="save-btn"
            :loading="saving"
            :disabled="!isDirty"
            @click="handleSave"
          >
            {{ isDirty ? '保存并应用' : '已是最新' }}
          </el-button>

          <p class="save-tip">
            保存后需<b>重新登录</b>，登录页与全站才会应用新皮肤。
          </p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { Message, MessageBox } from 'element-ui'
import { saveUserTheme } from '@/api/theme'
import {
  HUES,
  SHAPES,
  MODES,
  DEFAULT_HUE,
  DEFAULT_MODE,
  DEFAULT_SHAPE,
  parseThemeId,
  normalizeThemeId,
  themeLabel,
  previewVars,
  currentThemeId
} from '@/utils/theme'

export default {
  name: 'SystemTheme',
  data() {
    const { hue, mode, shape } = parseThemeId(currentThemeId())
    return {
      hues: HUES,
      modes: MODES,
      shapeList: Object.values(SHAPES),
      draft: { hue, mode, shape },
      // 当前页面实际在用的皮肤（登录时定的，本次不随选择变化）
      effectiveId: currentThemeId(),
      // 服务端已保存的偏好（保存成功后更新，用于展示「已落库」状态）
      savedPrefId: currentThemeId(),
      saving: false
    }
  },
  computed: {
    draftId() {
      return normalizeThemeId(`${this.draft.hue}-${this.draft.mode}-${this.draft.shape}`)
    },
    isDirty() {
      return this.draftId !== this.savedPrefId
    },
    previewStyle() {
      // 返回以 --cg-* 为键的对象，Vue 2 会用 setProperty 正确写入自定义属性
      return previewVars(this.draftId)
    }
  },
  methods: {
    pick(field, value) {
      this.draft = { ...this.draft, [field]: value }
    },
    labelOf(id) {
      return themeLabel(id)
    },
    /** 明暗卡片里的小色块：左深（侧栏）右浅（内容区） */
    swatchOf(mode) {
      const vars = previewVars(`${this.draft.hue}-${mode}-${this.draft.shape}`)
      return {
        background: `linear-gradient(90deg, ${vars['--cg-aside-from']} 0 34%, ${vars['--cg-bg-page']} 34% 100%)`
      }
    },
    resetDraft() {
      this.draft = { hue: DEFAULT_HUE, mode: DEFAULT_MODE, shape: DEFAULT_SHAPE }
    },
    async handleSave() {
      this.saving = true
      try {
        const res = await saveUserTheme(this.draftId)
        // 后端返回归一化后的 ID；异常结构时用本地值兜底
        this.savedPrefId = normalizeThemeId(
          res && typeof res.data === 'string' ? res.data : this.draftId
        )
        this.$store.commit('user/SET_THEME_PREF', this.savedPrefId)
        this.promptRelogin()
      } catch (e) {
        // 具体错误已由 request 拦截器统一提示，这里不重复弹
      } finally {
        this.saving = false
      }
    },
    promptRelogin() {
      MessageBox.confirm(
        `主题已保存为「${this.labelOf(this.savedPrefId)}」。<br/>为看到完整效果，需要退出后重新登录 —— 登录页与后台全站都会应用这套皮肤。`,
        '保存成功',
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: '立即重新登录',
          cancelButtonText: '稍后',
          type: 'success'
        }
      )
        .then(() => {
          this.$store.dispatch('user/logout').then(() => {
            this.$router.push(`/login`)
          })
        })
        .catch(() => {
          Message.info('已保存，下次登录时自动生效')
        })
    }
  }
}
</script>

<style scoped>
.theme-setting .sec-card + .sec-card {
  margin-top: 16px;
}

.sec-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.sec-head span:first-child {
  font-weight: 600;
  color: var(--cg-text);
}
.sec-sub {
  font-size: 12px;
  color: var(--cg-text-secondary);
}

/* ---------- 色相 ---------- */
.hue-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 10px 6px;
}
.hue-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 2px;
  border-radius: var(--cg-radius-sm);
  cursor: pointer;
  transition: background-color 0.16s;
}
.hue-item:hover {
  background: var(--cg-bg-hover);
}
.hue-dot {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  transition: transform 0.16s, box-shadow 0.16s;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.14);
}
.hue-item:hover .hue-dot {
  transform: scale(1.12);
}
.hue-item.active .hue-dot {
  box-shadow: 0 0 0 2px var(--cg-bg-container), 0 0 0 4px var(--cg-primary);
}
.hue-item em {
  font-style: normal;
  font-size: 11px;
  color: var(--cg-text-secondary);
  line-height: 1;
}
.hue-item.active em {
  color: var(--cg-text);
  font-weight: 600;
}

/* ---------- 明暗 ---------- */
.mode-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.mode-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1.5px solid var(--cg-border);
  border-radius: var(--cg-radius);
  cursor: pointer;
  transition: border-color 0.16s, background-color 0.16s;
}
.mode-item:hover {
  border-color: var(--cg-primary-light-5);
}
.mode-item.active {
  border-color: var(--cg-primary);
  background: var(--cg-primary-soft);
}
.mode-swatch {
  width: 52px;
  height: 34px;
  border-radius: var(--cg-radius-sm);
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px var(--cg-border-light);
}
.mode-txt b {
  display: block;
  font-size: 13px;
  color: var(--cg-text);
}
.mode-txt span {
  font-size: 12px;
  color: var(--cg-text-secondary);
}

/* ---------- 形态 ---------- */
.shape-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.shape-item {
  text-align: center;
  padding: 12px 6px 10px;
  border: 1.5px solid var(--cg-border);
  border-radius: var(--cg-radius);
  cursor: pointer;
  transition: border-color 0.16s, background-color 0.16s;
}
.shape-item:hover {
  border-color: var(--cg-primary-light-5);
}
.shape-item.active {
  border-color: var(--cg-primary);
  background: var(--cg-primary-soft);
}
.shape-demo {
  display: flex;
  justify-content: center;
  gap: 5px;
  margin-bottom: 8px;
}
.shape-demo i {
  display: block;
  width: 26px;
  height: 16px;
  background: var(--cg-primary);
  opacity: 0.9;
}
.shape-demo i:last-child {
  width: 16px;
  background: var(--cg-primary-light-3);
}
.shape-item b {
  display: block;
  font-size: 12.5px;
  color: var(--cg-text-regular);
}
.shape-item.active b {
  color: var(--cg-primary);
}
.shape-item span {
  font-size: 11px;
  color: var(--cg-text-placeholder);
  line-height: 1.4;
}

/* ---------- 预览 ---------- */
.pv-wrap {
  border-radius: var(--cg-radius);
  overflow: hidden;
  border: 1px solid var(--cg-border-light);
}
.pv-login {
  position: relative;
  height: 104px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--cg-hero-from), var(--cg-hero-via) 55%, var(--cg-hero-to));
  overflow: hidden;
}
.pv-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(26px);
  opacity: 0.5;
}
.pv-glow.g1 {
  width: 110px;
  height: 110px;
  background: var(--cg-glow-1);
  top: -40px;
  left: -30px;
}
.pv-glow.g2 {
  width: 90px;
  height: 90px;
  background: var(--cg-glow-3);
  bottom: -40px;
  right: -20px;
}
.pv-card {
  position: relative;
  z-index: 1;
  width: 108px;
  padding: 10px 11px;
  border-radius: var(--cg-radius);
  background: var(--cg-bg-elevated);
  box-shadow: var(--cg-shadow-login);
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.pv-badge {
  width: 16px;
  height: 16px;
  border-radius: var(--cg-radius-sm);
  background: linear-gradient(135deg, var(--cg-primary-solid), var(--cg-primary-active));
  margin-bottom: 2px;
}
.pv-line {
  display: block;
  height: 4px;
  border-radius: 2px;
  background: var(--cg-border);
}
.pv-line.w60 {
  width: 60%;
  background: var(--cg-text);
  opacity: 0.6;
}
.pv-line.w40 {
  width: 40%;
}
.pv-input {
  display: block;
  height: 10px;
  border-radius: var(--cg-radius-input);
  background: var(--cg-input-bg);
  border: 1px solid var(--cg-border);
}
.pv-btn {
  display: block;
  height: 12px;
  margin-top: 2px;
  border-radius: var(--cg-radius-btn);
  background: linear-gradient(90deg, var(--cg-primary-solid), var(--cg-primary-active));
}

.pv-dash {
  display: flex;
  height: 96px;
  background: var(--cg-bg-page);
}
.pv-aside {
  width: 40px;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: linear-gradient(180deg, var(--cg-aside-from), var(--cg-aside-to));
}
.pv-logo {
  display: block;
  height: 12px;
  margin-bottom: 4px;
  border-radius: var(--cg-radius-sm);
  background: linear-gradient(135deg, var(--cg-primary-solid), var(--cg-primary-active));
}
.pv-nav {
  display: block;
  height: 7px;
  border-radius: var(--cg-radius-nav);
  background: rgba(255, 255, 255, 0.16);
}
.pv-nav.on {
  background: linear-gradient(90deg, var(--cg-primary-solid), var(--cg-primary-active));
}
.pv-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.pv-topbar {
  height: 16px;
  background: var(--cg-header-bg);
  border-bottom: 1px solid var(--cg-border-light);
}
.pv-body {
  flex: 1;
  padding: 8px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}
.pv-block {
  display: block;
  border-radius: var(--cg-radius-sm);
  background: var(--cg-bg-container);
  box-shadow: var(--cg-shadow-card);
}

/* ---------- 保存区 ---------- */
.save-card {
  margin-top: 16px;
}
.state-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  line-height: 2;
  color: var(--cg-text-secondary);
}
.state-row b {
  font-weight: 500;
  color: var(--cg-text-regular);
}
.state-row.dirty b {
  color: var(--cg-primary);
  font-weight: 600;
}
.save-btn {
  width: 100%;
  margin-top: 14px;
}
.reset-btn {
  margin-top: 10px;
}
.save-tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--cg-text-placeholder);
}
.save-tip b {
  color: var(--cg-warning);
}
</style>
