<#-- V9.17：条码/二维码开关——缺省键一律视为关闭，显式布尔 false 也不误开；
     用 is_string + has_content 双重判定，兼容 FreeMarker 严格/经典两种布尔配置（零 diff 红线）。 -->
<#assign showBarcode = (printBarcode! '')?is_string && (printBarcode! '')?has_content />
<#assign showQr = (printQr! '')?is_string && (printQr! '')?has_content />
<#assign showCode = showBarcode || showQr />
<template>
  <div class="print-wrap" v-loading="loading">
    <!-- 屏幕工具条：打印时整体隐藏 -->
    <div class="print-toolbar no-print">
      <el-button type="primary" icon="Printer" @click="handlePrint">打印 / 另存为 PDF</el-button>
      <el-button icon="Back" @click="handleBack">返回</el-button>
    </div>

    <!-- A4 纸面：屏幕预览 794px 等比模拟，打印时交给 @page 控版 -->
    <div class="sheet">
      <div class="doc-title">${printTitle}</div>
      <div class="doc-meta">
        <span>单号：{{ master.id || '—' }}</span>
        <span>打印时间：{{ printTime }}</span>
      </div>

<#-- V9.17：条码/二维码区。printBarcode / printQr 为可选模型键（取值为 master 上的字段名，如 orderNo）；
     两键缺省时整块不渲染 → 存量产物逐字节零 diff。详见 docs/design/V9.17打印能力深度-设计.md -->
<#if showCode>
      <div class="code-zone">
<#if showBarcode>
        <div class="code-item">
          <div class="code-cap">条码（CODE128-B）</div>
          <svg class="barcode-svg" :viewBox="'0 0 ' + barcodeWidth + ' 48'" preserveAspectRatio="xMinYMid meet" role="img" aria-label="条码">
            <rect v-for="(b, i) in barcodeBars" :key="i" :x="b.x" y="0" :width="b.w" height="34" fill="#000"></rect>
            <text :x="barcodeWidth / 2" y="45" text-anchor="middle" font-size="11" fill="#1f2d3d">{{ barcodeText }}</text>
          </svg>
        </div>
</#if>
<#if showQr>
        <div class="code-item">
          <div class="code-cap">二维码（占位）</div>
<#-- 二维码占位说明：本批坚守「生成物零第三方依赖」，不内联 QR 编码器——
     QR 需 Reed-Solomon GF(256) 纠错 + 掩码评分 + 版本选择，成熟实现约 800~1200 行，
     且编码错误表现为「扫不出的码」而非编译错误，测试难以拦截；引入 qrcode 依赖又破坏红线。
     接入方式：将下方 <svg> 替换为 buildQr() 生成的 <rect> 矩阵（纯 JS 极简 QR 或
     生成物 package.json 可选依赖 + readme 声明），内容取 master[printQr]。详见设计文档 §5。 -->
          <svg class="qr-svg" viewBox="0 0 100 100" role="img" aria-label="二维码占位">
            <rect x="1" y="1" width="98" height="98" fill="#fff" stroke="#333" stroke-width="2" stroke-dasharray="6 4"></rect>
            <text x="50" y="57" text-anchor="middle" font-size="18" fill="#5a5e66">QR</text>
          </svg>
          <div class="code-cap-sub">{{ qrText }}</div>
        </div>
</#if>
      </div>
</#if>
<#if formColumns?size gt 0>
      <!-- 主表信息区：两列键值网格，列取 isForm -->
      <table class="kv-table">
<#list formColumns as c>
<#if c_index % 2 == 0>
        <tr>
</#if>
          <th class="kv-label">${c.commentLabel}</th>
          <td class="kv-value"><#if c.hasEnum>{{ labelOf('${c.javaField}', master.${c.javaField}) }}<#elseif c.isDatetime || c.isDate>{{ fmt(master.${c.javaField}) }}<#else>{{ master.${c.javaField} }}</#if></td>
<#if c_index % 2 == 1>
        </tr>
