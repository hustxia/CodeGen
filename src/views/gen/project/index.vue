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

        <!-- 只在新增时出现：编辑态改「复制来源」没有意义，路径直接改字段就行 -->
        <el-form-item v-if="!form.id" label="复制配置自">
          <el-select
            v-model="copyFromId"
            clearable
            placeholder="不复制，从空开始"
            style="width: 100%"
            @change="onCopyFromChange"
          >
            <el-option
              v-for="p in list"
              :key="p.id"
              :label="`${p.projectName}（${p.projectCode}）`"
              :value="String(p.id)"
            />
          </el-select>
          <div class="form-tip">
            只复制路径类字段与 17 个生成开关默认值。编码、名称、根包名、网关前缀不复制——
            这几项属于系统身份，抄过来会得到两套同名包，生成的代码互相覆盖。
          </div>
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

        <el-form-item label="工程结构">
          <el-select v-model="form.projectStructure" clearable placeholder="默认：单模块" style="width: 300px">
            <el-option label="单模块（跟随默认）" value="" />
            <el-option label="多模块（微服务就绪）" value="multi" />
            <el-option label="微服务设施（Nacos/网关/Feign）" value="micro" />
          </el-select>
          <div class="form-tip">
            多模块生成 parent + common/domain/service/bootstrap 四个子模块，模块边界即未来微服务拆分线；
            微服务设施在多模块基础上追加 gateway 网关模块与 OpenFeign 骨架，内置 Nacos 开关
            （默认关=与多模块行为一致，开=注册中心+负载均衡）；
            保存后下次脚手架生成生效
          </div>
        </el-form-item>

        <el-form-item label="流程引擎">
          <el-select v-model="form.flowEngine" clearable placeholder="默认：内置简易流" style="width: 300px">
            <el-option label="内置简易流（状态机直线流）" value="" />
            <el-option label="Activiti 8 引擎（BPMN/待办中心）" value="activiti" />
          </el-select>
          <div class="form-tip">
            绑定了流程编码（flow_code）的表按此引擎生成产物：内置流零依赖、够用直线审批；
            Activiti 引擎自动翻译节点定义为 BPMN 并部署，交付待办中心与完整审批历史（依赖经 Alfresco 仓库拉取）；
            保存后下次脚手架生成生效
          </div>
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

        <el-form-item label="应用显示名">
          <el-input v-model="form.appName" placeholder="如 悦集商城；留空用系统名" />
          <div class="form-tip">
            生成产物的浏览器标题、侧边栏品牌区、登录页标题统一取这个值；留空回落到系统名
          </div>
        </el-form-item>

        <el-form-item label="品牌主色">
          <el-color-picker v-model="form.appThemeColor" />
          <span class="brand-color-hint">{{ form.appThemeColor || '未配置（默认 #e1251b）' }}</span>
          <div class="form-tip">
            注入产物的 --app-primary 变量（品牌标记、登录按钮）；留空或非法值回落默认色
          </div>
        </el-form-item>

        <el-form-item label="Logo 地址">
          <el-input v-model="form.appLogo" placeholder="留空用内置 logo" />
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>

        <el-form-item label="生成开关默认">
          <div class="default-switch-grid">
            <div v-for="sw in genSwitchDefs" :key="sw.key" class="default-switch-item">
              <span>{{ sw.label }}</span>
              <el-switch v-model="form[sw.key]" :active-value="1" :inactive-value="0" />
            </div>
          </div>
          <div
            v-for="sw in triStateDefs"
            :key="sw.key"
            class="default-switch-item"
            style="margin-top: 8px"
          >
            <span>{{ sw.label }}</span>
            <el-select
              v-model="form[sw.key]"
              clearable
              :placeholder="sw.placeholder || '未配置（关）'"
              size="mini"
              style="width: 150px"
            >
              <el-option label="默认开启" :value="1" />
              <el-option label="默认关闭" :value="0" />
            </el-select>
          </div>
          <div class="form-tip">
            只影响以后导入的新表：导入时按这份默认值落表，已有表的开关不会被改写
            （避免改一次系统配置把存量表全部翻掉）。表配置弹窗里的「恢复系统默认」也读这里。
            {{ triStateDefs[0].tip }}
          </div>
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
  tableCount: 0,
  // 17 个生成开关的系统默认值（V3.1 P0-3）。null 表示未配置，后端按 1 处理
  defaultGenEntity: 1,
  defaultGenMapper: 1,
  defaultGenService: 1,
  defaultGenLog: 1,
  defaultGenController: 1,
  defaultGenPageApi: 1,
  defaultGenDetailApi: 1,
  defaultGenAddApi: 1,
  defaultGenEditApi: 1,
  defaultGenDeleteApi: 1,
  defaultGenFrontend: 1,
  defaultGenApiJs: 1,
  defaultGenVuePage: 1,
  defaultGenQueryForm: 1,
  defaultGenAddBtn: 1,
  defaultGenEditBtn: 1,
  defaultGenDeleteBtn: 1,
  // V4.5 打印单据默认：独立于 17 键体系（硬编码兜底是关而非开），null=未配置（关）
  defaultGenPrint: null,
  // V6.2 部署物默认：null=未配置（关）
  defaultGenDeploy: null,
  // V6.3 支付能力包：null=未配置（关）
  payEnabled: null,
  // V6.5 移动端工程：null=未配置（关）
  mobileEnabled: null,
  // V6.6 文件能力包：null=未配置（关）
  fileEnabled: null,
  // V6.7 测试骨架：null=未配置（开）——方向与其它能力包相反，测试零运行时侵入
  testEnabled: null,
  // V6.8 品牌外观：全部可空，空=回落（项目名 / 内置 logo / #e1251b）
  appName: '',
  appLogo: '',
  appThemeColor: '',
  // 工程结构：null/''=单模块，'multi'=Maven 多模块（微服务就绪单体），'micro'=微服务设施（V6.0：五模块+Nacos/Gateway/Feign）
  projectStructure: '',
  // V6.1 流程引擎：null/''=内置简易流，'activiti'=Activiti 8（BPMN 翻译+待办中心）
  flowEngine: ''
})

