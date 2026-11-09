# 码匠 · 代码生成器（CodeGen）

从数据库表结构出发，产出可编译、可运行、带菜单权限的 Spring Boot + Vue 前后端业务代码，并覆盖写盘、备份、记账、回滚的完整交付闭环。

| 目录 | 说明 | 技术栈 |
| --- | --- | --- |
| `codegen-server/` | 后端：接表、字段配置、模板渲染、内存编译自检、差异预览、生成台账与回滚 | Spring Boot 3 + MyBatis-Plus + MySQL |
| `codegen-web/` | 前端控制台：表配置、批量接管向导、差异预览、模板在线编辑、系统管理 | Vue 2.7 + Element UI |

各目录内 `README.md` / `README.en.md` 有完整的功能说明与部署步骤，设计文档在 `codegen-server/docs/`。

## 快速开始

```bash
cd codegen-server && mvn clean package -DskipTests   # 默认端口 8120
cd codegen-web && npm install && npm run serve       # 代理目标见 .env.development
```

后端需先按 `src/main/resources/application.yml` 配置数据库；`application-local.yml` 用于本地覆盖，不应入库。

## 关于这份源码快照

- 已排除 `target/`、`node_modules/`、`dist/`、`logs/`、`uploads/`、本地 IDE/Trae/Claude 工具配置、诊断截图脚本等构建产物与本机私有文件，clone 后需自行构建。
- 保留 `.env.development` / `.env.production`（仅含本地端口与 API 前缀）、`package-lock.json`、运行必需的 `ip2region.db`，以及 CI 参考实现 `.github/workflows/verify.yml`。
- 文档与配置中的本机绝对路径已替换为相对写法；口令均为开发默认值（如 `root`）或通过环境变量注入，不是真实环境凭据。
- 本快照是工作区当前文件状态的一次提交，不含本地开发过程中的中间提交历史。