<#elseif !c_has_next>
          <td class="kv-value"></td>
        </tr>
</#if>
</#list>
      </table>

<#-- V4.5 打印增强：主表键值区含金额列时，逐列补「人民币大写」行 -->
<#if hasMasterMoney>
<#list formColumns as c>
<#if c.isMoney>
      <div class="rmb-line">${c.commentLabel}大写：人民币{{ rmbUpper(master.${c.javaField}) }}</div>
</#if>
</#list>
</#if>

</#if>
<#if hasChild>
<#-- 明细块：每张子表一个，列取 isList（超 8 列截断），数值列自动合计 -->
<#assign detailNo = 0 />
<#list children as child>
<#if child.listColumns?size gt 0>
<#assign detailNo = detailNo + 1 />
<#assign cCols = child.listColumns />
<#if cCols?size gt 8><#assign cCols = cCols[0..7] /></#if>
<#assign sumIdx = -1 />
<#list cCols as c><#if sumIdx == -1 && (c.javaType == "BigDecimal" || c.javaType == "Double" || c.javaType == "Integer" || c.javaType == "Long")><#assign sumIdx = c_index /></#if></#list>
      <div class="section-title">明细${detailNo}：${child.comment}</div>
      <table class="detail-table">
        <thead>
          <tr>
            <th class="idx">#</th>
<#list cCols as c>
            <th>${c.commentLabel}</th>
</#list>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in rowsOf('${child.varName}')" :key="i">
            <td class="idx">{{ i + 1 }}</td>
<#list cCols as c>
            <td><#if c.hasEnum>{{ labelOf('${child.varName}.${c.javaField}', row.${c.javaField}) }}<#elseif c.isDatetime || c.isDate>{{ fmt(row.${c.javaField}) }}<#else>{{ row.${c.javaField} }}</#if></td>
</#list>
          </tr>
        </tbody>
<#if sumIdx != -1>
        <tfoot>
          <tr class="sum-row">
            <td :colspan="${sumIdx + 1}" class="sum-label">合&nbsp;&nbsp;计</td>
<#list cCols[sumIdx..] as c>
<#if c.javaType == "BigDecimal" || c.javaType == "Double" || c.javaType == "Integer" || c.javaType == "Long">
            <td class="sum-num">{{ colSum('${child.varName}', '${c.javaField}') }}</td>
<#else>
            <td></td>
</#if>
</#list>
          </tr>
<#if child.moneyField != ''>
<#-- V4.5 打印增强：明细含金额列时，合计行下补人民币大写（取最后一个金额列的合计） -->
          <tr class="rmb-row">
            <td colspan="${cCols?size + 1}" class="rmb-cell">金额大写：人民币{{ rmbUpper(colSum('${child.varName}', '${child.moneyField}')) }}</td>
          </tr>
</#if>
        </tfoot>
</#if>
      </table>

</#if>
</#list>
<#-- 孙表块：与子表块同版式平铺，行数据由前端从子表 rows 展平 -->
<#list children as child>
<#list child.grandChildren as gc>
<#if gc.listColumns?size gt 0>
<#assign detailNo = detailNo + 1 />
<#assign gCols = gc.listColumns />
<#if gCols?size gt 8><#assign gCols = gCols[0..7] /></#if>
<#assign gSumIdx = -1 />
<#list gCols as c><#if gSumIdx == -1 && (c.javaType == "BigDecimal" || c.javaType == "Double" || c.javaType == "Integer" || c.javaType == "Long")><#assign gSumIdx = c_index /></#if></#list>
      <div class="section-title">明细${detailNo}：${gc.comment}（${child.comment}下级）</div>
      <table class="detail-table">
        <thead>
          <tr>
            <th class="idx">#</th>
<#list gCols as c>
            <th>${c.commentLabel}</th>
</#list>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in rowsOf('${child.varName}.${gc.varName}')" :key="i">
            <td class="idx">{{ i + 1 }}</td>