// 与后端 GenSwitchDefaults.KEYS 一一对应，顺序也一致
const genSwitchDefs = [
  { key: 'defaultGenEntity', label: 'entity' },
  { key: 'defaultGenMapper', label: 'mapper' },
  { key: 'defaultGenService', label: 'service' },
  { key: 'defaultGenController', label: 'controller' },
  { key: 'defaultGenLog', label: '操作日志' },
  { key: 'defaultGenPageApi', label: '分页接口' },
  { key: 'defaultGenDetailApi', label: '详情接口' },
  { key: 'defaultGenAddApi', label: '新增接口' },
  { key: 'defaultGenEditApi', label: '修改接口' },
  { key: 'defaultGenDeleteApi', label: '删除接口' },
  { key: 'defaultGenFrontend', label: '前端' },
  { key: 'defaultGenApiJs', label: 'api.js' },
  { key: 'defaultGenVuePage', label: 'vue 页面' },
  { key: 'defaultGenQueryForm', label: '查询区' },
  { key: 'defaultGenAddBtn', label: '新增按钮' },
  { key: 'defaultGenEditBtn', label: '编辑按钮' },
  { key: 'defaultGenDeleteBtn', label: '删除按钮' }
]

// V4.5 独立回落开关（不在 17 键 grid 里）：三态，单独渲染下拉
const triStateDefs = [
  {
    key: 'defaultGenPrint',
    label: '打印单据默认',
    tip: '开=新表默认生成 A4 打印页；关=不生成；未配置=关'
  },
  {
    key: 'defaultGenDeploy',
    label: '部署物默认',
    tip: '开=脚手架追加 Dockerfile/compose/CI 模板；关=不生成；未配置=关'
  },
  {
    key: 'payEnabled',
    label: '支付能力包',
    tip: '开=生成支付模块（pay_order + 微信/支付宝/mock 三渠道 + 收银台）；关=不生成；未配置=关'
  },
  {
    key: 'mobileEnabled',
    label: '移动端工程',
    tip: '开=生成 uni-app 移动端工程（{前端模块}-mobile，单源 H5+微信小程序）；关=不生成；未配置=关'
  },
  {
    key: 'fileEnabled',
    label: '文件能力包',
    tip: '开=生成文件上传下载基座（sys_file + /file 端点 + ImageUpload/FileUpload 组件），html_type=图片/文件上传 的列自动带控件；关=不生成；未配置=关'
  },
  {
    key: 'testEnabled',
    label: '测试骨架',
    placeholder: '未配置（生成）',
    tip: '生成=产物自带集成测试（每业务表一份 CRUD 测试，H2 内存库，打开即可 mvn test 全绿）；不生成=产物无 src/test 目录；未配置=生成（默认，与其他开关方向相反）。multi/micro 工程暂不支持'
  }
]

export default {
  name: 'GenProject',
  data() {
    return {
      loading: false,
      submitting: false,
      list: [],
      dialogVisible: false,
      form: defaultForm(),
      genSwitchDefs,
      triStateDefs,
      // 复制来源系统 id（字符串，避免 Long 精度丢失）；空表示不复制
      copyFromId: '',
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
    // 深链：/gen/project?create=1 直接打开新增弹窗，方便从别处（如向导页）跳过来建系统
    if (this.$route.query.create === '1') {
      this.openDialog()
    }
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
      this.copyFromId = ''
      this.dialogVisible = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    /**
     * 选了复制来源就把路径回填到表单，让用户在保存前看得见、改得动。
     * 服务端也会再复制一次（只补请求里为空的项）：前端回填是为了所见即所得，
     * 服务端复制是为了防止绕过页面直接调接口时行为不一致。
     */
    onCopyFromChange(id) {
      if (!id) return
      const src = this.list.find(p => String(p.id) === String(id))
      if (!src) return
      const fields = ['backendRoot', 'frontendRoot', 'backendPath', 'frontendPath', 'selfcheckClasspath']
      fields.forEach(f => {
        if (!this.form[f]) this.form[f] = src[f] || ''
      })
      this.$message.success(`已复制「${src.projectName}」的路径配置`)
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
            await createProject(payload, this.copyFromId || undefined)
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
/* V6.8 品牌主色：色块与说明同行，未配置时显示回落值 */
.brand-color-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
/* 17 个开关的紧凑网格：放在系统弹窗里，两列就够，四列会把标签挤断 */
.default-switch-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 16px;
  width: 100%;
}

.default-switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--cg-bg-hover);
  font-size: 12px;
  color: var(--cg-text-regular);
}

.page-head {
  margin-bottom: 16px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cg-text);
  margin-right: 12px;
}

.page-desc {
  font-size: 12px;
  color: var(--cg-text-secondary);
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
  color: var(--cg-text-secondary);
}

.proj-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--cg-text);
}

.proj-code {
  margin-top: 2px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: var(--cg-text-secondary);
}

.path-main {
  color: var(--cg-text);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.path-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--cg-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.muted {
  color: var(--cg-text-placeholder);
  font-size: 12px;
}

.danger-btn {
  color: var(--cg-danger);
}

.form-tip {
  color: var(--cg-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
