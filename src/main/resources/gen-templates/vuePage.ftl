<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">${comment}</h2>
        <p class="page-desc">维护${comment}数据，可按条件筛选、编辑与导出</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>
<#if vueQueryColumns?size gt 0>
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.native.prevent>
<#list vueQueryColumns as c>
        <el-form-item label="${c.commentLabel}">
<#if c.isSelect || c.isRadio>
          <el-select v-model="query.${c.javaField}" placeholder="请选择${c.commentLabel}" clearable style="width: 160px">
            <el-option v-for="d in <#if c.hasDict>dicts.${c.dictType} || []<#elseif c.hasEnum>enumMap.${c.javaField} || []<#else>[]</#if>" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
<#elseif c.isSwitch>
          <el-select v-model="query.${c.javaField}" placeholder="请选择${c.commentLabel}" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
<#elseif c.isNumberCtrl>
          <el-input-number v-model="query.${c.javaField}" controls-position="right" style="width: 160px" />
<#elseif c.isDate>
          <el-date-picker v-model="query.${c.javaField}" type="date" value-format="yyyy-MM-dd" placeholder="请选择${c.commentLabel}" style="width: 160px" />
<#elseif c.isDatetime>
          <el-date-picker v-model="query.${c.javaField}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择${c.commentLabel}" style="width: 200px" />
<#else>
          <el-input v-model="query.${c.javaField}" placeholder="${c.commentLabel}" clearable @keyup.enter.native="handleSearch" />
</#if>
        </el-form-item>
</#list>
        <el-form-item>
          <el-button v-hasPermi="['${permsPrefix}:query']" type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

</#if>
    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <div class="card-title">
          <span class="title">${comment}</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <div class="card-actions">
<#if genAddBtn>
          <el-button v-hasPermi="['${permsPrefix}:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增${comment}</el-button>
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
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
<#if genTree>
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
</#if>
      >
        <el-table-column prop="id" label="ID" width="<#if idColWidth gt 0>${idColWidth}<#else>70</#if>" />
<#list listColumns as c>
<#if c.isMultiImage && fileEnabled>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" width="${c.colWidth}" align="center">
          <template slot-scope="{ row }">
            <el-image v-if="firstImage(row.${c.javaField})" :src="firstImage(row.${c.javaField})" :preview-src-list="imageListOf(row.${c.javaField})" fit="cover" style="width: ${imageListSize}px; height: ${imageListSize}px; border-radius: 4px">
              <div slot="error"><img src="/placeholder.svg" alt="" style="width: 100%; height: 100%; object-fit: cover" /></div>
            </el-image>
            <span v-else>—</span>
          </template>
        </el-table-column>
<#elseif c.isImage && fileEnabled>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" width="${c.colWidth}" align="center">
          <template slot-scope="{ row }">
            <el-image v-if="row.${c.javaField}" :src="row.${c.javaField}" :preview-src-list="[row.${c.javaField}]" fit="cover" style="width: ${imageListSize}px; height: ${imageListSize}px; border-radius: 4px">
              <div slot="error"><img src="/placeholder.svg" alt="" style="width: 100%; height: 100%; object-fit: cover" /></div>
            </el-image>
            <span v-else>—</span>
          </template>
        </el-table-column>
<#elseif c.isFile && fileEnabled>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" min-width="${c.colWidth}" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <el-link v-if="row.${c.javaField}" type="primary" :href="row.${c.javaField}" target="_blank" icon="el-icon-document">{{ row.${c.javaField}.split('/').pop() }}</el-link>
          </template>
        </el-table-column>
<#elseif c.hasRef>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" min-width="${c.colWidth}" show-overflow-tooltip
          :formatter="(row) => refText('${c.javaField}', row.${c.javaField})" />