<#list gCols as c>
            <td><#if c.hasEnum>{{ labelOf('${child.varName}.${gc.varName}.${c.javaField}', row.${c.javaField}) }}<#elseif c.isDatetime || c.isDate>{{ fmt(row.${c.javaField}) }}<#else>{{ row.${c.javaField} }}</#if></td>
</#list>
          </tr>
        </tbody>
<#if gSumIdx != -1>
        <tfoot>
          <tr class="sum-row">
            <td :colspan="${gSumIdx + 1}" class="sum-label">合&nbsp;&nbsp;计</td>
<#list gCols[gSumIdx..] as c>
<#if c.javaType == "BigDecimal" || c.javaType == "Double" || c.javaType == "Integer" || c.javaType == "Long">
            <td class="sum-num">{{ colSum('${child.varName}.${gc.varName}', '${c.javaField}') }}</td>
<#else>
            <td></td>
</#if>
</#list>
          </tr>
<#if gc.moneyField != ''>
<#-- V4.5 打印增强：孙表块大写行与子表块同口径 -->
          <tr class="rmb-row">
            <td colspan="${gCols?size + 1}" class="rmb-cell">金额大写：人民币{{ rmbUpper(colSum('${child.varName}.${gc.varName}', '${gc.moneyField}')) }}</td>
          </tr>
</#if>
        </tfoot>
</#if>
      </table>

</#if>
</#list>
</#list>
</#if>
      <div class="sign-bar">
        <span>制单人：____________</span>
        <span>审&nbsp;&nbsp;核：____________</span>
        <span>批&nbsp;&nbsp;准：____________</span>
      </div>
    </div>
  </div>
</template>

<script>
<#if hasChild>
import { get${className}WithChildren } from '@/api/${moduleName}'
<#else>
import { get${className}Detail } from '@/api/${moduleName}'
</#if>

// 注释枚举内联：打印页独立于后台导航壳，不依赖字典接口即可回显中文
const PRINT_ENUMS = {
<#assign enumEntryCount = 0 />
<#list enumColumns as c>
  '${c.javaField}': ${c.enumJs}<#assign enumEntryCount = enumEntryCount + 1 />,
</#list>
<#list children as child>
<#list child.listColumns as c>
<#if c.hasEnum>
  '${child.varName}.${c.javaField}': ${c.enumJs}<#assign enumEntryCount = enumEntryCount + 1 />,
</#if>
</#list>
<#list child.grandChildren as gc>
<#list gc.listColumns as c>
<#if c.hasEnum>
  '${child.varName}.${gc.varName}.${c.javaField}': ${c.enumJs}<#assign enumEntryCount = enumEntryCount + 1 />,
</#if>
</#list>
</#list>
</#list>
}

// 合计列配置：BigDecimal/Double 走「分」整数累加（规避 JS 浮点误差），整数类型直接累加
const SUM_FIELDS = {
<#list children as child>
<#list child.listColumns as c>
<#if c.javaType == "BigDecimal" || c.javaType == "Double" || c.javaType == "Integer" || c.javaType == "Long">
  '${child.varName}.${c.javaField}': '<#if c.javaType == "BigDecimal" || c.javaType == "Double">cents<#else>plain</#if>',
</#if>
</#list>
<#list child.grandChildren as gc>
<#list gc.listColumns as c>
<#if c.javaType == "BigDecimal" || c.javaType == "Double" || c.javaType == "Integer" || c.javaType == "Long">
  '${child.varName}.${gc.varName}.${c.javaField}': '<#if c.javaType == "BigDecimal" || c.javaType == "Double">cents<#else>plain</#if>',
</#if>
</#list>
</#list>
</#list>
}

