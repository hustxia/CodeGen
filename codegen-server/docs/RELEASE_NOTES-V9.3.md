# V9.3 Release Notes — 生成产物自动化测试 / Generated-artifact Automated Testing

> 发布日期：2026-11-14　｜　Tag：`9.3`　｜　仓库：`hustxia/CodeGen`
> 承接：V9.2（测试中心 + 验证中心）　｜　规划与实现记录：[`代码生成器-V9.3-规划.md`](./代码生成器-V9.3-规划.md)

## Highlights / 本次交付

在 V9.2 验证中心（S1–S9，平台自身质量门禁）之上，新增对**生成产物**的自动化验证。验证中心从「只验平台」升级为「平台 + 产物」双对象。

| 套件 | 内容 | 依赖 |
| --- | --- | --- |
| **S10 产物单测** | 对 `TC_GEN_ROOT` 下的 Spring Boot 后端模块真跑 `mvn test`，解析 surefire-reports 并输出 `TC_METRICS` | JDK + Maven |
| **S11 产物接口测试** | 产物 Controller 接口测试 | Docker / TestContainers（缺失如实 `SKIPPED`） |
| **S12 产物 E2E** | 生成前端端到端 | Playwright（缺失如实 `SKIPPED`） |

- 新增 **`GEN` 触发器**、`TC_GEN_ROOT` 子进程环境变量注入、`autoVerifyGen` 写盘钩子（默认 `false`）。
- 新增 **`POST /tc/runs/gen`** 手动入口与验证中心「验证生成产物」操作区；手动触发忽略开关，始终跑 S10/S11/S12。
- 请求体 `TcGenRunRequest` **只接 `genRoot`**，不接收命令 / path 字段；前端按 `TcSuiteVO.genProduct` 渲染标识，不硬编码套件号。
- run / case / artifact 三层落库复用 V9.2 结构；`TcSuite` 套件数 9 → 12，默认勾选 7 → 8。

## Verification / 验收

- 新建 **设备资产管理系统**（`v93_asset_demo`，4 张业务表）真跑 12 步流水线：`status=success`、`failReason=None`、后端 `8140` / 前端 `9040` / health `200`。
- 用**真实生成产物**触发 GEN 验证：`TC20261114-00147`、`TC20261114-00109` 均 **PASSED**；S10 真跑产物自带的 4 个 ControllerTest，**20/20 用例全过**；S11/S12 因缺 Docker / Playwright 如实 **SKIPPED**。
- 三层落库核对：`tc_run` 1 行 + `tc_case_result` 3 行 + `tc_artifact` 3 个日志。
- `mvn -o compile` / `package`、`TcSuiteRegistryTest`、控制台 `npm run build` 全部通过。

## Bug fixes / 全流程实测修复

1. 自检 `compileTests()` 补「肥 jar 缺 classpath 诚实跳过」+ 基座测试源码加入 javac 编译单元。
2. 脚手架模板占位符 `__PKG_` → `__PKG__`；`vuePage.ftl` 尾部 `gen:end` 标记改 HTML 注释、9 处 `>>>` → `:deep()`。
3. `PipelineExecutor` 命令参数路径绝对化；`stepDeployFrontend()` 按 `package.json.scripts` 自动选 Vite `dev` / vue-cli `serve`；健康检查统一 IPv4；新增 `stopPrevious()` 清进程树。
4. 重跑用 `LambdaUpdateWrapper.set(col, null)` 显式清场，修复 MyBatis-Plus NOT_NULL 策略下 `fail_reason` 残留。
5. `GenMenuServiceImpl` 分组占位组件改用 `h(RouterView)`（Vue3 下 `h('router-view')` 会渲染成原生自定义元素导致空白页）。

以上已沉淀至坑库 `dev-doc/02-坑/`（坑 65–69）。

## Docs / 文档与多语言

- `docs/version-history.json` 真源从 V9.1 补齐到 V9.3（34 个版本、46 语言 V9.2/V9.3 译本）。
- 96 份多语言 README / 项目介绍、根 README、后端 README、中文项目介绍、ROADMAP 全部同步到 V9.3；新增 4 个幂等同步脚本。
- 修复 `render_version_history.mjs` 的 `lang === 'zh'` 大小写 bug（`README_ZH.md` 实际返回 `ZH`）。

## Upgrade / 升级说明

- 无新增数据库迁移脚本；沿用 V9.2 的 `tc_*` 三表与 `test_*` 七表。
- 新增配置项 `app.test-center.auto-verify-gen`（默认 `false`）。开启后每次生成写盘都会自动跑一次产物验证并落库。
- 手动验证入口：`POST /tc/runs/gen`，body `{"genRoot": "<产物根目录>"}`；或在验证中心页面填写产物根后点「验证生成产物」。
- `TC_GEN_ROOT` 需指向包含 Spring Boot 后端模块（`pom.xml`）的产物根目录。

## Known limitations / 已知限制

- S11 / S12 尚未真实接入：需 Docker + Playwright，且生成产物内置 TestContainers / RestAssured / Playwright 测试骨架；当前按设计如实 SKIP，不伪造通过。
- `autoVerifyGen` 默认关闭；本轮验收走人工触发路径。
- 验证中心结果仍不接合并阻断门禁（沿用 V9.2「先观测不阻断」口径）。
