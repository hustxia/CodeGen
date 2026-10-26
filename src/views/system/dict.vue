<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="字典是生成页下拉／单选的选项来源：字段配置里的「字典编码」只能从这里已有的类型中选，拼错不再可能。左边选类型，右边维护该类型的选项。"
    />

    <el-alert
      v-if="orphans.length"
      type="warning"
      :closable="false"
      show-icon
      class="tip-bar"
    >
      <span>检测到 {{ orphans.length }} 个孤儿字典（有选项但没有类型记录）：{{ orphans.join('、') }}</span>
      <el-button type="text" @click="handleRepair">一键补建类型</el-button>
    </el-alert>

    <div class="dict-layout">
      <!-- 左：字典类型 -->
      <el-card shadow="never" class="dict-left">
        <div slot="header" class="card-header">
          <span>字典类型</span>
          <el-button type="primary" size="mini" icon="el-icon-plus" @click="openTypeDialog()">新增</el-button>
        </div>
        <el-input
          v-model="keyword"
          size="small"
          placeholder="搜索编码或名称"
          clearable
          prefix-icon="el-icon-search"
          class="mb10"
          @input="loadTypes"
        />
        <el-table
          v-loading="typeLoading"
          :data="types"
          size="mini"
          border
          highlight-current-row
          max-height="520"
          @row-click="handleTypeClick"
        >
          <el-table-column prop="dictCode" label="编码" min-width="130" show-overflow-tooltip />
          <el-table-column prop="dictName" label="名称" min-width="110" show-overflow-tooltip />
          <el-table-column prop="dataCount" label="选项" width="60" align="center" />
          <el-table-column label="操作" width="86" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" @click.stop="openTypeDialog(row)">编辑</el-button>
              <el-button type="text" class="danger-btn" @click.stop="handleDeleteType(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 右：字典项 -->
      <el-card shadow="never" class="dict-right">
        <div slot="header" class="card-header">
          <span>
            字典项
            <template v-if="currentCode">· {{ currentCode }}</template>
            <template v-else>· 请先在左侧选择类型</template>
          </span>
          <el-button
            type="primary"
            size="mini"
            icon="el-icon-plus"
            :disabled="!currentCode"
            @click="openDataDialog()"
          >
            新增选项
          </el-button>
        </div>
        <el-table v-loading="dataLoading" :data="dataList" size="mini" border max-height="560">
          <el-table-column prop="dictLabel" label="标签" min-width="120" />
          <el-table-column prop="dictValue" label="值" min-width="90" />
          <el-table-column prop="sortNo" label="排序" width="70" align="center" />
          <el-table-column label="状态" width="80" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="110" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" @click="openDataDialog(row)">编辑</el-button>
              <el-button type="text" class="danger-btn" @click="handleDeleteData(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!currentCode" class="form-tip">提示：生成页里 select/radio 控件引用的就是字典编码，删除被引用的类型会让对应下拉变空。</div>
      </el-card>
    </div>

    <!-- 弹窗：字典类型 -->
    <el-dialog :title="typeForm.id ? '编辑字典类型' : '新增字典类型'" :visible.sync="typeVisible" width="520px">
      <el-form :model="typeForm" label-width="90px" size="small">
        <el-form-item label="字典编码" required>
          <el-input v-model="typeForm.dictCode" :disabled="!!typeForm.id" placeholder="字母/数字/下划线，如 demo_goods_type" />
          <div class="form-tip">编码一旦被生成页引用就不建议改名（会导致下拉取不到选项）。</div>
        </el-form-item>
        <el-form-item label="字典名称">
          <el-input v-model="typeForm.dictName" placeholder="留空则与编码相同" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="typeVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveType">保 存</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：字典项 -->
    <el-dialog :title="dataForm.id ? '编辑字典项' : '新增字典项'" :visible.sync="dataVisible" width="520px">
      <el-form :model="dataForm" label-width="90px" size="small">
        <el-form-item label="字典编码">
          <el-input v-model="dataForm.dictCode" disabled />
        </el-form-item>
        <el-form-item label="标签" required>
          <el-input v-model="dataForm.dictLabel" placeholder="显示文本，如 启用" />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="dataForm.dictValue" placeholder="存库的值，如 1" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sortNo" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dataVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveData">保 存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listDictTypes, saveDictType, deleteDictType,
  listDictData, saveDictData, deleteDictData,
  listDictOrphans, repairDictOrphans
} from '@/api/dict'

