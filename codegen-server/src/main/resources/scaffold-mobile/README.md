# __PROJECT__-mobile（移动端工程）

V6.5 由代码生成器脚手架产出的 **uni-app 单源移动端工程**：一套页面编译 H5 与微信小程序两端，与 PC 前端（`__PROJECT__-web`）共用同一后端与 Bearer token 体系。

## 目录速览

```
src/
  pages.json            静态页面注册（login/index/todo/mine + 动态页三件），业务表零注册
  pages/index           首页业务宫格：自动聚合 src/config/tables/**/*.js
  pages/dynamic/        动态页三件（list/detail/form），按表配置渲染
  pages/todo            我的待办（联动产物 /flow/todo，V6.1 流程基座）
  config/tables/        每张业务表的声明式配置（生成侧产出，勿手改）
  utils/request.js      Bearer 拦截器 + Result 解包 + 401 跳登录
```

## 命令

| 命令 | 作用 |
|---|---|
| `npm install` | 安装依赖（uni-app 3.0.0-5030320261023001 + vite 5） |
| `npm run dev:h5` | H5 本机调试，`/dev-api` 代理到后端（vite.config.js） |
| `npm run build:h5` | 出 H5 静态包（`dist/build/h5`） |
| `npm run build:mp-weixin` | 出小程序包（`dist/build/mp-weixin`） |

## 小程序联调与发布（诚实边界）

1. **本地联调**：微信开发者工具导入 `dist/build/mp-weixin` 后，在「详情 → 本地设置」勾选**「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**，否则本地 http 请求直接被拒。
2. **真机/线上**：小程序 `uni.request` 域名必须 **HTTPS** 且在 mp.weixin.qq.com 后台「开发管理 → 开发设置 → 服务器域名 → request 合法域名」登记。
3. **API 地址**：H5 走代理无需配置；小程序经 `.env`（如 `.env.production`）设 `VITE_API_BASE=https://你的域名` 指向后端完整地址。
4. **CI 只出包不发布**：流水线 `package_mobile` 步只构建出包；真机预览、上传与审核发布由人在微信开发者工具完成——生成器不出「一键上架」。

## 新增业务表怎么上移动端

平台侧开启 `mobile_enabled` → 表配置勾选前端页面 → 重新生成写盘。每张标准表（非从表/非树表）会得到一份 `src/config/tables/{模块}/{表名}.js`，首页宫格与动态页自动出现，pages.json 无需改动。
