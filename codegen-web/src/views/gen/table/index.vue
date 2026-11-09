<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="操作路径：从数据库导入（或粘贴 DDL）→ 配置字段与生成开关 → 一键生成（自检→备份写盘→编译→注册菜单→重建路由）。想单步看效果时再用「生成预览 / 自检 / 注册菜单」。"
    />

    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item label="表名">
          <el-input v-model="query.tableName" placeholder="请输入表名" clearable @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="功能名">
          <el-input v-model="query.tableComment" placeholder="请输入功能名" clearable @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="生成状态">
          <el-select v-model="query.genStatus" clearable placeholder="全部" style="width: 140px" @change="handleSearch">
            <el-option label="已生成过" value="generated" />
            <el-option label="从未生成" value="ungenerated" />
            <el-option label="结构有漂移" value="drift" />
            <el-option label="已注册菜单" value="menu" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属系统">
          <el-select v-model="query.projectId" clearable placeholder="全部" style="width: 160px" @change="handleSearch">
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.projectName"
              :value="p.id"
            />
            <!-- -1 是后端约定的「未绑定」哨兵值，用来捞出迁移前的存量表 -->
            <el-option label="未绑定系统" :value="-1" />
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
        <span class="card-title">表配置列表</span>
        <!--
          按钮分三组，按「用户在什么时候会用到」排，而不是按功能清单排：
          1) 入口组（左侧）：把表弄进系统的方式——导入、DDL
          2) 主操作组（中间）：日常真正反复点的——一键生成、自检
          3) 更多组（右侧）：低频的预览/差异/菜单/下载，收进下拉避免横向铺满
          定稿前试过平铺 10 个按钮，1366 宽屏下会换行并把「下载 ZIP」挤到第二行。
        -->
        <div class="header-actions">
          <div class="action-group">
            <el-button icon="el-icon-upload2" @click="openDbDialog">从数据库导入</el-button>
            <el-button icon="el-icon-document-add" @click="openImportDialog">粘贴 DDL</el-button>
          </div>

          <div class="group-divider"></div>

          <div class="action-group">
            <el-button
              type="primary"
              icon="el-icon-magic-stick"
              :loading="applying"
              @click="openApplyDialog()"
            >一键生成</el-button>
            <el-button
              type="warning"
              icon="el-icon-circle-check"
              :loading="checking"
              @click="handleSelfCheck"
            >生成自检</el-button>
          </div>

          <div class="group-divider"></div>

          <el-dropdown trigger="click" @command="onMoreCommand">
            <el-button icon="el-icon-more" class="more-btn">更多<i class="el-icon-arrow-down el-icon--right"></i></el-button>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="preview" icon="el-icon-view">生成预览</el-dropdown-item>
              <el-dropdown-item command="diff" icon="el-icon-document-copy">差异预览</el-dropdown-item>
              <el-dropdown-item command="menu" icon="el-icon-menu">注册菜单</el-dropdown-item>
              <el-dropdown-item command="relation" icon="el-icon-connection">多对多识别</el-dropdown-item>
              <el-dropdown-item command="generate" icon="el-icon-download" divided>下载 ZIP</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </div>

      <!-- 批量操作条：勾选后才出现，避免占常态空间 -->
      <div v-if="selected.length" class="batch-bar">
        <span class="batch-count">已选 {{ selected.length }} 张表</span>
        <el-button size="mini" icon="el-icon-setting" @click="openBatchDialog">批量设置</el-button>
        <el-button size="mini" icon="el-icon-cpu" :loading="aiRecLoading" @click="openAiRecommend">AI 推荐配置</el-button>
        <el-button size="mini" type="success" icon="el-icon-magic-stick" :loading="applying" @click="openApplyDialog()">批量一键生成</el-button>
        <el-button size="mini" type="warning" icon="el-icon-circle-check" :loading="checking" @click="handleSelfCheck">批量自检</el-button>
        <el-button size="mini" icon="el-icon-document-copy" @click="openDiffDialog">差异预览</el-button>
        <el-button size="mini" type="danger" icon="el-icon-delete" @click="handleBatchDelete">批量删除</el-button>
        <span style="flex: 1"></span>
        <el-button size="mini" type="text" @click="clearSelection">清空选择</el-button>
      </div>

      <el-table
        ref="table"
        v-loading="loading"
        :data="list"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="tableName" label="表名" min-width="130" />
        <el-table-column prop="tableComment" label="表注释（功能名）" min-width="110" />
        <el-table-column prop="className" label="类名" width="105" />
        <el-table-column prop="moduleName" label="前端模块" width="85" />
        <el-table-column label="目标模块" width="95" align="center">
          <template slot-scope="{ row }">
            <el-tag v-if="row.targetModule" size="mini" type="success" plain>{{ row.targetModule }}</el-tag>
            <span v-else class="muted">默认</span>
          </template>
        </el-table-column>
        <el-table-column label="生成状态" width="150" align="center">
          <template slot-scope="{ row }">
            <el-tag v-if="row.lastGenTime" type="success" size="mini">已生成</el-tag>
            <span v-else class="muted">未生成</span>
            <div v-if="row.lastGenTime" class="cell-sub">{{ row.lastGenTime }}</div>
          </template>
        </el-table-column>
        <el-table-column label="结构" width="105" align="center">
          <template slot-scope="{ row }">
            <el-tooltip v-if="row.driftCount" placement="top">
              <div slot="content">
                <div v-if="row.driftNewColumns && row.driftNewColumns.length">
                  库中新增：{{ row.driftNewColumns.join(', ') }}
                </div>
                <div v-if="row.driftMissingColumns && row.driftMissingColumns.length">
                  配置里多余：{{ row.driftMissingColumns.join(', ') }}
                </div>
              </div>
              <el-tag type="warning" size="mini">漂移 {{ row.driftCount }}</el-tag>
            </el-tooltip>
            <el-tag v-else type="info" size="mini" plain>一致</el-tag>
            <div v-if="row.driftCount">
              <el-button type="text" size="mini" :loading="syncing" @click="handleSyncColumns(row)">同步</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="menuId" label="菜单" width="85" align="center">
          <template slot-scope="{ row }">
            <el-tag v-if="row.menuId" type="success" size="mini">已注册</el-tag>
            <span v-else class="muted">未注册</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openColumnDialog(row)">字段配置</el-button>
            <el-button type="text" style="color: var(--cg-primary)" @click="openDictDetect(row)">字典识别</el-button>
            <el-button type="text" style="color: var(--cg-success)" @click="openApplyDialog(row)">一键生成</el-button>
            <el-button type="text" style="color: var(--cg-warning)" @click="doSelfCheck([row.id])">自检</el-button>
            <el-button type="text" @click="doPreview([row.id])">预览</el-button>
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

    <!-- 弹窗：从数据库导入（多库 + 多选） -->
    <el-dialog title="从数据库导入" :visible.sync="dbVisible" width="820px" top="4vh">
      <div class="query-row">
        <span>数据库</span>
        <el-select v-model="dbSchema" size="small" style="width: 200px" placeholder="请选择数据库">
          <el-option v-for="s in schemas" :key="s" :label="s" :value="s" />
        </el-select>
        <span>导入到</span>
        <el-select v-model="dbProjectId" size="small" style="width: 220px" placeholder="不绑定系统" clearable>
          <el-option
            v-for="p in projectOptions"
            :key="p.id"
            :label="p.projectName + '（' + p.projectCode + '）'"
            :value="p.id"
          />
        </el-select>
        <el-button type="primary" size="small" :loading="dbLoading" @click="loadDbTables">加载表</el-button>
      </div>
      <div class="query-row">
        <span style="flex:1"></span>
        <el-input v-model="dbFilter" size="small" style="width: 180px" placeholder="表名过滤" clearable />
        <el-button size="small" @click="handleSelectAllDb">全选</el-button>
      </div>
      <el-table
        ref="dbTable"
        v-loading="dbLoading"
        :data="filteredDbTables"
        border
        size="mini"
        max-height="360"
        @selection-change="dbSelection = $event"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="name" label="表名" min-width="150" />
        <el-table-column prop="comment" label="注释" min-width="160" />
        <el-table-column label="状态" width="150">
          <template slot-scope="{ row }">
            <span :class="dbStatusClass(row.name)">{{ dbStatusText(row.name) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="importResults.length" class="import-results">
        <div v-for="r in importResults" :key="r.tableName" class="res-row">
          <span class="pill" :class="resultClass(r.status)">{{ r.status }}</span>
          <span>{{ r.tableName }}</span>
          <span class="res-msg">{{ r.message }}</span>
        </div>
      </div>
      <div class="form-tip">列信息直接读取 information_schema.columns 自动推断（跳过 id/deleted/审计字段）；已存在的表自动跳过。包名 / 网关前缀 / 输出路径留空，由所选系统提供，不在这里逐表填写。</div>
      <div slot="footer">
        <el-button @click="dbVisible = false">关 闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!dbSelection.length" @click="handleImportDb">
          导入所选（{{ dbSelection.length }}）
        </el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：生成预览（左树右码 + 写盘） -->
    <el-dialog title="生成预览" :visible.sync="previewVisible" width="1100px" top="3vh">
      <div class="preview">
        <div class="preview-tree">
          <el-tree
            :data="previewTree"
            :props="{ label: 'label', children: 'children' }"
            default-expand-all
            highlight-current
            @node-click="handlePreviewClick"
          />
        </div>
        <div class="preview-code">
          <div class="code-head">{{ currentPath || '点击左侧文件查看源码' }}</div>
          <pre class="code-body">{{ currentContent }}</pre>
        </div>
      </div>
      <div class="write-bar">
        <span class="write-tip">写入同名文件时覆盖</span>
        <el-switch v-model="writeOverwrite" />
        <el-button type="success" size="small" icon="el-icon-upload" :loading="writing" @click="handleWrite">
          写入磁盘
        </el-button>
        <el-button size="small" icon="el-icon-circle-check" :loading="checking" @click="doSelfCheck(previewIds)">
          生成自检
        </el-button>
        <el-button size="small" icon="el-icon-download" @click="handleGenerateFromPreview">下载 ZIP</el-button>
      </div>
      <div v-if="writeResults.length" class="import-results">
        <div v-for="r in writeResults" :key="r.path" class="res-row">
          <span class="pill" :class="resultClass(r.status)">{{ r.status }}</span>
          <span>{{ r.path }}</span>
          <span class="res-msg">{{ r.message }}</span>
        </div>
      </div>
      <div slot="footer">
        <el-button @click="previewVisible = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：AI 推荐配置（V7.3 P0-3 表级入口，与流水线共用同一套 diff 预览组件） -->
    <ai-recommend-dialog
      :visible.sync="aiRecVisible"
      :loading="aiRecLoading"
      :apply-loading="aiRecApplyLoading"
      :exists="aiRecExists"
      :tables="aiRecData.tables"
      :menus="aiRecData.menus"
      :drops="aiRecData.drops"
      :show-menus="false"
      title="AI 推荐配置（选中表）"
      empty-text="AI 未返回可用建议，请重试或检查服务端 LLM 配置"
      @apply="onApplyAiRecommend"
    />

    <!-- 弹窗：差异预览（V2.0 拆到 GenDiffDialog，自己拉数据） -->
    <gen-diff-dialog :visible.sync="diffVisible" :table-ids="diffTableIds" />

    <!-- 弹窗：粘贴 DDL 导入 -->
    <el-dialog title="粘贴 DDL 导入" :visible.sync="importVisible" width="960px" top="4vh">
      <el-form label-width="120px">
        <el-form-item label="建表语句" required>
          <el-input
            v-model="ddlText"
            type="textarea"
            :rows="8"
            placeholder="粘贴 CREATE TABLE 语句（一次一条）"
            class="mono"
          />
          <div class="form-tip">自动跳过 id / deleted / 审计字段；无主键的表无法导入；表名已存在时保存会提示覆盖。</div>
        </el-form-item>
      </el-form>

      <template v-if="parsed">
        <el-divider content-position="left">解析结果（可修正后再保存）</el-divider>
        <el-form :model="parsed" label-width="120px" inline>
          <el-form-item label="表名" required>
            <el-input v-model="parsed.tableName" style="width: 220px" />
          </el-form-item>
          <el-form-item label="表注释（功能名）" required>
            <el-input v-model="parsed.tableComment" style="width: 220px" />
          </el-form-item>
          <el-form-item label="实体类名" required>
            <el-input v-model="parsed.className" style="width: 220px" />
          </el-form-item>
          <el-form-item label="前端模块名" required>
            <el-input v-model="parsed.moduleName" style="width: 220px" />
          </el-form-item>
          <el-form-item label="Java 包名">
            <el-input v-model="parsed.packageName" style="width: 220px" />
          </el-form-item>
          <el-form-item label="后端根目录">
            <el-input v-model="parsed.backendRoot" style="width: 320px" :placeholder="rootPlaceholder('backendRoot')" />
          </el-form-item>
          <el-form-item label="前端根目录">
            <el-input v-model="parsed.frontendRoot" style="width: 320px" :placeholder="rootPlaceholder('frontendRoot')" />
          </el-form-item>
          <el-form-item label="后端输出路径">
            <el-input v-model="parsed.backendPath" style="width: 420px" />
          </el-form-item>
        </el-form>

        <el-table :data="parsed.columns" border size="mini" max-height="300">
          <el-table-column prop="columnName" label="列名" min-width="110" />
          <el-table-column label="注释" min-width="140">
            <template slot-scope="{ row }"><el-input v-model="row.columnComment" size="mini" /></template>
          </el-table-column>
          <el-table-column label="Java属性" min-width="110">
            <template slot-scope="{ row }"><el-input v-model="row.javaField" size="mini" /></template>
          </el-table-column>
          <el-table-column label="Java类型" width="140">
            <template slot-scope="{ row }">
              <el-select v-model="row.javaType" size="mini">
                <el-option v-for="t in javaTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="查询" width="60" align="center">
            <template slot-scope="{ row }"><el-switch v-model="row.isQuery" :active-value="1" :inactive-value="0" /></template>
          </el-table-column>
          <el-table-column label="方式" width="95">
            <template slot-scope="{ row }">
              <el-select v-model="row.queryType" size="mini">
                <el-option label="EQ" value="EQ" /><el-option label="LIKE" value="LIKE" />
                <el-option label="GE" value="GE" /><el-option label="LE" value="LE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="列表" width="60" align="center">
            <template slot-scope="{ row }"><el-switch v-model="row.isList" :active-value="1" :inactive-value="0" /></template>
          </el-table-column>
          <el-table-column label="表单" width="60" align="center">
            <template slot-scope="{ row }"><el-switch v-model="row.isForm" :active-value="1" :inactive-value="0" /></template>
          </el-table-column>
          <el-table-column label="排序" width="85">
            <template slot-scope="{ row }"><el-input-number v-model="row.sort" size="mini" :min="1" style="width: 100%" controls-position="right" /></template>
          </el-table-column>
        </el-table>
      </template>

      <div slot="footer">
        <el-button @click="importVisible = false">取 消</el-button>
        <el-button :loading="parsing" @click="handleParse">重 新 解 析</el-button>
        <el-button type="primary" :loading="saving" :disabled="!parsed" @click="handleSaveImport">保 存</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：字段配置 -->
    <el-dialog
      :title="`字段配置 - ${currentTable.tableName}（${currentTable.tableComment || ''}）`"
      :visible.sync="columnVisible"
      width="1480px"
      top="3vh"
    >
      <el-form :model="currentTable" label-width="100px" inline>
        <el-form-item label="所属系统">
          <el-select
            v-model="currentTable.projectId"
            clearable
            placeholder="未绑定（回落全局默认）"
            style="width: 200px"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="p.projectName + '（' + p.projectCode + '）'"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="前端模块名"><el-input v-model="currentTable.moduleName" style="width: 160px" /></el-form-item>
        <el-form-item label="Java 包名">
          <el-input
            v-model="currentTable.packageName"
            style="width: 200px"
            :placeholder="projectPlaceholder('packageName')"
          />
        </el-form-item>
        <el-form-item label="网关前缀">
          <el-input
            v-model="currentTable.gatewayPrefix"
            style="width: 100px"
            :placeholder="projectPlaceholder('gatewayPrefix')"
          />
        </el-form-item>
        <el-form-item label="后端根目录">
          <el-input v-model="currentTable.backendRoot" style="width: 280px" :placeholder="rootPlaceholder('backendRoot')" />
        </el-form-item>
        <el-form-item label="前端根目录">
          <el-input v-model="currentTable.frontendRoot" style="width: 280px" :placeholder="rootPlaceholder('frontendRoot')" />
        </el-form-item>
      </el-form>
      <div class="form-tip">
        V3.0 起配置分三层回落：本表字段 → 所属系统 → 全局默认（application.yml 的 gen.output：后端 {{ outputConfig.backendRoot || '未配置' }}，前端 {{ outputConfig.frontendRoot || '未配置' }}）。
        只有本表要偏离所属系统时才需要单独填，留空即继承。
      </div>

      <el-divider content-position="left">目标模块与表结构形态（V1.4）</el-divider>
      <el-form :model="currentTable" label-width="105px" inline>
        <el-form-item label="目标模块">
          <el-select
            v-model="currentTable.targetModule"
            clearable
            placeholder="默认模块"
            style="width: 150px"
            @change="onTargetModuleChange"
          >
            <el-option v-for="m in moduleOptions" :key="m.value" :label="m.label" :value="m.value">
              <span>{{ m.label }}</span>
              <span class="opt-sub">{{ m.packageName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="树形父字段">
          <el-select v-model="currentTable.treeParentColumn" clearable placeholder="非树形表" style="width: 150px">
            <el-option v-for="c in treeCandidates" :key="c.columnName" :label="c.columnName" :value="c.columnName" />
          </el-select>
        </el-form-item>
        <el-form-item label="树形展示字段">
          <el-select
            v-model="currentTable.treeLabelColumn"
            clearable
            placeholder="默认首列"
            style="width: 150px"
            :disabled="!currentTable.treeParentColumn"
          >
            <el-option v-for="c in currentColumns" :key="c.columnName" :label="c.columnName" :value="c.columnName" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属主表">
          <el-select
            v-model="currentTable.masterTableId"
            clearable
            filterable
            placeholder="独立表"
            style="width: 190px"
            @change="onMasterChange"
          >
            <el-option
              v-for="m in masterOptions"
              :key="m.id"
              :label="`${m.tableName}（${m.tableComment || ''}）`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主表外键">
          <el-input
            v-model="currentTable.masterForeignKey"
            style="width: 140px"
            placeholder="order_id"
            :disabled="!currentTable.masterTableId"
          />
        </el-form-item>
        <el-form-item label="删除级联">
          <el-select
            v-model="currentTable.deleteCascadeMode"
            clearable
            placeholder="未配置（跟随系统默认）"
            style="width: 190px"
            :disabled="!!currentTable.masterTableId"
          >
            <el-option label="级联（同步删除全部明细）" :value="1" />
            <el-option label="阻止（有明细时拒删）" :value="0" />
            <el-option label="不处理（仅删主表）" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="打印单据">
          <el-select
            v-model="currentTable.genPrint"
            clearable
            placeholder="跟随系统默认（默认关）"
            style="width: 190px"
            :disabled="!!currentTable.masterTableId"
          >
            <el-option label="生成 A4 打印页" :value="1" />
            <el-option label="不生成" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="form-tip">
        目标模块命中 application.yml 的 gen.output.modules 时，后端路径/包名/网关前缀自动切成该模块；树形父字段填 parent_id 后生成 tree 接口与树表格；
        所属主表填了就是从表，保存走同事务的 save-with-children，主表页面会出现明细子表。
        删除级联只对主表生效：从表上不可配；未配置时跟随系统默认（sys_project.default_delete_cascade_mode，再默认级联）。
        打印单据开启后产物多一张 A4 打印页（print.vue）与列表「打印」按钮，需开前端与详情接口。
      </div>

      <el-divider content-position="left">生成开关矩阵</el-divider>
      <div class="preset-bar">
        <span class="preset-label">快捷预设</span>
        <el-button size="mini" @click="applyPreset('backend-only')">仅后端 API</el-button>
        <el-button size="mini" @click="applyPreset('standard')">标准 CRUD</el-button>
        <el-button size="mini" @click="applyPreset('readonly')">只读列表</el-button>
        <el-button size="mini" :loading="resettingSwitches" @click="resetSwitchesToProject">
          恢复系统默认
        </el-button>
        <span class="inline-tip">预设只是快速勾选，仍可手动微调后再保存</span>
      </div>
      <div class="switch-grid">
        <div v-for="sw in switchDefs" :key="sw.key" class="switch-item">
          <span class="switch-label">
            {{ sw.label }}
            <em
              v-if="isProjectDefault(sw.key)"
              class="switch-default"
              title="与所属系统默认值一致"
            >默认</em>
          </span>
          <el-switch v-model="currentTable[sw.key]" :active-value="1" :inactive-value="0" />
        </div>
      </div>
      <div v-if="!currentTable.projectId" class="form-tip">
        当前表未绑定系统，开关只能逐张手动调。绑定系统后，新表会按系统默认值落地。
      </div>

      <el-divider content-position="left">字段列表</el-divider>
      <el-table :data="currentColumns" border size="mini" max-height="300">
        <el-table-column prop="columnName" label="列名" min-width="110" />
        <el-table-column prop="columnComment" label="注释" min-width="140" />
        <el-table-column label="Java属性" min-width="110">
          <template slot-scope="{ row }"><el-input v-model="row.javaField" size="mini" /></template>
        </el-table-column>
        <el-table-column label="Java类型" width="140">
          <template slot-scope="{ row }">
            <el-select v-model="row.javaType" size="mini">
              <el-option v-for="t in javaTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="查询" width="60" align="center">
          <template slot-scope="{ row }"><el-switch v-model="row.isQuery" :active-value="1" :inactive-value="0" /></template>
        </el-table-column>
        <el-table-column label="方式" width="95">
          <template slot-scope="{ row }">
            <el-select v-model="row.queryType" size="mini">
              <el-option label="EQ" value="EQ" /><el-option label="LIKE" value="LIKE" />
              <el-option label="GE" value="GE" /><el-option label="LE" value="LE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="列表" width="60" align="center">
          <template slot-scope="{ row }"><el-switch v-model="row.isList" :active-value="1" :inactive-value="0" /></template>
        </el-table-column>
        <el-table-column label="表单" width="60" align="center">
          <template slot-scope="{ row }"><el-switch v-model="row.isForm" :active-value="1" :inactive-value="0" /></template>
        </el-table-column>
        <el-table-column label="控件" width="120">
          <template slot-scope="{ row }">
            <el-select v-model="row.htmlType" size="mini" @change="onHtmlTypeChange(row)">
              <el-option v-for="h in htmlTypes" :key="h.value" :label="h.label" :value="h.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="字典编码" width="180">
          <template slot-scope="{ row }">
            <el-select
              v-model="row.dictType"
              size="mini"
              filterable
              clearable
              placeholder="选字典"
              :disabled="row.htmlType !== 'select' && row.htmlType !== 'radio'"
            >
              <el-option
                v-for="d in dictTypes"
                :key="d.dictCode"
                :label="`${d.dictCode}（${d.dictName}）`"
                :value="d.dictCode"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="校验规则" width="190">
          <template slot-scope="{ row }">
            <el-input v-model="row.validateRule" size="mini" placeholder="required,maxlen=50" />
          </template>
        </el-table-column>
        <el-table-column label="关联表" width="160">
          <template slot-scope="{ row }">
            <el-select
              v-model="row.refTable"
              size="mini"
              filterable
              allow-create
              clearable
              default-first-option
              placeholder="普通字段"
              @change="onRefTableChange(row)"
            >
              <el-option v-for="t in masterCandidates" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="展示字段" width="130">
          <template slot-scope="{ row }">
            <el-input v-model="row.refLabel" size="mini" placeholder="name" :disabled="!row.refTable" />
          </template>
        </el-table-column>
        <el-table-column label="Excel导出" width="80" align="center">
          <template slot-scope="{ row }">
            <el-switch v-model="row.excelExport" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="Excel导入" width="80" align="center">
          <template slot-scope="{ row }">
            <el-switch v-model="row.excelImport" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="85">
          <template slot-scope="{ row }"><el-input-number v-model="row.sort" size="mini" :min="1" style="width: 100%" controls-position="right" /></template>
        </el-table-column>
      </el-table>
      <div class="form-tip">
        控件：input/textarea/number/select/radio/switch/date/datetime。校验规则逗号分隔：required、maxlen=50、min=1、max=9999、email、phone、url、regex=^\d+$，
        会同时生成后端 @NotBlank/@Size/@Min 等注解与前端 el-form rules。select/radio 的字典编码从下拉选（字典不足先去「系统管理 → 字典管理」补），不再手打。
        关联表填了会生成远端搜索下拉（el-select remote），值存 id、展示取「展示字段」；Excel导出/导入控制该列是否进导入模板与导出文件。
      </div>

      <div slot="footer">
        <el-button @click="columnVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveColumns">保 存</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：生成自检（V2.0 拆到 GenCheckDialog，纯展示） -->
    <gen-check-dialog :visible.sync="checkVisible" :result="checkResult" />

    <!-- 弹窗：P2-3 字典自动识别 -->
    <dict-detect-dialog ref="dictDetect" @applied="onDictApplied" />

    <!-- 弹窗：P3-2 多对多识别 -->
    <relation-dialog ref="relation" @applied="fetchList" />

    <!-- 弹窗：注册菜单 -->
    <el-dialog title="注册菜单与按钮权限" :visible.sync="menuVisible" width="820px" top="6vh">
      <el-form label-width="120px" size="small">
        <el-form-item label="目标表">
          <span>{{ menuTargetNames }}</span>
        </el-form-item>
        <el-form-item label="父菜单 ID">
          <el-input-number v-model="menuForm.parentId" :min="0" controls-position="right" style="width: 140px" />
          <span class="inline-tip">0 = 一级菜单</span>
        </el-form-item>
        <el-form-item label="授权角色 ID">
          <el-input v-model="menuForm.roleId" placeholder="留空自动取管理员角色（兜底 1）" style="width: 260px" />
        </el-form-item>
        <el-form-item label="同步前端路由">
          <el-switch v-model="menuForm.syncRoute" />
          <span class="inline-tip">写入 src/router/gen-routes.js，侧边栏即可见</span>
        </el-form-item>
      </el-form>
      <div class="form-tip">
        每张表注册 1 条菜单（type=2，路径 /模块/list）+ 查询/新增/修改/删除 4 个按钮（type=3），并写入 sys_role_menu 授权；已注册的表会自动跳过。
      </div>
      <div v-if="menuResult" class="mt12">
        <div v-for="m in menuResult.menus" :key="m.tableName" class="res-row">
          <span class="pill" :class="resultClass(m.status)">{{ m.status }}</span>
          <span>{{ m.tableName }}</span>
          <span class="res-msg">{{ m.path }} · {{ m.message }}</span>
        </div>
        <div v-if="menuResult.message" class="form-tip">{{ menuResult.message }}</div>
        <div v-if="menuResult.routeFile" class="form-tip">路由文件：{{ menuResult.routeFile }}</div>
      </div>
      <div slot="footer">
        <el-button @click="menuVisible = false">取 消</el-button>
        <el-button :loading="registering" @click="handleRegisterMenu(true)">预 览</el-button>
        <el-button type="primary" :loading="registering" @click="handleRegisterMenu(false)">注 册</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：一键生成 -->
    <el-dialog title="一键生成" :visible.sync="applyVisible" width="920px" top="5vh">
      <el-form label-width="130px" size="small">
        <el-form-item label="目标表">
          <span>{{ applyTargetNames }}</span>
        </el-form-item>
        <el-form-item label="覆盖已有文件">
          <el-switch v-model="applyForm.overwrite" />
          <span class="inline-tip">覆盖前自动备份到各仓库的 .gen-backup/&lt;批次号&gt;/</span>
        </el-form-item>
        <el-form-item label="注册菜单">
          <el-switch v-model="applyForm.registerMenu" />
          <span class="inline-tip">写入 sys_menu + 4 个按钮权限并授权管理员</span>
        </el-form-item>
        <el-form-item label="重建前端路由">
          <el-switch v-model="applyForm.syncRoute" />
        </el-form-item>
        <el-form-item label="写盘后编译">
          <el-switch v-model="applyForm.compile" />
          <span class="inline-tip">执行 mvn compile，10~60 秒；不勾也能生成，只是不验编译</span>
        </el-form-item>
      </el-form>
      <div class="form-tip">
        顺序固定：自检 → 备份 → 写盘 →（编译）→ 注册菜单 → 重建路由。自检不通过会直接中止，一个文件都不会写。
        顺序不能颠倒：路由指向的 views/&lt;模块&gt;/index.vue 必须先落盘，否则点菜单 404。
      </div>

      <div v-if="applyResult" class="mt12">
        <el-alert
          :type="applyResult.passed ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="`${applyResult.passed ? '一键生成完成' : '有阶段失败'}：批次 ${applyResult.batchNo}，写入 ${applyResult.written}，跳过 ${applyResult.skipped}，失败 ${applyResult.failed}，耗时 ${applyResult.elapsedMs} ms`"
        />
        <el-table :data="applyResult.stages" size="mini" border class="mt12">
          <el-table-column prop="stage" label="阶段" width="90" />
          <el-table-column label="结果" width="90">
            <template slot-scope="{ row }">
              <span class="pill" :class="stageClass(row.status)">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="300" show-overflow-tooltip />
          <el-table-column prop="elapsedMs" label="耗时" width="90" align="right">
            <template slot-scope="{ row }">{{ row.elapsedMs }}ms</template>
          </el-table-column>
        </el-table>
        <div v-if="applyDetails.length" class="apply-details">
          <div v-for="(d, i) in applyDetails" :key="i" class="detail-line">{{ d }}</div>
        </div>
        <div v-if="applyResult.backupDir" class="form-tip">备份目录：{{ applyResult.backupDir }}</div>
        <el-button
          v-if="applyResult.written"
          size="mini"
          type="danger"
          plain
          icon="el-icon-refresh-left"
          :loading="rollingBack"
          class="mt12"
          @click="handleRollback(applyResult.batchNo)"
        >
          回滚这一批
        </el-button>
      </div>

      <div slot="footer">
        <el-button @click="applyVisible = false">关 闭</el-button>
        <el-button type="primary" :loading="applying" @click="handleApply">执 行</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：批量设置 -->
    <el-dialog title="批量设置" :visible.sync="batchVisible" width="720px">
      <el-form label-width="130px" size="small">
        <el-form-item label="目标表">
          <span>{{ applyTargetNames }}</span>
        </el-form-item>
        <el-form-item label="开关预设">
          <el-radio-group v-model="batchForm.preset">
            <el-radio label="">不修改</el-radio>
            <el-radio label="backend-only">仅后端 API</el-radio>
            <el-radio label="standard">标准 CRUD</el-radio>
            <el-radio label="readonly">只读列表</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属系统">
          <el-select v-model="batchForm.projectId" placeholder="不修改（保持原系统）" clearable filterable style="width: 320px">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectName}（${p.projectCode}）`" :value="p.id" />
          </el-select>
          <span class="inline-tip">先把旧系统下导入的表转移到新系统时用</span>
        </el-form-item>
        <el-form-item label="前端模块名">
          <el-input v-model="batchForm.moduleName" placeholder="留空不修改，如 demo_goods" style="width: 320px" />
        </el-form-item>
        <el-form-item label="Java 包名">
          <el-input v-model="batchForm.packageName" placeholder="留空不修改" style="width: 320px" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="batchForm.author" placeholder="留空不修改" style="width: 320px" />
        </el-form-item>
        <el-form-item label="后端根目录">
          <el-input v-model="batchForm.backendRoot" :placeholder="rootPlaceholder('backendRoot')" style="width: 400px" />
        </el-form-item>
        <el-form-item label="前端根目录">
          <el-input v-model="batchForm.frontendRoot" :placeholder="rootPlaceholder('frontendRoot')" style="width: 400px" />
        </el-form-item>
        <el-form-item label="网关前缀">
          <el-input v-model="batchForm.gatewayPrefix" placeholder="留空不修改，如 admin" style="width: 200px" />
        </el-form-item>
      </el-form>
      <div class="form-tip">留空的项不会被修改。批量设置只改配置，不触发生成；改所属系统会影响包名/输出路径的继承来源。</div>
      <div slot="footer">
        <el-button @click="batchVisible = false">取 消</el-button>
        <el-button type="primary" :loading="batching" @click="handleBatchUpdate">保 存</el-button>
      </div>
    </el-dialog>

    <!-- 弹窗：ZIP 文件清单 -->
    <el-dialog title="生成成功 - gen_code.zip（文件清单）" :visible.sync="filesVisible" width="680px">
      <ul class="file-list">
        <li v-for="f in genFiles" :key="f">{{ f }}</li>
      </ul>
      <div class="form-tip">ZIP 已开始下载；解压后将文件按相对路径合并到对应仓库，路由片段（router-snippet.txt）需手动追加。</div>
      <div slot="footer">
        <el-button type="primary" @click="filesVisible = false">知 道 了</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  parseDdl, getGenTablePage, getGenTableDetail, createGenTable,
  updateGenTable, deleteGenTable, downloadGenCode,
  listDbSchemas, listDbTables, importDbTables, previewGenCode, writeGenCode,
  getGenOutputConfig, selfCheckGenCode, registerGenMenu,
  applyGenCode, rollbackGenBatch, batchUpdateGenTables, batchDeleteGenTables,
  syncGenColumns, listProjectOptions, resetGenSwitchDefaults,
  aiRecommendTables, aiRecommendTablesApply
} from '@/api/gen'
import { listDictTypes } from '@/api/dict'
import GenDiffDialog from './GenDiffDialog.vue'
import GenCheckDialog from './GenCheckDialog.vue'
import DictDetectDialog from './DictDetectDialog.vue'
import RelationDialog from './RelationDialog.vue'
import AiRecommendDialog from '../components/AiRecommendDialog.vue'

// V3.0：包名 / 网关前缀 / 输出路径不再硬编码具体系统。
// 原先写死 'com.shop.admin' 在多系统下会把新表填进旧系统的包，
// 用户得逐字段改回来。现在留空 = 继承所属系统，只在需要偏离时才填。
const defaultTableConfig = () => ({
  id: undefined,
  tableName: '',
  tableComment: '',
  className: '',
  moduleName: '',
  packageName: '',
  backendPath: '',
  backendRoot: '',
  frontendRoot: '',
  gatewayPrefix: '',
  // 所属系统：新建时由调用方从 projectOptions 里取默认系统填入
  projectId: undefined,
  genEntity: 1, genMapper: 1, genService: 1, genController: 1, genLog: 1,
  genPageApi: 1, genDetailApi: 1, genAddApi: 1, genEditApi: 1, genDeleteApi: 1,
  genFrontend: 1, genApiJs: 1, genVuePage: 1,
  genQueryForm: 1, genAddBtn: 1, genEditBtn: 1, genDeleteBtn: 1,
  // V1.4：目标模块 / 树形表 / 主子表
  targetModule: '',
  treeParentColumn: '',
  treeLabelColumn: '',
  masterTableId: null,
  masterForeignKey: '',
  deleteCascadeMode: null,
  // V4.5 打印单据：null=跟随系统默认（默认关）
  genPrint: null,
  columns: []
})

export default {
  name: 'SystemGen',
  components: { GenDiffDialog, GenCheckDialog, DictDetectDialog, RelationDialog, AiRecommendDialog },
  data() {
    return {
      loading: false,
      generating: false,
      previewing: false,
      writing: false,
      saving: false,
      parsing: false,
      importing: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10, tableName: '', tableComment: '', genStatus: '', projectId: undefined },
      // V3.0 系统下拉：表列表按系统筛选，新建表时预选默认系统
      projectOptions: [],
      selected: [],
      // V7.3 P0-3 表级 AI 推荐：token 由服务端签发，应用时原样回传
      aiRecVisible: false,
      aiRecLoading: false,
      aiRecApplyLoading: false,
      aiRecExists: false,
      aiRecToken: '',
      aiRecData: { tables: [], menus: [], drops: [] },
      // 数据库导入
      dbVisible: false,
      dbSchema: 'shop',
      schemas: [],
      dbTables: [],
      dbFilter: '',
      // 导入后归属的系统：批量导入的一批表通常属于同一个系统，在这里统一指定
      dbProjectId: undefined,
      dbLoading: false,
      dbSelection: [],
      importResults: [],
      existingTables: [],
      // 预览 + 写盘
      previewVisible: false,
      previewTree: [],
      currentPath: '',
      currentContent: '',
      writeOverwrite: false,
      writeResults: [],
      previewIds: [],
      // DDL 导入
      importVisible: false,
      ddlText: '',
      parsed: null,
      // 字段配置
      columnVisible: false,
      currentTable: defaultTableConfig(),
      currentColumns: [],
      // ZIP 清单
      filesVisible: false,
      genFiles: [],
      // 生成自检
      checking: false,
      checkVisible: false,
      checkResult: null,
      // 菜单注册
      registering: false,
      menuVisible: false,
      menuResult: null,
      menuTargets: [],
      menuForm: { parentId: 0, roleId: '', syncRoute: true },
      // 字典类型（字段配置的字典下拉数据源）
      dictTypes: [],
      // V1.2 一键生成
      applying: false,
      applyVisible: false,
      applyResult: null,
      applyTargets: [],
      applyForm: { overwrite: true, registerMenu: true, syncRoute: true, compile: false },
      rollingBack: false,
      // V1.2 批量设置
      batchVisible: false,
      batching: false,
      batchForm: {
        preset: '', moduleName: '', packageName: '', author: '',
        backendRoot: '', frontendRoot: '', gatewayPrefix: '',
        // '' = 不修改；提交前转成 null，后端 DTO 才能区分「不改」与「改」
        projectId: ''
      },
      // 生成输出全局配置（gen.output.*）
      outputConfig: {},
      // 所属系统的 17 个开关默认值（详情接口带回），用于「默认」标记与恢复按钮
      projectGenDefaults: {},
      resettingSwitches: false,
      // V1.3 diff 预览
      diffVisible: false,
      // V1.3 结构同步
      syncing: false,
      // V1.4 主子表：可选主表清单
      masterCandidates: [],
      javaTypes: ['String', 'Integer', 'Long', 'BigDecimal', 'Double', 'Boolean', 'LocalDateTime', 'LocalDate'],
      htmlTypes: [
        { value: 'input', label: '文本框' },
        { value: 'textarea', label: '多行文本' },
        { value: 'number', label: '数字' },
        { value: 'select', label: '下拉' },
        { value: 'radio', label: '单选' },
        { value: 'switch', label: '开关' },
        { value: 'date', label: '日期' },
        { value: 'datetime', label: '日期时间' },
        { value: 'image', label: '图片上传' },
        { value: 'file', label: '文件上传' }
      ],
      switchDefs: [
        { key: 'genEntity', label: '生成 entity' },
        { key: 'genMapper', label: '生成 mapper' },
        { key: 'genService', label: '生成 service' },
        { key: 'genController', label: '生成 controller' },
        { key: 'genLog', label: '操作日志' },
        { key: 'genPageApi', label: '分页接口' },
        { key: 'genDetailApi', label: '详情接口' },
        { key: 'genAddApi', label: '新增接口' },
        { key: 'genEditApi', label: '修改接口' },
        { key: 'genDeleteApi', label: '删除接口' },
        { key: 'genFrontend', label: '生成前端' },
        { key: 'genApiJs', label: 'api.js' },
        { key: 'genVuePage', label: 'vue 页面' },
        { key: 'genQueryForm', label: '查询区' },
        { key: 'genAddBtn', label: '新增按钮' },
        { key: 'genEditBtn', label: '编辑按钮' },
        { key: 'genDeleteBtn', label: '删除按钮' }
      ]
    }
  },
  computed: {
    filteredDbTables() {
      const kw = (this.dbFilter || '').trim().toLowerCase()
      if (!kw) return this.dbTables
      return this.dbTables.filter(t => t.name.toLowerCase().includes(kw))
    },
    menuTargetNames() {
      return this.menuTargets.map(t => `${t.tableName}（${t.tableComment || ''}）`).join('，') || '未选择'
    },
    applyTargetNames() {
      const targets = this.applyTargets.length ? this.applyTargets : this.selected
      return targets.map(t => t.tableName).join('，') || '未选择'
    },
    // 一键生成里各阶段的明细，摊平成一段文本方便查看
    applyDetails() {
      if (!this.applyResult || !this.applyResult.stages) return []
      const lines = []
      this.applyResult.stages.forEach(s => {
        (s.details || []).forEach(d => lines.push(`[${s.stage}] ${d}`))
      })
      return lines
    },
    // V1.4-3 目标模块下拉（来自 application.yml 的 gen.output.modules）
    moduleOptions() {
      const modules = (this.outputConfig && this.outputConfig.modules) || {}
      return Object.keys(modules).map(key => ({
        value: key,
        label: key,
        backendPath: modules[key].backendPath,
        packageName: modules[key].packageName,
        gatewayPrefix: modules[key].gatewayPrefix
      }))
    },
    // V1.4-1 主表下拉：排除自己，避免自引用
    masterOptions() {
      const selfId = this.currentTable && this.currentTable.id
      return this.masterCandidates.filter(t => String(t.id) !== String(selfId))
    },
    // V1.4-2 树形父字段候选：整数/长整型字段
    treeCandidates() {
      return (this.currentColumns || []).filter(c =>
        ['Long', 'Integer'].includes(c.javaType) && c.isPk !== 1)
    },
    // 差异预览弹窗要对比的表：当前勾选行
    diffTableIds() {
      return (this.selected || []).map(r => r.id)
    }
  },
  created() {
    this.fetchList()
    this.loadOutputConfig()
    this.loadDictTypes()
    this.loadProjectOptions()
  },
  methods: {
    /**
     * 「更多」下拉的派发。
     *
     * 这几个动作都保留了各自的 loading 状态（previewing / registering / generating），
     * 所以不能用 el-dropdown-item 的 disabled 简单禁用；统一在这里先判一次
     * 是否正在进行，避免用户连点两次弹出两个弹窗。
     */
    onMoreCommand(cmd) {
      switch (cmd) {
        case 'preview':
          if (!this.previewing) this.handlePreview()
          break
        case 'diff':
          this.openDiffDialog()
          break
        case 'menu':
          if (!this.registering) this.openMenuDialog()
          break
        case 'relation':
          this.openRelationDialog()
          break
        case 'generate':
          if (!this.generating) this.handleGenerate()
          break
        default:
          break
      }
    },
    async loadOutputConfig() {
      try {
        const res = await getGenOutputConfig()
        this.outputConfig = res.data || {}
      } catch (e) { /* 拦截器已提示 */ }
    },
    rootPlaceholder(key) {
      const v = this.outputConfig[key]
      return v ? `留空=全局：${v}` : '留空=全局配置（application.yml 的 gen.output）'
    },
    /**
     * 新建表时的预选系统：取默认系统，没有默认就取第一个启用的。
     *
     * <p>不预选的话，用户忘了选就会落成「未绑定」，生成时静默回落到全局配置，
     * 产物写到了想不到的仓库里。宁可替用户选中，让他能看见并改掉。
     */
    defaultProjectId() {
      const d = this.projectOptions.find(p => p.isDefault === 1)
      if (d) return d.id
      return this.projectOptions.length ? this.projectOptions[0].id : undefined
    },
    /**
     * 包名/网关前缀的占位提示：提示的是所属系统会给出什么值，
     * 而不是全局值——因为这块最常被误填，用户需要看到「留空会继承到哪」。
     */
    projectPlaceholder(key) {
      const p = this.projectOptions.find(o => String(o.id) === String(this.currentTable.projectId))
      if (p && p[key]) return `留空=系统：${p[key]}`
      return '留空=跟随所属系统'
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await getGenTablePage(this.query)
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
      this.query = { page: 1, size: this.query.size, tableName: '', tableComment: '', genStatus: '' }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    handleSelectionChange(rows) {
      this.selected = rows
    },
    clearSelection() {
      this.$refs.table && this.$refs.table.clearSelection()
      this.selected = []
    },
    async loadDictTypes() {
      try {
        const res = await listDictTypes()
        this.dictTypes = res.data || []
      } catch (e) {
        this.dictTypes = []
      }
    },
    /**
     * V3.0 系统下拉。系统数量在几十量级，一次拉全量给筛选和弹窗共用，
     * 不做分页也不在切 tab 时重拉。
     */
    async loadProjectOptions() {
      try {
        const res = await listProjectOptions()
        this.projectOptions = res.data || []
      } catch (e) {
        this.projectOptions = []
      }
    },

    /* ---------- V1.2 一键生成 ---------- */
    openApplyDialog(row) {
      const targets = row ? [row] : this.selected
      if (!targets.length) {
        this.$message.warning('请先勾选要生成的表')
        return
      }
      this.applyTargets = targets
      this.applyResult = null
      this.applyVisible = true
    },
    async handleApply() {
      if (!this.applyTargets.length) return
      this.applying = true
      try {
        const res = await applyGenCode({
          ids: this.applyTargets.map(t => t.id),
          overwrite: this.applyForm.overwrite,
          registerMenu: this.applyForm.registerMenu,
          syncRoute: this.applyForm.syncRoute,
          compile: this.applyForm.compile
        })
        this.applyResult = res.data || null
        if (this.applyResult) {
          if (this.applyResult.passed) {
            this.$message.success(`一键生成完成（${this.applyResult.elapsedMs} ms）`)
          } else {
            const failed = (this.applyResult.stages || []).filter(s => s.status === 'FAILED')
            this.$message.error(`阶段失败：${failed.map(s => s.stage).join('、')}`)
          }
          // V4.2 台账失败不静默：文件落盘了但 gen_output 没记上账
          if (this.applyResult.ledgerErrors > 0) {
            this.$message.warning(`${this.applyResult.ledgerErrors} 条台账记录失败，按批次回滚前需人工核对`)
          }
          this.fetchList()
        }
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.applying = false
      }
    },
    async handleRollback(batchNo) {
      try {
        await this.$confirm(`回滚批次 ${batchNo}？有备份的文件会还原，首次生成的文件会被删除。`, '回滚确认', { type: 'warning' })
      } catch (e) {
        return
      }
      this.rollingBack = true
      try {
        const res = await rollbackGenBatch(batchNo)
        const d = res.data || {}
        this.$message.success(`回滚完成：还原 ${d.restored || 0}，删除 ${d.removed || 0}`)
        if (d.problems && d.problems.length) {
          this.$message.warning(`${d.problems.length} 个文件回滚失败，见控制台`)
          console.warn('回滚失败文件：', d.problems)
        }
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.rollingBack = false
      }
    },
    stageClass(status) {
      if (status === 'PASS') return 'ok'
      if (status === 'SKIPPED') return 'skip'
      return 'err'
    },

    /* ---------- V1.2 批量设置 ---------- */
    openBatchDialog() {
      if (!this.selected.length) {
        this.$message.warning('请先勾选要批量设置的表')
        return
      }
      // 下拉里是转移目标系统，必须是最新的：否则刚建的财务系统不出现
      this.loadProjectOptions()
      this.batchForm = {
        preset: '', moduleName: '', packageName: '', author: '',
        backendRoot: '', frontendRoot: '', gatewayPrefix: '',
        projectId: ''
      }
      this.batchVisible = true
    },
    async handleBatchUpdate() {
      this.batching = true
      try {
        const res = await batchUpdateGenTables({
          ids: this.selected.map(t => t.id),
          ...this.batchForm,
          // 空串→null：后端按「null=不修改」处理，空串会被当成转移目标报错
          projectId: this.batchForm.projectId || null
        })
        this.$message.success(`已更新 ${res.data || 0} 张表的配置`)
        this.batchVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.batching = false
      }
    },
    async handleBatchDelete() {
      if (!this.selected.length) return
      try {
        await this.$confirm(
          `确定删除这 ${this.selected.length} 张表的配置吗？已注册的菜单会一并清理，代码文件不会删除。`,
          '批量删除', { type: 'warning' }
        )
      } catch (e) {
        return
      }
      try {
        const res = await batchDeleteGenTables(this.selected.map(t => t.id))
        this.$message.success(`已删除 ${res.data || 0} 张表的配置`)
        this.selected = []
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ }
    },
    // 字段配置弹窗里的开关预设（只改本地状态，保存时才落库）
    applyPreset(preset) {
      const readonly = preset === 'readonly'
      const backendOnly = preset === 'backend-only'
      Object.assign(this.currentTable, {
        genEntity: 1, genMapper: 1, genService: 1, genController: 1,
        genPageApi: 1, genDetailApi: 1,
        genAddApi: readonly ? 0 : 1,
        genEditApi: readonly ? 0 : 1,
        genDeleteApi: readonly ? 0 : 1,
        genFrontend: backendOnly ? 0 : 1,
        genApiJs: backendOnly ? 0 : 1,
        genVuePage: backendOnly ? 0 : 1,
        genQueryForm: backendOnly ? 0 : 1,
        genAddBtn: readonly || backendOnly ? 0 : 1,
        genEditBtn: readonly || backendOnly ? 0 : 1,
        genDeleteBtn: readonly || backendOnly ? 0 : 1
      })
      this.$message.success('已套用预设，记得点保存')
    },

    // 当前值是否等于所属系统默认值。只是「看起来一致」，不代表一定是继承来的——
    // 用户手动调成一样的值也会亮这个标，所以文案用「默认」而不是「继承」
    isProjectDefault(key) {
      const defaults = this.projectGenDefaults || {}
      const def = defaults[key]
      if (def === undefined || def === null) return false
      return Number(this.currentTable[key]) === Number(def)
    },

    // 恢复系统默认：只改开关，包名/路径/字段配置一律不动
    async resetSwitchesToProject() {
      if (!this.currentTable.id) {
        // 还没落库的新表：本地按详情里带回的默认值填，等保存时一起提交
        const defaults = this.projectGenDefaults || {}
        const patch = {}
        this.switchDefs.forEach(sw => {
          const def = defaults[sw.key]
          if (def !== undefined && def !== null) patch[sw.key] = Number(def)
        })
        if (Object.keys(patch).length === 0) {
          this.$message.warning('所属系统没有配置默认值，无法恢复')
          return
        }
        Object.assign(this.currentTable, patch)
        this.$message.success('已按系统默认值重置开关，记得点保存')
        return
      }
      this.resettingSwitches = true
      try {
        const res = await resetGenSwitchDefaults(this.currentTable.id)
        const defaults = res.data || {}
        const patch = {}
        this.switchDefs.forEach(sw => {
          if (defaults[sw.key] !== undefined) patch[sw.key] = defaults[sw.key]
        })
        Object.assign(this.currentTable, patch)
        this.projectGenDefaults = defaults
        this.$message.success('开关已恢复为系统默认值')
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        this.resettingSwitches = false
      }
    },

    /* ---------- 数据库导入 ---------- */
    async openDbDialog() {
      this.dbVisible = true
      this.importResults = []
      // 先刷新系统列表再取默认：默认系统可能在系统管理页刚被改过，
      // 页面创建时拉的那份 options 是旧的，直接用会把表导进旧默认系统
      await this.loadProjectOptions()
      // 每次打开都重设：上次导到别的系统，这次不该沿用
      this.dbProjectId = this.defaultProjectId()
      if (!this.schemas.length) {
        try {
          const res = await listDbSchemas()
          this.schemas = res.data || []
        } catch (e) { /* 拦截器已提示 */ }
      }
      if (this.dbSchema && !this.dbTables.length) {
        this.loadDbTables()
      }
    },
    async loadDbTables() {
      if (!this.dbSchema) {
        this.$message.warning('请先选择数据库')
        return
      }
      this.dbLoading = true
      this.dbSelection = []
      try {
        const res = await listDbTables(this.dbSchema)
        this.dbTables = res.data || []
        // 已导入的表标注状态
        const page = await getGenTablePage({ page: 1, size: 500 })
        this.existingTables = (page.data.list || []).map(t => t.tableName)
      } catch (e) {
        this.dbTables = []
      } finally {
        this.dbLoading = false
      }
    },
    dbStatusClass(name) {
      if (this.importResults.find(r => r.tableName === name)) return 'pill ' + this.resultClass(this.importResults.find(r => r.tableName === name).status)
      return this.existingTables.includes(name) ? 'pill skip' : 'pill ok'
    },
    dbStatusText(name) {
      const r = this.importResults.find(r => r.tableName === name)
      if (r) return `${r.status}：${r.message}`
      return this.existingTables.includes(name) ? '已在配置中' : '待导入'
    },
    handleSelectAllDb() {
      this.filteredDbTables.forEach(row => this.$refs.dbTable.toggleRowSelection(row, true))
    },
    async handleImportDb() {
      if (!this.dbSelection.length) return
      this.importing = true
      try {
        const res = await importDbTables(this.dbSchema, this.dbSelection.map(r => r.name), this.dbProjectId)
        this.importResults = res.data || []
        const saved = this.importResults.filter(r => r.status === 'SAVED').length
        this.$message.success(`导入完成：成功 ${saved}，跳过 ${this.importResults.filter(r => r.status === 'EXISTS').length}`)
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.importing = false
      }
    },

    /* ---------- 预览 + 写盘 ---------- */
    buildTree(files) {
      const root = { children: {} }
      for (const f of files) {
        const parts = f.path.split('/')
        let node = root
        for (let i = 0; i < parts.length; i++) {
          const isLeaf = i === parts.length - 1
          const key = parts[i]
          if (!node.children[key]) {
            node.children[key] = isLeaf
              ? { label: key, path: f.path, content: f.content, tableName: f.tableName }
              : { label: key, children: {} }
          }
          node = node.children[key]
        }
      }
      const toNodes = (container) => Object.values(container.children)
        .map(n => n.children ? { label: n.label, children: toNodes(n) } : n)
        .sort((a, b) => (a.children ? 0 : 1) - (b.children ? 0 : 1) || a.label.localeCompare(b.label))
      return toNodes(root)
    },
    async doPreview(ids) {
      this.previewing = true
      this.writeResults = []
      this.currentPath = ''
      this.currentContent = ''
      this.previewIds = ids
      try {
        const res = await previewGenCode(ids)
        const files = res.data || []
        //if (!files.length) {
        //  this.$message.warning('没有可生成的文件')
        //  return
      // }
        this.previewTree = this.buildTree(files)
        this.previewVisible = true
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.previewing = false
      }
    },
    handlePreview() {
      if (!this.selected.length) {
        this.$message.warning('请先勾选要生成的表')
        return
      }
      this.doPreview(this.selected.map(r => r.id))
    },
    handlePreviewOne(row) {
      this.doPreview([row.id])
    },
    handlePreviewClick(node) {
      if (node.content !== undefined) {
        this.currentPath = node.path
        this.currentContent = node.content
      }
    },
    async handleWrite() {
      this.writing = true
      try {
        const res = await writeGenCode(this.previewIds, this.writeOverwrite)
        // V4.2 台账合一：返回体包一层 { batchNo, ledgerErrors, files }
        const data = res.data || {}
        this.writeResults = data.files || []
        const written = this.writeResults.filter(r => r.status === 'WRITTEN').length
        const skipped = this.writeResults.filter(r => r.status === 'SKIPPED').length
        const errors = this.writeResults.filter(r => r.status === 'ERROR').length
        const ledgerErrors = data.ledgerErrors || 0
        if (ledgerErrors > 0) {
          // 台账写失败不静默：文件落盘了但没记上账，回滚时这些文件会缺失
          this.$message.warning(`写盘完成：写入 ${written}，跳过 ${skipped}，失败 ${errors}；另有 ${ledgerErrors} 条台账记录失败，回滚前需人工核对`)
        } else {
          this.$message.success(`写盘完成：写入 ${written}，跳过 ${skipped}，失败 ${errors}`)
        }
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.writing = false
      }
    },
    handleGenerateFromPreview() {
      this.previewVisible = false
      this.doGenerate(this.previewIds)
    },

    /* ---------- 生成自检 ---------- */
    handleSelfCheck() {
      if (!this.selected.length) {
        this.$message.warning('请先勾选要自检的表')
        return
      }
      this.doSelfCheck(this.selected.map(r => r.id))
    },
    async doSelfCheck(ids) {
      if (!ids || !ids.length) {
        this.$message.warning('请先勾选要自检的表')
        return
      }
      this.checking = true
      try {
        const res = await selfCheckGenCode(ids)
        this.checkResult = res.data || {
          passed: true, issues: [], fileCount: 0, javaCount: 0,
          errorCount: 0, warningCount: 0, elapsedMs: 0, compilerAvailable: true
        }
        this.checkVisible = true
        if (this.checkResult.passed) {
          this.$message.success(`自检通过（${this.checkResult.elapsedMs} ms）`)
        } else {
          this.$message.error(`自检发现 ${this.checkResult.errorCount} 个错误`)
        }
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.checking = false
      }
    },

    /* ---------- 菜单注册 ---------- */
    openMenuDialog(row) {
      const targets = row ? [row] : this.selected
      if (!targets.length) {
        this.$message.warning('请先勾选要注册菜单的表')
        return
      }
      this.menuTargets = targets
      this.menuResult = null
      this.menuVisible = true
    },
    async handleRegisterMenu(dryRun) {
      if (!this.menuTargets.length) return
      this.registering = true
      try {
        const res = await registerGenMenu({
          ids: this.menuTargets.map(t => t.id),
          parentId: this.menuForm.parentId || 0,
          roleId: this.menuForm.roleId ? Number(this.menuForm.roleId) : null,
          syncRoute: this.menuForm.syncRoute,
          dryRun
        })
        this.menuResult = res.data || null
        if (!dryRun && this.menuResult) {
          const ok = (this.menuResult.menus || []).filter(m => m.status === 'REGISTERED').length
          const skip = (this.menuResult.menus || []).filter(m => m.status === 'SKIPPED').length
          this.$message.success(`注册完成：新增 ${ok}，跳过 ${skip}`)
          this.fetchList()
        }
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.registering = false
      }
    },
    onHtmlTypeChange(row) {
      if (row.htmlType !== 'select' && row.htmlType !== 'radio') {
        row.dictType = ''
      }
    },

    /* ---------- DDL 导入 ---------- */
    openImportDialog() {
      this.ddlText = ''
      this.parsed = null
      this.importVisible = true
    },
    async handleParse() {
      if (!this.ddlText.trim()) {
        this.$message.warning('请先粘贴建表语句')
        return
      }
      this.parsing = true
      try {
        // 预选默认系统前先刷新：默认可能刚在系统管理页被改过
        await this.loadProjectOptions()
        const res = await parseDdl(this.ddlText)
        this.parsed = { ...defaultTableConfig(), projectId: this.defaultProjectId(), ...res.data, overwrite: false }
        this.$message.success('解析成功，请核对后保存')
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.parsing = false
      }
    },
    async handleSaveImport() {
      const p = this.parsed
      if (!p.tableName || !p.className || !p.moduleName) {
        this.$message.warning('表名 / 实体类名 / 前端模块名不能为空')
        return
      }
      if (!p.columns || !p.columns.length) {
        this.$message.warning('解析结果没有可用字段')
        return
      }
      this.saving = true
      try {
        await createGenTable(p)
        this.$message.success('保存成功')
        this.importVisible = false
        this.fetchList()
      } catch (e) {
        this.$confirm('表名已存在，是否覆盖原配置？', '提示', { type: 'warning' })
          .then(async () => {
            try {
              await createGenTable({ ...p, overwrite: true })
              this.$message.success('覆盖成功')
              this.importVisible = false
              this.fetchList()
            } catch (e2) { /* 拦截器已提示 */ } finally {
              this.saving = false
            }
          })
          .catch(() => { this.saving = false })
        return
      }
      this.saving = false
    },

    /* ---------- P2-3 字典自动识别 ---------- */
    openDictDetect(row) {
      this.$refs.dictDetect.open(String(row.id))
    },
    openRelationDialog() {
      this.$refs.relation.open()
    },
    // 落库后会改 dict_type，字典下拉也刷新一下，避免字段配置里看不到新编码
    onDictApplied() {
      this.loadDictTypes()
    },

    /* ---------- 字段配置 ---------- */
    async openColumnDialog(row) {
      const res = await getGenTableDetail(String(row.id))
      const data = res.data || {}
      this.currentTable = { ...defaultTableConfig(), ...data }
      this.projectGenDefaults = data.projectGenDefaults || {}
      this.currentColumns = (data.columns || []).map(c => ({
        ...c,
        excelExport: c.excelExport == null ? 1 : c.excelExport,
        excelImport: c.excelImport == null ? 0 : c.excelImport
      }))
      this.columnVisible = true
      // 字典可能刚在字典管理页新增，打开时刷新一次
      this.loadDictTypes()
      this.loadMasterCandidates()
    },
    // 选了关联表：控件切下拉，展示字段默认 name
    onRefTableChange(row) {
      if (row.refTable) {
        if (!row.refLabel) row.refLabel = 'name'
        row.htmlType = 'select'
        row.isForm = 1
      } else {
        row.refLabel = ''
      }
    },
    async handleSaveColumns() {
      this.saving = true
      try {
        await updateGenTable(this.currentTable.id, {
          ...this.currentTable,
          columns: this.currentColumns
        })
        this.$message.success('保存成功')
        this.columnVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.saving = false
      }
    },

    /* ---------- ZIP 下载与删除 ---------- */
    async doGenerate(ids) {
      this.generating = true
      try {
        const files = await downloadGenCode(ids)
        this.genFiles = files || []
        this.filesVisible = true
      } catch (e) {
        this.$message.error(e.message || '生成失败')
      } finally {
        this.generating = false
      }
    },
    handleGenerate() {
      if (!this.selected.length) {
        this.$message.warning('请先勾选要生成的表')
        return
      }
      this.doGenerate(this.selected.map(r => r.id))
    },
    handleDelete(row) {
      this.$confirm(`确定删除表配置「${row.tableName}」吗？其字段配置将一并删除。`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteGenTable(String(row.id))
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    resultClass(status) {
      if (status === 'WRITTEN' || status === 'SAVED') return 'ok'
      if (status === 'SKIPPED' || status === 'EXISTS') return 'skip'
      return 'err'
    },

    /* ---------- V1.3 diff 预览（弹窗已拆到 GenDiffDialog） ---------- */
    /* ---------- V7.3 P0-3 表级 AI 推荐 ---------- */

    /**
     * 选中表直接出建议：不依赖流水线，也不需要先跑生成。
     * 服务端把建议落盘并返回 token，应用时凭 token 读回——建议值不经客户端。
     */
    async openAiRecommend() {
      const ids = this.selected.map(t => t.id)
      if (!ids.length) return this.$message.warning('请先勾选要推荐配置的表')
      const projectIds = [...new Set(this.selected.map(t => t.projectId).filter(Boolean))]
      if (!projectIds.length) {
        return this.$message.warning('选中的表未绑定系统，请先在「批量设置」里指定所属系统')
      }
      if (projectIds.length > 1) {
        return this.$message.warning('AI 推荐一次只能处理同一个系统的表，请分批勾选')
      }
      this.aiRecVisible = true
      this.aiRecLoading = true
      this.aiRecExists = false
      this.aiRecToken = ''
      this.aiRecData = { tables: [], menus: [], drops: [] }
      try {
        const res = await aiRecommendTables({ projectId: projectIds[0], tableIds: ids })
        const vo = res.data || {}
        this.aiRecExists = !!vo.exists
        this.aiRecToken = vo.token || ''
        this.aiRecData = {
          tables: vo.tables || [],
          menus: vo.menus || [],
          drops: vo.drops || []
        }
      } catch (e) {
        // 请求层已提示具体原因（AI 未启用/超时），关掉空壳对话框
        this.aiRecVisible = false
      } finally {
        this.aiRecLoading = false
      }
    },

    async onApplyAiRecommend(dto) {
      if (!this.aiRecToken) return this.$message.warning('建议已过期，请重新推荐')
      this.aiRecApplyLoading = true
      try {
        const res = await aiRecommendTablesApply({ token: this.aiRecToken, ...dto })
        const n = res.data || 0
        this.$message.success(`已应用 ${n} 个字段，重新生成即可生效`)
        this.aiRecVisible = false
        this.handleSearch()
      } finally {
        this.aiRecApplyLoading = false
      }
    },

    openDiffDialog() {
      if (!this.selected.length) {
        this.$message.warning('请先勾选要对比的表')
        return
      }
      this.diffVisible = true
    },

    /* ---------- V1.3 结构漂移同步 ---------- */
    handleSyncColumns(row) {
      const extra = (row.driftNewColumns || []).length
        ? `库中新增：${(row.driftNewColumns || []).join('、')}`
        : '库中无新增字段'
      const missing = (row.driftMissingColumns || []).length
        ? `；配置里多余：${(row.driftMissingColumns || []).join('、')}（需手动确认后再删）`
        : ''
      this.$confirm(`${extra}${missing}。是否把库中新增字段补进配置？`, '同步表结构', { type: 'info' })
        .then(async () => {
          this.syncing = true
          try {
            const res = await syncGenColumns(row.id, false)
            const d = res.data || {}
            const parts = []
            if ((d.added || []).length) parts.push(`新增 ${d.added.length} 列`)
            if ((d.changed || []).length) parts.push(`类型变化 ${d.changed.length} 列`)
            if ((d.removed || []).length) parts.push(`多余 ${d.removed.length} 列未删除`)
            this.$message.success(parts.length ? `同步完成：${parts.join('，')}` : '结构已一致，无需同步')
            this.fetchList()
          } catch (e) { /* 拦截器已提示 */ } finally {
            this.syncing = false
          }
        })
        .catch(() => {})
    },

    /* ---------- V1.4 目标模块 / 主子表 ---------- */
    async loadMasterCandidates() {
      try {
        // 注意：后端分页参数是 pageNum/pageSize（PageQuery），不是 page/size
      const res = await getGenTablePage({ pageNum: 1, pageSize: 500 })
        this.masterCandidates = (res.data.list || []).map(t => ({
          id: t.id, tableName: t.tableName, tableComment: t.tableComment
        }))
      } catch (e) {
        this.masterCandidates = []
      }
    },
    // 选目标模块时，顺手把后端路径/包名/网关前缀填成该模块的值（仍可手改）
    onTargetModuleChange(key) {
      const m = this.moduleOptions.find(o => o.value === key)
      if (!m) return
      this.currentTable.backendPath = m.backendPath || this.currentTable.backendPath
      this.currentTable.packageName = m.packageName || this.currentTable.packageName
      this.currentTable.gatewayPrefix = m.gatewayPrefix || this.currentTable.gatewayPrefix
    },
    onMasterChange(id) {
      const m = this.masterOptions.find(o => o.id === id)
      if (m && !this.currentTable.masterForeignKey) {
        const base = m.tableName.split('_').pop()
        this.currentTable.masterForeignKey = `${base}_id`
      }
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
  gap: 16px;
  /* 窄屏下按钮组换行而不是被压缩变形 */
  flex-wrap: wrap;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cg-text);
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 用一条竖线把「导入」「生成」「更多」三类动作分开，
   比纯靠间距更容易一眼看出哪些按钮是一组的 */
.group-divider {
  width: 1px;
  height: 20px;
  background: var(--cg-border);
}

.more-btn {
  padding-left: 12px;
  padding-right: 10px;
}

.pagination {
  margin-top: 16px;
  text-align: right;
}

.danger-btn {
  color: var(--cg-danger);
}

.tip-bar {
  margin-bottom: 16px;
}

.form-tip {
  color: var(--cg-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
}

.mt12 {
  margin-top: 12px;
}

.muted {
  color: var(--cg-text-placeholder);
  font-size: 12px;
}

.inline-tip {
  margin-left: 8px;
  color: var(--cg-text-secondary);
  font-size: 12px;
}

.mono >>> textarea {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}

/* V1.2 批量操作条 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--cg-primary-soft);
  border: 1px solid var(--cg-primary-light-7);
  border-radius: 4px;
}

.batch-count {
  font-size: 13px;
  color: var(--cg-primary);
  font-weight: 600;
}

.cell-sub {
  font-size: 11px;
  color: var(--cg-text-secondary);
  line-height: 1.4;
}

.preset-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.preset-label {
  font-size: 13px;
  color: var(--cg-text-regular);
}

.apply-details {
  margin-top: 10px;
  padding: 8px 10px;
  background: var(--cg-bg-hover);
  border: 1px solid var(--cg-border-light);
  border-radius: 4px;
  max-height: 180px;
  overflow: auto;
}

.detail-line {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: var(--cg-text-regular);
  line-height: 1.7;
  word-break: break-all;
}

.query-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.import-results {
  border-top: 1px solid var(--cg-border-light);
  margin-top: 10px;
  padding-top: 8px;
  max-height: 180px;
  overflow: auto;
}

.res-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
}

.res-msg {
  color: var(--cg-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pill {
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-family: sans-serif;
  flex-shrink: 0;
}

.pill.ok {
  background: var(--cg-success-bg);
  color: var(--cg-success);
  border: 1px solid transparent;
}

.pill.skip {
  background: var(--cg-warning-bg);
  color: var(--cg-warning);
  border: 1px solid transparent;
}

.pill.err {
  background: var(--cg-danger-bg);
  color: var(--cg-danger);
  border: 1px solid transparent;
}

.preview {
  display: flex;
  gap: 14px;
  height: 440px;
}

.preview-tree {
  width: 44%;
  border: 1px solid var(--cg-border-light);
  border-radius: 4px;
  overflow: auto;
  padding: 8px;
}

.preview-code {
  flex: 1;
  border: 1px solid var(--cg-border-light);
  border-radius: 4px;
  background: #282c34;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.code-head {
  color: #abb2bf;
  font-size: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid #3e4451;
  font-family: Consolas, Monaco, monospace;
}

.code-body {
  color: #98c379;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.65;
  padding: 12px;
  margin: 0;
  overflow: auto;
  white-space: pre;
  flex: 1;
}

.write-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.write-tip {
  font-size: 13px;
  color: var(--cg-text-regular);
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px 16px;
  margin-bottom: 8px;
}

.switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--cg-bg-hover);
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--cg-text-regular);
}

.switch-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

/* 「默认」不是按钮，只是标记：与所属系统默认值一致时亮起，
   用很轻的描边，避免和真正可点的标签混淆 */
.switch-default {
  font-style: normal;
  font-size: 11px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  color: var(--cg-text-secondary);
  border: 1px solid var(--cg-border);
  background: var(--cg-bg-container);
  cursor: help;
}

.file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 320px;
  overflow: auto;
}

.file-list li {
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
  color: var(--cg-text);
  background: var(--cg-bg-hover);
  border-radius: 4px;
  padding: 7px 12px;
  margin: 6px 0;
}

/* ---------- V1.3 diff 预览 ---------- */
.opt-sub {
  float: right;
  color: var(--cg-text-secondary);
  font-size: 12px;
  margin-left: 10px;
}
</style>
