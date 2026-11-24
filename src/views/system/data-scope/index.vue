<template>
  <div class="app-container">
    <!--
      V9.17 数据权限配置页（安全配置相关页）。
      契约：接口路径与「生成物」后端 SysRoleController 对齐（/sysRole/*），
      因此本页既可作为平台侧参考实现，也可整体拷入目标工程前端直接使用。
      平台自身后端若未提供这些端点，页面会优雅降级并给出提示，不影响其它页面。
    -->
    <el-alert
      title="数据范围按角色配置：多角色取最宽（all > dept > self）。修改后需用户重新登录或刷新会话才生效。"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-row :gutter="16">
      <!-- 角色列表 -->
      <el-col :span="8">
        <el-card shadow="never">
          <div slot="header" class="card-header">
            <span>角色列表</span>
            <el-button icon="el-icon-refresh" size="mini" @click="loadRoles">刷新</el-button>
          </div>
          <el-input v-model="roleKeyword" prefix-icon="el-icon-search" placeholder="筛选角色" clearable size="small" />
          <div v-loading="roleLoading" class="role-list">
            <div
              v-for="role in filteredRoles"
              :key="role.id"
              class="role-item"
              :class="{ active: currentRoleId === role.id }"
              @click="selectRole(role)"
            >
              <span>{{ role.roleName || role.name }}</span>
              <el-tag v-if="role.roleKey === 'admin' || role.id === 1" size="mini" type="warning">超管</el-tag>
            </div>
            <div v-if="!filteredRoles.length && !roleLoading" class="empty-tip">暂无角色（后端未就绪时请检查 /sysRole/list）</div>
          </div>
        </el-card>
      </el-col>

      <!-- 数据范围配置 -->
      <el-col :span="16">
        <el-card shadow="never">
          <div slot="header" class="card-header">
            <span>数据范围配置<span v-if="currentRole" class="role-badge"> · {{ currentRole.roleName || currentRole.name }}</span></span>
          </div>

          <el-alert v-if="apiHint" :title="apiHint" type="warning" show-icon :closable="false" style="margin-bottom: 14px" />

          <el-form v-if="currentRole" label-width="110px" size="small">
            <el-form-item label="数据范围">
              <el-radio-group v-model="form.dataScope">
                <el-radio label="all">全部数据</el-radio>
                <el-radio label="dept">本部门数据</el-radio>
                <el-radio label="self">仅本人数据</el-radio>
                <el-radio label="custom">自定义部门</el-radio>
              </el-radio-group>
              <div class="scope-help">{{ scopeHelp }}</div>
            </el-form-item>

            <el-form-item v-if="form.dataScope === 'dept' || form.dataScope === 'custom'" label="可见部门">
              <el-tree
                ref="deptTree"
                :data="deptTree"
                :props="treeProps"
                node-key="id"
                show-checkbox
                check-strictly
                default-expand-all
                :empty-text="deptHint"
              />
              <div class="scope-help">勾选该角色可访问的部门；未建 sys_dept 表时可在下方手动填写部门 ID。</div>
            </el-form-item>

            <el-form-item v-if="form.dataScope === 'dept' || form.dataScope === 'custom'" label="部门 ID">
              <el-input
                v-model="deptIdsText"
                placeholder="多个用逗号分隔，如 1,2,3（与上方勾选二选一，保存时合并）"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
              <el-button @click="selectRole(currentRole)">重置</el-button>
            </el-form-item>
          </el-form>

          <div v-else class="empty-tip large">请从左侧选择一个角色开始配置</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import request from '@/utils/request'

// 接口统一走生成物后端契约；单个失败不影响页面加载（容错见各 load 方法）
const API = {
  roleList: '/sysRole/list',
  dataScope: '/sysRole/dataScope',
  assignDataScope: '/sysRole/assignDataScope',
  deptList: '/sysRole/dept/list'
}

const SCOPE_HELP = {
  all: '该角色可查看全部数据，拦截器不追加过滤条件。',
  dept: '该角色仅可查看本部门数据（取登录用户 dept_id）。',
  self: '该角色仅可查看本人数据（按 create_by / owner_id 等归属列过滤）。',
  custom: '该角色仅可查看勾选部门的数据（自定义数据范围）。'
}

export default {
  name: 'SystemDataScope',
  data() {
    return {
      roleLoading: false,
      saving: false,
      roles: [],
      roleKeyword: '',
      currentRoleId: undefined,
      form: { dataScope: 'self' },
      deptTree: [],
      deptIdsText: '',
      apiHint: '',
      deptHint: '暂无部门数据（可选：后端建 sys_dept 表，或手动填 ID）',
      treeProps: { label: 'name', children: 'children', value: 'id' }
    }
  },
  computed: {
    filteredRoles() {
      const kw = (this.roleKeyword || '').trim().toLowerCase()
      if (!kw) return this.roles
      return this.roles.filter(r => ((r.roleName || r.name || '') + (r.roleKey || r.code || '')).toLowerCase().includes(kw))
    },
    currentRole() {
      return this.roles.find(r => r.id === this.currentRoleId)
    },
    scopeHelp() {
      return SCOPE_HELP[this.form.dataScope] || ''
    }
  },
  created() {
    this.loadRoles()
    this.loadDepts()
  },
  watch: {
    roles(list) {
      // 角色页跳转带 roleId 时自动选中
      const fromQuery = this.$route && this.$route.query && this.$route.query.roleId
      if (fromQuery && list && list.length && this.currentRoleId === undefined) {
        const hit = list.find(r => String(r.id) === String(fromQuery))
        if (hit) this.selectRole(hit)
      }
    }
  },
  methods: {
    async loadRoles() {
      this.roleLoading = true
      this.apiHint = ''
      try {
        const res = await request({ url: API.roleList, method: 'post' })
        this.roles = (res && res.data) || []
      } catch (e) {
        this.roles = []
        this.apiHint = '平台后端未提供 /sysRole/* 数据权限端点；本页为生成物前端配置页模板，接入目标工程后即可正常使用。'
      } finally {
        this.roleLoading = false
      }
    },
    async loadDepts() {
      try {
        const res = await request({ url: API.deptList, method: 'post', data: {} })
        this.deptTree = (res && res.data) || []
      } catch (e) {
        this.deptTree = []
      }
    },
    async selectRole(role) {
      if (!role) return
      this.currentRoleId = role.id
      this.form.dataScope = 'self'
      this.deptIdsText = ''
      this.$nextTick(() => { if (this.$refs.deptTree) this.$refs.deptTree.setCheckedKeys([]) })
      try {
        const res = await request({ url: API.dataScope, method: 'post', data: { roleId: role.id } })
        const data = (res && res.data) || {}
        this.form.dataScope = data.dataScope || 'self'
        const ids = data.deptIds || []
        this.deptIdsText = ids.join(',')
        this.$nextTick(() => { if (this.$refs.deptTree) this.$refs.deptTree.setCheckedKeys(ids) })
      } catch (e) {
        // 读取失败保持默认 self，安全侧不放大权限
        this.form.dataScope = 'self'
      }
    },
    collectDeptIds() {
      const set = new Set()
      this.deptIdsText.split(',').map(s => Number(s.trim())).filter(n => Number.isFinite(n) && n > 0).forEach(n => set.add(n))
      if (this.$refs.deptTree) {
        this.$refs.deptTree.getCheckedKeys().forEach(id => set.add(id))
      }
      return Array.from(set)
    },
    async save() {
      if (!this.currentRole) return
      const deptIds = this.collectDeptIds()
      if ((this.form.dataScope === 'dept' || this.form.dataScope === 'custom') && !deptIds.length) {
        this.$message.warning('请至少选择一个可见部门，或填写部门 ID')
        return
      }
      this.saving = true
      try {
        await request({
          url: API.assignDataScope,
          method: 'post',
          data: { roleId: this.currentRole.id, dataScope: this.form.dataScope, deptIds }
        })
        this.$message.success('数据范围已保存，用户重新登录后生效')
      } catch (e) {
        this.$message.error('保存失败：后端未启用数据权限配置端点或缺少 data_scope 列')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.role-badge { color: #409eff; font-weight: normal; }
.role-list { margin-top: 10px; max-height: 560px; overflow-y: auto; }
.role-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 9px 10px; cursor: pointer; border-radius: 4px; font-size: 14px;
}
.role-item:hover { background: #f5f7fa; }
.role-item.active { background: #ecf5ff; color: #409eff; }
.scope-help { color: #909399; font-size: 12px; line-height: 1.6; margin-top: 4px; }
.empty-tip { color: #909399; font-size: 13px; padding: 18px 4px; text-align: center; }
.empty-tip.large { padding: 80px 0; font-size: 14px; }
</style>
