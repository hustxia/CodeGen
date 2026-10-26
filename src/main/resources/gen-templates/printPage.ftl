<template>
  <div class="print-wrap" v-loading="loading">
    <!-- 屏幕工具条：打印时整体隐藏 -->
    <div class="print-toolbar no-print">
      <el-button type="primary" icon="el-icon-printer" @click="handlePrint">打印 / 另存为 PDF</el-button>
      <el-button icon="el-icon-back" @click="handleBack">返回</el-button>
    </div>

    <!-- A4 纸面：屏幕预览 794px 等比模拟，打印时交给 @page 控版 -->
    <div class="sheet">
      <div class="doc-title">${comment}</div>
      <div class="doc-meta">
        <span>单号：{{ master.id || '—' }}</span>
        <span>打印时间：{{ printTime }}</span>
      </div>

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

export default {
  name: 'Print${className}',
  data() {
    return {
      loading: false,
      master: {},
      // [{key, comment, rows}]，与 /with-children 响应同构
      groups: [],
      printTime: ''
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
  width: 794px;
  margin: 0 auto 12px;
  text-align: right;
}

.sheet {
  width: 794px;
  min-height: 1000px;
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
    size: A4 portrait;
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
