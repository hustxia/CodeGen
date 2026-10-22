<template>
  <div class="app-container">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item label="角色名称">
          <el-input v-model="query.name" placeholder="角色名称" clearable @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div slot="header" class="card-header">
        <span>角色列表</span>
        <el-button type="primary" icon="el-icon-plus" @click="openDialog()">新增角色</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="code" label="角色编码" width="130" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="210" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openDialog(row)">编辑</el-button>
            <el-tooltip :disabled="!isSuperRole(row)" content="超管固定拥有全部权限，不允许在界面上删减" placement="top">
              <span>
                <el-button type="text" :disabled="isSuperRole(row)" @click="openPermDrawer(row)">分配权限</el-button>
              </span>
            </el-tooltip>
            <el-button type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑角色' : '新增角色'" :visible.sync="dialogVisible" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="form.code" placeholder="如：admin / operator" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述" />
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

    <!-- 分配权限抽屉：菜单 + 按钮 一棵树 -->
    <el-drawer
      :title="`分配权限 · ${permRole.name || ''}（${permRole.code || ''}）`"
      :visible.sync="permVisible"
      size="520px"
      :wrapper-closable="false"
    >
      <div v-loading="permLoading" class="perm-body">
        <div class="perm-toolbar">
          <el-checkbox v-model="permExpandAll" @change="toggleExpandAll">展开全部</el-checkbox>
          <el-checkbox v-model="permCheckAll" @change="toggleCheckAll">全选</el-checkbox>
          <span class="perm-hint">勾选菜单后，页面才能进入；勾选按钮后，页面内的操作按钮才显示</span>
        </div>
        <el-tree
          ref="permTree"
          :data="permTreeData"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          show-checkbox
          :default-expand-all="permExpandAll"
          :expand-on-click-node="false"
          class="perm-tree"
        >
          <span slot-scope="{ data }" class="perm-node">
            <span>{{ data.name }}</span>
            <el-tag v-if="data.type === 3" size="mini" type="success" class="perm-node-tag">按钮</el-tag>
            <span v-if="data.perms" class="perm-node-perms">{{ data.perms }}</span>
          </span>
        </el-tree>
      </div>
      <div class="perm-footer">
        <el-button @click="permVisible = false">取 消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerms">保 存</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import {
  getRoleList, createRole, updateRole, deleteRole,
  getEnabledMenuTree, getRoleMenuIds, saveRoleMenus
} from '@/api/system'

const defaultForm = () => ({
  id: undefined,
  name: '',
  code: '',
  description: '',
  status: 1
})

export default {
  name: 'SystemRole',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, name: '', status: undefined },
      dialogVisible: false,
      submitting: false,
      form: defaultForm(),
      // 分配权限
      permVisible: false,
      permLoading: false,
      permSaving: false,
      permRole: {},
      permTreeData: [],
      permExpandAll: true,
      permCheckAll: false
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    async fetchList() {
      this.loading = true
      try {
        const res = await getRoleList(this.query)
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
      this.query = { page: 1, size: this.query.size, name: '', status: undefined }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    openDialog(row) {
      this.form = row ? { ...defaultForm(), ...row } : defaultForm()
      this.dialogVisible = true
    },
    async submitForm() {
      if (!this.form.name) {
        this.$message.warning('请输入角色名称')
        return
      }
      this.submitting = true
      try {
        if (this.form.id) {
          await updateRole(this.form.id, this.form)
        } else {
          await createRole(this.form)
        }
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    isSuperRole(row) {
      return !!row && (String(row.id) === '1' || ['admin', 'super_admin'].indexOf(row.code) !== -1)
    },
    /* ---------- 分配权限 ---------- */
    async openPermDrawer(row) {
      this.permRole = { ...row }
      this.permVisible = true
      this.permLoading = true
      this.permCheckAll = false
      try {
        const [treeRes, idsRes] = await Promise.all([getEnabledMenuTree(), getRoleMenuIds(row.id)])
        this.permTreeData = treeRes.data || []
        const granted = (idsRes.data || []).map(String)
        const parentIds = new Set()
        const walk = nodes => {
          for (const n of nodes || []) {
            if (n.children && n.children.length) {
              parentIds.add(String(n.id))
              walk(n.children)
            }
          }
        }
        walk(this.permTreeData)
        // 只回填叶子节点：父节点一旦 setChecked 会连带勾满所有子节点，等于越权
        const leaves = granted.filter(id => !parentIds.has(id))
        this.$nextTick(() => {
          if (this.$refs.permTree) this.$refs.permTree.setCheckedKeys(leaves)
        })
      } catch (e) {
        this.permTreeData = []
      } finally {
        this.permLoading = false
      }
    },
    toggleExpandAll(val) {
      const tree = this.$refs.permTree
      if (!tree) return
      const nodes = tree.store.nodesMap
      Object.keys(nodes).forEach(key => { nodes[key].expanded = val })
    },
    toggleCheckAll(val) {
      const tree = this.$refs.permTree
      if (!tree) return
      const ids = []
      const walk = nodes => {
        for (const n of nodes || []) {
          ids.push(n.id)
          walk(n.children)
        }
      }
      walk(this.permTreeData)
      tree.setCheckedKeys(val ? ids : [])
    },
    async savePerms() {
      const tree = this.$refs.permTree
      if (!tree) return
      // 半选的父节点也要写进去，否则菜单树断枝、子菜单在侧边栏挂不上
      const menuIds = [...tree.getCheckedKeys(), ...tree.getHalfCheckedKeys()]
      this.permSaving = true
      try {
        await saveRoleMenus(this.permRole.id, menuIds)
        this.$message.success(`已保存 ${menuIds.length} 项授权，相关账号重新登录后生效`)
        this.permVisible = false
        // 改的是自己所属角色时，顺手刷新权限码（按钮显隐立即生效）
        const myRoles = this.$store.getters.roles || []
        if (myRoles.indexOf(this.permRole.code) !== -1) {
          this.$store.dispatch('user/getInfo').catch(() => {})
        }
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.permSaving = false
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteRole(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    }
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
  color: var(--cg-danger);
}

/* ---------- 分配权限抽屉 ---------- */
.perm-body {
  padding: 0 20px 60px;
  height: 100%;
  overflow: auto;
}

.perm-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--cg-border-light);
  margin-bottom: 10px;
}

.perm-hint {
  font-size: 12px;
  color: var(--cg-text-secondary);
}

.perm-tree {
  font-size: 13px;
}

.perm-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.perm-node-tag {
  transform: scale(0.9);
}

.perm-node-perms {
  font-family: Consolas, Monaco, monospace;
  font-size: 11.5px;
  color: var(--cg-text-placeholder);
}

.perm-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 20px;
  border-top: 1px solid var(--cg-border-light);
  background: #fff;
  text-align: right;
}
</style>
