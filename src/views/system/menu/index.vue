<template>
  <div class="app-container">
    <!-- 列表 -->
    <el-card shadow="never">
      <div slot="header" class="card-header">
        <span>菜单列表</span>
        <div>
          <el-tag type="info" size="small" class="tip-tag">
            动态路由下「路由路径」就是浏览器地址，「组件路径」是 src/views 下的真实文件
          </el-tag>
          <el-button type="primary" icon="el-icon-plus" @click="openDialog()">新增菜单</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="tree"
        border
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="菜单标题" min-width="170" />
        <el-table-column label="类型" width="86" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="typeTagOf(row.type)" size="small">{{ typeNameOf(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="component" label="组件路径" min-width="170" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <span v-if="row.type === 1" class="muted">—</span>
            <span v-else-if="row.component" class="mono">{{ row.component }}</span>
            <el-tooltip v-else content="没有组件路径的菜单会显示「页面未实现」占位页" placement="top">
              <el-tag type="warning" size="mini">未绑定</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="perms" label="权限码" min-width="170">
          <template slot-scope="{ row }">
            <span v-if="row.perms" class="mono">{{ row.perms }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="150" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <template v-if="row.icon">
              <i :class="row.icon" /> <span class="mono">{{ row.icon }}</span>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="86" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template slot-scope="{ row }">
            <el-button v-if="row.type !== 3" type="text" @click="openDialog(null, row)">新增子级</el-button>
            <el-button type="text" @click="openDialog(row)">编辑</el-button>
            <el-button type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑菜单' : '新增菜单'" :visible.sync="dialogVisible" width="600px">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" filterable placeholder="不选为顶级菜单" style="width: 100%">
            <el-option label="顶级菜单" :value="0" />
            <el-option
              v-for="opt in parentOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
              :disabled="opt.id === form.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单标题" prop="name">
          <el-input v-model="form.name" placeholder="显示在侧边栏 / 授权树上的名字" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :label="1">目录</el-radio>
            <el-radio :label="2">菜单</el-radio>
            <el-radio :label="3">按钮</el-radio>
          </el-radio-group>
          <div class="field-tip">
            目录只负责分组；菜单会生成一条路由；按钮只贡献权限码，不出现在侧边栏。
          </div>
        </el-form-item>
        <template v-if="form.type !== 3">
          <el-form-item label="路由路径" prop="path">
            <el-input v-model="form.path" placeholder="绝对路径，如 /goods/list" />
          </el-form-item>
          <el-form-item v-if="form.type === 2" label="组件路径" prop="component">
            <el-input v-model="form.component" placeholder="相对 src/views，不带扩展名，如 goods/list" />
            <div class="field-tip">
              填错或留空不会白屏：前端会渲染「页面未实现」占位页并在控制台打出菜单 ID。
            </div>
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="form.icon" placeholder="Element 图标 class，如 el-icon-setting" />
          </el-form-item>
        </template>
        <el-form-item label="权限码" :prop="form.type === 3 ? 'perms' : undefined">
          <el-input v-model="form.perms" placeholder="如 goods:list / goods:add" />
          <div class="field-tip">
            按钮必填。生成页的按钮用 v-hasPermi 匹配同一个码，两边对不上按钮就不显示（自检会报）。
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '@/api/system'

const defaultForm = () => ({
  id: undefined,
  parentId: 0,
  name: '',
  path: '',
  component: '',
  perms: '',
  icon: '',
  type: 2,
  sort: 0,
  status: 1
})

export default {
  name: 'SystemMenu',
  data() {
    const validateComponent = (rule, value, callback) => {
      if (this.form.type !== 2) return callback()
      if (!value) return callback(new Error('菜单必须绑定前端组件路径'))
      if (/^\/|\.vue$/.test(value)) return callback(new Error('填相对路径即可，如 goods/list（不要带 / 和 .vue）'))
      return callback()
    }
    return {
      loading: false,
      tree: [],
      dialogVisible: false,
      submitting: false,
      form: defaultForm(),
      rules: {
        name: [{ required: true, message: '请输入菜单标题', trigger: 'blur' }],
        path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }],
        component: [{ validator: validateComponent, trigger: 'blur' }],
        perms: [{ required: true, message: '按钮必须填权限码', trigger: 'blur' }]
      }
    }
  },
  computed: {
    // 上级菜单候选：只能是目录或菜单，按钮不能当父级
    parentOptions() {
      const out = []
      const walk = (nodes, depth) => {
        for (const n of nodes || []) {
          if (n.type !== 3) {
            out.push({ id: n.id, label: `${'　'.repeat(depth)}${n.name}` })
            walk(n.children, depth + 1)
          }
        }
      }
      walk(this.tree, 0)
      return out
    }
  },
  created() {
    this.fetchTree()
  },
  methods: {
    typeNameOf(type) {
      return { 1: '目录', 2: '菜单', 3: '按钮' }[type] || '未知'
    },
    typeTagOf(type) {
      return { 1: 'warning', 2: '', 3: 'success' }[type] || 'info'
    },
    async fetchTree() {
      this.loading = true
      try {
        const res = await getMenuTree()
        this.tree = res.data || []
      } catch (e) {
        this.tree = []
      } finally {
        this.loading = false
      }
    },
    openDialog(row, parent) {
      if (row) {
        this.form = {
          ...defaultForm(),
          ...row,
          parentId: row.parentId || 0,
          component: row.component || '',
          perms: row.perms || ''
        }
      } else {
        this.form = {
          ...defaultForm(),
          parentId: parent ? parent.id : 0,
          // 从「菜单」下新增子级，默认是按钮（最常见的用法）
          type: parent && parent.type === 1 ? 2 : 3
        }
      }
      this.dialogVisible = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    async submitForm() {
      if (!this.$refs.form) return
      try {
        await this.$refs.form.validate()
      } catch (e) {
        return
      }
      this.submitting = true
      const payload = { ...this.form }
      // 按钮没有路由/图标/组件，避免把上一轮的残留写进库
      if (payload.type === 3) {
        payload.path = null
        payload.component = null
        payload.icon = null
      }
      if (payload.type === 1) {
        payload.component = null
      }
      try {
        if (payload.id) {
          await updateMenu(payload.id, payload)
        } else {
          await createMenu(payload)
        }
        this.$message.success(payload.id ? '更新成功（重新登录后侧边栏生效）' : '创建成功（重新登录后侧边栏生效）')
        this.dialogVisible = false
        this.fetchTree()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    handleDelete(row) {
      const extra = row.children && row.children.length ? '，其下子菜单会一起隐藏' : ''
      this.$confirm(`确定删除菜单「${row.name}」吗${extra}？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteMenu(row.id)
          this.$message.success('删除成功（重新登录后侧边栏生效）')
          this.fetchTree()
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tip-tag {
  margin-right: 10px;
}

.field-tip {
  font-size: 12px;
  color: var(--cg-text-secondary);
  line-height: 1.5;
  margin-top: 2px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
}

.muted {
  color: var(--cg-text-placeholder);
}

.danger-btn {
  color: var(--cg-danger);
}
</style>
