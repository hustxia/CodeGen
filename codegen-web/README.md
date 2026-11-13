# 代码生成器前端（CodeGen Web）

代码生成平台的**平台控制台**。Vue 2.7 + Element UI，对接 `codegen-server`（默认 `http://127.0.0.1:8120`）。

> **技术栈说明**：本仓库是平台自身的控制台，仍为 Vue 2.7 + Element UI + Vue CLI 5。V9.0 起**生成物**（`codegen-server` 脚手架产出的目标项目）已升级为 **Vue 3 + Element Plus + Vite**（`package.json` / `vite.config.js` / `src/main.js` / `index.html`）。两者互不耦合，请勿把本仓库的依赖当成生成物的技术栈。

## 功能

| 模块 | 说明 |
|---|---|
| 代码生成 | 表配置管理、列级控件/校验/字典配置、批量接管向导 |
| 差异预览 | 生成物与磁盘现状 diff，确认后再落盘 |
| 自检 | 内存编译检查结果、结构与权限一致性问题清单 |
| 生成台账 | 批次历史、产出文件四态（新建/合并/跳过/失败）、回滚 |
| 代码模板 | 12 个模板在线编辑、版本历史与回滚 |
| 系统管理 | 用户、角色、菜单、字典 |

侧边栏菜单由后端 `/auth/routers` 下发，前端按 `component` 字符串从 `src/views` 动态解析，**加页面不用改路由文件**。

## 快速开始

```bash
npm install
npm run serve
```

开发端口 **9530**，`/dev-api` 代理到后端 8120。

先启动后端：

```bash
cd ../codegen-server
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
mvn package -DskipTests && java -jar target/codegen-server.jar
```

默认账号 `admin / admin123`。

## 环境变量

| 文件 | 变量 | 说明 |
|---|---|---|
| `.env.development` | `VUE_APP_BASE_API` | 接口前缀，默认 `/dev-api` |
| | `VUE_APP_PROXY_TARGET` | 代理目标，默认 `http://127.0.0.1:8120` |
| `.env.production` | `VUE_APP_BASE_API` | 生产接口前缀，按部署环境修改 |

本机覆盖放 `.env.local` / `.env.development.local`（已忽略，不入库）。

## 构建

```bash
npm run build
```

产物在 `dist/`。history 模式部署需服务端配置 fallback 到 `index.html`。

## 目录

```
src/
├── api/         接口封装（gen / genTpl / genOutput / system / user / dict）
├── layout/      主框架（侧边栏从菜单树派生）
├── router/      constantRoutes + 运行时动态注册
├── store/       user / permission / app
├── utils/       request（axios + toPageData）、auth
└── views/
    ├── gen/     table（表配置）/ wizard（批量接管）/ template / output
    ├── system/  user / role / menu / dict
    └── login/   登录页
```

## 说明

生成业务页面时会用到 `@/api/dict` 或直接走 `@/utils/request`，取决于模板版本；当前模板已去掉对 `@/api/dict` 的硬依赖，宿主工程没有字典接口时下拉会自动降级为空选项。
