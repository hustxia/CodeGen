import request, { toPageData } from '@/utils/request'

// ${comment}接口：${tableName}<#if isMicro>（经平台网关 /${moduleName}）<#elseif gatewayPrefix?? && gatewayPrefix != ''>（经网关 /api/${gatewayPrefix}）</#if>
// <gen:begin key="api-fns">
<#if genPageApi>function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
</#if>
<#if genPageApi>export function get${className}List(params) {
  return request({
    url: '${apiBase}/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
</#if>
<#if genTree>export function get${className}Tree() {
  return request({
    url: '${apiBase}/tree',
    method: 'get'
  })
}
</#if>
<#if hasRef>export function get${className}Options(refTable, label, keyword) {
  return request({
    url: '${apiBase}/options',
    method: 'get',
    params: { refTable, label, keyword }
  })
}
</#if>
<#if hasChild>export function get${className}WithChildren(id) {
  return request({
    url: `${apiBase}/${'$'}{id}/with-children`,
    method: 'get'
  })
}

// payload = { master, ...各子表 {childVar}Rows }；子表字段 null=不动，[]=清空
export function save${className}WithChildren(payload) {
  return request({
    url: '${apiBase}/save-with-children',
    method: 'post',
    data: payload
  })
}
</#if>
<#if hasExcelExport>export function export${className}(params) {
  return request({
    url: '${apiBase}/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
</#if>
<#if hasExcelImport>export function import${className}(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '${apiBase}/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 空白导入模板（表头 + 说明行），让用户不用猜列名
export function download${className}Template() {
  return request({
    url: '${apiBase}/import-template',
    method: 'get',
    responseType: 'blob'
  })
}
</#if>
<#if genDetailApi>export function get${className}Detail(id) {
  return request({
    url: `${apiBase}/${'$'}{id}`,
    method: 'get'
  })
}
</#if>
<#if genAddApi>export function create${className}(data) {
  return request({
    url: '${apiBase}',
    method: 'post',
    data
  })
}
</#if>
<#if genEditApi>export function update${className}(id, data) {
  return request({
    url: '${apiBase}',
    method: 'put',
    data: { ...data, id }
  })
}
</#if>
<#if genDeleteApi>export function delete${className}(id) {
  return request({
    url: `${apiBase}/${'$'}{id}`,
    method: 'delete'
  })
}
</#if>
<#if hasFlow>// P3-3 审批流：四个动作走宿主统一的 /flow 接口，业务表只负责把 id 传过去
export function flowSubmit${className}(id, comment) {
  return request({
    url: '/flow/submit',
    method: 'post',
    data: { flowCode: '${flowCode}', bizId: id, comment }
  })
}

export function flowApprove${className}(id, comment) {
  return request({
    url: '/flow/approve',
    method: 'post',
    data: { flowCode: '${flowCode}', bizId: id, comment }
  })
}

export function flowReject${className}(id, comment) {
  return request({
    url: '/flow/reject',
    method: 'post',
    data: { flowCode: '${flowCode}', bizId: id, comment }
  })
}

export function get${className}FlowRecords(id) {
  return request({
    url: '/flow/records',
    method: 'get',
    params: { flowCode: '${flowCode}', bizId: id }
  })
}
</#if>
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====
