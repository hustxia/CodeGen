<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      class="tip-bar"
      title="一句话建系统：确认功能清单后才生成表结构，确认完整 SQL 后才建库建表，建表完成后仍需单独确认构建启动。AI 与 SQL 预览均不写数据库；确认建表前的清单、表结构和 SQL 都是本页暂存，刷新页面会丢失。"
    />

    <el-card shadow="never" class="main-card">
      <el-steps :active="step" finish-status="success" align-center class="steps">
        <el-step title="① 录入需求" />
        <el-step title="② 确认功能清单" />
        <el-step title="③ 表结构与 SQL" />
        <el-step title="④ 建表与构建" />
      </el-steps>

      <!-- ============ 第①步：录入需求 ============ -->
      <div v-show="step === 0">
        <el-form label-width="120px" style="max-width: 720px; margin-top: 8px" @submit.native.prevent>
          <el-form-item label="系统名称" required>
            <el-input v-model="form.systemName" placeholder="如：仓库管理系统" maxlength="64" />
          </el-form-item>
          <el-form-item label="一句话描述">
            <el-input
              v-model="form.systemDesc"
              type="textarea"
              :rows="2"
              placeholder="如：管理图书入库、读者借阅、归还与逾期处理。写清业务对象和动作，AI 规划的模块才贴合该系统"
              maxlength="512"
            />
          </el-form-item>
          <el-form-item label="数据库名" required>
            <el-input v-model="form.dbName" placeholder="小写字母开头的 snake_case，如 wms_new" maxlength="63" />
          </el-form-item>
          <el-form-item label="建模方式">
            <el-radio-group v-model="form.mode">
              <el-radio label="ai" :disabled="!aiEnabled">AI 规划功能与表结构</el-radio>
              <el-radio label="manual">手动粘贴 DDL</el-radio>
            </el-radio-group>
            <div v-if="!aiEnabled" style="font-size:12px;color:#909399;line-height:1.6;">
              AI 建模未启用（服务端未配置 LLM key），请在「手动粘贴 DDL」中完成建表
            </div>
            <div v-else-if="form.mode === 'ai'" class="audit-note">
              首次只生成功能清单，可编辑、增删并勾选本次范围；此阶段不会生成表或写数据库。
            </div>
          </el-form-item>
          <el-form-item v-if="form.mode === 'manual'" label="建表 DDL">
            <el-input
              v-model="form.ddlText"
              type="textarea"
              :rows="10"
              placeholder="粘贴 CREATE DATABASE / CREATE TABLE 语句。缺少审计列（id/create_time/update_time/create_by/update_by/deleted）时由服务端自动补齐：只增不改，补齐明细随后如实展示"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="featureLoading" @click="onStart">
              {{ form.mode === 'ai' ? '生成功能清单' : '下一步：确认 DDL' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- ============ 第②步：确认功能清单 ============ -->
      <div v-show="step === 1">
        <div class="confirm-bar">
          <span>基于「<strong>{{ form.systemName }}</strong>」规划出 {{ featureModules.length }} 个模块，已选 {{ checkedModuleCount }} 个；可编辑清单并勾选本次建模范围。</span>
          <div>
            <el-button size="small" icon="el-icon-refresh" :loading="featureLoading" @click="onRegenerate">重新生成</el-button>
            <el-button size="small" icon="el-icon-plus" @click="addModule">新增模块</el-button>
            <el-button size="small" @click="selectAllModules">全选</el-button>
          </div>
        </div>
        <el-alert v-if="relevanceWarning" type="warning" :closable="false" class="tip-bar">
          <template slot="title">
            <div>{{ relevanceTip }}</div>
            <div style="margin-top: 4px">可点「上一步」补充业务描述后重新生成，或直接在下方编辑模块清单。</div>
          </template>
        </el-alert>
        <div class="module-grid">
          <el-card v-for="(m, mi) in featureModules" :key="mi" shadow="never" class="module-card">
            <div slot="header" class="module-head">
              <el-checkbox v-model="m.checked">参与本次建模</el-checkbox>
              <el-button type="text" class="danger-text" @click="featureModules.splice(mi, 1)">删除模块</el-button>
            </div>
            <el-input v-model="m.name" size="small" placeholder="模块名称" class="module-name" />
            <el-input v-model="m.desc" size="small" placeholder="模块说明" class="module-desc" />
            <div class="function-title">功能点</div>
            <div v-for="(fn, fi) in m.functions" :key="fi" class="function-row">
              <el-input v-model="m.functions[fi]" size="mini" placeholder="功能点名称" />
              <el-button type="text" class="danger-text" @click="m.functions.splice(fi, 1)">删除</el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="m.functions.push('')">新增功能点</el-button>
          </el-card>
        </div>
        <el-alert v-if="moduleErrors.length" type="warning" :closable="false" class="tip-bar">
          <template slot="title">
            <div>以下模块未生成成功，可在上一步调整或重试：</div>
            <div v-for="e in moduleErrors" :key="e.name">{{ e.name }}：{{ e.message }}</div>
          </template>
        </el-alert>
        <div v-if="moduleLoading" class="module-progress">{{ moduleProgressText }}</div>
        <div class="confirm-bar">
          <el-button size="small" icon="el-icon-back" @click="step = 0">上一步</el-button>
          <span>确认后仅逐模块生成表结构和 SQL，仍不会建表</span>
          <el-button type="primary" :loading="moduleLoading" @click="onBuildTables">确认清单，生成表结构和 SQL</el-button>
        </div>
      </div>

      <!-- ============ 第③步：表结构与 SQL ============ -->
      <div v-show="step === 2">
        <template v-if="form.mode === 'ai'">
          <el-alert v-if="moduleErrors.length" type="warning" :closable="false" class="tip-bar">
            <template slot="title">
              未生成模块：{{ moduleErrors.map(e => e.name).join('、') }}。当前只预览成功模块的表，不会隐藏失败范围。
            </template>
          </el-alert>
          <el-tabs v-model="activeSchemaTab">
            <el-tab-pane :label="`表结构（${tables.length} 张）`" name="schema">
              <div class="confirm-bar">
                <span>表与列可增删改；任何修改都会使已生成的 SQL 预览过期。</span>
                <el-button size="small" icon="el-icon-plus" @click="addTable">新增表</el-button>
              </div>
              <div v-for="(t, ti) in tables" :key="ti" class="table-block">
                <div class="table-head">
                  <el-input v-model="t.tableName" size="small" style="width: 240px" placeholder="表名 snake_case">
                    <template slot="prepend">表名</template>
                  </el-input>
                  <el-input v-model="t.tableComment" size="small" style="width: 220px" placeholder="表注释">
                    <template slot="prepend">注释</template>
                  </el-input>
                  <el-button size="small" icon="el-icon-plus" @click="addColumn(t)">加列</el-button>
                  <el-button size="small" type="danger" plain icon="el-icon-delete" @click="removeTable(ti)">删表</el-button>
                </div>
                <el-table :data="t.columns" size="mini" border>
                  <el-table-column label="列名" width="220">
                    <template slot-scope="{ row }"><el-input v-model="row.name" size="mini" placeholder="列名" /></template>
                  </el-table-column>
                  <el-table-column label="类型" width="180">
                    <template slot-scope="{ row }">
                      <el-select v-model="row.type" size="mini" filterable allow-create>
                        <el-option v-for="opt in TYPE_OPTIONS" :key="opt" :label="opt" :value="opt" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="注释" min-width="200">
                    <template slot-scope="{ row }"><el-input v-model="row.comment" size="mini" /></template>
                  </el-table-column>
                  <el-table-column label="操作" width="70">
                    <template slot-scope="{$index}">
                      <el-button type="text" class="danger-text" @click="t.columns.splice($index, 1)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <div class="audit-note">服务端组装 SQL 时自动补齐：id（主键）、create_time、update_time、create_by、update_by、deleted</div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="SQL 脚本" name="sql">
              <div class="confirm-bar">
                <span>以下完整 SQL 由服务端组装，仅供确认，尚未执行。</span>
                <div>
                  <el-button size="small" icon="el-icon-document-copy" :disabled="!sqlPreview || sqlStale" @click="copySql">复制 SQL</el-button>
                  <el-button size="small" type="primary" icon="el-icon-refresh" :loading="previewLoading" @click="refreshSqlPreview">刷新预览</el-button>
                </div>
              </div>
              <el-alert v-if="sqlStale" type="warning" :closable="false" class="tip-bar" title="表结构或数据库名已变化，旧 SQL 已过期；必须刷新预览后才能建表。" />
              <el-alert v-if="sqlError" type="error" :closable="false" class="tip-bar" :title="`SQL 预览失败：${sqlError}。未创建任何任务或数据库。`" />
              <div v-if="sqlPreview" class="sql-meta">
                <span>目标库：{{ sqlPreview.dbName }}</span>
                <span>{{ sqlPreview.tableCount }} 张业务表</span>
                <span>{{ sqlPreview.statementCount }} 条 DDL 语句</span>
              </div>
              <pre v-if="sqlPreview" class="sql-preview">{{ sqlPreview.ddl }}</pre>
              <el-empty v-else description="尚未生成 SQL 预览，请点击刷新预览" :image-size="80" />
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else>
          <el-input v-model="form.ddlText" type="textarea" :rows="16" />
          <div class="audit-note">执行前将按 BaseEntity 约定检查审计列，缺列由服务端自动补齐（只增不改），补齐明细在下一步展示。</div>
        </template>

        <!-- V5.2 数据初始化（两种模式共用） -->
        <div class="seed-bar">
          <el-radio-group v-model="form.seedMode" size="small">
            <el-radio label="rule">规则生成示例数据（推荐）</el-radio>
            <el-radio label="ai">AI 增强（贴合业务）</el-radio>
            <el-radio label="off">跳过</el-radio>
          </el-radio-group>
          <div class="seed-tip">规则生成：建表后按外键依赖自动灌入中文示例数据（主数据 12 行、单据 8 行、明细跟随主表），枚举/唯一/必填约束全部满足，单次上限 2000 行。AI 增强：由 AI 依据系统名与表结构生成业务贴合的值池（商品名/公司名/备注等），约束仍由规则保证；AI 失败自动回落规则词库，需已配置 pipeline.ai。</div>
        </div>

        <div class="confirm-bar">
          <el-button size="small" icon="el-icon-back" @click="step = form.mode === 'ai' ? 1 : 0">上一步</el-button>
          <span>确认后才会创建流水线、系统台账并执行建库建表</span>
          <el-button type="primary" :loading="dbLoading" :disabled="prepareDisabled" @click="onPrepareDb">
            {{ form.mode === 'ai' ? '确认 SQL 并建库建表' : '确认 DDL 并建库建表' }}
          </el-button>
        </div>
      </div>

      <!-- ============ 第④步：建表与构建 ============ -->
      <div v-show="step === 3">
        <!-- V9.4：全部取自 prepare-db 执行器回报，不硬编码「0 失败」、不用建表数冒充导入数 -->
        <div v-if="prepareResult" class="prepare-result">
          <div class="result-stat"><strong>{{ prepareResult.tableCount }}</strong><span>成功建表（库 {{ prepareResult.dbName }}）</span></div>
          <div class="result-stat"><strong>{{ prepareResult.importedTableCount }}</strong><span>导入生成配置</span></div>
          <div class="result-stat"><strong>{{ prepareResult.failedStatementCount }}</strong><span>失败 / 拦截 {{ prepareResult.blockedStatementCount }} · 共 {{ prepareResult.statementCount }} 条语句</span></div>
          <div class="result-stat"><strong>{{ executedStepCount }}</strong><span>已运行构建步骤</span></div>
        </div>
        <el-alert
          v-if="prepareError"
          type="error"
          :closable="false"
          class="tip-bar"
          :title="`建库建表失败：${prepareError}。未进入构建，可返回修改后重试。`"
        />
        <el-alert
          v-if="detail && detail.status === 'await_confirm'"
          type="success"
          :closable="false"
          class="tip-bar"
          title="建库建表已完成，代码生成、打包、部署和健康检查尚未执行。点击“开始构建并启动”后才会继续。"
        />
        <div v-if="detail" class="progress-head">
          <div>
            <span class="sys-name">{{ detail.systemName }}</span>
            <el-tag size="mini" :type="STATUS_TYPE[detail.status]">{{ STATUS_TEXT[detail.status] || detail.status }}</el-tag>
            <span class="meta">{{ detail.pipelineNo }} · 库 {{ detail.dbName }} · 后端 :{{ detail.backendPort || '—' }} · 前端 :{{ detail.frontendPort || '—' }}</span>
          </div>
          <div>
            <el-button
              v-if="detail.status === 'await_confirm' || detail.status === 'failed'"
              type="primary"
              size="small"
              icon="el-icon-video-play"
              :loading="runLoading"
              @click="onRun"
            >开始构建并启动</el-button>
            <el-button
              v-if="detail.status === 'success'"
              type="success"
              size="small"
              icon="el-icon-link"
              @click="openSystem"
            >打开系统</el-button>
            <el-button
              v-if="detail.status === 'success'"
              type="warning"
              size="small"
              plain
              icon="el-icon-magic-stick"
              :loading="recLoading"
              @click="openRecommend"
            >AI 推荐配置</el-button>
            <el-button
              v-if="detail.status === 'running' || detail.status === 'success'"
              size="small"
              type="danger"
              plain
              icon="el-icon-switch-button"
              @click="onStop"
            >停止</el-button>
            <el-button size="small" icon="el-icon-refresh" @click="refreshDetail">刷新</el-button>
          </div>
        </div>

        <!-- V5.1：手动 DDL 补齐明细徽标（如实展示补了什么） -->
        <el-alert v-if="patchedReport.length" type="success" :closable="true" class="tip-bar">
          <template slot="title">
            建库时已自动补齐审计列（只增不改）：
            <el-tag
              v-for="p in patchedReport"
              :key="p.table"
              size="mini"
              type="success"
              style="margin: 0 6px 4px 0"
            >{{ p.table }} 补 {{ p.cols.join('/') }}</el-tag>
          </template>
        </el-alert>

        <!-- V5.1：审计列检查 + 一键补齐（构建前把缺列表修到合规） -->
        <div v-if="detail && ['await_confirm', 'failed'].includes(detail.status)" class="lint-card">
          <div class="lint-head">
            <span class="lint-title">审计列检查（目标库 {{ detail.dbName }}）</span>
            <div>
              <el-button size="mini" icon="el-icon-search" :loading="lintLoading" @click="onLint">检查</el-button>
              <el-button
                size="mini"
                type="primary"
                icon="el-icon-magic-stick"
                :disabled="fixDisabled"
                :loading="fixLoading"
                @click="onLintFix"
              >一键补齐</el-button>
            </div>
          </div>
          <el-table v-if="lintItems.length" :data="lintItems" size="mini" border>
            <el-table-column prop="table" label="表名" width="200" />
            <el-table-column label="缺失列" min-width="240">
              <template slot-scope="{ row }">
                <template v-if="row.missingCols && row.missingCols.length">
                  <el-tag v-for="c in row.missingCols" :key="c" size="mini" style="margin-right: 4px">{{ c }}</el-tag>
                </template>
                <span v-else class="meta">—</span>
              </template>
            </el-table-column>
            <el-table-column label="补齐结果" min-width="200">
              <template slot-scope="{ row }">
                <el-tag v-if="row.action === 'PATCHED'" size="mini" type="success">已补 {{ (row.patchedCols || []).length }} 列</el-tag>
                <el-tag v-else-if="row.action === 'SKIPPED'" size="mini" type="info">{{ row.error || '合规，跳过' }}</el-tag>
                <el-tag v-else-if="row.action === 'FAILED'" size="mini" type="danger" :title="row.error">失败：{{ row.error }}</el-tag>
                <el-tag v-else-if="row.missingCols && row.missingCols.length" size="mini" type="warning">待补齐</el-tag>
                <span v-else class="meta">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-else-if="lintChecked" class="audit-note">全部合规，可直接开始构建</div>
        </div>

        <!-- 三轨步骤：全局轨 / 后端轨 / 前端轨 -->
        <template v-if="detail">
          <div class="track">
            <div class="track-title">主流程</div>
            <div class="track-steps">
              <div v-for="s in stepsOf('pipeline')" :key="s.id" class="step-chip" :class="['st-' + s.status, { 'st-report': !!reportIdOf(s) }]" @click="openQualityReport(s)">
                <i :class="STEP_ICON[s.status] || 'el-icon-more'" />
                <span class="step-name">{{ s.stepName }}</span>
                <span v-if="s.message" class="step-msg">{{ s.message }}</span>
                <span v-if="reportIdOf(s)" class="step-report">❌→✅ 查看报告</span>
              </div>
            </div>
          </div>
          <div class="track">
            <div class="track-title">后端轨</div>
            <div class="track-steps">
              <div v-for="s in stepsOf('backend')" :key="s.id" class="step-chip" :class="['st-' + s.status, { 'st-report': !!reportIdOf(s) }]" @click="openQualityReport(s)">
                <i :class="STEP_ICON[s.status] || 'el-icon-more'" />
                <span class="step-name">{{ s.stepName }}</span>
                <span v-if="s.message" class="step-msg">{{ s.message }}</span>
                <span v-if="reportIdOf(s)" class="step-report">❌→✅ 查看报告</span>
              </div>
            </div>
          </div>
          <div class="track">
            <div class="track-title">前端轨</div>
            <div class="track-steps">
              <div v-for="s in stepsOf('frontend')" :key="s.id" class="step-chip" :class="['st-' + s.status, { 'st-report': !!reportIdOf(s) }]" @click="openQualityReport(s)">
                <i :class="STEP_ICON[s.status] || 'el-icon-more'" />
                <span class="step-name">{{ s.stepName }}</span>
                <span v-if="s.message" class="step-msg">{{ s.message }}</span>
                <span v-if="reportIdOf(s)" class="step-report">❌→✅ 查看报告</span>
              </div>
            </div>
          </div>

          <!-- 完成卡片 -->
          <el-result v-if="detail.status === 'success'" icon="success" title="部署完成" class="done-card">
            <template slot="extra">
              <div class="done-url">
                前端访问地址：
                <el-link type="primary" :underline="false" @click="openSystem">{{ detail.frontendUrl }}</el-link>
                <span class="meta">（默认账号 admin / admin123）</span>
              </div>
            </template>
          </el-result>
          <el-alert
            v-if="detail.status === 'failed'"
            type="error"
            :closable="false"
            :title="'失败：' + (detail.failReason || '详见步骤日志')"
            class="fail-bar"
          />
        </template>
      </div>
    </el-card>

    <!-- 历史任务 -->
    <el-card shadow="never" class="history-card">
      <div slot="header">
        <span>建系统任务</span>
        <el-button size="mini" type="text" icon="el-icon-refresh" style="float: right" @click="fetchPage">刷新</el-button>
      </div>
      <el-table :data="pageData" size="mini" border stripe>
        <el-table-column prop="pipelineNo" label="流水线号" width="200" />
        <el-table-column prop="systemName" label="系统名称" min-width="140" />
        <el-table-column prop="dbName" label="目标库" width="140" />
        <el-table-column prop="mode" label="方式" width="70">
          <template slot-scope="{ row }">{{ row.mode === 'ai' ? 'AI' : '手动' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="STATUS_TYPE[row.status]">{{ STATUS_TEXT[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="180">
          <template slot-scope="{ row }">
            <el-button type="text" size="mini" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'success'"
              type="text"
              size="mini"
              @click="openUrl(row.frontendUrl)"
            >打开系统</el-button>
            <el-button
              v-if="row.status === 'running'"
              type="text"
              size="mini"
              class="danger-text"
              @click="onStop(row)"
            >停止</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        :current-page.sync="page.page"
        :page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next"
        style="margin-top: 10px"
        @current-change="fetchPage"
      />
    </el-card>

    <!-- V7.0 AI 推荐预览：diff + 勾选应用（应用前零写库）。V7.3 抽成公共组件，表配置页复用 -->
    <ai-recommend-dialog
      :visible.sync="recVisible"
      :loading="recLoading"
      :apply-loading="recApplyLoading"
      :exists="recExists"
      :tables="recData.tables"
      :menus="recData.menus"
      :drops="recData.drops"
      @apply="onApplyRecommend"
    />

    <!-- M2.2 生成代码质量门禁报告：行级 ❌→✅ -->
    <el-dialog title="生成代码质量门禁报告" :visible.sync="qualityVisible" width="1080px" top="6vh" append-to-body>
      <div v-loading="qualityLoading" class="qg-body">
        <template v-if="qualityReport">
          <div class="qg-summary">
            <el-tag size="mini" :type="qualityReport.blocked ? 'danger' : 'success'">
              {{ qualityReport.blocked ? 'BLOCKED 已阻断落盘' : 'PASS 通过' }}
            </el-tag>
            <el-tag size="mini" type="info">引擎 {{ qualityReport.engine }}</el-tag>
            <el-tag size="mini" type="danger">BLOCKER {{ qualityReport.blockerCount }}</el-tag>
            <el-tag size="mini" type="warning">WARN {{ qualityReport.warnCount }}</el-tag>
            <el-tag size="mini" type="info">{{ qualityReport.ruleCount }} 条规则</el-tag>
            <span class="meta">{{ qualityReport.message }}</span>
          </div>
          <el-alert
            v-if="qualityReport.llmState !== 'APPLIED'"
            type="warning"
            :closable="false"
            style="margin-bottom: 10px"
            :title="qualityReport.llmState === 'DEGRADED' ? 'LLM 引擎不可用，已回落确定性引擎' : 'AI 未启用，仅执行确定性引擎'"
            :description="qualityReport.degradedReason || ''"
          />
          <el-table v-if="qualityReport.issues && qualityReport.issues.length" :data="qualityReport.issues" size="mini" border max-height="520">
            <el-table-column label="级别" width="82">
              <template slot-scope="{ row }">
                <el-tag size="mini" :type="row.severity === 'BLOCKER' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ruleId" label="规则" width="72" />
            <el-table-column label="文件:行" min-width="220">
              <template slot-scope="{ row }">
                <span class="qg-file">{{ row.file }}<b v-if="row.lineNo">:{{ row.lineNo }}</b></span>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="问题" min-width="180" />
            <el-table-column label="❌ 命中" min-width="200">
              <template slot-scope="{ row }"><code class="qg-bad">{{ row.badSnippet }}</code></template>
            </el-table-column>
            <el-table-column label="✅ 修正" min-width="200">
              <template slot-scope="{ row }"><code class="qg-good">{{ row.fixSnippet }}</code></template>
            </el-table-column>
            <el-table-column prop="sourceRef" label="坑库来源" width="130" />
          </el-table>
          <div v-else class="qg-empty">未发现问题，52 条规则全部通过</div>
        </template>
      </div>
      <span slot="footer">
        <el-button size="mini" @click="qualityVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { aiModel, aiModules, previewPipelineDdl, createPipeline, preparePipelineDb, runPipeline, getPipeline, getPipelinePage, stopPipeline, getConfig, lintDb, fixPipelineLint, aiRecommendPreview, aiRecommendApply, getQualityReport } from '@/api/pipeline'
import { getToken } from '@/utils/auth'
import AiRecommendDialog from '../components/AiRecommendDialog.vue'

const STATUS_TEXT = {
  draft: '待建库',
  await_confirm: '待构建',
  running: '运行中',
  success: '成功',
  failed: '失败',
  stopped: '已停止'
}
const STATUS_TYPE = {
  draft: 'info',
  await_confirm: 'warning',
  running: 'primary',
  success: 'success',
  failed: 'danger',
  stopped: 'info'
}
const STEP_ICON = {
  pending: 'el-icon-time',
  running: 'el-icon-loading',
  success: 'el-icon-success',
  failed: 'el-icon-error',
  skipped: 'el-icon-remove-outline'
}

/* V7.0 建议字段中文名 */

export default {
  name: 'GenPipeline',
  components: { AiRecommendDialog },
  data() {
    return {
      step: 0,
      aiEnabled: false,
      form: { systemName: '', systemDesc: '', dbName: '', mode: 'ai', ddlText: '', seedMode: 'rule' },
      featureModules: [],
      featureLoading: false,
      relevanceWarning: false,
      relevanceTip: '',
      moduleLoading: false,
      moduleProgressText: '',
      moduleErrors: [],
      tables: [],
      activeSchemaTab: 'sql',
      sqlPreview: null,
      sqlStale: false,
      sqlError: '',
      confirmedTables: null,
      schemaVersion: 0,
      previewLoading: false,
      prepareError: '',
      dbLoading: false,
      runLoading: false,
      detail: null,
      pageData: [],
      page: { page: 1, size: 10, total: 0 },
      es: null,
      prepareResult: null,
      lintChecked: false,
      lintLoading: false,
      fixLoading: false,
      lintItems: [],
      recVisible: false,
      recLoading: false,
      recApplyLoading: false,
      recExists: false,
      recData: { tables: [], menus: [], drops: [] },
      qualityVisible: false,
      qualityLoading: false,
      qualityReport: null,
      STATUS_TEXT,
      STATUS_TYPE,
      STEP_ICON,
      TYPE_OPTIONS: ['bigint', 'int', 'varchar(32)', 'varchar(64)', 'varchar(128)', 'varchar(255)', 'decimal(18,2)', 'text', 'datetime', 'date', 'tinyint']
    }
  },
  computed: {
    checkedModuleCount() {
      return this.featureModules.filter(m => m.checked).length
    },
    prepareDisabled() {
      return this.form.mode === 'ai' && (!this.sqlPreview || this.sqlStale || this.previewLoading ||
        !this.confirmedTables || !this.tables.length)
    },
    executedStepCount() {
      return ((this.detail && this.detail.steps) || []).filter(s => ['success', 'failed'].includes(s.status)).length
    },
    /* V5.1：建库时被补齐的表（PATCHED），供结果区徽标展示 */
    patchedReport() {
      const list = (this.prepareResult && this.prepareResult.auditReport) || []
      return list.filter(i => i.action === 'PATCHED').map(i => ({ table: i.table, cols: i.patchedCols || [] }))
    },
    /* V5.1：没有待补/失败的表时禁用一键补齐（含未检查状态） */
    fixDisabled() {
      if (!this.lintChecked) return true
      return !this.lintItems.some(i => (i.action ? i.action === 'FAILED' : (i.missingCols || []).length > 0))
    },
    /* V7.0：生成期被白名单丢弃的 AI 建议项（提示用户哪些建议没进来） */
    recDrops() {
      return this.recData.drops || []
    }
  },
  watch: {
    tables: {
      deep: true,
      handler() {
        this.schemaVersion++
        if (this.sqlPreview) this.sqlStale = true
        this.confirmedTables = null
      }
    },
    'form.dbName'() {
      this.schemaVersion++
      if (this.sqlPreview) this.sqlStale = true
      this.confirmedTables = null
    }
  },
  created() {
    this.fetchPage()
    // AI 可用性前置下发：未配 LLM key 时直接禁用 AI 选项，不让用户点了才报错
    getConfig().then(res => {
      this.aiEnabled = !!(res.data && res.data.aiEnabled)
      if (!this.aiEnabled && this.form.mode === 'ai') {
        this.form.mode = 'manual'
      }
    }).catch(() => {})
  },
  beforeDestroy() {
    this.closeSse()
  },
  methods: {
    /* ---------- 第①步：录入需求 ---------- */
    async onStart() {
      if (!this.form.systemName.trim()) return this.$message.warning('请输入系统名称')
      if (!this.form.dbName.trim()) return this.$message.warning('请填写数据库名')
      if (this.form.mode === 'manual') {
        if (!this.form.ddlText.trim()) return this.$message.warning('请粘贴 DDL')
        this.step = 2
        return
      }
      if (!this.aiEnabled) {
        return this.$message.warning('AI 建模未启用（服务端未配置 LLM key），请使用手动粘贴 DDL')
      }
      this.featureLoading = true
      try {
        const res = await aiModules({ systemName: this.form.systemName, systemDesc: this.form.systemDesc })
        const modules = (res.data && res.data.modules) || []
        if (!modules.length) {
          this.$message.warning('AI 未能拆分出业务模块，请重试或改用手动 DDL')
          return
        }
        this.relevanceWarning = !!(res.data && res.data.relevanceWarning)
        this.relevanceTip = (res.data && res.data.relevanceTip) || ''
        this.featureModules = modules.map(m => ({
          name: m.name || '',
          desc: m.desc || '',
          functions: Array.isArray(m.functions) ? m.functions.filter(fn => typeof fn === 'string' && fn.trim()).map(fn => fn.trim()) : [],
          checked: true
        }))
        this.moduleErrors = []
        this.tables = []
        this.sqlPreview = null
        this.sqlError = ''
        this.sqlStale = false
        this.confirmedTables = null
        this.prepareError = ''
        this.step = 1
      } catch (e) {
        this.$message.error(`AI 功能清单生成失败：${e.message || '未知错误'}。未创建任务、未写数据库，可重试或改用手动 DDL`)
      } finally {
        this.featureLoading = false
      }
    },

    /* ---------- 第②步：确认功能清单 ---------- */
    async onRegenerate() {
      try {
        await this.$confirm('重新生成会覆盖当前已编辑的模块清单，确认继续？', '重新生成功能清单', { type: 'warning' })
      } catch (e) {
        return
      }
      await this.onStart()
    },
    addModule() {
      this.featureModules.push({ name: '', desc: '', functions: [''], checked: true })
    },
    selectAllModules() {
      this.featureModules.forEach(m => { m.checked = true })
    },

    /** 列结构统一成编辑器需要的形状 */
    normalizeTables(list) {
      return (list || []).map(t => ({
        tableName: t.tableName,
        tableComment: t.tableComment,
        columns: (t.columns || []).map(c => ({ name: c.name, type: c.type, comment: c.comment }))
      }))
    },

    async onBuildTables() {
      const selected = this.featureModules.filter(m => m.checked)
      if (!selected.length) return this.$message.warning('请至少勾选一个模块')
      if (selected.some(m => !m.name.trim())) return this.$message.warning('已勾选模块中存在空名称')
      this.moduleLoading = true
      this.moduleErrors = []
      this.moduleProgressText = ''
      const base = { systemName: this.form.systemName, systemDesc: this.form.systemDesc }
      const merged = []
      const seen = new Set()
      const errors = []
      try {
        for (let i = 0; i < selected.length; i++) {
          const m = selected[i]
          this.moduleProgressText = `正在生成模块 ${i + 1}/${selected.length}：${m.name}，已合并 ${merged.length} 张表`
          try {
            const res = await aiModel({ ...base, moduleName: m.name })
            for (const t of this.normalizeTables((res.data && res.data.tables) || [])) {
              const key = (t.tableName || '').trim().toLowerCase()
              if (key && !seen.has(key)) {
                seen.add(key)
                merged.push(t)
              }
            }
          } catch (e) {
            errors.push({ name: m.name, message: e.message || '生成失败' })
          }
        }
        this.moduleErrors = errors
        if (!merged.length) {
          this.$message.error('所有勾选模块均未生成表结构，请查看失败模块后重试')
          return
        }
        this.tables = merged
        this.step = 2
        this.activeSchemaTab = 'sql'
        const ok = await this.refreshSqlPreview()
        if (!ok) this.$message.warning('SQL 预览未就绪或已过期，请检查失败原因后点击刷新预览')
        if (errors.length) this.$message.warning(`${errors.length} 个模块生成失败，已在页面列明`)
      } catch (e) {
        this.$message.error(`生成表结构失败：${e.message || '未知错误'}。未创建任务、未写数据库`)
      } finally {
        this.moduleLoading = false
        this.moduleProgressText = ''
      }
    },

    /* ---------- 第③步：表结构与 SQL ---------- */
    async refreshSqlPreview() {
      if (!this.form.dbName.trim()) {
        this.$message.warning('请填写数据库名')
        return false
      }
      if (!this.tables.length) {
        this.$message.warning('没有可预览的表结构')
        return false
      }
      this.previewLoading = true
      this.sqlError = ''
      // 预览请求与后续建表必须用同一份表结构快照，避免「看了 A 的 SQL、执行了 B 的 DDL」
      const snapshot = JSON.parse(JSON.stringify(this.tables))
      try {
        await this.$nextTick()
        const version = this.schemaVersion
        const res = await previewPipelineDdl({ dbName: this.form.dbName.trim(), tables: snapshot })
        this.sqlPreview = res.data
        this.sqlStale = version !== this.schemaVersion
        this.confirmedTables = this.sqlStale ? null : snapshot
        this.activeSchemaTab = 'sql'
        return !this.sqlStale
      } catch (e) {
        this.sqlError = e.message || '未知错误'
        this.sqlPreview = null
        this.confirmedTables = null
        this.$message.error(`SQL 预览失败：${this.sqlError}。未创建任务、未写数据库`)
        return false
      } finally {
        this.previewLoading = false
      }
    },
    async copySql() {
      if (!this.sqlPreview || this.sqlStale) return
      if (!navigator.clipboard) return this.$message.warning('当前浏览器不支持剪贴板，请手动选择 SQL 复制')
      try {
        await navigator.clipboard.writeText(this.sqlPreview.ddl)
        this.$message.success('SQL 已复制')
      } catch (e) {
        this.$message.warning('浏览器未允许剪贴板访问，请手动选择 SQL 复制')
      }
    },
    addTable() {
      this.tables.push({ tableName: '', tableComment: '', columns: [{ name: '', type: 'varchar(64)', comment: '' }] })
    },
    removeTable(i) {
      this.tables.splice(i, 1)
    },
    addColumn(t) {
      t.columns.push({ name: '', type: 'varchar(255)', comment: '' })
    },
    async onPrepareDb() {
      if (!this.form.dbName.trim()) return this.$message.warning('请填写数据库名')
      if (this.form.mode === 'manual' && !this.form.ddlText.trim()) return this.$message.warning('请粘贴 DDL')
      let confirmed = this.tables
      if (this.form.mode === 'ai') {
        if (!this.sqlPreview || this.sqlStale || !this.confirmedTables) {
          return this.$message.warning('SQL 预览已缺失或过期，请先刷新预览')
        }
        // 用预览时的表结构快照，保证「确认过的 SQL」与「执行的 DDL」输入完全一致
        confirmed = this.confirmedTables
        if (confirmed.some(t => !t.tableName.trim())) return this.$message.warning('存在未命名的表')
      }
      try {
        await this.$confirm(
          `将在数据库 ${this.form.dbName} 中建表${this.form.mode === 'ai' ? `（共 ${confirmed.length} 张）` : ''}。已确认${this.form.mode === 'ai' ? '服务端 SQL 预览' : '上述 DDL'}，已存在的同名库/表不会覆盖。确认执行？`,
          this.form.mode === 'ai' ? '确认 SQL 并建库建表' : '确认 DDL 并建库建表',
          { type: 'warning' }
        )
      } catch (e) {
        // 取消确认不产生任何副作用，也不能留下未捕获的 Promise rejection
        return
      }
      this.prepareError = ''
      this.dbLoading = true
      try {
        // 只有确认 SQL/DDL 后才创建任务与系统台账，并复用现有 prepare-db 执行链
        const created = await createPipeline({ ...this.form, tables: confirmed })
        const pipelineId = created.data.id
        const prep = await preparePipelineDb(pipelineId, {
          tables: this.form.mode === 'ai' ? confirmed : null,
          ddlText: this.form.mode === 'manual' ? this.form.ddlText : null
        })
        this.prepareResult = prep.data || null
        this.resetLint()
        await this.loadDetail(pipelineId)
        this.step = 3
        this.fetchPage()
      } catch (e) {
        this.prepareError = e.message || '未知错误'
        this.prepareResult = null
        this.$message.error(`建库建表失败：${this.prepareError}。未进入构建`)
      } finally {
        this.dbLoading = false
      }
    },

    /* ---------- 第④步：建表后独立确认构建 ---------- */
    async onRun() {
      try {
        await this.$confirm('将开始代码生成、打包、部署和健康检查，确认开始构建并启动？', '开始构建并启动', { type: 'warning' })
      } catch (e) {
        return
      }
      this.runLoading = true
      try {
        await runPipeline(this.detail.id)
        await this.loadDetail(this.detail.id)
        this.subscribeSse(this.detail.id)
      } catch (e) {
        this.$message.error(`启动构建失败：${e.message || '未知错误'}`)
      } finally {
        this.runLoading = false
      }
    },
    async onStop(row) {
      const id = row && row.id ? row.id : this.detail.id
      await this.$confirm('将停止后端与前端进程并置为已停止，确认？', '停止系统', { type: 'warning' })
      await stopPipeline(id)
      this.closeSse()
      if (this.detail && this.detail.id === id) {
        await this.loadDetail(id)
      }
      this.fetchPage()
    },
    openSystem() {
      this.openUrl(this.detail.frontendUrl)
    },
    openUrl(url) {
      if (url) window.open(url, '_blank')
    },
    stepsOf(side) {
      return (this.detail && this.detail.steps || []).filter(s => s.side === side)
    },
    /* M2.2 从步骤消息里提取质量门禁报告号（后端 gen 步骤会带「报告 #123」） */
    reportIdOf(step) {
      const m = step && step.message ? /报告\s*#(\d+)/.exec(step.message) : null
      return m ? m[1] : null
    },
    async openQualityReport(step) {
      const reportId = this.reportIdOf(step)
      if (!reportId) return
      this.qualityVisible = true
      this.qualityLoading = true
      this.qualityReport = null
      try {
        const res = await getQualityReport(reportId)
        this.qualityReport = res.data
      } catch (e) {
        this.$message.error(`加载质量门禁报告失败：${e.message || '未知错误'}`)
      } finally {
        this.qualityLoading = false
      }
    },
    async loadDetail(id) {
      const res = await getPipeline(id)
      this.detail = res.data
      if (this.detail.status === 'running') {
        this.subscribeSse(id)
      }
    },
    openDetail(row) {
      this.prepareResult = null
      this.resetLint()
      this.loadDetail(row.id)
      this.step = 3
    },

    /* ---------- V5.1 审计列检查/一键补齐 ---------- */
    resetLint() {
      this.lintChecked = false
      this.lintItems = []
    },
    async onLint() {
      if (!this.detail) return
      this.lintLoading = true
      try {
        const res = await lintDb(this.detail.dbName)
        this.lintItems = (res.data && res.data.items) || []
        this.lintChecked = true
      } finally {
        this.lintLoading = false
      }
    },
    async onLintFix() {
      if (!this.detail) return
      this.fixLoading = true
      try {
        const res = await fixPipelineLint(this.detail.id)
        this.lintItems = (res.data && res.data.items) || []
        this.lintChecked = true
        if (res.data && res.data.ok) {
          this.$message.success('补齐完成，复检全部合规')
        } else {
          this.$message.warning('部分表补齐失败，详见补齐结果列')
        }
      } finally {
        this.fixLoading = false
      }
    },

    async refreshDetail() {
      if (this.detail) await this.loadDetail(this.detail.id)
    },

    /* ---------- V7.0 AI 推荐预览与应用 ---------- */

    async openRecommend() {
      if (!this.detail) return
      this.recVisible = true
      this.recLoading = true
      this.recExists = false
      this.recData = { tables: [], menus: [], drops: [] }
      try {
        const res = await aiRecommendPreview(this.detail.id)
        const vo = res.data || {}
        this.recExists = !!vo.exists
        this.recData = {
          tables: vo.tables || [],
          menus: vo.menus || [],
          drops: vo.drops || []
        }
        // 勾选态由 AiRecommendDialog 内部按 manual 标记初始化，这里只管数据
      } finally {
        this.recLoading = false
      }
    },

    async onApplyRecommend(dto) {
      this.recApplyLoading = true
      try {
        const res = await aiRecommendApply(this.detail.id, dto)
        const n = res.data || 0
        this.$message.success(`已应用 ${n} 个字段，重新生成即可生效`)
        this.recVisible = false
        await this.refreshDetail()
      } finally {
        this.recApplyLoading = false
      }
    },

    /* ---------- SSE ---------- */
    subscribeSse(id) {
      this.closeSse()
      const base = process.env.VUE_APP_BASE_API || ''
      const url = `${base}/gen/pipeline/${id}/stream?access_token=${getToken()}`
      this.es = new EventSource(url)
      this.es.addEventListener('progress', e => {
        try {
          this.detail = JSON.parse(e.data)
        } catch (err) {
          /* 忽略半包 */
        }
        if (this.detail && ['success', 'failed', 'stopped'].includes(this.detail.status)) {
          this.closeSse()
          this.fetchPage()
        }
      })
      this.es.onerror = () => {
        // 断流不等于失败：终态前的断线由刷新兜底，避免误报
      }
    },
    closeSse() {
      if (this.es) {
        this.es.close()
        this.es = null
      }
    },

    /* ---------- 任务分页 ---------- */
    async fetchPage() {
      const res = await getPipelinePage({ page: this.page.page, size: this.page.size })
      this.pageData = res.data.list
      this.page.total = res.data.total
    }
  }
}
</script>

<style scoped>
.tip-bar { margin-bottom: 12px; }
.main-card { margin-bottom: 12px; }
.steps { margin-bottom: 18px; }
.module-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
.module-card { border-color: #dcdfe6; }
.module-head { display: flex; justify-content: space-between; align-items: center; }
.module-name { margin-bottom: 8px; }
.module-desc { margin-bottom: 10px; }
.function-title { color: #606266; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.function-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.module-progress { color: #409eff; font-size: 13px; margin: 10px 0; }
.sql-meta { display: flex; gap: 20px; color: #606266; font-size: 13px; margin-bottom: 8px; }
.sql-preview { max-height: 480px; overflow: auto; margin: 0; padding: 14px; border-radius: 4px; background: #1f2937; color: #d1e7dd; font-size: 12px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; user-select: text; }
.prepare-result { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.result-stat { padding: 14px; border: 1px solid #ebeef5; border-radius: 4px; text-align: center; background: #fafafa; }
.result-stat strong { display: block; color: #67c23a; font-size: 26px; line-height: 1.2; }
.result-stat span { color: #909399; font-size: 12px; }
.confirm-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin: 8px 0 12px; color: #606266; font-size: 13px;
}
.table-block { margin-bottom: 16px; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; }
.table-head { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.audit-note { color: #909399; font-size: 12px; margin-top: 6px; }
.seed-bar {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  background: #ecf5ff;
}
.seed-tip { color: #909399; font-size: 12px; margin-top: 8px; line-height: 1.6; }
.progress-head {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;
}
.sys-name { font-size: 16px; font-weight: 600; margin-right: 8px; }
.meta { color: #909399; font-size: 12px; margin-left: 8px; }
.track { margin-bottom: 12px; }
.track-title { font-size: 13px; color: #303133; font-weight: 600; margin-bottom: 6px; }
.track-steps { display: flex; flex-wrap: wrap; gap: 8px; }
.step-chip {
  display: inline-flex; align-items: center; gap: 6px;
  border: 1px solid #ebeef5; border-radius: 16px; padding: 4px 12px; font-size: 13px;
  background: #fafafa;
}
.step-chip i { font-size: 14px; }
.st-success { border-color: #b3e19d; background: #f0f9eb; color: #67c23a; }
.st-running { border-color: #a0cfff; background: #ecf5ff; color: #409eff; }
.st-failed { border-color: #fab6b6; background: #fef0f0; color: #f56c6c; }
.st-pending { color: #909399; }
.step-msg { color: #909399; font-size: 12px; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.st-report { cursor: pointer; }
.st-report:hover { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12); }
.step-report { color: #409eff; font-size: 12px; white-space: nowrap; }

/* M2.2 质量门禁报告弹层 */
.qg-body { min-height: 120px; }
.qg-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.qg-file { font-family: Consolas, Monaco, monospace; font-size: 12px; word-break: break-all; }
.qg-bad { color: #f56c6c; background: #fef0f0; padding: 1px 4px; border-radius: 3px; white-space: pre-wrap; word-break: break-all; }
.qg-good { color: #67c23a; background: #f0f9eb; padding: 1px 4px; border-radius: 3px; white-space: pre-wrap; word-break: break-all; }
.qg-empty { color: #909399; font-size: 13px; padding: 24px 0; text-align: center; }
.done-card { margin-top: 10px; }
.done-url { font-size: 14px; }
.fail-bar { margin-top: 10px; }
.history-card { margin-top: 12px; }
.danger-text { color: #f56c6c; }
.lint-card { border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; margin-bottom: 14px; }
.lint-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.lint-title { font-size: 14px; font-weight: 600; }

/* V7.0 AI 推荐预览 */
.rec-tip { font-size: 12px; color: #909399; margin-bottom: 10px; line-height: 1.6; }
.rec-table { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px 12px; margin-bottom: 10px; }
.rec-table-head { margin-bottom: 8px; }
.rec-table-head .meta { margin-left: 8px; }
.rec-fields .el-checkbox { margin-right: 16px; }
.rec-group { font-size: 12px; color: #909399; margin-right: 6px; }
.rec-col { display: flex; align-items: center; flex-wrap: wrap; padding: 3px 0 3px 14px; }
.rec-colname { display: inline-block; min-width: 120px; font-size: 13px; color: #606266; }
.rec-col .el-checkbox { margin-right: 16px; }
</style>
