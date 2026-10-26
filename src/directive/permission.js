import store from '@/store'

/**
 * 按钮级权限指令：v-hasPermi="['demo_goods:add']"
 *
 * 权限码由后端 /auth/info 下发，超管为通配 '*:*:*'。
 * 无权限时直接把元素从 DOM 移除（与 Element UI 的 el-button 兼容）。
 */
function checkPermission(el, binding) {
  const { value } = binding
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error('v-hasPermi 需要数组参数，例如 v-hasPermi="[\'demo_goods:add\']"')
  }
  const permissions = store.getters.permissions || []
  if (permissions.includes('*:*:*')) {
    return
  }
  const allowed = value.some(perm => permissions.includes(perm))
  if (!allowed && el.parentNode) {
    el.parentNode.removeChild(el)
  }
}

export default {
  inserted(el, binding) {
    checkPermission(el, binding)
  }
}