<#elseif c.isSwitch>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" width="${c.colWidth}" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.${c.javaField} === 1 ? 'success' : 'info'" size="mini">
              {{ row.${c.javaField} === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
<#elseif c.hasDict>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" min-width="${c.colWidth}" show-overflow-tooltip
          :formatter="(row) => dictText('${c.dictType}', row.${c.javaField})" />
<#elseif c.hasEnum>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" min-width="${c.colWidth}" show-overflow-tooltip
          :formatter="(row) => enumText('${c.javaField}', row.${c.javaField})" />
<#elseif c.isDatetime>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" width="${c.colWidth}" align="center" show-overflow-tooltip />
<#elseif c.isDate>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" width="${c.colWidth}" align="center" />
<#elseif c.isNumberCtrl || c.isNumber>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" width="${c.colWidth}" align="right" show-overflow-tooltip />
<#elseif c.isText || c.isTextarea>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" min-width="${c.colWidth}" show-overflow-tooltip />
<#else>
        <el-table-column prop="${c.javaField}" label="${c.commentLabel}" min-width="${c.colWidth}" show-overflow-tooltip />
</#if>
</#list>
        <el-table-column prop="updateTime" label="更新时间" width="170" align="center" />
<#if genEditBtn || genDeleteBtn || hasDetail || hasFlow || genPrint>
        <el-table-column label="操作" width="${opColWidth}" fixed="right" align="center">
          <template slot-scope="{ row }">
<#if hasDetail>
            <el-button type="text" @click="openDetail(row)">详情</el-button>
</#if>
<#if genPrint>
            <el-button type="text" @click="handlePrintRow(row)">打印</el-button>
</#if>
<#if hasFlow>
            <!--
              审批按钮按单据状态出现，避免用户对着「已通过」的单子再点提交。
              状态值约定：0 草稿 / 1 待审批 / 2 已通过 / 3 已驳回。
              服务端还会按流程节点配的角色再校验一次，这里只做界面收敛。
            -->
            <el-button v-if="row.${flowStatusField} === '0' || row.${flowStatusField} === '3'" type="text" @click="handleFlow('submit', row)">提交</el-button>
            <el-button v-if="row.${flowStatusField} === '1'" type="text" @click="handleFlow('approve', row)">审批</el-button>
            <el-button v-if="row.${flowStatusField} === '1'" type="text" class="danger-btn" @click="handleFlow('reject', row)">驳回</el-button>
            <el-button type="text" @click="openFlowRecords(row)">流程</el-button>
</#if>
<#if hasPayStatus>
            <!-- V6.3 支付接入约定：待支付的行给「去支付」入口，跳收银台并携带业务定位与金额（amount 列约定为分） -->
            <el-button v-if="row.payStatus === 0 || row.payStatus === '0'" type="text" @click="goPay(row)">去支付</el-button>
</#if>
<#if genEditBtn>
            <el-button v-hasPermi="['${permsPrefix}:edit']" type="text" @click="openDialog(row)">编辑</el-button>
</#if>
<#if genDeleteBtn>
            <el-button v-hasPermi="['${permsPrefix}:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
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
<#assign twoColForm = (formColumns?size gt 6)>
    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑${comment}' : '新增${comment}'" :visible.sync="dialogVisible" width="<#if hasChild>900px<#elseif twoColForm>760px<#else>560px</#if>" top="6vh" class="form-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-width="<#if twoColForm>96px<#else>110px</#if>">
<#if twoColForm>
        <el-row :gutter="20">
</#if>
<#list formColumns as c>
<#if twoColForm>
          <el-col :span="12">
</#if>
        <el-form-item<#if c.hasLink> v-if="link.visible('${c.javaField}')"</#if> label="${c.commentLabel}" prop="${c.javaField}">
<#if c.isMultiImage && fileEnabled>
          <multi-image-upload v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> />
<#elseif c.isImage && fileEnabled>
          <image-upload v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> />
<#elseif c.isFile && fileEnabled>
          <file-upload v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> />
<#elseif c.hasRef>
          <el-select v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> filterable remote clearable placeholder="输入关键字搜索${c.commentLabel}"
            :remote-method="kw => searchRef('${c.javaField}', '${c.refTable}', '${c.refLabel}', kw)"
            :loading="refLoading['${c.javaField}']" style="width: 100%">
            <el-option v-for="o in refOptions['${c.javaField}'] || []" :key="o.id" :label="o.label" :value="o.id" />
          </el-select>
<#elseif c.isSelect>
          <el-select v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> placeholder="请选择${c.commentLabel}" clearable style="width: 100%">
            <el-option v-for="d in <#if c.hasDict>dicts.${c.dictType} || []<#elseif c.hasEnum>enumMap.${c.javaField} || []<#else>[]</#if>" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
<#elseif c.isRadio>
          <el-radio-group v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if>>
            <el-radio v-for="d in <#if c.hasDict>dicts.${c.dictType} || []<#elseif c.hasEnum>enumMap.${c.javaField} || []<#else>[]</#if>" :key="d.value" :label="d.value">{{ d.label }}</el-radio>
          </el-radio-group>
<#elseif c.isSwitch>
          <el-switch v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> :active-value="1" :inactive-value="0" />
<#elseif c.isNumberCtrl>
          <el-input-number v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> controls-position="right" style="width: 100%" />
<#elseif c.isDate>
          <el-date-picker v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> type="date" value-format="yyyy-MM-dd" placeholder="请选择${c.commentLabel}" style="width: 100%" />
<#elseif c.isDatetime>
          <el-date-picker v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择${c.commentLabel}" style="width: 100%" />
<#elseif c.isTextarea>
          <el-input v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> type="textarea" :rows="3" placeholder="请输入${c.commentLabel}" />
<#else>
          <el-input v-model="form.${c.javaField}"<#if c.hasLink> :disabled="link.disabled('${c.javaField}')"</#if> placeholder="请输入${c.commentLabel}" />
</#if>
        </el-form-item>
<#if twoColForm>
          </el-col>
</#if>
</#list>
<#if twoColForm>
        </el-row>
</#if>
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
      <el-tabs v-model="activeChildTab" type="card" class="child-tabs">
<#list children as child>
        <el-tab-pane label="${child.comment}" name="${child.varName}">
          <div class="child-bar">
            <el-button type="primary" icon="el-icon-plus" size="mini" @click="addChildRow('${child.varName}')">添加明细</el-button>
            <span class="child-tip">主表与子表在同一事务中保存（先清后插）</span>
          </div>
          <el-table :data="${child.varName}Rows" border size="mini" class="child-table">
<#list child.formColumns as c>
            <el-table-column label="${c.commentLabel}" min-width="130">
              <template slot-scope="{ row }">
<#if c.isMultiImage && fileEnabled>
                <multi-image-upload v-model="row.${c.javaField}" />
<#elseif c.isImage && fileEnabled>
                <image-upload v-model="row.${c.javaField}" width="${imageListSize}px" height="${imageListSize}px" />
<#elseif c.isFile && fileEnabled>
                <file-upload v-model="row.${c.javaField}" />
<#elseif c.isSwitch>
                <el-switch v-model="row.${c.javaField}" :active-value="1" :inactive-value="0" />
<#elseif c.isNumberCtrl>
                <el-input-number v-model="row.${c.javaField}" controls-position="right" size="mini" style="width: 100%" />
<#elseif c.isDate>
                <el-date-picker v-model="row.${c.javaField}" type="date" value-format="yyyy-MM-dd" size="mini" style="width: 100%" />
<#elseif c.isDatetime>
                <el-date-picker v-model="row.${c.javaField}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" size="mini" style="width: 100%" />
<#else>
                <el-input v-model="row.${c.javaField}" size="mini" placeholder="${c.commentLabel}" />
</#if>
              </template>
            </el-table-column>
</#list>
            <el-table-column label="操作" width="<#if child.hasGrandChild>150<#else>70</#if>" align="center">
              <template slot-scope="{ row, $index }">
<#list child.grandChildren as gc>
                <el-button type="text" @click="openGrandRows('${child.varName}', '${gc.varName}', row)">${gc.comment}</el-button>
</#list>
                <el-button type="text" class="danger-btn" @click="${child.varName}Rows.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
<#if child.hasGrandChild>
          <div class="child-tip" style="margin-top: 6px">
            下级明细在点「确定」时与本级明细一起全量替换，弹窗里改完不必单独保存。
          </div>
</#if>
        </el-tab-pane>
</#list>
      </el-tabs>
</#if>

      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </div>
    </el-dialog>
<#if hasGrandDialog>

    <!-- 三级明细编辑：直接改选中行的下级数组（引用直改），保存时随主表一起提交 -->
    <el-dialog
      :title="grandTitle"
      :visible.sync="grandVisible"
      width="720px"
      append-to-body
    >
      <div class="child-bar">
        <el-button type="primary" icon="el-icon-plus" size="mini" @click="addGrandRow">添加{{ grandTitle }}</el-button>
      </div>
<#list children as child>
<#list child.grandChildren as gc>
      <el-table v-show="grandDialog === '${child.varName}.${gc.varName}'" :data="grandRows" border size="mini" class="child-table">
<#list gc.formColumns as c>
        <el-table-column label="${c.commentLabel}" min-width="130">
          <template slot-scope="{ row }">
<#if c.isMultiImage && fileEnabled>
            <multi-image-upload v-model="row.${c.javaField}" />
<#elseif c.isImage && fileEnabled>
            <image-upload v-model="row.${c.javaField}" width="${imageListSize}px" height="${imageListSize}px" />
<#elseif c.isFile && fileEnabled>
            <file-upload v-model="row.${c.javaField}" />
<#elseif c.isSwitch>
            <el-switch v-model="row.${c.javaField}" :active-value="1" :inactive-value="0" />
<#elseif c.isNumberCtrl>
            <el-input-number v-model="row.${c.javaField}" controls-position="right" size="mini" style="width: 100%" />
<#elseif c.isDate>
            <el-date-picker v-model="row.${c.javaField}" type="date" value-format="yyyy-MM-dd" size="mini" style="width: 100%" />
<#elseif c.isDatetime>
            <el-date-picker v-model="row.${c.javaField}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" size="mini" style="width: 100%" />
<#else>
            <el-input v-model="row.${c.javaField}" size="mini" placeholder="${c.commentLabel}" />
</#if>
          </template>
        </el-table-column>
</#list>
        <el-table-column label="操作" width="70" align="center">
          <template slot-scope="{ $index }">
            <el-button type="text" class="danger-btn" @click="grandRows.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
</#list>
</#list>
      <div slot="footer">
        <el-button @click="grandVisible = false">关闭</el-button>
      </div>
    </el-dialog>
</#if>
</#if>

<#if hasDetail>
    <!-- 详情抽屉（只读）。主子表：宽抽屉 + el-tabs，tab1 主表字段，tab2..N 每张明细一张只读表；
         数据复用 /with-children 一次拉全，不逐 tab 发请求 -->
    <el-drawer title="${comment}详情" :visible.sync="detailVisible" size="<#if hasChild>62%<#else>420px</#if>">
<#if hasChild>
      <el-tabs v-model="activeDetailTab" type="card" class="detail-tabs">
        <el-tab-pane label="主表信息" name="main">
          <div class="detail-body">
<#else>
      <div class="detail-body">
</#if>
<#if hasChild>            <div class="detail-row">
              <span class="detail-label">ID</span>
              <span class="detail-value">{{ detail.id }}</span>
            </div>
<#else>
        <div class="detail-row">
          <span class="detail-label">ID</span>
          <span class="detail-value">{{ detail.id }}</span>
        </div>
</#if>
<#list listColumns as c>
<#if hasChild>          <div class="detail-row">
            <span class="detail-label">${c.commentLabel}</span>
<#else>        <div class="detail-row">
          <span class="detail-label">${c.commentLabel}</span>
</#if>
<#if c.isMultiImage && fileEnabled>
<#if hasChild>            <span class="detail-value"><span v-if="imageListOf(detail.${c.javaField}).length"><el-image v-for="(u, i) in imageListOf(detail.${c.javaField})" :key="i" :src="u" :preview-src-list="imageListOf(detail.${c.javaField})" fit="cover" style="width: ${imageDetailSize}px; height: ${imageDetailSize}px; border-radius: 4px; margin-right: 6px" /></span><span v-else>—</span></span>
<#else>          <span class="detail-value"><span v-if="imageListOf(detail.${c.javaField}).length"><el-image v-for="(u, i) in imageListOf(detail.${c.javaField})" :key="i" :src="u" :preview-src-list="imageListOf(detail.${c.javaField})" fit="cover" style="width: ${imageDetailSize}px; height: ${imageDetailSize}px; border-radius: 4px; margin-right: 6px" /></span><span v-else>—</span></span>
</#if>
<#elseif c.isImage && fileEnabled>
<#if hasChild>            <span class="detail-value"><el-image v-if="detail.${c.javaField}" :src="detail.${c.javaField}" :preview-src-list="[detail.${c.javaField}]" fit="cover" style="width: ${imageDetailSize}px; height: ${imageDetailSize}px; border-radius: 4px"><div slot="error"><img src="/placeholder.svg" alt="" style="width: 100%; height: 100%; object-fit: cover" /></div></el-image><span v-else>—</span></span>
<#else>          <span class="detail-value"><el-image v-if="detail.${c.javaField}" :src="detail.${c.javaField}" :preview-src-list="[detail.${c.javaField}]" fit="cover" style="width: ${imageDetailSize}px; height: ${imageDetailSize}px; border-radius: 4px"><div slot="error"><img src="/placeholder.svg" alt="" style="width: 100%; height: 100%; object-fit: cover" /></div></el-image><span v-else>—</span></span>
</#if>
<#elseif c.isFile && fileEnabled>
<#if hasChild>            <span class="detail-value"><el-link v-if="detail.${c.javaField}" type="primary" :href="detail.${c.javaField}" target="_blank" icon="el-icon-document">{{ detail.${c.javaField}.split('/').pop() }}</el-link></span>
<#else>          <span class="detail-value"><el-link v-if="detail.${c.javaField}" type="primary" :href="detail.${c.javaField}" target="_blank" icon="el-icon-document">{{ detail.${c.javaField}.split('/').pop() }}</el-link></span>
</#if>
<#elseif c.hasRef>
<#if hasChild>            <span class="detail-value">{{ refText('${c.javaField}', detail.${c.javaField}) }}</span>
<#else>          <span class="detail-value">{{ refText('${c.javaField}', detail.${c.javaField}) }}</span>
</#if>
<#elseif c.isSwitch>
<#if hasChild>            <span class="detail-value">{{ detail.${c.javaField} === 1 ? '启用' : '停用' }}</span>
<#else>          <span class="detail-value">{{ detail.${c.javaField} === 1 ? '启用' : '停用' }}</span>
</#if>
<#elseif c.hasDict>
<#if hasChild>            <span class="detail-value">{{ dictText('${c.dictType}', detail.${c.javaField}) }}</span>
<#else>          <span class="detail-value">{{ dictText('${c.dictType}', detail.${c.javaField}) }}</span>
</#if>
<#elseif c.hasEnum>
<#if hasChild>            <span class="detail-value">{{ enumText('${c.javaField}', detail.${c.javaField}) }}</span>
<#else>          <span class="detail-value">{{ enumText('${c.javaField}', detail.${c.javaField}) }}</span>
</#if>
<#else>
<#if hasChild>            <span class="detail-value">{{ detail.${c.javaField} }}</span>
<#else>          <span class="detail-value">{{ detail.${c.javaField} }}</span>
</#if>
</#if>
<#if hasChild>          </div>
<#else>        </div>
</#if>
</#list>
<#if hasChild>          <div class="detail-row">
            <span class="detail-label">创建时间</span>
            <span class="detail-value">{{ detail.createTime }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">更新时间</span>
            <span class="detail-value">{{ detail.updateTime }}</span>
          </div>
          </div>
        </el-tab-pane>
<#list children as child>
        <el-tab-pane label="${child.comment}" name="${child.varName}">
          <el-table :data="detailGroupRows('${child.varName}')" border size="mini" max-height="520">
<#list child.listColumns as c>
            <el-table-column label="${c.commentLabel}" min-width="120" prop="${c.javaField}">
              <template slot-scope="{ row }">
<#if c.hasRef>
                {{ refText('${c.javaField}', row.${c.javaField}) }}
<#elseif c.isSwitch>
                {{ row.${c.javaField} === 1 ? '启用' : '停用' }}
<#elseif c.hasDict>
                {{ dictText('${c.dictType}', row.${c.javaField}) }}
<#elseif c.hasEnum>
                {{ enumText('${c.javaField}', row.${c.javaField}) }}
<#else>
                {{ row.${c.javaField} }}
</#if>
              </template>
            </el-table-column>
</#list>
          </el-table>
        </el-tab-pane>
</#list>
      </el-tabs>
<#else>
        <div class="detail-row">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ detail.createTime }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">更新时间</span>
          <span class="detail-value">{{ detail.updateTime }}</span>
        </div>
      </div>
</#if>
    </el-drawer>
</#if>

<#if hasExcelImport>
    <!-- 导入对话框 -->
    <el-dialog title="导入${comment}" :visible.sync="importVisible" width="480px">
      <div class="import-tpl-tip">
        表头必须与模板一致。模板第 2 行是填写说明，
        <b>填写真实数据前请先删掉说明行</b>。
        <el-button type="text" size="mini" :loading="tplLoading" @click="downloadTemplate">下载模板</el-button>
      </div>
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
        <div class="import-summary">
          共 {{ importResult.total }} 行：成功 <b class="ok">{{ importResult.success }}</b> 条，
          跳过 <b>{{ importResult.skipped || 0 }}</b> 条（已存在），
          失败 <b class="bad">{{ importResult.failedCount || 0 }}</b> 条
        </div>
        <div v-if="importResult.failedCount" class="import-fail-box">
          <div class="import-fail-head">
            <span>失败明细（显示前 20 条）</span>
            <el-button type="text" size="mini" @click="downloadFailed">下载全部明细</el-button>
          </div>
          <div v-for="(f, i) in importResult.failed.slice(0, 20)" :key="i" class="import-fail">
            第 {{ f.row }} 行：{{ f.msg }}
          </div>
          <div v-if="importResult.failedCount > 20" class="import-fail-more">
            还有 {{ importResult.failedCount - 20 }} 条未显示，请下载明细查看
          </div>
        </div>
      </div>
      <div slot="footer">
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
      </div>
    </el-dialog>
</#if>
<#if hasFlow>
    <!-- 提交/审批/驳回的确认框，顺便收一句意见 -->
    <el-dialog
      :title="flowAction === 'submit' ? '提交审批' : (flowAction === 'reject' ? '驳回' : '审批通过')"
      :visible.sync="flowDialogVisible"
      width="420px"
      append-to-body
    >
      <el-input
        v-model="flowComment"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        :placeholder="flowAction === 'reject' ? '请填写驳回理由' : '审批意见（选填）'"
      />
      <div slot="footer">
        <el-button @click="flowDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="flowSubmitting" @click="submitFlow">确定</el-button>
      </div>
    </el-dialog>
    <!-- 审批记录抽屉：流程流水是只追加的，看的就是「谁在什么时候把单子推到了哪一步」 -->
    <el-drawer title="${comment}审批记录" :visible.sync="flowVisible" size="480px">
      <div v-loading="flowLoading" class="detail-body">
        <div v-if="!flowRecords.length" class="flow-empty">还没有流转记录</div>
        <el-timeline v-else>
          <el-timeline-item
            v-for="(r, i) in flowRecords"
            :key="i"
            :timestamp="r.operTime"
            placement="top"
            :color="flowColor(r.action)"
          >
            <div class="flow-node">{{ flowActionText(r.action) }}　<span class="flow-status">{{ flowStatusText(r.toStatus) }}</span></div>
            <div class="flow-meta">{{ r.operName || '系统' }}<span v-if="r.comment">：{{ r.comment }}</span></div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
</#if>
    <!-- <gen:end key="vue-template"> -->
  </div>
</template>

<script>
import {
<#if genPageApi>  get${className}List<#if genTree || genAddApi || genEditApi || genDeleteApi || hasChild || hasRef || hasExcelExport || hasExcelImport || hasFlow>,</#if>
</#if>
<#if genTree>  get${className}Tree<#if genAddApi || genEditApi || genDeleteApi || hasChild || hasRef || hasExcelExport || hasExcelImport || hasFlow>,</#if>
</#if>
<#if hasRef>  get${className}Options<#if genAddApi || genEditApi || genDeleteApi || hasChild || hasExcelExport || hasExcelImport || hasFlow>,</#if>
</#if>
<#if hasChild>  get${className}WithChildren,
  save${className}WithChildren<#if genAddApi || genEditApi || genDeleteApi || hasExcelExport || hasExcelImport || hasFlow>,</#if>
</#if>
<#if hasExcelExport>  export${className}<#if genAddApi || genEditApi || genDeleteApi || hasExcelImport || hasFlow>,</#if>
</#if>
<#if hasExcelImport>  import${className}, download${className}Template<#if genAddApi || genEditApi || genDeleteApi || hasFlow>,</#if>
</#if>
<#if genAddApi>  create${className}<#if genEditApi || genDeleteApi || hasFlow>,</#if>
</#if>
<#if genEditApi>  update${className}<#if genDeleteApi || hasFlow>,</#if>
</#if>
<#if genDeleteApi>  delete${className}<#if hasFlow>,</#if>
</#if>
<#if hasFlow>
  flowSubmit${className},
  flowApprove${className},
  flowReject${className},
  get${className}FlowRecords,
</#if>
} from '@/api/${moduleName}'
<#if vueHasDict || hasSingleUnique || hasExcelImport>
// 字典兜底、唯一性查重、导入失败明细下载都直接用通用 request，不依赖宿主是否提供专用 api 模块
import request from '@/utils/request'
</#if>
<#if hasUpload>
// V6.6 文件能力包：页面里有 image/file 列才引入上传组件（fileEnabled 网控在模型层，未开时列按文本框渲染）
import ImageUpload from '@/components/ImageUpload.vue'
import FileUpload from '@/components/FileUpload.vue'
<#if hasMultiImage>
import MultiImageUpload from '@/components/MultiImageUpload.vue'
</#if>
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
// 每张子表的新行工厂：键 = 子表 varName（与后端 children[].key 一致）
const defaultChildMap = {
<#list children as child>
  '${child.varName}': () => ({<#list child.formColumns as c> ${c.javaField}: ${c.jsInit}<#if c_has_next>,</#if></#list> })<#if child_has_next>,</#if>
</#list>
}
<#if hasGrandDialog>
// 三级明细新行工厂：键 = '子表.varName'，与 grandDialog 状态对齐
const defaultGrandMap = {
<#list children as child>
<#list child.grandChildren as gc>
  '${child.varName}.${gc.varName}': () => ({<#list gc.formColumns as c> ${c.javaField}: ${c.jsInit}<#if c_has_next>,</#if></#list> })<#if child_has_next || gc_has_next>,</#if>
</#list>
</#list>
}
const grandTitleMap = {
<#list children as child>
<#list child.grandChildren as gc>
  '${child.varName}.${gc.varName}': '${gc.comment}'<#if child_has_next || gc_has_next>,</#if>
</#list>
</#list>
}
</#if>
</#if>

export default {
  name: '${className}',
<#if hasUpload>
  components: { ImageUpload, FileUpload<#if hasMultiImage>, MultiImageUpload</#if> },
</#if>
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
<#if hasChild>
      // V4.4 详情多 tab：children 组数组 [{key, comment, rows}]，与 /with-children 响应同构
      detailChildren: [],
      activeDetailTab: 'main',
</#if>
</#if>
<#if genTree>
      treeOptions: [],
</#if>
<#if hasRef>
      refOptions: {},
      refLoading: {},
</#if>
<#if hasRefHelper>
      // 列表/详情里把 xxxId 显示成名称用的字典：{ 字段名: { id: label } }
      refLabels: {},
</#if>
<#if hasChild>
      activeChildTab: '${(children[0].varName)!""}',
<#list children as child>
      ${child.varName}Rows: [],
</#list>
<#if hasGrandDialog>
      // 三级明细：grandRows 指向选中行的下级数组（直接改引用，弹窗里编辑等于改主结构），
      // grandDialog 记录当前弹的是哪组（'子表.varName'），决定显示哪张编辑表
      grandVisible: false,
      grandRows: [],
      grandTitle: '',
      grandDialog: '',
</#if>
</#if>
<#if hasExcelImport>
      importVisible: false,
      importing: false,
      importFiles: [],
      importResult: null,
      tplLoading: false,
</#if>
<#if hasFlow>
      flowVisible: false,
      flowLoading: false,
      flowRecords: [],
      flowRow: null,
      flowDialogVisible: false,
      flowAction: '',
      flowComment: '',
      flowSubmitting: false,
</#if>
<#if vueHasDict>
      dicts: {<#list dictCodes as code> ${code}: []<#if code_has_next>,</#if></#list> },
</#if>
<#if vueHasEnum>
      // 注释枚举内联在页面里：不依赖后端字典接口，页面秒开即可回显
      enumMap: {
<#list enumColumns as c>        ${c.javaField}: ${c.enumJs}<#if c_has_next>,</#if>
</#list>      },
</#if>
      rules: {<#list formColumns as c><#if c.hasVueRule>
        ${c.javaField}: ${c.vueRule},</#if></#list>
<#if hasSingleUnique>
        // P1-3 唯一性校验：失焦时异步查重，后端同样有兜底校验
<#list uniqueColumns as c>
        ${c.javaField}: [
          { required: ${c.isRequired?c}, message: '${c.commentLabel}不能为空', trigger: 'blur' },
          { validator: this.uniqueValidator('${c.javaField}'), trigger: 'blur' }
        ],</#list>
</#if>
      },
      form: defaultForm()<#if hasLink>,
      // P1-4 表单联动：由字段配置的 linkRule 驱动，visible/disabled/required 三类规则
      linkRules: [<#list formColumns as c><#if c.hasLink>{ field: '${c.javaField}', label: '${c.commentLabel}', rule: ${c.linkRule} }<#if c_has_next>,</#if></#if></#list>]<#if hasLink>,
      linkState: {}</#if></#if>
      // <gen:end key="vue-data">
    }
  },
  created() {
<#if vueHasDict>
    this.loadDicts()
</#if>
<#if hasRef>
    this.loadRefLabels()
</#if>
    this.fetchList()
  },
<#if hasLink>
  computed: {
    /**
     * P1-4 表单联动求值器。模板里用 link.visible(field) / link.disabled(field) 取状态，
     * 规则统一从 linkRules 读取，生成物里不写死任何业务字段名。
     * 规则格式：[{ field, label, rule: { when, op, value, effect, else } }]
     *   when  触发源字段（可与 field 相同）
     *   op    eq / ne / in / notIn / notEmpty / empty
     *   value 比对值（in / notIn 用数组）
     *   effect 满足条件时的效果：show / hide / enable / disable / required / optional
     *   else   不满足条件时的效果（缺省按 effect 的反义）
     */
    link() {
      const hit = field => this.linkState[field] || {}
      return {
        // 没配联动规则的字段恒显示、恒可用，行为与未启用该特性时完全一致
        visible: field => (this.hasLinkRule(field) ? hit(field).visible !== false : true),
        disabled: field => hit(field).disabled === true,
        required: field => hit(field).required === true
      }
    }
  },
  watch: {
    // P1-4 联动触发源变化时刷新状态；deep 保证对象/数组字段变化也能捕获
    form: {
      deep: true,
      handler() {
        this.applyLink()
      }
    }
  },
</#if>
  methods: {
    // <gen:begin key="vue-methods">
<#if hasLink>
    /**
     * P1-4 表单联动：按 linkRules 重新计算每个字段的 显隐/禁用/必填 状态。
     * 表单变化时由 watch(form) 触发，打开弹窗时由 openDialog 主动调用一次。
     */
    applyLink() {
      const next = {}
      this.linkRules.forEach(item => {
        const rule = item.rule || {}
        const source = rule.when || item.field
        next[item.field] = this.evalLink(rule, this.form[source])
      })
      Object.keys(next).forEach(field => {
        const state = next[field]
        this.$set(this.linkState, field, state)
        // 必填状态同步进校验规则；隐藏字段不参与校验，避免提交时被看不见的字段拦下
        if (!state.visible) {
          this.$set(this.rules, field, [])
        } else if (state.required) {
          this.$set(this.rules, field, [{ required: true, message: this.linkLabel(field) + '不能为空', trigger: 'blur' }])
        } else if (this.rules[field] && this.rules[field].some(r => r.required)) {
          this.$set(this.rules, field, [])
        }
      })
    },
    /**
     * 单个规则求值。
     * rule = { when, op, value, effect, else }
     *   when   触发源字段，缺省为字段自身
     *   op     eq / ne / in / notIn / notEmpty / empty
     *   value  eq/ne 用标量，in/notIn 用数组
     *   effect 条件成立时的效果：show/hide/enable/disable/required/optional
     *   else   条件不成立时的效果，缺省取 effect 的反义
     */
    evalLink(rule, value) {
      const op = rule.op || 'eq'
      const expect = rule.value
      const isBlank = v => v === '' || v === null || v === undefined
        || (Array.isArray(v) && v.length === 0)
      const same = v => String(v) === String(value)
      let matched = true
      if (op === 'eq') matched = same(expect)
      else if (op === 'ne') matched = !same(expect)
      else if (op === 'in') matched = (expect || []).some(same)
      else if (op === 'notIn') matched = !(expect || []).some(same)
      else if (op === 'notEmpty') matched = !isBlank(value)
      else if (op === 'empty') matched = isBlank(value)

      const state = { visible: true, disabled: false, required: false }
      const apply = effect => {
        if (effect === 'show') state.visible = true
        else if (effect === 'hide') state.visible = false
        else if (effect === 'enable') state.disabled = false
        else if (effect === 'disable') state.disabled = true
        else if (effect === 'required') state.required = true
        else if (effect === 'optional') state.required = false
      }
      const inverse = {
        show: 'hide', hide: 'show',
        enable: 'disable', disable: 'enable',
        required: 'optional', optional: 'required'
      }
      const hitEffect = rule.effect || 'show'
      apply(matched ? hitEffect : (rule.else || inverse[hitEffect] || 'hide'))
      return state
    },
    hasLinkRule(field) {
      return this.linkRules.some(item => item.field === field)
    },
    linkLabel(field) {
      const hit = this.linkRules.find(item => item.field === field)
      return hit ? hit.label : field
    },
</#if>
<#if hasSingleUnique>
    /**
     * P1-3 唯一性异步校验：字段失焦时查重。
     * 编辑场景要排除自身 id，否则「不改这个字段直接保存」会被自己挡住。
     */
    uniqueValidator(field) {
      return (rule, value, callback) => {
        if (value === '' || value === null || value === undefined) {
          callback()
          return
        }
        request({
          url: '${apiBase}/check-unique',
          method: 'get',
          params: { field, value, excludeId: this.form.id || undefined }
        }).then(res => {
          if (res.data === true) {
            callback(new Error(this.columnLabel(field) + '已存在'))
          } else {
            callback()
          }
        }).catch(() => {
          // 查重接口不可用时不阻断用户提交，后端保存时仍有兜底校验
          callback()
        })
      }
    },
    columnLabel(field) {
      const map = {<#list uniqueColumns as c> ${c.javaField}: '${c.commentLabel}'<#if c_has_next>,</#if></#list> }
      return map[field] || field
    },
</#if>
<#if hasMultiImage>
    // V6.8 多图字段：库里存 URL JSON 数组字符串。解析失败一律降级为空数组，
    // 不让一条脏数据把整页渲染打挂（列表/详情/详情预览三处共用这两个方法）。
    imageListOf(raw) {
      if (!raw) return []
      if (Array.isArray(raw)) return raw.filter(Boolean)
      try {
        const arr = JSON.parse(raw)
        return Array.isArray(arr) ? arr.filter(Boolean) : []
      } catch (e) {
        return []
      }
    },
    firstImage(raw) {
      const list = this.imageListOf(raw)
      return list.length ? list[0] : ''
    },
</#if>
<#if vueHasDict>
    async loadDicts() {
      const codes = [<#list dictCodes as code>'${code}'<#if code_has_next>, </#if></#list>]
      await Promise.all(codes.map(async code => {
        try {
          // 直接走通用 request，不依赖宿主工程是否提供 @/api/dict；
          // 宿主没有字典接口时这里会抛错，下面 catch 把选项降级为空数组，页面照常可用
          const res = await request({ url: '${apiRoot}/system/dict/data/type/' + code, method: 'get' })
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
<#if vueHasEnum>
    // 枚举翻译：值可能是数字也可能是字符串，统一按字符串比，避免 1 与 '1' 对不上
    enumText(field, value) {
      const hit = (this.enumMap[field] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null || value === '' ? '' : value)
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
    // 未开列表/树接口时没有数据可拉，但筛选区与分页条仍会渲染并绑定这些方法，
    // 缺一个就是点击即 TypeError: xxx is not a function —— 必须给齐空实现。
    async fetchList() {},
    handleSearch() {},
    handleReset() {},
    handleSizeChange() {},
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
    /**
     * 列表里的 xxxId 显示成名称。
     * 关联表通常是字典/客户/产品这类小表，一次性拉成 { id: 名称 } 比逐行查省得多；
     * 拉不到就退回显示原始 ID，页面不会因为关联表没权限/没数据而白屏。
     */
    loadRefLabels() {
<#list refColumns as c>
      this.fetchRefLabels('${c.javaField}', '${c.refTable}', '${c.refLabel}')
</#list>
    },
    async fetchRefLabels(field, refTable, label) {
      try {
        const res = await get${className}Options(refTable, label, '')
        const map = {}
        ;(res.data || []).forEach(o => { map[String(o.id)] = o.label })
        this.$set(this.refLabels, field, map)
      } catch (e) {
        this.$set(this.refLabels, field, {})
      }
    },
</#if>
<#if hasRefHelper>
    /**
     * 把 xxxId 显示成名称：主表列与子表明细列都会用到，定义跟随 hasRefHelper
     * （主表无引用列但子表外键有引用时也必须存在，否则明细行渲染崩溃）。
     */
    refText(field, id) {
      if (id === null || id === undefined || id === '') {
        return ''
      }
      const map = this.refLabels[field]
      return (map && map[String(id)]) || id
    },
</#if>
<#if hasDetail>
    openDetail(row) {
      this.detail = row || {}
      this.detailVisible = true
<#if hasChild>
      this.activeDetailTab = 'main'
      this.loadDetailChildren(row && row.id)
</#if>
    },
</#if>
<#if hasDetail && hasChild>
    /**
     * 详情抽屉的明细数据：复用 /with-children 一次拉全，不逐 tab 发请求
     */
    async loadDetailChildren(id) {
      this.detailChildren = []
      if (!id) return
      try {
        const res = await get${className}WithChildren(id)
        this.detailChildren = (res.data && res.data.children) || []
      } catch (e) {
        /* 拦截器已提示；明细保持为空 */
      }
    },
    detailGroupRows(key) {
      const group = this.detailChildren.find(g => g.key === key)
      return (group && group.rows) || []
    },
</#if>
<#if hasDialog>
    openDialog(row) {
      this.form = row ? { ...defaultForm(), ...row } : defaultForm()
<#if hasChild>
<#list children as child>
      this.${child.varName}Rows = []
</#list>
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
<#if hasLink>
        this.applyLink()
</#if>
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
<#if hasChild>
    async loadChildren(id) {
<#list children as child>
      this.${child.varName}Rows = []
</#list>
      try {
        const res = await get${className}WithChildren(id)
        // 后端 children: [{key, tableName, comment, rows}]；key 即子表 varName
        const groups = (res.data && res.data.children) || []
        groups.forEach(g => {
          if (this[g.key + 'Rows'] !== undefined) {
            this[g.key + 'Rows'] = g.rows || []
          }
        })
      } catch (e) {
        /* 拦截器已提示；保持空明细 */
      }
    },
    addChildRow(tab) {
      const factory = defaultChildMap[tab]
      this[tab + 'Rows'].push(factory ? factory() : {})
    },
<#if hasGrandDialog>
    /**
     * 打开某一行的下级明细。用 $set 保障数组存在，
     * 因为后端返回的 VO 里该字段可能为 null（没有下级时）。
     */
    openGrandRows(childVar, gcVar, row) {
      const field = gcVar + 's'
      if (!row[field]) {
        this.$set(row, field, [])
      }
      this.grandRows = row[field]
      this.grandDialog = childVar + '.' + gcVar
      this.grandTitle = grandTitleMap[this.grandDialog] || '下级明细'
      this.grandVisible = true
    },
    addGrandRow() {
      const factory = defaultGrandMap[this.grandDialog]
      this.grandRows.push(factory ? factory() : {})
    },
</#if>
</#if>
<#if genPrint>
    /**
     * 打印单据：跳转独立打印页（无后台导航壳，window.print 直出 A4）
     */
    handlePrintRow(row) {
      this.$router.push('/print/${moduleName}/' + row.id)
    },
</#if>
<#if hasPayStatus>
    /**
     * V6.3 去支付：跳收银台，携带业务定位（businessType/businessId）、标题与金额。
     * 金额约定取 amount 列（单位分）；表里没有 amount 列时收银台手动输入。
     */
    goPay(row) {
      this.$router.push({ path: '/pay/cashier', query: {
        subject: '${comment} ' + row.id,
        amount: row.amount !== undefined && row.amount !== null ? (row.amount / 100).toFixed(2) : '',
        businessType: '${tableName}',
        businessId: row.id,
        redirect: this.$route.fullPath
      } })
    },
</#if>
    async submitForm() {
      const valid = await this.$refs.form.validate().catch(() => false)
      if (!valid) return
      this.submitting = true
      try {
<#if hasChild>
        // 子表按组提交：与后端约定 null=不动该子表、[]=清空；编辑页全部组都提交（全量替换）
        const payload = { master: this.form }
<#list children as child>
        payload.${child.varName}Rows = this.${child.varName}Rows
</#list>
        await save${className}WithChildren(payload)
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
    }<#if hasExcelExport || hasExcelImport || hasFlow>,</#if>
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
    }<#if hasExcelImport || hasFlow>,</#if>
</#if>
<#if hasExcelImport>
    handleImportChange(file, fileList) {
      this.importFiles = fileList.slice(-1)
      this.importResult = null
    },
    /**
     * P2-4 下载空白模板：表头与后端 /import 的表头匹配逻辑同源生成，
     * 用户拿到的模板一定能导回去。
     */
    async downloadTemplate() {
      this.tplLoading = true
      try {
        const res = await download${className}Template()
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '${comment}导入模板.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.tplLoading = false
      }
    },
    async submitImport() {
      if (!this.importFiles.length) {
        this.$message.warning('请先选择文件')
        return
      }
      this.importing = true
      try {
        const res = await import${className}(this.importFiles[0].raw)
        this.importResult = res.data || { total: 0, success: 0, skipped: 0, failedCount: 0, failed: [] }
        const summary = '导入完成：成功 ' + this.importResult.success + ' 条'
          + (this.importResult.skipped ? '，跳过 ' + this.importResult.skipped + ' 条' : '')
          + (this.importResult.failedCount ? '，失败 ' + this.importResult.failedCount + ' 条' : '')
        if (this.importResult.failedCount) {
          this.$message.warning(summary)
        } else {
          this.$message.success(summary)
        }
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.importing = false
      }
    },
    /**
     * P1-5 失败明细下载：把「行号|原因」传给后端生成 xlsx，
     * 前端只负责触发浏览器下载，不在页面里堆几百行错误。
     */
    async downloadFailed() {
      const list = (this.importResult && this.importResult.failed) || []
      if (!list.length) {
        this.$message.warning('没有失败记录')
        return
      }
      try {
        const res = await request({
          url: '${apiBase}/import/failed',
          method: 'get',
          params: { msg: list.map(f => f.row + '|' + f.msg) },
          responseType: 'blob'
        })
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '${comment}导入失败明细.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
      } catch (e) { /* 拦截器已提示 */ }
    }<#if hasFlow>,</#if>
</#if>
<#if hasFlow>
    /**
     * 提交 / 审批 / 驳回共用一条通道，避免三份几乎一样的代码。
     * 审批意见走一个小弹窗，不逼用户在列表里用 prompt —— prompt 没法做多行和必填校验。
     */
    handleFlow(action, row) {
      this.flowAction = action
      this.flowRow = row
      this.flowComment = ''
      this.flowDialogVisible = true
    },
    async submitFlow() {
      if (!this.flowRow) return
      const map = {
        submit: { fn: flowSubmit${className}, text: '提交' },
        approve: { fn: flowApprove${className}, text: '审批通过' },
        reject: { fn: flowReject${className}, text: '驳回' }
      }
      const act = map[this.flowAction]
      if (!act) return
      this.flowSubmitting = true
      try {
        await act.fn(this.flowRow.id, this.flowComment)
        this.$message.success(act.text + '成功')
        this.flowDialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.flowSubmitting = false
      }
    },
    async openFlowRecords(row) {
      this.flowRow = row
      this.flowRecords = []
      this.flowVisible = true
      this.flowLoading = true
      try {
        const res = await get${className}FlowRecords(row.id)
        this.flowRecords = res.data || []
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.flowLoading = false
      }
    },
    /** 动作枚举转中文，后端只存 SUBMIT / APPROVE / REJECT */
    flowActionText(action) {
      return ({ SUBMIT: '提交', APPROVE: '审批通过', REJECT: '驳回' })[action] || action
    },
    flowStatusText(status) {
      return ({ '0': '草稿', '1': '待审批', '2': '已通过', '3': '已驳回' })[status] || status
    },
    flowColor(action) {
      return ({ SUBMIT: '#909399', APPROVE: '#67c23a', REJECT: '#f56c6c' })[action] || '#c0c4cc'
    }
</#if>
    // <gen:end key="vue-methods">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
  }
}
</script>

<style scoped>
/* <gen:begin key="vue-style"> */
.app-container {
  padding: 0;
  background: transparent;
}

/* ===== 页头 ===== */
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.2px;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8a94a6;
}

.page-head__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 4px;
}

.meta-pill {
  padding: 4px 12px;
  border-radius: 20px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 12px;
  font-weight: 500;
}

/* ===== 卡片 ===== */
.filter-card,
.table-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 6px 20px rgba(16, 24, 40, 0.05);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card >>> .el-card__body {
  padding: 18px 18px 4px;
}

.filter-card >>> .el-form-item {
  margin-right: 16px;
  margin-bottom: 14px;
}

.filter-card >>> .el-form-item__label {
  color: #5a6478;
  font-size: 13px;
}

.table-card >>> .el-card__header {
  padding: 16px 18px;
  border-bottom: 1px solid #eef1f7;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: baseline;
}

.card-title .title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.card-title .subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.card-actions >>> .el-button + .el-button {
  margin-left: 8px;
}

/* ===== 表格 ===== */
.data-table >>> .table-head-cell {
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table >>> td {
  padding: 11px 0;
}

.data-table >>> .el-table__row:hover > td {
  background: #f5f9ff;
}

.data-table >>> .el-button--text {
  padding: 4px 6px;
}

/* ===== 分页 ===== */
.pagination {
  margin-top: 18px;
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

.import-summary {
  margin-bottom: 8px;
}

.import-tpl-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #606266;
}

.import-summary .ok {
  color: #67c23a;
}

.import-summary .bad {
  color: #f56c6c;
}

.import-fail-box {
  max-height: 200px;
  overflow: auto;
  padding: 8px;
  background: #fdf6ec;
  border-radius: 4px;
}

.import-fail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-weight: 600;
}

.import-fail {
  color: #f56c6c;
  font-size: 12px;
  line-height: 20px;
}

.import-fail-more {
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
<#if hasFlow>

.flow-empty {
  padding: 40px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.flow-node {
  font-size: 14px;
  color: #303133;
}

.flow-status {
  font-size: 12px;
  color: #909399;
}

.flow-meta {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}
</#if>
</style>
/* <gen:end key="vue-style"> */
