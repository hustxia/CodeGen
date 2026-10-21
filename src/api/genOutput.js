import request, { toPageData } from '@/utils/request'

// 生成台账接口：codegen-server 单体服务（直接 /gen/output、/gen/verify）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

// 写盘台账分页（文件级一行一条，F13）
export function getGenOutputPage(params) {
  return request({
    url: '/gen/output/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

// 写盘批次摘要（按批次聚合，F13）
export function getGenOutputBatches(limit) {
  return request({
    url: '/gen/output/batches',
    method: 'get',
    params: { limit: limit || 20 }
  })
}

// 前端产物真校验（读磁盘实际文件静态解析，F14）
export function verifyGenOutput(ids) {
  return request({
    url: '/gen/verify',
    method: 'post',
    data: { ids }
  })
}
