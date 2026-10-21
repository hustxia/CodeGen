<template>
  <div class="app-container">
    <div class="page-head">
      <span class="page-title">系统管理</span>
      <span class="page-desc">
        一个系统 = 一套输出配置（前后端工程路径、根包名、网关前缀、依赖 classpath）。
        表的生成配置按「表级 → 所属系统 → 全局默认」三级回落，改系统配置即批量生效。
      </span>
    </div>

    <div class="toolbar">
      <el-button type="primary" icon="el-icon-plus" @click="openDialog()">新增系统</el-button>
      <el-button icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
      <span class="toolbar-tip">
        系统编码一旦有表挂载就不允许修改 —— 它是生成物路径与字典/权限前缀的锚点。
      </span>
    </div>

    <el-table v-loading="loading" :data="list" border size="small">
      <el-table-column label="系统" min-width="170">
        <template slot-scope="{ row }">
          <div class="proj-name">
            <span>{{ row.projectName }}</span>
            <el-tag v-if="row.isDefault === 1" size="mini" type="success">默认</el-tag>
            <el-tag v-if="row.enabled !== 1" size="mini" type="info">停用</el-tag>
          </div>
          <div class="proj-code">{{ row.projectCode }}</div>
        </template>
      </el-table-column>

      <el-table-column label="后端工程" min-width="240" show-overflow-tooltip>
        <template slot-scope="{ row }">
          <div class="path-main">{{ row.backendRoot || '—' }}</div>
          <div class="path-sub">{{ row.backendPath || '—' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="前端工程" min-width="200" show-overflow-tooltip>
        <template slot-scope="{ row }">
          <div class="path-main">{{ row.frontendRoot || '—' }}</div>
          <div class="path-sub">{{ row.frontendPath || '（根目录）' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="根包 / 网关" width="190">
        <template slot-scope="{ row }">
          <div class="path-main">{{ row.rootPackage || '—' }}</div>
          <div class="path-sub">/api/{{ row.gatewayPrefix || '—' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="依赖 classpath" min-width="170" show-overflow-tooltip>
        <template slot-scope="{ row }">
          <span v-if="row.selfcheckClasspath" class="mono">{{ row.selfcheckClasspath }}</span>
          <span v-else class="muted">沿用全局 gen.selfcheck</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template slot-scope="{ row }">
          <el-button type="text" @click="openDialog(row)">编辑</el-button>
          <el-button
            v-if="row.isDefault !== 1"
            type="text"
            :disabled="row.enabled !== 1"
            @click="handleSetDefault(row)"
          >设为默认</el-button>
          <el-button
            type="text"
            class="danger-btn"
            :disabled="row.isDefault === 1"
            @click="handleDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      :title="form.id ? '编辑系统' : '新增系统'"
      :visible.sync="dialogVisible"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="form" :model="form" :rules="rules" label-width="130px" size="small">
        <el-form-item label="系统编码" prop="projectCode">
          <el-input
            v-model="form.projectCode"
            :disabled="!!form.id && form.tableCount > 0"
            placeholder="小写字母/数字/下划线，如 crm"
          />
          <div v-if="form.id && form.tableCount > 0" class="form-tip">
            该系统下已有 {{ form.tableCount }} 张表，编码不可修改。
          </div>
        </el-form-item>

        <el-form-item label="系统名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="如 客户管理系统" />
        </el-form-item>

        <el-form-item label="后端工程根">
          <el-input v-model="form.backendRoot" placeholder="绝对路径，如 D:/proj/crm-server" />
        </el-form-item>

        <el-form-item label="后端相对路径">
          <el-input v-model="form.backendPath" placeholder="如 crm-server/src/main/java/com/crm" />
        </el-form-item>

        <el-form-item label="前端工程根">
          <el-input v-model="form.frontendRoot" placeholder="绝对路径，如 D:/proj/crm-web" />
        </el-form-item>

        <el-form-item label="前端相对路径">
          <el-input v-model="form.frontendPath" placeholder="留空=前端工程根本身；monorepo 填子目录" />
        </el-form-item>

        <el-form-item label="根包名" prop="rootPackage">
          <el-input v-model="form.rootPackage" placeholder="如 com.crm，生成物 import 基座类用" />
        </el-form-item>

        <el-form-item label="网关前缀">
          <el-input v-model="form.gatewayPrefix" placeholder="如 crm，接口路径会是 /api/crm/..." />
        </el-form-item>

        <el-form-item label="依赖 classpath">
          <el-input
            v-model="form.selfcheckClasspath"
            placeholder="支持 dir/* 通配符；留空沿用全局 gen.selfcheck"
          />
          <div class="form-tip">
            自检要在内存里编译生成物，需要该系统的依赖。各系统依赖版本不同，必须分开配。
          </div>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>

      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listProject,
  createProject,
  updateProject,
  deleteProject,
  setDefaultProject
} from '@/api/gen'

const defaultForm = () => ({
  id: undefined,
  projectCode: '',
  projectName: '',
  backendRoot: '',
  frontendRoot: '',
  backendPath: '',
  frontendPath: '',
  rootPackage: '',
  gatewayPrefix: '',
  selfcheckClasspath: '',
  enabled: 1,
  sort: 0,
  remark: '',
  tableCount: 0
})

export default {
  name: 'GenProject',
  data() {
    return {
      loading: false,
      submitting: false,
      list: [],
      dialogVisible: false,
      form: defaultForm(),
      rules: {
        projectCode: [
          { required: true, message: '请输入系统编码', trigger: 'blur' },
          {
            pattern: /^[a-z][a-z0-9_]{0,63}$/,
            message: '只能是小写字母、数字、下划线，且以字母开头',
            trigger: 'blur'
          }
        ],
        projectName: [{ required: true, message: '请输入系统名称', trigger: 'blur' }],
        rootPackage: [
          {
            pattern: /^[a-z][a-z0-9_.]*$/,
            message: '应为 com.example 这样的形式',
            trigger: 'blur'
          }
        ]
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await listProject()
        this.list = res.data || []
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.loading = false
      }
    },
    openDialog(row) {
      this.form = row ? { ...defaultForm(), ...row, tableCount: row.tableCount || 0 } : defaultForm()
      this.dialogVisible = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    submit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        this.submitting = true
        try {
          const payload = { ...this.form }
          delete payload.tableCount
          if (payload.id) {
            await updateProject(payload)
          } else {
            await createProject(payload)
          }
          this.$message.success('保存成功')
          this.dialogVisible = false
          this.load()
        } catch (e) {
          /* 拦截器已提示 */
        } finally {
          this.submitting = false
        }
      })
    },
    handleSetDefault(row) {
      this.$confirm(`将「${row.projectName}」设为默认系统？新建表时会预选它。`, '提示', { type: 'info' })
        .then(async () => {
          await setDefaultProject(String(row.id))
          this.$message.success('已设为默认')
          this.load()
        })
        .catch(() => {})
    },
    handleDelete(row) {
      this.$confirm(`确定删除系统「${row.projectName}」吗？系统下还有表时无法删除。`, '提示', {
        type: 'warning'
      })
        .then(async () => {
          await deleteProject(String(row.id))
          this.$message.success('删除成功')
          this.load()
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.page-head {
  margin-bottom: 16px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-right: 12px;
}

.page-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.toolbar-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.proj-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
}

.proj-code {
  margin-top: 2px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #909399;
}

.path-main {
  color: #303133;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.path-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.muted {
  color: #c0c4cc;
  font-size: 12px;
}

.danger-btn {
  color: #f56c6c;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