<#if showBarcode>
// V9.17：CODE128 字符集宽度表（107 项 = 索引 0..106，末项 2331112 为 STOP 的 13 模块 termination）。
// 数据图案各 11 模块（bar/space 交替 6 段），纯查表零依赖；仅开条码时内联。
const CODE128_PAT = '212222,222122,222221,121223,121322,131222,122213,122312,132212,221213,221312,231212,112232,122132,122231,113222,123122,123221,223211,221132,221231,213212,223112,312131,311222,321122,321221,312212,322112,322211,212123,212321,232121,111323,131123,131321,112313,132113,132311,211313,231113,231311,112133,112331,132131,113123,113321,133121,313121,211331,231131,213113,213311,213131,311123,311321,331121,312113,312311,332111,314111,221411,431111,111224,111422,121124,121421,141122,141221,112214,112412,122114,122411,142112,142211,241211,221114,413111,241112,134111,111242,121142,121241,114212,124112,124211,411212,421112,421211,212141,214121,412121,111143,111341,131141,114113,114311,411113,411311,113141,114131,311141,411131,211412,211214,211232,2331112'.split(',')
</#if>
export default {
  name: 'Print${className}',
  data() {
    return {
      loading: false,
      master: {},
      // [{key, comment, rows}]，与 /with-children 响应同构
      groups: [],
      printTime: ''
<#if showBarcode>
      ,barcodeBars: []
      ,barcodeWidth: 0
      ,barcodeText: ''
</#if>
<#if showQr>
      ,qrText: ''
</#if>
    }
  },
  created() {
    this.printTime = this.now()
    this.fetchData()
  },
  methods: {
    async fetchData() {
      this.loading = true
      try {
        const id = this.$route.params.id
<#if hasChild>
        const res = await get${className}WithChildren(id)
        const data = (res && res.data) || {}
        this.master = data.master || {}
        this.groups = data.children || []
<#else>
        const res = await get${className}Detail(id)
        this.master = (res && res.data) || {}
</#if>
<#if showBarcode>
        this.buildBarcode()
</#if>
<#if showQr>
        this.qrText = String(this.master['${printQr?string?js_string}'] == null ? '' : this.master['${printQr?string?js_string}'])
</#if>
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.loading = false
      }
    },
    rowsOf(key) {
      const dot = key.indexOf('.')
      if (dot === -1) {
        const g = this.groups.find(item => item.key === key)
        return (g && g.rows) || []
      }
      // 孙表：从父组每行 gVar + 's' 里展平
      const parentKey = key.slice(0, dot)
      const gcField = key.slice(dot + 1) + 's'
      const g = this.groups.find(item => item.key === parentKey)
      const out = []
      ;((g && g.rows) || []).forEach(row => {
        ;(row[gcField] || []).forEach(item => out.push(item))
      })
      return out
    },
    labelOf(key, value) {
      if (value === null || value === undefined || value === '') return ''
      const opts = PRINT_ENUMS[key] || []
      const hit = opts.find(o => String(o.value) === String(value))
      return hit ? hit.label : value
    },
    colSum(groupKey, field) {
      const mode = SUM_FIELDS[groupKey + '.' + field] || 'plain'
      const rows = this.rowsOf(groupKey)
      let sum = 0
      rows.forEach(row => {
        const v = Number(row[field])
        if (!isNaN(v)) {
          sum += mode === 'cents' ? Math.round(v * 100) : v
        }
      })
      return mode === 'cents' ? (sum / 100).toFixed(2) : String(sum)
    },
<#if printRmb>
    // V4.5 打印增强：人民币大写（生成物零依赖纯 JS）。
    // 入参为数字或「分」求和后的两位小数字符串；精确到分、支持到万亿级；
    // 零角零分以「整」收尾（央行票据规范：有角无分不写整，分前零要写）。
    rmbUpper(v) {
      const s = String(v).trim()
      if (s === '' || isNaN(Number(s))) return ''
      const neg = s.charAt(0) === '-'
      const body = neg ? s.slice(1) : s
      // 字符串按位转「分」：万亿级（14 位有效数字）走 Number 乘法会丢分，绕开 n * 100
      const dot = body.indexOf('.')
      const intPart = dot === -1 ? body : body.slice(0, dot)
      const decPart = dot === -1 ? '' : (body.slice(dot + 1) + '00').slice(0, 2)
      if (!/^\d*$/.test(intPart) || !/^\d*$/.test(decPart)) return ''
      const cents = Number(intPart) * 100 + Number(decPart === '' ? '0' : decPart)
      // 规格上限「万亿」：分值超 Number 安全整数区（>9e15 分 ≈ 9 万亿元）不保证精度，显式拒绝
      if (cents === 0) return '零元整'
      if (cents > Number.MAX_SAFE_INTEGER) return ''
      const core = this.rmbCore(cents)
      return neg ? '负' + core : core
    },
    // 非负整数「分」→ 大写主体
    rmbCore(cents) {
      const digits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖']
      const intUnits = ['', '拾', '佰', '仟']
      const groupUnits = ['', '万', '亿', '万亿']
      const yuan = Math.floor(cents / 100)
      const jiao = Math.floor((cents % 100) / 10)
      const fen = cents % 10
      if (yuan === 0) {
        return jiao > 0 ? digits[jiao] + '角' + (fen > 0 ? digits[fen] + '分' : '')
            : digits[fen] + '分'
      }
      // 整数部分按万进制 4 位分组，高位到低位转换
      const groups = []
      let y = yuan
      while (y > 0) {
        groups.push(y % 10000)
        y = Math.floor(y / 10000)
      }
      const parts = []
      let lastGi = -1
      for (let gi = groups.length - 1; gi >= 0; gi--) {
        const g = groups[gi]
        if (g === 0) continue
        const d4 = [g % 10, Math.floor(g / 10) % 10, Math.floor(g / 100) % 10, Math.floor(g / 1000)]
        let inner = ''
        let zero = false
        for (let pos = 3; pos >= 0; pos--) {
          if (d4[pos] === 0) {
            if (inner !== '') zero = true
          } else {
            if (zero) inner += '零'
            inner += digits[d4[pos]] + intUnits[pos]
            zero = false
          }
        }
        // 组间补零：跳过整组（如 亿→千 跨过 万）或低位组千位缺位（壹万零伍）
        const lead = lastGi !== -1 && (gi < lastGi - 1 || g < 1000) ? '零' : ''
        parts.push(lead + inner + groupUnits[gi])
        lastGi = gi
      }
      let out = parts.join('') + '元'
      if (jiao === 0 && fen === 0) {
        out += '整'
      } else if (jiao === 0) {
        out += '零' + digits[fen] + '分'
      } else {
        out += digits[jiao] + '角' + (fen > 0 ? digits[fen] + '分' : '')
      }
      return out
    },
</#if>
<#if showBarcode>
    // V9.17：CODE128-B 条码（纯 JS 零依赖）。仅支持 ASCII 32~126——条码内容应取业务单号/编号，
    // 不要把中文字段直接编码（会返回 null、条码区自动隐藏）；含中文时请改用英文/数字单号。
    code128Bars(text) {
      const s = String(text == null ? '' : text)
      const vals = []
      for (let i = 0; i < s.length; i++) {
        const c = s.charCodeAt(i)
        if (c < 32 || c > 126) return null
        vals.push(c - 32)
      }
      if (vals.length === 0) return null
      let sum = 104 // START B
      vals.forEach((v, i) => { sum += (i + 1) * v })
      const syms = [104].concat(vals, [sum % 103, 106]) // CHECK + STOP
      const Q = 10 // 静区（模块）
      let x = Q * 2
      const bars = []
      syms.forEach(sym => {
        const p = CODE128_PAT[sym].split('').map(Number)
        for (let i = 0; i < p.length; i += 2) {
          bars.push({ x: x, w: p[i] * 2 })
          x += (p[i] + (p[i + 1] || 0)) * 2
        }
      })
      return { bars: bars, width: x + Q * 2, text: s }
    },
    buildBarcode() {
      const v = this.master['${printBarcode?string?js_string}']
      const r = this.code128Bars(v == null || v === '' ? this.master.id : v)
      if (!r) {
        this.barcodeBars = []
        this.barcodeWidth = 0
        this.barcodeText = ''
        return
      }
      this.barcodeBars = r.bars
      this.barcodeWidth = r.width
      this.barcodeText = r.text
    },
</#if>
    fmt(v) {
      if (v === null || v === undefined || v === '') return ''
      return String(v).replace('T', ' ')
    },
    now() {
      const d = new Date()
      const p = n => String(n).padStart(2, '0')
      return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
        + ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
    },
    handlePrint() {
      window.print()
    },
    handleBack() {
      if (window.history.length > 1) {
        window.history.back()
      } else {
        window.close()
      }
    }
  }
}
</script>

