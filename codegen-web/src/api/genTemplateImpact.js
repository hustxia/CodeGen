import request from '@/utils/request'

// 模板文件清单（只读）：扫描 gen-templates / scaffold* 下的 *.ftl
export function getTemplateFiles() {
  return request({
    url: '/gen/template/files',
    method: 'get'
  })
}

// 模板影响面预览（只读）：template 为空表示「全部模板」聚合视图
export function getTemplateImpact(template) {
  const params = {}
  if (template) {
    params.template = template
  }
  return request({
    url: '/gen/template/impact',
    method: 'get',
    params
  })
}
