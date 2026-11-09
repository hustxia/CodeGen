import request from '@/utils/request'

/**
 * 保存当前登录用户的界面主题偏好。
 *
 * 后端只落库、不返回整个用户对象，返回值就是归一化后的主题 ID。
 * 保存后不会立即改变当前页面 —— 按产品约定需重新登录才生效。
 */
export function saveUserTheme(themeId) {
  return request({
    url: '/user/theme',
    method: 'put',
    data: { themeId }
  })
}
