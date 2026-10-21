import request from '@/utils/request'

// 字典接口（管理服务 shop-service-admin，经网关 /api/dict）
// 生成页面的下拉 / 单选 / 列表回显统一从这里取，对应库表 sys_dict_data
export function getDictData(code) {
  return request({
    url: `/api/dict/${code}`,
    method: 'get'
  })
}

/* ---------- 字典维护（字典管理页） ---------- */

// 字典类型列表（含每个类型的字典项数量）
export function listDictTypes(keyword) {
  return request({
    url: '/api/dict/type/list',
    method: 'get',
    params: { keyword }
  })
}

export function saveDictType(data) {
  return request({
    url: '/api/dict/type/save',
    method: 'post',
    data
  })
}

export function deleteDictType(id) {
  return request({
    url: `/api/dict/type/delete/${id}`,
    method: 'post'
  })
}

// 某类型的全部字典项（含停用）
export function listDictData(code) {
  return request({
    url: '/api/dict/data/list',
    method: 'get',
    params: { code }
  })
}

export function saveDictData(data) {
  return request({
    url: '/api/dict/data/save',
    method: 'post',
    data
  })
}

export function deleteDictData(id) {
  return request({
    url: `/api/dict/data/delete/${id}`,
    method: 'post'
  })
}

// 孤儿字典：有字典项但没有类型记录
export function listDictOrphans() {
  return request({
    url: '/api/dict/orphans',
    method: 'get'
  })
}

export function repairDictOrphans() {
  return request({
    url: '/api/dict/orphans/repair',
    method: 'post'
  })
}
