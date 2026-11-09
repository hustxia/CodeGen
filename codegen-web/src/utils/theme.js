/**
 * 主题引擎 —— 24 色相 × 2 明暗 × 5 形态 = 240 套皮肤
 *
 * 设计要点：
 * 1. 主题不是 240 份 CSS 文件，而是「一组参数」。运行时算出 50 多个 CSS 变量，
 *    注入 :root。包体积与主题数量无关，新增色相只多一行配置。
 * 2. 色阶生成沿用 Element UI 的 tint/shade 算法（mix 白/黑），
 *    这样自定义主题与 Element 自带主题的明度过渡手感一致。
 * 3. 语义色（成功/警告/危险）默认固定，但当主色相与之接近时自动偏转明度，
 *    否则「朱砂主题」里删除按钮会和主色糊成一片。
 *
 * 主题 ID 格式：`{hue}-{mode}-{shape}`，如 `blue-light-rounded`。
 * 任何非法 ID 一律回落到 DEFAULT_THEME，不抛错 —— 主题偏好是脏数据时不能白屏。
 */

/* =========================================================
   1. 色相表（按色相角 0°→345° 排列，每 15° 一个）
   ========================================================= */
export const HUES = [
  // 红橙系
  { id: 'cinnabar', name: '朱砂', color: '#e5484d', group: '红橙' },
  { id: 'scarlet', name: '绯红', color: '#ec5a3c', group: '红橙' },
  { id: 'terracotta', name: '赤陶', color: '#e8682a', group: '红橙' },
  { id: 'orange', name: '暖橙', color: '#f5731f', group: '红橙' },
  // 黄绿系
  { id: 'amber', name: '琥珀', color: '#d99e07', group: '黄绿' },
  { id: 'ginkgo', name: '银杏', color: '#b3a80c', group: '黄绿' },
  { id: 'lime', name: '青柠', color: '#86ba14', group: '黄绿' },
  { id: 'grass', name: '草绿', color: '#4caf50', group: '黄绿' },
  // 绿青系
  { id: 'bamboo', name: '竹青', color: '#16a34a', group: '绿青' },
  { id: 'jade', name: '翠绿', color: '#0d9f6e', group: '绿青' },
  { id: 'teal', name: '碧海', color: '#0d9488', group: '绿青' },
  { id: 'cyan', name: '青蓝', color: '#0e93a8', group: '绿青' },
  // 蓝系
  { id: 'lake', name: '湖蓝', color: '#0891b2', group: '蓝' },
  { id: 'sky', name: '天蓝', color: '#0ea5e9', group: '蓝' },
  { id: 'azure', name: '亮蓝', color: '#3b82f6', group: '蓝' },
  { id: 'blue', name: '晶蓝', color: '#3d6df5', group: '蓝' },
  // 蓝紫系
  { id: 'cobalt', name: '钴蓝', color: '#4f5ff0', group: '蓝紫' },
  { id: 'indigo', name: '靛青', color: '#4f46e5', group: '蓝紫' },
  { id: 'violet', name: '罗兰', color: '#7c3aed', group: '蓝紫' },
  { id: 'mist', name: '雾紫', color: '#9333ea', group: '蓝紫' },
  // 紫粉系
  { id: 'lilac', name: '藕荷', color: '#a855f7', group: '紫粉' },
  { id: 'wisteria', name: '紫藤', color: '#c026d3', group: '紫粉' },
  { id: 'magenta', name: '品红', color: '#d946a8', group: '紫粉' },
  { id: 'rose', name: '玫红', color: '#e83e6d', group: '紫粉' }
]

/* =========================================================
   2. 形态表（控制圆角、控件高度、字号、阴影强度）
   ========================================================= */