<style scoped>
/* 屏幕预览：按 A4 宽度 794px 等比模拟 */
.print-wrap {
  min-height: 100vh;
  background: #f0f2f5;
  padding: 16px 0 32px;
}

.print-toolbar {
  width: <#if printLandscape == 1>1123<#else>794</#if>px;
  margin: 0 auto 12px;
  text-align: right;
}

.sheet {
  width: <#if printLandscape == 1>1123<#else>794</#if>px;
  min-height: <#if printLandscape == 1>794<#else>1000</#if>px;
  margin: 0 auto;
  padding: 40px 34px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  box-sizing: border-box;
  color: #1f2d3d;
}

.doc-title {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 4px;
  margin-bottom: 10px;
}

.doc-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #5a5e66;
  margin-bottom: 14px;
}

<#if showCode>
/* V9.17：条码/二维码区（仅表级配置开启时输出，默认零 diff） */
.code-zone {
  display: flex;
  gap: 28px;
  align-items: flex-start;
  margin-bottom: 12px;
  break-inside: avoid;
}

.code-item {
  text-align: center;
}

.code-cap {
  font-size: 12px;
  color: #5a5e66;
  margin-bottom: 4px;
}

.code-cap-sub {
  font-size: 10px;
  color: #5a5e66;
  margin-top: 2px;
  word-break: break-all;
}

