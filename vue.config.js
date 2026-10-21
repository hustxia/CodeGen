'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const port = process.env.port || process.env.npm_config_port || 9530

module.exports = {
  publicPath: '/',
  outputDir: 'dist',
  assetsDir: 'static',
  lintOnSave: false,
  productionSourceMap: false,
  devServer: {
    port: port,
    open: true,
    // history 模式必须开：否则刷新 /gen/table 这类深层链接直接 404
    historyApiFallback: true,
    proxy: {
      // 开发环境代理：/dev-api -> codegen-server（单体服务，无网关）
      [process.env.VUE_APP_BASE_API]: {
        target: process.env.VUE_APP_PROXY_TARGET || 'http://127.0.0.1:8120',
        changeOrigin: true,
        pathRewrite: {
          ['^' + process.env.VUE_APP_BASE_API]: ''
        }
      }
    }
  },
  configureWebpack: {
    name: 'codegen-web',
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },
    // 屏蔽 vue-loader@17 + Vue 2.7 scoped style 的已知无害告警（仅 dev 模式出现）
    ignoreWarnings: [
      /export 'default' \(imported as 'style\d+'\) was not found/
    ]
  }
}
