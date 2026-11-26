# 码匠 CodeGen — 从 DDL 到跑起来的 Spring Boot + Vue 系统

**[在线演示](https://hustxia.github.io/CodeGen/)** · **[GitHub](https://github.com/hustxia/CodeGen)** · **[English](README.md)**

[![version](https://img.shields.io/badge/V9.19-blue)](https://github.com/hustxia/CodeGen/releases) [![verify](https://github.com/hustxia/CodeGen/actions/workflows/verify.yml/badge.svg?branch=main)](https://github.com/hustxia/CodeGen/actions/workflows/verify.yml) [![i18n contract](https://github.com/hustxia/CodeGen/actions/workflows/i18n-contract.yml/badge.svg?branch=main)](https://github.com/hustxia/CodeGen/actions/workflows/i18n-contract.yml) [![tests 574](https://img.shields.io/badge/tests-574%20passing-green.svg)](#十一文档导航) [![License: MulanPSL-2.0](https://img.shields.io/badge/License-MulanPSL--2.0-blue.svg)](LICENSE)

---

> **从 DDL 到一个跑得起来的系统 —— 全部真实截图，绝非示意图。**
> 导入数据库表结构，一次生成可编译的 Spring Boot + Vue3 全栈代码，
> 带菜单、权限、多语言。前后端零漂移。

[![CodeGen 一句话建系统全流程动图](docs/images/showcase/demo.gif)](docs/images/showcase/demo.gif)

---

## 60 秒启动

```bash
# 1. 建库 + 初始化
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql

# 2. 跑后端
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
mvn package -DskipTests && java -jar target/codegen-server.jar

# 3. 跑前端
cd ../codegen-web && npm install && npm run serve
# 打开 http://localhost:9530  →  admin / admin123
```

---

## 和同类生成器 / 低代码平台的区别

| 维度 | 传统生成器 | 低代码平台 | CodeGen |
|---|---|---|---|
| 输入 | 单表配置 | 拖拽建模 | **DDL / 直连数据库** |
| 前后端漂移 | 手工对齐 | 平台锁死 | **同一份元数据 → 零漂移** |
| 编译自检 | 少见 | 平台内部 | **真实 `javac` 自检** |
| 存量合并 | 覆盖风险 | 平台锁死 | **标记块 + 三路合并** |
| 回滚 | 无 | 平台快照 | **四态账本 + 一键回滚** |
| 国际化 | 事后补 | 部分 | **206 语言框架，12 种专业翻译** |
| 交付物 | 代码片段 | 平台应用 | **可编译、可运行的完整工程** |

**跳转：** [实战截图](#实战截图-用-codegen-生成的系统) · [快速上手](#十快速上手) · [文档导航](#十一文档导航) · [Release 订阅](https://github.com/hustxia/CodeGen/releases)

---

> **多语言支持**：生成的系统内置整页国际化，框架覆盖 **206 种语言**；其中 **12 种为专业翻译**（zh-CN、en-US、ja-JP、ko-KR、fr-FR、de-DE、es-ES、ar-SA、ru-RU、pt-BR、id-ID、vi-VN），其余语言统一回退到简体中文。想贡献翻译？见 [`多语言项目介绍/CONTRIBUTING.md`](多语言项目介绍/CONTRIBUTING.md)。


## 实战截图 · 用 CodeGen 生成的系统

> 以下都是本生成器端到端生成的**真实可运行系统**——从数据库建表到前端页面、菜单与权限一应俱全。绝非示意图：每张图对应的列表都灌有真实数据。点击图片可查看大图。

**智慧警务 · 64 页 / 79 表** —— 依据功能设计文档一句话建系统，覆盖辖区数据、接处警记录、重点场所台账。

| 110 接警与警情登记 | 重点场所台账 |
| :---: | :---: |
| [![110接警](docs/images/showcase/police-110-dispatch.png)](docs/images/showcase/police-110-dispatch.png) | [![重点场所](docs/images/showcase/police-key-places.png)](docs/images/showcase/police-key-places.png) |

**服装 ERP · 71 页** —— 款色码矩阵、BOM 物料清单、缸号批次追溯、售罄率动销分析。

| 款色码 SKU | 售罄率与翻单分析 |
| :---: | :---: |
| [![款色码SKU](docs/images/showcase/garment-sku.png)](docs/images/showcase/garment-sku.png) | [![售罄率](docs/images/showcase/garment-sellthrough.png)](docs/images/showcase/garment-sellthrough.png) |

**智慧人力资源 · 68 页** —— 组织人事、招聘、考勤、薪酬、绩效共 14 个 HR 业务域。

| 薪资核算 | 面试安排 |
| :---: | :---: |
| [![薪资核算](docs/images/showcase/hr-payroll.png)](docs/images/showcase/hr-payroll.png) | [![面试安排](docs/images/showcase/hr-interview.png)](docs/images/showcase/hr-interview.png) |

**制造业 MES · 68 页** —— 生产工单、质量门禁、设备管理、批次序列号追溯、安灯异常。

| 生产工单 | 批次追溯链路 |
| :---: | :---: |
| [![生产工单](docs/images/showcase/mes-work-order.png)](docs/images/showcase/mes-work-order.png) | [![追溯链路](docs/images/showcase/mes-traceability.png)](docs/images/showcase/mes-traceability.png) |

> 每套系统都走过同一条 12 步流水线：DDL 预览 → 建库 → 生成 → 灌数据 → 全页验收（零空列表、零页面错误）。实战博文见[文档导航](#十一文档导航)。

---

## 一、这是什么

CodeGen 是一套“以表为源”的代码工厂。你只要把业务表接进来（连库导入或贴一段 DDL），配置好字段与生成开关，点一下“生成”，它就替你把后端 Entity/Mapper/Service/Controller/DTO/VO、前端 api.js 与 Vue 页面、菜单与路由、甚至 A4 打印页和集成测试骨架全部生成好，并安全地写进你的目标工程。

它不只“吐代码”，而是一条**带质量门禁与回滚能力的交付流水线**：

```
接表（库导入 / DDL）
   → 字段与生成开关配置（含 AI 推荐）
      → 一键生成：自检 → 备份 → 写盘 → 编译/校验 → 注册菜单 → 重建路由
         → 台账记账 → 可回滚
```

---

## 二、用户是谁，会怎么用

| 使用者     | 典型诉求                     | 平台给的路径                  |
| ------- | ------------------------ | ----------------------- |
| 后端开发    | 手上有一批表（或 DDL），想快速拉起 CRUD | 表配置页：导入 → 配字段 → 一键生成    |
| 全栈/独立开发 | 想从零起一个完整系统（含登录、权限、字典）    | 流水线页：一句话建系统             |
| 团队负责人   | 想统一代码风格、代码规范、目录结构        | 系统管理：一套系统 = 一套输出配置，批量生效 |
| 平台维护者   | 想改模板而不改代码                | 模板管理：在线编辑，保存即生效，支持历史回滚  |

---

## 三、解决什么问题

| 痛点 | CodeGen 的做法 |
| --- | --- |
| 重复 CRUD 地狱，每张表都要手写一堆样板代码 | 表级开关一键产出完整前后端，含增删改查、分页、校验、导入导出 |
| 前后端字段/接口对不齐，联调反复改 | 同一份表元数据同时驱动后端接口与前端页面，字段语义零偏差 |
| 团队规范难以统一，每人写出来的风格各异 | 模板即规范，22 个 FreeMarker 模板集中管控、支持在线编辑与版本回滚 |
| 生成器“覆盖即丢失”，手写逻辑被冲掉 | 标记块（marker block）合并机制：手写代码被保留，只替换生成区 |
| 生成物不可信，编译才发现问题 | 内存编译自检（真实 javac）、结构契约检查、磁盘 diff 预览，编译不过不准落盘 |
| 新系统从 0 搭脚手架费时 | 流水线“一句话建系统”：建库→脚手架→生成→打包→注册菜单全自动 |

---

## 四、核心能力全景

- **接表**：DDL 解析、库表直连导入、批量接管向导、列配置自动推导（控件 / 校验 / 字典 / 关联）。
- **表形态**：单表、树形表（tree 接口 + 树表格）、主子表（同事务保存 + 明细 Tab）、多对多中间表。
- **生成产物**：Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue 页面 + A4 打印页 + 集成测试骨架。
- **业务增强**：关联下拉、字典翻译、脱敏、唯一性校验、表单联动、数据权限、Excel 导入导出、审批流、支付 / 文件 / 移动端能力包，以及 V9.15 数据可视化图表包（`chart_enabled` 三态开关；饼/线/柱/仪表盘纯由列元数据推导、零领域关键词；每表 `/{module}/stats` 聚合接口 + 生成期字段白名单；ECharts 图卡与跨表看板、系列色跟随 `--app-primary`；关闭时产物零 diff）。
- **质量门禁**：内存编译自检（真实 `javac`）、结构契约检查、权限一致性检查、磁盘 diff 预览、前端产物真校验。
- **安全落地**：写盘 + `.gen-backup` 批次备份、标记块合并保留手写代码、`gen_output` 四态台账（新建 / 合并 / 跳过 / 失败）、历史回滚。
- **多系统隔离**：每套目标工程是一份“系统配置”（前后端路径、包名、网关前缀、依赖 classpath、生成开关默认值），可并行服务多个项目。
- **AI 增强**：一句话建系统（AI 建模）、种子数据 AI 增强、表级 AI 推荐配置（diff 预览 + 勾选应用）。

---

## 五、典型场景

| 场景 | 说明 |
| --- | --- |
| 树形表 | 商品分类等层级数据，自动产出 tree 接口与树表格、上级选择下拉 |
| 单表 CRUD | 客户、商品等，外键下拉 + 字典翻译 + 导入导出开箱即用 |
| 主子表 | 销售订单 + 订单明细，同事务保存，详情抽屉双 Tab（主表信息 / 明细） |
| 只读 / 统计 | 库存流水等，关闭增删改开关，仅保留查询与导出 |
| 打印单据 | 勾选“打印”开关，产出 A4 打印页与路由，支持表头 + 明细表 |
| 一句话建系统 | 描述业务域，AI 建库建表 + 脚手架 + 全量生成 + 注册菜单一气呵成 |

> 以上场景均有可运行演示（`demo_admin` 工程）与配套截图，详见 `docs/典型场景操作指南-演示后台demo.md`。

---

## 六、核心工作流

```
┌──────────┐   ┌──────────────┐   ┌───────────────────────────────────────┐
│  接表     │ → │  字段/开关配置 │ → │  一键生成（自检→备份→写盘→编译→菜单→路由）│ → │ 台账 · 回滚 │
└──────────┘   └──────────────┘   └───────────────────────────────────────┘
   库导入/DDL      控件/校验/字典       质量门禁全过才落盘，手写代码保留           批次级追溯
```

- **差异预览**：生成物与磁盘现状逐文件 diff，确认后再落盘，避免误覆盖。
- **生成台账**：每个文件记录动作、字节数、MD5、备份位置，批次可整体回滚。

---

## 七、一句话建系统（Pipeline）

面向“从零搭一个后台”的高阶能力。描述业务域后，流水线按 5 个阶段全自动推进：

| 阶段 | 含义 |
| --- | --- |
| `CHECK` | 校验目标工程配置与依赖 classpath |
| `WRITE` | 建库、建表、写脚手架与生成物 |
| `COMPILE` | 后端 `mvn package` / 前端构建，内存编译自检 |
| `MENU` | 产出并注册菜单权限（双出口：artifact SQL / internal 直写） |
| `ROUTE` | 重建前端路由片段 |

任一阶段失败即中断并给出可读错误，便于定位。

---

## 八、系统架构

前后端分离，两个仓库协同：

| 仓库 | 角色 | 技术栈 |
| --- | --- | --- |
| `codegen-server` | 后端：生成引擎、模板、校验、台账、流水线 | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | 前端：控制台（表配置 / 模板 / 台账 / 系统管理） | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router(history) |

后端分层：

```
controller/   接口层（Gen* 生成链路 / Sys* 系统管理 / 流水线）
service/impl/ 核心实现（生成链路、模板引擎、合并、校验、流水线执行）
util/         模板引擎、模型装配、标记块合并、自检编译
config/       输出配置、自检配置、模板种子器
common/       Result / BaseEntity / 异常 / 审计
resources/gen-templates/   22 个 FreeMarker 模板（backend / frontend / shared / test 分组）
```

**目标工程必须自带的基座能力**（生成器只产业务代码，基座缺失会静默故障）：

1. MyBatis-Plus 分页插件（`PaginationInnerInterceptor`）
2. `Result` / `BaseEntity` / `PageQuery` 等基座类（包名前缀与 `root-package` 一致）
3. 前端 `utils/request.js` 的 `toPageData`（把 `Page{records,total}` 转成 `{list,total}`）
4. 依赖 `springdoc-openapi-starter-webmvc-ui`（生成物用 `@Operation`）与 `poi-ooxml`（Excel）

---

## 九、技术栈一览

| 维度 | 选型 |
| --- | --- |
| 后端语言 / 框架 | Java 21 / Spring Boot 3.2.5 |
| 持久层 | MyBatis-Plus 3.5.5 |
| 模板引擎 | FreeMarker（22 个种子模板，支持在线编辑 + 版本回滚） |
| 安全 | JWT 鉴权 |
| 数据库 | MySQL 8 |
| 前端 (generated projects) | Vue 3 + Element Plus + Vite |
| 前端 (platform console) | Vue 2.7 + Element UI |
| 本地 AI | qwen2.5-coder:7b（建模 / 推荐配置 / 种子数据增强） |
| 接口文档 | springdoc-openapi（Swagger UI） |

---

## 十、快速开始

### 1. 建库（13 张平台表 + 默认账号 admin/admin123）

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. 配置目标工程

复制配置模板并按本机路径修改：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` 已在 `.gitignore` 中排除，不会提交。主要配置项：

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # 生成物写盘基准
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # monorepo 时前端子目录
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # 生成物 import 基座类的前缀
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

自检的依赖目录这样导出：

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. 启动后端

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

服务默认端口 **8120**，接口文档 `/swagger-ui/index.html`。

### 4. 启动前端

```bash
cd ../codegen-web
npm install
npm run serve
```

前端默认端口 **9530**，`/dev-api` 代理到后端 8120。默认账号 `admin / admin123`。

### 5. 关键配置项

| 配置 | 说明 |
| --- | --- |
| `gen.output.*` | 生成物写盘路径、包名、模块映射 |
| `gen.selfcheck.classpath` | 自检内存编译的依赖 classpath，支持 `dir/*` |
| `gen.menu.outlet` | `artifact` 只产出菜单 SQL 与路由片段；`internal` 同时写入平台自身 `sys_menu` |

---

## 十一、文档导航

| 文档 | 内容 |
| --- | --- |
| `docs/images/showcase/` | 真实系统实战截图——智慧警务 / 服装 ERP / 智慧人力 / 制造业 MES |
| `docs/代码生成器-操作手册.md` | 部署、五分钟上手、表配置、批量接管、模板管理、台账、接口目录、流水线全流程（含实拍截图） |
| `docs/典型场景操作指南-演示后台demo.md` | 6 张表覆盖 5 类典型场景，含可运行 demo 与实跑截图 |
| `docs/项目介绍.md` | 与本文同源的项目介绍文档 |
| `docs/代码生成器-Vx.x-规划.md` | V3.1 → V7.3 各版本规划与功能盘点 |
| `codegen-web/README.md` | 前端控制台快速上手参考 |

---

## 十二、历史版本演进

| 版本 | 版本主题 / 规划文档 | 落地提交 |
| --- | --- | --- |
| V1.2 / V1.3 | 一键生成 / 批量 / 回滚；diff 预览与结构同步 | —（仅见 `src/api/gen.js` 注释） |
| V2.0 | 动态路由（后端下发菜单树） | — |
| V3.0 / V3.1 | 多系统台账；多系统闭环 + 首批自动化测试 | V3.1（174 行） |
| V4.2 | 生成结果可追溯、可回滚（写盘台账） | V4.2（292 行） |
| V4.3 | 模板回归与质量护栏 | V4.3（200 行） |
| V4.4 | 一主多子、级联写完整化、模板分道 | V4.4（154 行） |
| V4.5 | A4 打印单据（零第三方依赖） | V4.5（127 行） |
| V5.0 | 一句话建系统：流水线 + AI 建模 + 脚手架基座 | — |
| V5.1 | DDL 审计列自动补齐 | V5.1（100 行） |
| V5.2 | 流水线 seed 数据初始化步 | V5.2（107 行），84ec8dd |
| V5.5 | 多模块工程生成收尾 | af73dd3 |
| V6.0 | 微服务设施（Nacos / Gateway / Feign，第三态 micro） | V6.0（111 行），1deecbd |
| V6.1 | Activiti 工作流双引擎 | V6.1（100 行），02b671e |
| V6.2 | 产物交付面（CLI / 部署物 / 配置安全） | V6.2（114 行），02b671e |
| V6.3 | 支付能力包（微信 / 支付宝 + Mock） | V6.3（104 行），1c4ee6a、22eede5 |
| V6.4 | 权限安全基线（token 持久化 / BCrypt / 接口权限 / 登录防护） | V6.4（107 行），007a202 |
| V6.5 | 移动端 uni-app（H5 + 微信小程序） | V6.5（105 行），779ac41 |
| V6.6 | 文件能力包 | 98f3c09 |
| V6.7 | 产物测试出厂（test_enabled 三态 + H2 骨架） | V6.7（134 行），4e7d318 |
| V6.8 | 生成物可维护性（dryRun / 受保护跳过 / 品牌参数化） | V6.8（141 行），be6f858 |
| V6.9 | 平台文档中心（后端基座，前端收尾同批推进） | — |
| V6.10 | 平台可观测性：Actuator + 内存指标窗口 + 内嵌监控页 | — |
| V7.0 | AI 页面与菜单推荐（preview / apply） | V7.0（94 行），45f2670 |
| V7.1 | 种子数据 AI 增强（AI 管语义、规则管结构） | V7.1（42 行），5ca6e72 |
| V7.2 | 流水线成功后 SQL 交付物落盘 `db/` | eebff88 |
| **V7.3** | **交付可信度：自检编译产物测试 / 统一 SQL 交付出口 / 表级 AI 推荐** | **V7.3（138 行）** |
| **V7.4** | 可重复交付：空库能部署、每次提交都保证能跑 | c306e29 |
| **V7.5** | 项目语言配置：206 语言清单 / 默认语言 / 启用语言，生成物 lang 与 dir 注入 | 7a9afa0 + abac56d |
| **V8.0** | 全页面国际化：206 语言框架（8 种精译），双轨制 i18n、OFF 逐字节零 diff 契约 | 9e5dd57 → 7d57aea |
| **V8.1** | 质量加固：离线契约 harness（9 模式 OFF 零 diff）+ i18n-core 单测 + CI 卡点 + README 诚实拆分（框架 206 / 精译 8）+ check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | 生成物现代化：Vue 3 + Element Plus + Vite；运行时验证（40/40 断言、vite build 成功、14 张截图含 ar-SA RTL）；修复 3 个运行时缺陷 | 93a12d5 + e49330b  |
| **V9.1** | **可维护性重构：GenScaffoldServiceImpl 5072 → 931 行拆为 8 个 Writer；产物逐字节零差异（新旧 jar 共 119 + 62 文件 diff -r 为空）；312/312 单测；离线 i18n 契约 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **测试中心+验证中心：用例中心 7 表 / 验证中心 9 套件（S1-S9，全局 409 单跑锁，CI 共享 JSON 摘要）；373/373 单测（新增 50），i18n 契约 9/9** | 8be92a9 + f9f16d2 |
| **V9.3** | **生成产物运行时修复（模板 3 处 Vue2 遗留）+ 验证中心前端接入 + 一句话建系统端到端跑通（自检/子进程路径/前端启动/重跑清场）；96 份多语言文档版本历史同步至 V9.3** | 52f3bab / 8dabbd1 / 85e279c |
| **V9.4** | **一句话建系统分阶段确认：AI 推荐 → 编辑/预览（同源 DDL 只读）→ 确认落草稿/建库，阶段门禁；DDL 预览与建库执行共用同一组装方法，所见即所得** | b3f0587（含遗留入库） |
| **V9.5** | **AI 建表推荐与人工确认（/gen/ai-build）：AI 推荐表结构 → 可编辑 → 同源 DDL 预览 → 确认落草稿/建库；补表按目标系统已有表做差集，服务端硬过滤重复表** | 570f6fc + 3104317 |
| **V9.6** | **大型/超大型分批建模任务：任务/批次两表落库，条件更新认领调度（2s tick 多 worker 多实例安全），超时回收重试（900s/2 次），前端 2s 轮询批次进度；实测 11 批/70 表/8s（stub 4 worker）；流水线号防撞（4 位序号 + uk_no 换号重试）** | 5a2bcac + ecbe2ba（web 2ff1c74） |
| **V9.7** | **快速建表：两段式骨架生成（AI 只列表名/注释/字段名，类型与主键由命名语义规则补全，真模型实测 3m13s → 54s，约 3.6 倍）+ 行业模板库（电商/ERP/CRM/CMS 四套 JSON 模板包，与 AI 同收口克隆，秒级零 AI）** | d38ba7c + fd2440c（web 3e9eb92） |
| **V9.8** | **多流水线并发运行：PipelineRunGate 闸门（全局 max-running 上限 + 同系统互斥，跨系统并行安全）取代单线程执行器 + 全局 AtomicBoolean；启动恢复——服务重启后 running 孤儿状态自动置 failed（修复实际事故）；462/462 单测全绿** | 157f5ca + 07e8607 |
| **V9.9** | **行业模板库扩至 250+ 套 / 19 大类 / 2100+ 表：可复用领域蓝图 + 生成脚本（scripts/gen_industry_templates.py）；模板清单改为轻量（不表明细），明细按需加载，支持分类筛选与搜索；464/464 单测全绿** | 7e16b72（web 7a5d1c7） |
| **V9.10** | **行业模板字段展开：2191 张表按表语义补充业务字段（编码/单号/金额/联系方式/时间区间/正文/排序/状态/备注），平均每表 6.12 → 7.87 列，状态 100% / 备注 96% / 编码 82% 覆盖；语义等价组防止重复列；修复历史处方表 create_time 与审计列撞名（会导致建表重复列失败）；465/465 单测全绿** | 11efe55 |
| **V9.11** | **行业模板升级为完整系统：每套追加 15 张平台通用表（审批流/附件/操作日志/任务/消息/字典/配置）+ 所属分类 10-15 张专属业务表（19 大类共 248 张）。每套模板 29-45 张表（平均 34.8，原 8.6）、每表 10-20 字段（平均 14.25，原 7.87）；字段二次深化按辅助表语义避免假字段；466/466 单测全绿** | 16d39ec |
| **V9.12** | **智慧村务专用模板 village_affairs（85 表/1414 字段）+ 生成器全链路修复：Tag 撞 Swagger @Tag 歧义、肥 jar 自检 classpath、scaffold 包占位符与测试基座编译单元、流水线路径绝对化、Vue3 dev/vite 部署、重跑残留 failReason 清理、Vue3 页面遗留修复。端到端：12/12 步骤全绿，生成系统已运行（后端 8140/前端 9040），90 个 .vue 页面 0 编译错误；468/468 单测全绿** | 25e2dcc..3fabb7b |
| **V9.13** | **智慧村务系统完善：菜单重规划为 12 个村务业务域（替代「其他66页」）；28 张表种子数据（98菜单/5用户/户籍土地三资党建便民流程数据，全部关联）；审批流端到端跑通（提交0→1、通过1→2、驳回1→3）。修复两个生成器缺陷：流程表 id 未自增（submit 500）、鉴权过滤器未注入 userId（approve 403、审计 create_by 空）；3 项防回归测试，468/468 全绿** | 049027a..e4b9df2 |
| **V9.14** | **智慧村务界面与数据完善：字段中文名（源头修 52 个外键注释 + 同步 129 处页面 label）、12 业务域菜单图标、首页真实统计（DashboardController 白名单 COUNT + 12 统计卡片）、90 张业务表全部演示数据（100% 覆盖、真实外键关联）；修 3 个脚本层坑（Map 图标无效、日期规则误伤、英文 token 注释）；3 项契约测试，474/474 全绿** | 039ae7e |
| **V9.15** | **数据可视化图表能力包（通用、零领域耦合）：chart_enabled 三态开关（NULL/0=关、1=开，关闭时产物零 diff）；GenChartDeriver 纯按列元数据推导饼图/折线/柱状/仪表盘，零领域关键词；每表 /{module}/stats 聚合接口，生成期字段白名单防 SQL 注入；ECharts 图卡组件 + 跨表看板，系列色跟随 --app-primary；scaffold / 写盘 / ZIP / 路由 / 依赖全链路注入；平台 486/486 单测全绿，产物 64 个 StatsController 编译通过，真浏览器 4 类图表 0 控制台错误** | 69d5a06（web 5097231）, **当前版本** |
| **V9.16** | **一键试用与演示体验：多阶段 Dockerfile + docker-compose（MySQL8 healthcheck + codegen-server），scripts/docker/init 33 个按数字版本序的初始化 SQL，V1.0 自带 admin/admin123 种子；新增 H2 内存库 demo 模式（application-h2.yml + SQL 转换器）；演示页 8 图 PNG→WebP（877KB→332KB，38%）、懒加载与访问统计占位；补 quick-start-docker 文档。** | d1ca428 |
| **V9.17** | **能力包深度补全：5 个 agent 并行补齐支付、工作流、打印、安全、唯一性校验。支付补 JSAPI/H5 下单、退款查询与 pay_code 表级绑定；工作流补 SVG 节点进度图、会签/委派与轻量流程设计器；打印支持 print_title、横版、金额大写与 CODE128 条码（二维码占位）；安全补接口限流、数据权限加载与配置页、密码策略、互踢提示；唯一性校验 400 断言前后端对齐。后续 6a83e79 重生成 Docker init 35 号与 H2 转换脚本。** | 88da5ad, 6a83e79 |
| **V9.18** | **生成质量与可维护性：新增模板影响面预览（扫描 scaffold-*.ftl，按表给出产物路径与形态提示）与控制台页；gen_table 加 child_save_mode（0=全量替换、1=按主键三路合并），一对多按主键、一对一按外键、多对多按关联键，MySQL/docker/H2 幂等加列，mode=0 零 diff（基线 14 + 契约 8 + i18n 快照 9/9）；S8/S9 进验证中心，verify.yml 加 CI 门禁。** | pending |
| **V9.19** | **建模规模化收尾：confirm 建库分批化，新增 gen.pipeline.db-batch-size（默认 50），DDL 分批、每批独立提交、失败报批号与摘要，DdlBatchPlanner + 7 单测；i18n 覆盖矩阵 12 语言 × 60 键 100%，CI 加 backend-i18n-coverage，补 ru-RU、pt-BR、id-ID、vi-VN；模板市场：外部模板目录支持 ZIP 导出、JSON 导入（校验、冲突拒绝、overwrite、防穿越）与来源标记，控制台新增页。** | pending |

---

> **V6.10 平台可观测性**：接入 Actuator + Micrometer，5 秒采样写入内存环形缓冲（保留 30 分钟窗口），新增内嵌「系统监控」页——后端 `/monitor/*` 聚合接口 + 前端 ECharts 面板。端点仅白名单放行 `health/info/metrics`，`/actuator/health` 免 token，`metrics` 等需鉴权；`env/beans/heapdump/threaddump/loggers` 永不暴露。采样窗口仅进程内、重启即丢、多实例不共享。详见 `docs/可观测性说明.md`。

## 十三、项目状态

- 当前发布版本 **V9.19**：通用数据可视化图表能力包（`chart_enabled` 三态开关 + 零领域耦合的 `GenChartDeriver` 推导饼/线/柱/仪表盘 + 每表 `/{module}/stats` 聚合接口 + ECharts 图卡与跨表看板，关闭时产物零 diff）；往前依次为 V9.14 智慧村务界面与数据完善、V9.13 智慧村务系统完善、V9.12 村务专用模板与全链路修复、V9.11 行业模板升级为完整系统、V9.10 字段展开、V9.9 250+ 模板库、V9.8 多流水线并发运行（`PipelineRunGate` 全局并发上限 + 同系统互斥，跨系统并行安全）+ 服务重启后 `running` 孤儿流水线自动置 failed、V9.7 快速建表（两段式骨架生成，真模型实测提速约 3.6 倍）、V9.6 大型/超大型分批建模、V9.5 AI 建表推荐与人工确认、V9.4 分阶段确认、V9.2 测试中心与验证中心。单测基线 **486/486 全绿**。
- 已沉淀配套演示工程 `demo_admin`（后端 8140 / 前端 9040），覆盖树形 / CRUD / 主子表 / 只读 / 打印全场景，可独立运行验证。
- 两仓库均托管于 Gitee：`https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 十四、作者与技术支持

- **作者**：中国程序员（独立开发者）
- **联系邮箱**：`hustxiayuhong@126.com`
- **技术支持**：请用**中文**写邮件，附上问题描述、运行环境（JDK / MySQL / 版本号）与复现步骤

> 所有邮件我都会看，看到就会回复。问题反馈、功能建议、合作洽谈都欢迎。

---

## 十五、同类产品比较

CodeGen 既不是片段代码生成器，也不是全栈低代码平台——它处在一个非常具体的生态位。下面是它和 Spring Boot / Java 生态里常见工具的定位对比。

### vs. 片段生成辅助工具（JPA Buddy · MapStruct · Spring Initializr）

**JPA Buddy**、**MapStruct**、**Spring Initializr** 这类工具擅长脚手架和小范围开发提效，但产物都是**片段**：一个实体类、一个 Mapper 接口、几份 DTO 转换 helper，或者一个空项目壳。CRUD 页面、菜单、路由、权限码仍然要你手动接；表字段一改，重新手工改一轮。

CodeGen 产出的是**零修改可跑的完整垂直切片**：后端（Entity → Mapper → Service → Controller → DTO/VO）+ 前端（`api.js` + Vue 页面 + A4 打印页）+ 菜单 + 路由注册。粘贴 DDL、点一下生成，功能直接上线。

### vs. 脚手架框架（JeecgBoot · RuoYi · dlow）

**JeecgBoot**、**RuoYi** 这类框架是先给你一套完整的后台管理基座，再在上面挂一个代码生成器。你必须 **fork 他们的脚手架**，承担他们的约定、认证层、菜单 schema、文件布局，以及——关键——他们的版本升级路径。生成器**继承了父框架的偏见**（比如动态数据源约定、Shiro/Spring Security 选择、前端技术锁死）。

CodeGen 走的是另一条路：**绝不触碰目标工程的基础设施**。它假设你的项目已经具备 `Result`、`BaseEntity`、`PageQuery`、`PaginationInnerInterceptor`、以及一个能返回 `{list, total}` 的 `utils/request.js`。它只做一件事——在你现有的目录布局里**干净地丢进业务代码**，和你已经选好的认证栈、UI 框架、项目约定并存。不 fork。不劫持约定。干净、精准。

### vs. 脚手架内置生成器（云宅 · JeecgBoot 内置 · RuoYi 内置）

不少脚手架现在都内置了代码生成器，跑起来产物是能用的——但质量护栏普遍偏弱：没有真实编译校验、没有结构化契约测试、没有模板版本管理、没有回滚、没有变更台账。一旦模板改动生成了坏掉的产物，你只能到运行时才发现。

CodeGen 的**质量护栏链**是它和这些内置生成器最本质的差异：

| 环节 | 它实际做了什么 |
|---|---|
| 预览与差异 | 先在内存生成，逐文件渲染对磁盘当前状态的 diff，确认后才写盘 |
| 写盘带备份 | 每个被改动的文件在覆盖前都会拷贝到 `.gen-backup/<批次号>/` |
| 标记区合并 | `// gen:begin-end` 之间的手写代码原样保留，只替换生成区 |
| 内存自检验证 | 对所有生成的后端产物跑一次**真实 `javac` 编译**（不是正则式 lint）——没编译过的文件不会落盘 |
| 独立 classpath 编译 | 生成的集成测试用目标工程自己的 classpath 再编译一次——缺什么依赖写盘时报什么，不是运行时才炸 |
| 四状态台账 | `gen_output` 把每个文件记为 **created / merged / skipped / failed**，附带字节数、MD5、备份路径 |
| 批次级回滚 | 任意一批生成可一键回滚，恢复到那一批执行前的精确状态 |

预览 → 备份 → 合并 → javac 自检 → classpath 编译 → 台账 → 回滚，这**七环质量护栏**——据作者所知，**市面上主流脚手架的内置生成器没有一家是完整具备的**。
