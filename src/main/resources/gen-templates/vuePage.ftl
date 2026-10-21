<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
<#if vueQueryColumns?size gt 0>
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
<#list vueQueryColumns as c>
        <el-form-item label="${c.columnComment}">
<#if c.isSelect || c.isRadio>
          <el-select v-model="query.${c.javaField}" placeholder="请选择${c.columnComment}" clearable style="width: 160px">
            <el-option v-for="d in <#if c.hasDict>dicts.${c.dictType} || []<#else>[]</#if>" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
<#elseif c.isSwitch>
          <el-select v-model="query.${c.javaField}" placeholder="请选择${c.columnComment}" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
<#elseif c.isNumberCtrl>
          <el-input-number v-model="query.${c.javaField}" controls-position="right" style="width: 160px" />
<#elseif c.isDate>
          <el-date-picker v-model="query.${c.javaField}" type="date" value-format="yyyy-MM-dd" placeholder="请选择${c.columnComment}" style="width: 160px" />
<#elseif c.isDatetime>
          <el-date-picker v-model="query.${c.javaField}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择${c.columnComment}" style="width: 200px" />
<#else>
          <el-input v-model="query.${c.javaField}" placeholder="${c.columnComment}" clearable @keyup.enter.native="handleSearch" />
</#if>
        </el-form-item>
</#list>
        <el-form-item>
          <el-button v-hasPermi="['${tableName}:query']" type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

</#if>
    <!-- 列表 -->
    <el-card shadow="never">
      <div slot="header" class="card-header">
        <span>${comment}列表</span>
        <div>
<#if genAddBtn>
          <el-button v-hasPermi="['${tableName}:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增${comment}</el-button>
</#if>
<#if hasExcelExport>
          <el-button icon="el-icon-download" size="small" @click="handleExport">导出</el-button>
</#if>
<#if hasExcelImport>
          <el-button icon="el-icon-upload2" size="small" @click="importVisible = true">导入</el-button>
</#if>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        border
        stripe
<#if genTree>
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
</#if>
      >
        <el-table-column prop="id" label="ID" width="70" />