export const SHAPES = {
  rounded: {
    id: 'rounded', name: '圆润', desc: '默认，通用业务系统',
    radiusSm: 6, radius: 10, radiusLg: 16,
    radiusBtn: 6, radiusInput: 6, radiusNav: 8, radiusTag: 4, radiusBadge: 14,
    controlH: 36, fontSize: 14, shadow: 'md'
  },
  square: {
    id: 'square', name: '直角', desc: '紧凑干练，偏数据后台',
    radiusSm: 2, radius: 4, radiusLg: 6,
    radiusBtn: 2, radiusInput: 2, radiusNav: 2, radiusTag: 2, radiusBadge: 4,
    controlH: 34, fontSize: 13.5, shadow: 'flat'
  },
  pill: {
    id: 'pill', name: '胶囊', desc: '大圆角，偏消费级产品',
    radiusSm: 8, radius: 20, radiusLg: 24,
    radiusBtn: 999, radiusInput: 999, radiusNav: 999, radiusTag: 999, radiusBadge: 999,
    controlH: 38, fontSize: 14, shadow: 'md'
  },
  soft: {
    id: 'soft', name: '柔和', desc: '大圆角 + 重阴影，偏设计感',
    radiusSm: 10, radius: 22, radiusLg: 28,
    radiusBtn: 12, radiusInput: 12, radiusNav: 12, radiusTag: 8, radiusBadge: 18,
    controlH: 40, fontSize: 14.5, shadow: 'xl'
  },
  compact: {
    id: 'compact', name: '紧凑', desc: '小控件，一屏放更多行',
    radiusSm: 4, radius: 8, radiusLg: 10,
    radiusBtn: 4, radiusInput: 4, radiusNav: 6, radiusTag: 3, radiusBadge: 8,
    controlH: 30, fontSize: 13, shadow: 'sm'
  }
}

export const MODES = [
  { id: 'light', name: '浅色', desc: '白天办公默认' },
  { id: 'dark', name: '深色', desc: '长时间盯屏护眼' }
]

export const DEFAULT_HUE = 'blue'
export const DEFAULT_MODE = 'light'
export const DEFAULT_SHAPE = 'rounded'
export const DEFAULT_THEME = `${DEFAULT_HUE}-${DEFAULT_MODE}-${DEFAULT_SHAPE}`

/** 主题总数：24 × 2 × 5 = 240 */
export const THEME_COUNT = HUES.length * MODES.length * Object.keys(SHAPES).length

/* =========================================================
   3. 颜色工具
   ========================================================= */
function hex2rgb(hex) {
  let h = String(hex).replace('#', '')
  if (h.length === 3) h = h.split('').map(c => c + c).join('')
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16)
  ]
}

function rgb2hex(arr) {
  return '#' + arr
    .map(v => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0'))
    .join('')
}

/**
 * 混色。weight 是「第一个颜色」的占比。
 * mix('#ffffff', c, 0.3) 表示 30% 白 + 70% c —— 与 SCSS 的 mix(white, $c, 30%) 一致。
 */
function mix(c1, c2, weight) {
  const a = hex2rgb(c1)
  const b = hex2rgb(c2)
  return rgb2hex([0, 1, 2].map(i => a[i] * weight + b[i] * (1 - weight)))
}

function alpha(hex, a) {
  const [r, g, b] = hex2rgb(hex)
  return `rgba(${r},${g},${b},${a})`
}

function rgb2hsl(hex) {
  const [r0, g0, b0] = hex2rgb(hex)
  const r = r0 / 255, g = g0 / 255, b = b0 / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  let h = 0
  let s = 0
  const l = (max + min) / 2
  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    if (max === r) h = (g - b) / d + (g < b ? 6 : 0)
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h /= 6
  }
  return [h * 360, s * 100, l * 100]
}

/** 色相环上的最短距离（0~180） */
function hueDist(a, b) {
  const d = Math.abs(a - b) % 360
  return d > 180 ? 360 - d : d
}

