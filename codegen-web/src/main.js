import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'

import App from './App.vue'
import router from './router'
import store from './store'
import hasPermi from './directive/permission'

// 顺序要求：theme.css 必须在 element 样式之后（覆盖它）、在 index.css 之前（提供变量）
import '@/styles/theme.css'
import '@/styles/index.css'
import { applyTheme, readThemeCache, DEFAULT_THEME } from '@/utils/theme'
import './permission'

Vue.use(ElementUI, { size: 'medium' })
Vue.directive('hasPermi', hasPermi)

Vue.config.productionTip = false

/**
 * 主题初始化。
 *
 * 首屏的变量注入已经由 public/index.html 的内联脚本完成（避免闪白），
 * 这里只负责补齐 data-theme / data-mode / data-hue / data-shape 四个属性 ——
 * 深色模式的补充样式靠 html[data-mode="dark"] 选择器命中，缺了属性会退化成浅色。
 *
 * persist 传 false：不重写缓存，避免把「无缓存时回落的默认主题」误存成用户偏好。
 */
const cached = readThemeCache()
applyTheme(cached ? cached.id : DEFAULT_THEME, { persist: false })

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