<#list listColumns as c>
<#if c.isSwitch>
        <el-table-column prop="${c.javaField}" label="${c.columnComment}" width="100" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.${c.javaField} === 1 ? 'success' : 'info'" size="mini">
              {{ row.${c.javaField} === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
<#elseif c.hasDict>
        <el-table-column prop="${c.javaField}" label="${c.columnComment}" min-width="120" show-overflow-tooltip
          :formatter="(row) => dictText('${c.dictType}', row.${c.javaField})" />
<#else>
        <el-table-column prop="${c.javaField}" label="${c.columnComment}" min-width="120" show-overflow-tooltip />
</#if>
</#list>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
<#if genEditBtn || genDeleteBtn || hasDetail>
        <el-table-column label="操作" width="<#if hasDetail>170<#else>120</#if>" fixed="right">
          <template slot-scope="{ row }">
<#if hasDetail>
            <el-button type="text" @click="openDetail(row)">详情</el-button>
</#if>
<#if genEditBtn>
            <el-button v-hasPermi="['${tableName}:edit']" type="text" @click="openDialog(row)">编辑</el-button>
</#if>
<#if genDeleteBtn>
            <el-button v-hasPermi="['${tableName}:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
</#if>
          </template>
        </el-table-column>
</#if>
      </el-table>

<#if genPageApi && !genTree>
      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        :current-page.sync="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
</#if>
    </el-card>

<#if hasDialog>
    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑${comment}' : '新增${comment}'" :visible.sync="dialogVisible" width="<#if hasChild>860px<#else>560px</#if>">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
<#list formColumns as c>
        <el-form-item label="${c.columnComment}" prop="${c.javaField}">
<#if c.hasRef>
          <el-select v-model="form.${c.javaField}" filterable remote clearable placeholder="输入关键字搜索${c.columnComment}"
            :remote-method="kw => searchRef('${c.javaField}', '${c.refTable}', '${c.refLabel}', kw)"
            :loading="refLoading['${c.javaField}']" style="width: 100%">
            <el-option v-for="o in refOptions['${c.javaField}'] || []" :key="o.id" :label="o.label" :value="o.id" />
          </el-select>
<#elseif c.isSelect>
          <el-select v-model="form.${c.javaField}" placeholder="请选择${c.columnComment}" clearable style="width: 100%">
            <el-option v-for="d in <#if c.hasDict>dicts.${c.dictType} || []<#else>[]</#if>" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
<#elseif c.isRadio>
          <el-radio-group v-model="form.${c.javaField}">
            <el-radio v-for="d in <#if c.hasDict>dicts.${c.dictType} || []<#else>[]</#if>" :key="d.value" :label="d.value">{{ d.label }}</el-radio>
          </el-radio-group>
<#elseif c.isSwitch>
          <el-switch v-model="form.${c.javaField}" :active-value="1" :inactive-value="0" />
<#elseif c.isNumberCtrl>
          <el-input-number v-model="form.${c.javaField}" controls-position="right" style="width: 100%" />
<#elseif c.isDate>
          <el-date-picker v-model="form.${c.javaField}" type="date" value-format="yyyy-MM-dd" placeholder="请选择${c.columnComment}" style="width: 100%" />
<#elseif c.isDatetime>
          <el-date-picker v-model="form.${c.javaField}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择${c.columnComment}" style="width: 100%" />
<#elseif c.isTextarea>
          <el-input v-model="form.${c.javaField}" type="textarea" :rows="3" placeholder="请输入${c.columnComment}" />
<#else>
          <el-input v-model="form.${c.javaField}" placeholder="请输入${c.columnComment}" />
</#if>
        </el-form-item>
</#list>
<#if genTree>
        <el-form-item label="上级节点">
          <el-cascader
            v-model="form.${treeParentField}"
            :options="treeOptions"
            :props="{ value: 'id', label: '<#if treeLabelField?has_content>${treeLabelField}<#else>id</#if>', children: 'children', checkStrictly: true, emitPath: false }"
            clearable
            placeholder="不选则为根节点"
            style="width: 100%"
          />
        </el-form-item>
</#if>
      </el-form>

<#if hasChild>
      <el-divider content-position="left">${child.comment}</el-divider>
      <div class="child-bar">
        <el-button type="primary" icon="el-icon-plus" size="mini" @click="addChildRow">添加明细</el-button>
        <span class="child-tip">主表与子表在同一事务中保存（先清后插）</span>
      </div>
      <el-table :data="childRows" border size="mini" class="child-table">
<#list child.formColumns as c>
        <el-table-column label="${c.columnComment}" min-width="130">
          <template slot-scope="{ row }">
<#if c.isSwitch>
            <el-switch v-model="row.${c.javaField}" :active-value="1" :inactive-value="0" />
<#elseif c.isNumberCtrl>
            <el-input-number v-model="row.${c.javaField}" controls-position="right" size="mini" style="width: 100%" />
<#elseif c.isDate>
            <el-date-picker v-model="row.${c.javaField}" type="date" value-format="yyyy-MM-dd" size="mini" style="width: 100%" />
<#elseif c.isDatetime>
            <el-date-picker v-model="row.${c.javaField}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" size="mini" style="width: 100%" />
<#else>
            <el-input v-model="row.${c.javaField}" size="mini" placeholder="${c.columnComment}" />
</#if>
          </template>
        </el-table-column>
</#list>
        <el-table-column label="操作" width="70" align="center">
          <template slot-scope="{ $index }">
            <el-button type="text" class="danger-btn" @click="childRows.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
</#if>

      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </div>
    </el-dialog>
</#if>

<#if hasDetail>
    <!-- 详情抽屉（只读） -->
    <el-drawer title="${comment}详情" :visible.sync="detailVisible" size="420px">
      <div class="detail-body">
        <div class="detail-row">
          <span class="detail-label">ID</span>
          <span class="detail-value">{{ detail.id }}</span>
        </div>
<#list listColumns as c>
        <div class="detail-row">
          <span class="detail-label">${c.columnComment}</span>
<#if c.isSwitch>
          <span class="detail-value">{{ detail.${c.javaField} === 1 ? '启用' : '停用' }}</span>
<#elseif c.hasDict>
          <span class="detail-value">{{ dictText('${c.dictType}', detail.${c.javaField}) }}</span>
<#else>
          <span class="detail-value">{{ detail.${c.javaField} }}</span>
</#if>
        </div>
</#list>
        <div class="detail-row">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ detail.createTime }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">更新时间</span>
          <span class="detail-value">{{ detail.updateTime }}</span>
        </div>
      </div>
    </el-drawer>
</#if>

<#if hasExcelImport>
    <!-- 导入对话框 -->
    <el-dialog title="导入${comment}" :visible.sync="importVisible" width="480px">
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        :limit="1"
        :file-list="importFiles"
        :on-change="handleImportChange"
        accept=".xlsx"
      >
        <i class="el-icon-upload" />
        <div class="el-upload__text">把 xlsx 拖到这里，或<em>点击选择</em></div>
      </el-upload>
      <div v-if="importResult" class="import-result">
        <div>导入成功 {{ importResult.success }} 条</div>
        <div v-for="(f, i) in importResult.failed" :key="i" class="import-fail">第 {{ f.row }} 行：{{ f.msg }}</div>
      </div>
      <div slot="footer">
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </div>
    </el-dialog>
</#if>
    <!-- <gen:end key="vue-template"> -->
  </div>
</template>

<script>
import {
<#if genPageApi>  get${className}List<#if genTree || genAddApi || genEditApi || genDeleteApi || hasChild || hasRef || hasExcelExport || hasExcelImport>,</#if>
</#if>
<#if genTree>  get${className}Tree<#if genAddApi || genEditApi || genDeleteApi || hasChild || hasRef || hasExcelExport || hasExcelImport>,</#if>
</#if>
<#if hasRef>  get${className}Options<#if genAddApi || genEditApi || genDeleteApi || hasChild || hasExcelExport || hasExcelImport>,</#if>
</#if>
<#if hasChild>  get${className}WithChildren,
  save${className}WithChildren<#if genAddApi || genEditApi || genDeleteApi || hasExcelExport || hasExcelImport>,</#if>
</#if>
<#if hasExcelExport>  export${className}<#if genAddApi || genEditApi || genDeleteApi || hasExcelImport>,</#if>
</#if>
<#if hasExcelImport>  import${className}<#if genAddApi || genEditApi || genDeleteApi>,</#if>
</#if>
<#if genAddApi>  create${className}<#if genEditApi || genDeleteApi>,</#if>
</#if>
<#if genEditApi>  update${className}<#if genDeleteApi>,</#if>
</#if>
<#if genDeleteApi>  delete${className}</#if>
} from '@/api/${moduleName}'
<#if vueHasDict>
import request from '@/utils/request'
</#if>

const defaultForm = () => ({
  id: undefined,
<#list formColumns as c>
  ${c.javaField}: ${c.jsInit},
</#list>
<#if genTree>
  ${treeParentField}: undefined,
</#if>
})
<#if hasChild>
const defaultChild = () => ({
<#list child.formColumns as c>
  ${c.javaField}: ${c.jsInit},
</#list>
})
</#if>

export default {
  name: '${className}',
  data() {
    return {
      // <gen:begin key="vue-data">
      loading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10<#list vueQueryColumns as c>, ${c.javaField}: ${c.queryJsInit}</#list> },
      dialogVisible: false,
      submitting: false,
<#if hasDetail>
      detailVisible: false,
      detail: {},
</#if>
<#if genTree>
      treeOptions: [],
</#if>
<#if hasRef>
      refOptions: {},
      refLoading: {},
</#if>
<#if hasChild>
      childRows: [],
</#if>
<#if hasExcelImport>
      importVisible: false,
      importing: false,
      importFiles: [],
      importResult: null,
</#if>
<#if vueHasDict>
      dicts: {<#list dictCodes as code> ${code}: []<#if code_has_next>,</#if></#list> },
</#if>
      rules: {<#list formColumns as c><#if c.hasVueRule>
        ${c.javaField}: ${c.vueRule},</#if></#list>
      },
      form: defaultForm()
      // <gen:end key="vue-data">
    }
  },
  created() {
<#if vueHasDict>
    this.loadDicts()
</#if>
    this.fetchList()
  },
  methods: {
    // <gen:begin key="vue-methods">
<#if vueHasDict>
    async loadDicts() {
      const codes = [<#list dictCodes as code>'${code}'<#if code_has_next>, </#if></#list>]
      await Promise.all(codes.map(async code => {
        try {
          // 直接走通用 request，不依赖宿主工程是否提供 @/api/dict；
          // 宿主没有字典接口时这里会抛错，下面 catch 把选项降级为空数组，页面照常可用
          const res = await request({ url: '/api/system/dict/data/type/' + code, method: 'get' })
          this.$set(this.dicts, code, (res && res.data) || [])
        } catch (e) {
          this.$set(this.dicts, code, [])
        }
      }))
    },
    dictText(code, value) {
      const hit = (this.dicts[code] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null ? '' : value)
    },
</#if>
<#if genTree>
    async fetchList() {
      this.loading = true
      try {
        const res = await get${className}Tree()
        this.list = res.data || []
        this.treeOptions = res.data || []
        this.total = this.list.length
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size<#list vueQueryColumns as c>, ${c.javaField}: ${c.jsInit}</#list> }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.fetchList()
    },
<#elseif genPageApi>
    async fetchList() {
      this.loading = true
      try {
        const res = await get${className}List(this.query)
        this.list = res.data.list || []
        this.total = res.data.total || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size<#list vueQueryColumns as c>, ${c.javaField}: ${c.jsInit}</#list> }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
<#else>
    async fetchList() {},
</#if>
<#if hasRef>
    async searchRef(field, refTable, label, keyword) {
      this.$set(this.refLoading, field, true)
      try {
        const res = await get${className}Options(refTable, label, keyword)
        this.$set(this.refOptions, field, res.data || [])
      } catch (e) {
        this.$set(this.refOptions, field, [])
      } finally {
        this.$set(this.refLoading, field, false)
      }
    },
    initRefs() {
<#list refColumns as c>
      this.searchRef('${c.javaField}', '${c.refTable}', '${c.refLabel}', '')
</#list>
    },
</#if>
<#if hasDetail>
    openDetail(row) {
      this.detail = row || {}
      this.detailVisible = true
    },
</#if>
<#if hasDialog>
    openDialog(row) {
      this.form = row ? { ...defaultForm(), ...row } : defaultForm()
<#if hasChild>
      this.childRows = []
</#if>
      this.dialogVisible = true
<#if hasRef>
      this.initRefs()
</#if>
<#if hasChild>
      if (row && row.id) {
        this.loadChildren(row.id)
      }
</#if>
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
<#if hasChild>
    async loadChildren(id) {
      try {
        const res = await get${className}WithChildren(id)
        this.childRows = (res.data && res.data.children) || []
      } catch (e) {
        this.childRows = []
      }
    },
    addChildRow() {
      this.childRows.push(defaultChild())
    },
</#if>
    async submitForm() {
      const valid = await this.$refs.form.validate().catch(() => false)
      if (!valid) return
      this.submitting = true
      try {
<#if hasChild>
        await save${className}WithChildren(this.form, this.childRows)
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
<#else>
        if (this.form.id) {
          await update${className}(this.form.id, this.form)
        } else {
          await create${className}(this.form)
        }
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
</#if>
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
</#if>
<#if genDeleteBtn>
    handleDelete(row) {
      this.$confirm(`确定删除该${comment}吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await delete${className}(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    }<#if hasExcelExport || hasExcelImport>,</#if>
</#if>
<#if hasExcelExport>
    async handleExport() {
      try {
        const res = await export${className}(this.query)
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '${comment}.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
      } catch (e) { /* 拦截器已提示 */ }
    }<#if hasExcelImport>,</#if>
</#if>
<#if hasExcelImport>
    handleImportChange(file, fileList) {
      this.importFiles = fileList.slice(-1)
      this.importResult = null
    },
    async submitImport() {
      if (!this.importFiles.length) {
        this.$message.warning('请先选择文件')
        return
      }
      this.importing = true
      try {
        const res = await import${className}(this.importFiles[0].raw)
        this.importResult = res.data || { success: 0, failed: [] }
        this.$message.success('导入完成：成功 ' + this.importResult.success + ' 条')
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.importing = false
      }
    }
</#if>
    // <gen:end key="vue-methods">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
  }
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  text-align: right;
}

.danger-btn {
  color: #f56c6c;
}

.child-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.child-tip {
  color: #909399;
  font-size: 12px;
}

.child-table {
  margin-bottom: 8px;
}

.detail-body {
  padding: 0 20px;
}

.detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
}

.detail-label {
  width: 110px;
  color: #909399;
  flex-shrink: 0;
}

.detail-value {
  color: #303133;
  word-break: break-all;
}

.import-result {
  margin-top: 12px;
  font-size: 13px;
}

.import-fail {
  color: #f56c6c;
}
</style>