/** 相对亮度（WCAG 2.1），用于对比度校验 */
export function luminance(hex) {
  const [r, g, b] = hex2rgb(hex).map(v => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** 对比度（1~21） */
export function contrastRatio(fg, bg) {
  const l1 = luminance(fg)
  const l2 = luminance(bg)
  const light = Math.max(l1, l2)
  const dark = Math.min(l1, l2)
  return (light + 0.05) / (dark + 0.05)
}

/* =========================================================
   4. 语义色避让
   ========================================================= */
const BASE_SEMANTIC = {
  // fallback 是「主色与语义色撞色」时的替代色，必须分明暗：
  // 深红在深色底上只有 2.6 的对比度，会直接看不见。
  success: { hue: 145, light: '#0f9d58', dark: '#34d399', fbLight: '#047857', fbDark: '#6ee7b7' },
  warning: { hue: 33, light: '#d97706', dark: '#fbbf24', fbLight: '#b45309', fbDark: '#fcd34d' },
  danger: { hue: 0, light: '#e5484d', dark: '#f87171', fbLight: '#b91c1c', fbDark: '#fca5a5' }
}

/**
 * 主色与语义色同色相时，语义色改用对比度更高的一档。
 * 否则「竹青主题」里成功提示与主按钮同色、「朱砂主题」里删除按钮与主按钮同色。
 */
function semanticOf(key, baseHex, isDark) {
  const cfg = BASE_SEMANTIC[key]
  const [baseHue] = rgb2hsl(baseHex)
  const tooClose = hueDist(baseHue, cfg.hue) <= (key === 'danger' ? 18 : 12)
  if (!tooClose) return isDark ? cfg.dark : cfg.light
  return isDark ? cfg.fbDark : cfg.fbLight
}

/**
 * 求一个「文字与底色对比度 ≥ 4.5」的按钮配色。
 * 返回 { bg, fg }，bg 可能与主色不同（压暗或提亮过），fg 是白或近黑。
 */
function resolveButtonColor(base) {
  const W = '#ffffff'
  const D = '#111827'
  const AA = 4.5

  if (contrastRatio(W, base) >= AA) return { bg: base, fg: W }
  if (contrastRatio(D, base) >= AA) return { bg: base, fg: D }

  // 中等亮度区：压暗走白字（每次 2.5%，最多 30%）
  let dark = base
  for (let i = 0; i < 12; i++) {
    dark = mix('#000000', dark, 0.025)
    if (contrastRatio(W, dark) >= AA) return { bg: dark, fg: W }
  }

  // 兜底：提亮走深字
  let light = base
  for (let i = 0; i < 12; i++) {
    light = mix('#ffffff', light, 0.025)
    if (contrastRatio(D, light) >= AA) return { bg: light, fg: D }
  }

  return { bg: base, fg: W }
}

/* =========================================================
   5. 核心：生成一套主题的全部 CSS 变量
   ========================================================= */
export function buildThemeVars(hueId, modeId, shapeId) {
  const hue = HUES.find(h => h.id === hueId) || HUES.find(h => h.id === DEFAULT_HUE)
  const shape = SHAPES[shapeId] || SHAPES[DEFAULT_SHAPE]
  const base = hue.color
  const dark = modeId === 'dark'
  const W = '#ffffff'
  const B = '#000000'

  /* ---- 主色阶 ---- */
  const primary = base
  const primaryLight3 = mix(W, base, 0.30)
  const primaryLight5 = mix(W, base, 0.50)
  const primaryLight7 = mix(W, base, 0.70)
  const primaryLight9 = mix(W, base, 0.90)
  const primaryHover = dark ? mix(W, base, 0.14) : mix(W, base, 0.16)
  const primaryActive = mix(B, base, 0.14)
  const primarySoft = dark ? alpha(base, 0.16) : primaryLight9
  const primaryRing = alpha(base, dark ? 0.30 : 0.15)
  const primaryGlow = alpha(base, dark ? 0.50 : 0.32)

  /* ---- 按钮底色与文字色 ----
     目标是「按钮上的字一定能看清」，这里按对比度求解，而不是拍一个固定值：
     1. 白字 + 原色就够 4.5 → 直接用（深色系主题多数走这条）
     2. 深字 + 原色够 4.5 → 用深字（琥珀、青柠、银杏这类高亮度色）
     3. 都不够（红/紫这类中等亮度色，白字约 3.9、深字约 4.1）→
        逐步压暗底色走进白字区间，最多压 30%
     4. 仍不够 → 反过来提亮走深字

     这解决了「按钮要么白字糊、要么深字脏」的两难：
     亮色相得到深字亮底，暗色相得到白字深底，中间地带靠压暗兜底。 */
  const button = resolveButtonColor(base)
  const primaryText = button.fg
  const primarySolid = button.bg

  /* ---- 中性色 ---- */
  const n = dark ? {
    page: '#0d1320',
    container: '#161d2d',
    elevated: '#1a2233',
    hover: '#212b40',
    active: '#26324b',
    inputBg: '#1a2233',
    inputBgFocus: '#1e2739',
    border: '#2a3448',
    borderLight: '#222b3d',
    borderLighter: '#1d2534',
    text: '#e9effc',
    textRegular: '#c2cee2',
    textSecondary: '#8794ad',
    textPlaceholder: '#5b6883',
    headerBg: '#161d2d',
    headerShadow: '0 1px 0 #232c40',
    tableHead: '#1a2233',
    tableRowHover: '#1f2839',
    mask: 'rgba(6, 10, 18, 0.72)',
    heroFooter: 'rgba(255, 255, 255, 0.45)'
  } : {
    page: '#f3f5fa',
    container: '#ffffff',
    elevated: '#ffffff',
    hover: '#f5f8fe',
    active: '#eef3fc',
    inputBg: '#ffffff',
    inputBgFocus: '#ffffff',
    border: '#dde5f2',
    borderLight: '#e9eef8',
    borderLighter: '#f0f4fb',
    text: '#1f2d50',
    textRegular: '#3b4867',
    textSecondary: '#7c87a3',
    // 占位符不能太浅：白底上 #a7b1c7 只有 2.15 的对比度，输入框提示文字几乎看不见
    textPlaceholder: '#9fa8bd',
    headerBg: '#ffffff',
    headerShadow: '0 1px 6px rgba(16, 30, 75, 0.08)',
    tableHead: '#f7f9fc',
    tableRowHover: '#f5f8fe',
    mask: 'rgba(16, 24, 48, 0.45)',
    heroFooter: 'rgba(255, 255, 255, 0.55)'
  }

  /* ---- 侧边栏：带品牌色温的深色 ---- */
  const asideFrom = dark ? mix(base, '#0f1626', 0.07) : mix(base, '#101a30', 0.11)
  const asideTo = dark ? '#0a0f1a' : mix(base, '#080e1c', 0.045)
  // 二级菜单浮层：比侧栏底色再压一档，避免与侧栏糊在一起
  const asidePanel = dark ? mix(W, asideFrom, 0.06) : mix(B, asideFrom, 0.06)
  const asideText = dark ? '#9aa7c0' : '#a7b1c8'

  /* ---- 登录页 hero ---- */
  // 从「深蓝底」过渡到「品牌主色」，与原设计的 #16224a → #2b57d9 一致：
  // 起点几乎全深蓝（10% 主色），终点几乎全主色（仅压暗 8%）。
  const heroFrom = mix(base, '#0b1122', dark ? 0.06 : 0.10)
  const heroVia = mix(base, '#0b1122', dark ? 0.26 : 0.38)
  const heroTo = dark ? mix(B, base, 0.22) : mix(B, base, 0.08)
  const glow1 = mix(W, base, 0.28)
  const glow2 = mix(W, base, 0.58)
  // 第三个光斑偏一个色相，避免整屏单色发闷
  const [gh, gs, gl] = rgb2hsl(base)
  const glow3 = hslToHex(gh + 46, Math.min(100, gs * 0.86), Math.min(92, gl * 1.18))

  /* ---- 阴影档位 ---- */
  const shadowMap = {
    flat: {
      card: dark ? 'none' : '0 1px 2px rgba(16, 30, 75, 0.06)',
      login: dark ? '0 18px 48px rgba(0, 0, 0, 0.55)' : '0 18px 48px rgba(6, 14, 40, 0.32)',
      btn: 'none',
      pop: '0 8px 24px rgba(6, 14, 40, 0.16)'
    },
    sm: {
      card: dark ? '0 2px 8px rgba(0, 0, 0, 0.30)' : '0 2px 8px rgba(16, 30, 75, 0.06)',
      login: dark ? '0 18px 48px rgba(0, 0, 0, 0.55)' : '0 18px 48px rgba(6, 14, 40, 0.32)',
      btn: `0 2px 8px ${alpha(base, 0.25)}`,
      pop: '0 10px 28px rgba(6, 14, 40, 0.18)'
    },
    md: {
      card: dark ? '0 4px 16px rgba(0, 0, 0, 0.32)' : '0 1px 3px rgba(16, 30, 75, 0.05), 0 4px 16px rgba(16, 30, 75, 0.06)',
      login: dark ? '0 24px 64px rgba(0, 0, 0, 0.65)' : '0 24px 64px rgba(6, 14, 40, 0.45)',
      btn: `0 8px 20px ${alpha(base, 0.32)}`,
      pop: '0 12px 32px rgba(6, 14, 40, 0.22)'
    },
    xl: {
      card: dark ? '0 12px 36px rgba(0, 0, 0, 0.42)' : '0 12px 32px rgba(16, 30, 75, 0.10)',
      login: dark ? '0 32px 80px rgba(0, 0, 0, 0.70)' : '0 32px 80px rgba(6, 14, 40, 0.50)',
      btn: `0 12px 28px ${alpha(base, 0.40)}`,
      pop: '0 16px 44px rgba(6, 14, 40, 0.26)'
    }
  }
  const shd = shadowMap[shape.shadow] || shadowMap.md

  const success = semanticOf('success', base, dark)
  const warning = semanticOf('warning', base, dark)
  const danger = semanticOf('danger', base, dark)
  const info = dark ? '#94a3b8' : '#64748b'

  return {
    /* ---- 主色 ---- */
    '--cg-primary': primary,
    '--cg-primary-text': primaryText,
    '--cg-primary-solid': primarySolid,
    '--cg-primary-light-3': primaryLight3,
    '--cg-primary-light-5': primaryLight5,
    '--cg-primary-light-7': primaryLight7,
    '--cg-primary-light-9': primaryLight9,
    '--cg-primary-hover': primaryHover,
    '--cg-primary-active': primaryActive,
    '--cg-primary-soft': primarySoft,
    '--cg-primary-ring': primaryRing,
    '--cg-primary-glow': primaryGlow,

    /* ---- 语义色 ---- */
    '--cg-success': success,
    '--cg-success-bg': alpha(success, dark ? 0.14 : 0.10),
    '--cg-warning': warning,
    '--cg-warning-bg': alpha(warning, dark ? 0.14 : 0.12),
    '--cg-danger': danger,
    '--cg-danger-bg': alpha(danger, dark ? 0.14 : 0.09),
    '--cg-info': info,
    '--cg-info-bg': alpha(info, dark ? 0.14 : 0.09),

    /* ---- 文本 ---- */
    '--cg-text': n.text,
    '--cg-text-regular': n.textRegular,
    '--cg-text-secondary': n.textSecondary,
    '--cg-text-placeholder': n.textPlaceholder,
    '--cg-text-inverse': dark ? '#0d1320' : '#ffffff',

    /* ---- 背景 ---- */
    '--cg-bg-page': n.page,
    '--cg-bg-container': n.container,
    '--cg-bg-elevated': n.elevated,
    '--cg-bg-hover': n.hover,
    '--cg-bg-active': n.active,
    '--cg-bg-mask': n.mask,

    /* ---- 边框 ---- */
    '--cg-border': n.border,
    '--cg-border-light': n.borderLight,
    '--cg-border-lighter': n.borderLighter,

    /* ---- 输入 ---- */
    '--cg-input-bg': n.inputBg,
    '--cg-input-bg-focus': n.inputBgFocus,

    /* ---- 侧栏 ---- */
    '--cg-aside-from': asideFrom,
    '--cg-aside-to': asideTo,
    '--cg-aside-panel': asidePanel,
    '--cg-aside-text': asideText,
    '--cg-aside-text-active': '#ffffff',
    '--cg-aside-hover': 'rgba(255, 255, 255, 0.08)',
    '--cg-aside-shadow': dark ? '1px 0 0 #1c2436' : '2px 0 12px rgba(10, 18, 45, 0.35)',

    /* ---- 顶栏 ---- */
    '--cg-header-bg': n.headerBg,
    '--cg-header-shadow': n.headerShadow,

    /* ---- 表格 ---- */
    '--cg-table-head': n.tableHead,
    '--cg-table-row-hover': n.tableRowHover,

    /* ---- 登录页 hero ---- */
    '--cg-hero-from': heroFrom,
    '--cg-hero-via': heroVia,
    '--cg-hero-to': heroTo,
    '--cg-glow-1': glow1,
    '--cg-glow-2': glow2,
    '--cg-glow-3': glow3,
    '--cg-hero-footer': n.heroFooter,

    /* ---- 形状 ---- */
    '--cg-radius-sm': `${shape.radiusSm}px`,
    '--cg-radius': `${shape.radius}px`,
    '--cg-radius-lg': `${shape.radiusLg}px`,
    '--cg-radius-btn': `${shape.radiusBtn}px`,
    '--cg-radius-input': `${shape.radiusInput}px`,
    '--cg-radius-nav': `${shape.radiusNav}px`,
    '--cg-radius-tag': `${shape.radiusTag}px`,
    '--cg-radius-badge': `${shape.radiusBadge}px`,
    '--cg-control-h': `${shape.controlH}px`,
    '--cg-font-size': `${shape.fontSize}px`,
    '--cg-font-size-sm': `${Math.round(shape.fontSize * 0.86)}px`,

    /* ---- 阴影 ---- */
    '--cg-shadow-card': shd.card,
    '--cg-shadow-login': shd.login,
    '--cg-shadow-btn': shd.btn,
    '--cg-shadow-pop': shd.pop
  }
}

/** hsl → hex（仅内部用，避免额外依赖） */
function hslToHex(h, s, l) {
  h = ((h % 360) + 360) % 360 / 360
  s = Math.min(100, Math.max(0, s)) / 100
  l = Math.min(100, Math.max(0, l)) / 100
  if (s === 0) {
    const v = Math.round(l * 255)
    return rgb2hex([v, v, v])
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s
  const p = 2 * l - q
  const f = t => {
    let x = (t + 1) % 1
    if (x < 1 / 6) return p + (q - p) * 6 * x
    if (x < 1 / 2) return q
    if (x < 2 / 3) return p + (q - p) * (2 / 3 - x) * 6
    return p
  }
  return rgb2hex([f(h + 1 / 3) * 255, f(h) * 255, f(h - 1 / 3) * 255])
}

/* =========================================================
   6. 主题 ID 解析
   ========================================================= */
export function parseThemeId(themeId) {
  const parts = String(themeId || '').split('-')
  // 色相 id 可能含连字符（当前没有，但保留兼容），从两端取 mode/shape
  const shape = parts.length > 2 ? parts.pop() : DEFAULT_SHAPE
  const mode = parts.length > 1 ? parts.pop() : DEFAULT_MODE
  const hue = parts.length ? parts.join('-') : DEFAULT_HUE
  return { hue, mode, shape }
}

/** 校验并回落：脏数据不抛错，直接给默认主题 */
export function normalizeThemeId(themeId) {
  const { hue, mode, shape } = parseThemeId(themeId)
  const okHue = HUES.some(h => h.id === hue) ? hue : DEFAULT_HUE
  const okMode = MODES.some(m => m.id === mode) ? mode : DEFAULT_MODE
  const okShape = SHAPES[shape] ? shape : DEFAULT_SHAPE
  return `${okHue}-${okMode}-${okShape}`
}

export function themeLabel(themeId) {
  const { hue, mode, shape } = parseThemeId(normalizeThemeId(themeId))
  const h = HUES.find(x => x.id === hue)
  const m = MODES.find(x => x.id === mode)
  const s = SHAPES[shape]
  return `${h.name} · ${m.name} · ${s.name}`
}

/* =========================================================
   7. 缓存与注入
   =========================================================
   缓存里同时存 id 和已算好的变量，是为了 public/index.html 的
   内联脚本能「零计算、零依赖」地把变量贴到 :root 上，避免首屏闪色。
   只存 id 的话内联脚本就得内嵌一份主题引擎，重复且易漂移。
   存储体积约 1.5KB，可忽略。
*/
const CACHE_KEY = 'cg-theme'

export function readThemeCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data || !data.id || !data.vars) return null
    return { id: normalizeThemeId(data.id), vars: data.vars }
  } catch (e) {
    return null
  }
}