const emptyType = () => ({ id: undefined, dictCode: '', dictName: '', status: 1 })
const emptyData = () => ({ id: undefined, dictCode: '', dictLabel: '', dictValue: '', sortNo: 1, status: 1, remark: '' })

export default {
  name: 'SystemDict',
  data() {
    return {
      keyword: '',
      types: [],
      typeLoading: false,
      currentCode: '',
      dataList: [],
      dataLoading: false,
      orphans: [],
      // 类型弹窗
      typeVisible: false,
      typeForm: emptyType(),
      // 字典项弹窗
      dataVisible: false,
      dataForm: emptyData(),
      saving: false
    }
  },
  created() {
    this.loadTypes()
    this.loadOrphans()
  },
  methods: {
    async loadTypes() {
      this.typeLoading = true
      try {
        const res = await listDictTypes(this.keyword)
        this.types = res.data || []
        // 保持当前选中项
        if (this.currentCode && !this.types.some(t => t.dictCode === this.currentCode)) {
          this.currentCode = ''
          this.dataList = []
        }
      } catch (e) {
        this.types = []
      } finally {
        this.typeLoading = false
      }
    },
    async loadOrphans() {
      try {
        const res = await listDictOrphans()
        this.orphans = res.data || []
      } catch (e) {
        this.orphans = []
      }
    },
    handleTypeClick(row) {
      this.currentCode = row.dictCode
      this.loadData()
    },
    async loadData() {
      if (!this.currentCode) return
      this.dataLoading = true
      try {
        const res = await listDictData(this.currentCode)
        this.dataList = res.data || []
      } catch (e) {
        this.dataList = []
      } finally {
        this.dataLoading = false
      }
    },
    openTypeDialog(row) {
      this.typeForm = row
        ? { id: row.id, dictCode: row.dictCode, dictName: row.dictName, status: row.status }
        : emptyType()
      this.typeVisible = true
    },
    async handleSaveType() {
      if (!this.typeForm.dictCode || !this.typeForm.dictCode.trim()) {
        this.$message.warning('字典编码不能为空')
        return
      }
      this.saving = true
      try {
        await saveDictType(this.typeForm)
        this.$message.success('保存成功')
        this.typeVisible = false
        this.loadTypes()
        this.loadOrphans()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.saving = false
      }
    },
    async handleDeleteType(row) {
      try {
        await this.$confirm(
          `删除字典类型「${row.dictCode}」会同时删除其 ${row.dataCount} 个选项，确定吗？`,
          '删除确认', { type: 'warning' }
        )
      } catch (e) {
        return
      }
      try {
        const res = await deleteDictType(row.id)
        this.$message.success(`已删除，连同 ${res.data || 0} 个选项`)
        if (this.currentCode === row.dictCode) {
          this.currentCode = ''
          this.dataList = []
        }
        this.loadTypes()
      } catch (e) { /* 拦截器已提示 */ }
    },
    openDataDialog(row) {
      this.dataForm = row
        ? { ...row }
        : { ...emptyData(), dictCode: this.currentCode, sortNo: (this.dataList.length || 0) + 1 }
      this.dataVisible = true
    },
    async handleSaveData() {
      if (!this.dataForm.dictLabel || !this.dataForm.dictValue) {
        this.$message.warning('标签与值都不能为空')
        return
      }
      this.saving = true
      try {
        await saveDictData(this.dataForm)
        this.$message.success('保存成功')
        this.dataVisible = false
        this.loadData()
        this.loadTypes()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.saving = false
      }
    },
    async handleDeleteData(row) {
      try {
        await this.$confirm(`删除选项「${row.dictLabel}」？`, '删除确认', { type: 'warning' })
      } catch (e) {
        return
      }
      try {
        await deleteDictData(row.id)
        this.$message.success('删除成功')
        this.loadData()
        this.loadTypes()
      } catch (e) { /* 拦截器已提示 */ }
    },
    async handleRepair() {
      try {
        const res = await repairDictOrphans()
        this.$message.success(`已补建 ${res.data || 0} 个字典类型`)
        this.loadTypes()
        this.loadOrphans()
      } catch (e) { /* 拦截器已提示 */ }
    }
  }
}
</script>

<style scoped>
.tip-bar {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dict-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.dict-left {
  width: 40%;
  min-width: 420px;
}

.dict-right {
  flex: 1;
}

.mb10 {
  margin-bottom: 10px;
}

.danger-btn {
  color: var(--cg-danger);
}

.form-tip {
  color: var(--cg-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
}
</style>
