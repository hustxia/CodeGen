import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// V6.5 移动端：H5 dev 代理与 PC vue.config.js 同口径——/dev-api 剥前缀直连后端；
// 小程序无代理（request 域名须 HTTPS 白名单），联调期用 .env 设 VITE_API_BASE 指向完整地址
export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5273,
    proxy: {
      '/dev-api': {
        target: 'http://127.0.0.1:__API_PORT__',
        changeOrigin: true,
        // 与 PC vue.config.js 同口径：剥 /dev-api，再剥网关段 /api/__GW__（产物 Controller 是 /{module} 裸路径）
        rewrite: (path) => path.replace(/^\/dev-api(\/api\/__GW__)?/, '')
      }
    }
  }
})