export function writeThemeCache(themeId, vars) {
  const id = normalizeThemeId(themeId)
  let payload = vars
  if (!payload) {
    const p = parseThemeId(id)
    payload = buildThemeVars(p.hue, p.mode, p.shape)
  }
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ id, vars: payload }))
  } catch (e) {
    // 隐私模式 / 配额满：主题仍在内存生效，只是下次首屏会闪一下，不影响功能
  }
}

export function clearThemeCache() {
  try {
    localStorage.removeItem(CACHE_KEY)
  } catch (e) {
    /* ignore */
  }
}

/** 当前生效的主题 ID（从 data-theme 读，避免多处状态不同步） */
export function currentThemeId() {
  return normalizeThemeId(document.documentElement.getAttribute('data-theme') || DEFAULT_THEME)
}

/**
 * 应用主题：写变量 + 打标记。persist=true 时同步写入缓存供下次首屏使用。
 * 返回实际生效的主题 ID（可能因非法入参而回落）。
 */
export function applyTheme(themeId, { persist = true } = {}) {
  const id = normalizeThemeId(themeId)
  const { hue, mode, shape } = parseThemeId(id)
  const vars = buildThemeVars(hue, mode, shape)
  const root = document.documentElement

  Object.keys(vars).forEach(k => root.style.setProperty(k, vars[k]))
  root.setAttribute('data-theme', id)
  root.setAttribute('data-mode', mode)
  root.setAttribute('data-shape', shape)
  root.setAttribute('data-hue', hue)

  if (persist) writeThemeCache(id, vars)
  return id
}

/** 仅预计算，不写 DOM（设置页预览用） */
export function previewVars(themeId) {
  const { hue, mode, shape } = parseThemeId(normalizeThemeId(themeId))
  return buildThemeVars(hue, mode, shape)
}
