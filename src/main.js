import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'

import App from './App.vue'
import router from './router'
import store from './store'
import hasPermi from './directive/permission'

import '@/styles/index.css'
import './permission'

Vue.use(ElementUI, { size: 'medium' })
Vue.directive('hasPermi', hasPermi)

Vue.config.productionTip = false

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
