import request from '@/utils/request'

// 字典接口（codegen-server 单体服务，直接 /dict）
// 生成页面的下拉 / 单选 / 列表回显统一从这里取，对应库表 sys_dict_data
export function getDictData(code) {
  return request({
    url: `/dict/${code}`,
    method: 'get'
  })
}

/* ---------- 字典维护（字典管理页） ---------- */

// 字典类型列表（含每个类型的字典项数量）
export function listDictTypes(keyword) {
  return request({
    url: '/dict/type/list',
    method: 'get',
    params: { keyword }
  })
}

export function saveDictType(data) {
  return request({
    url: '/dict/type/save',
    method: 'post',
    data
  })
}

export function deleteDictType(id) {
  return request({
    url: `/dict/type/delete/${id}`,
    method: 'post'
  })
}

// 某类型的全部字典项（含停用）
export function listDictData(code) {
  return request({
    url: '/dict/data/list',
    method: 'get',
    params: { code }
  })
}

export function saveDictData(data) {
  return request({
    url: '/dict/data/save',
    method: 'post',
    data
  })
}

export function deleteDictData(id) {
  return request({
    url: `/dict/data/delete/${id}`,
    method: 'post'
  })
}

// 孤儿字典：有字典项但没有类型记录
export function listDictOrphans() {
  return request({
    url: '/dict/orphans',
    method: 'get'
  })
}

export function repairDictOrphans() {
  return request({
    url: '/dict/orphans/repair',
    method: 'post'
  })
}