.barcode-svg {
  width: 220px;
  height: 48px;
}

.qr-svg {
  width: 76px;
  height: 76px;
}
</#if>
.section-title {
  font-size: 14px;
  font-weight: 700;
  margin: 16px 0 6px;
}

.kv-table,
.detail-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.kv-table th,
.kv-table td,
.detail-table th,
.detail-table td {
  border: 1px solid #333;
  padding: 5px 6px;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}

.kv-table th {
  width: 96px;
  background: #f7f8fa;
  font-weight: 600;
  text-align: left;
}

.detail-table th {
  background: #f7f8fa;
  font-weight: 600;
}

.detail-table .idx {
  width: 34px;
  text-align: center;
}

/* 跨页行为：表头自动重复、行不拦腰截断 */
.detail-table thead {
  display: table-header-group;
}

.detail-table tr {
  break-inside: avoid;
}

.sum-row td {
  font-weight: 700;
  background: #fbfbfc;
}

.sum-num {
  text-align: right;
}
<#if printRmb>
/* V4.5 打印增强：人民币大写行样式（仅金额列表才会渲染这段） */
.rmb-line {
  font-size: 12px;
  margin-top: 6px;
  color: #1f2d3d;
}

.rmb-row td {
  background: #fbfbfc;
  font-weight: 700;
}

.rmb-cell {
  text-align: left;
}
</#if>

.sign-bar {
  display: flex;
  justify-content: space-between;
  margin-top: 42px;
  padding: 0 12px;
  font-size: 13px;
}

/* 打印：隐藏一切非单据元素，版面交给 @page */
@media print {
  @page {
    size: A4 <#if printLandscape == 1>landscape<#else>portrait</#if>;
    margin: 14mm 12mm;
  }

  .no-print {
    display: none !important;
  }

  .print-wrap {
    background: #fff;
    padding: 0;
  }

  .sheet {
    width: auto;
    min-height: 0;
    padding: 0;
    box-shadow: none;
  }
}
</style>
