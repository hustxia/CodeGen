码匠 · 后端（CodeGen Server）
**_

代码生成平台的服务端 — Spring Boot + MySQL，默认监听 http://127.0.0.1:8120，为 codegen-web 前端控制台提供全部业务接口。
前端地址 ：https://gitee.com/hustxiayuhong/code-survivor-frontend _**

📖 简介
码匠后端是代码生成平台的核心引擎，承载 建表建模 → 模板渲染 → 差异比对 → 产物落盘 → 台账回滚 的完整流水线，并对外提供鉴权、菜单下发、字典、模板版本、批次台账等能力。

前端侧边栏菜单由本服务的 /auth/routers 接口下发，前端按 component 字符串动态解析视图 —— 新增页面只需在后端配置菜单，无需改动前端路由文件。

🧱 技术栈
| 分类 | 选型 |
|---|---|
| 语言 / 框架 | Java + Spring Boot |
| 构建工具 | Maven（mvn package -DskipTests） |
| 数据库 | MySQL（库名 gen_platform，字符集 utf8mb4） |
| 鉴权 | Token 持久化 + BCrypt 密码加密 + 登录防护 |
| 模板引擎 | 12 套可在线编辑的代码模板，支持版本历史与回滚 |
| 工作流 | 双引擎：内置引擎 / Activiti 8.7.0（GenBpmnBuilder 翻译） |
| 交付形态 | 可执行 JAR（target/codegen-server.jar） |

✨ 功能模块
| 模块 | 说明 |
|---|---|
| 代码生成 | 表配置、列级控件 / 校验 / 字典、批量接管、路径回落（GenPathResolver）、命名（GenNaming） |
| 差异预览 | 生成物与磁盘现状 diff 计算，确认后才执行落盘 |
| 自检 | 内存编译检查、结构与权限一致性问题清单 |
| 生成台账 | 批次历史、产出文件四态（新建 / 合并 / 跳过 / 失败）、备份覆盖与撤销回滚（RBK） |
| 代码模板 | 12 个模板的在线编辑、版本快照与回滚 |
| 系统管理 | 用户、角色、菜单、字典 |
| 权限安全 | /auth/routers 菜单下发、接口权限后端强制、登录防护三件套 |
| 工作流 | 双引擎待办中心，BPMN 自动翻译 |
| 支付能力 | pay_enabled 三态 + 微信 / 支付宝 / mock 三渠道 + markPaid 幂等 |
| 移动端 | mobile_enabled 三态 + uni-app 单源工程骨架 |

## 🎯 典型应用场景

> 本节图片使用在线占位图（dummyimage.com），可直接显示。  
> 替换为真实截图时，把 `src` 改为 `./docs/images/scenarios/xx.png` 并把图片提交到仓库即可。  
> 建议尺寸 1440×900，敏感字段打码。

| 场景 | 适合人群 | 关键能力 |
|---|---|---|
| 从 DDL 一句话建系统 | 后端 / 全栈 | 建系统向导、DDL 导入、建模规模选择 |
| 单表 / 主从表 CRUD 后台 | 业务研发 | 表配置、控件校验、一主多子表、级联删除 |
| 生成前差异预览与落盘 | 谨慎交付型团队 | diff 预览、确认落盘、四态台账 |
| 批次回滚与产物治理 | 运维 / 交付 | 备份覆盖、RBK 撤销回滚、批次重名治理 |
| 模板在线定制与回归护栏 | 平台管理员 | 12 套模板编辑、版本快照、基线零 diff 门禁 |
| 工作流待办中心 | 审批型业务 | 内置引擎 / Activiti 双引擎、BPMN 翻译 |
| 支付收银台接入 | 商业化项目 | `pay_enabled` 三态、微信/支付宝/mock、`markPaid` 幂等 |
| 移动端 uni-app 骨架 | 多端交付 | `mobile_enabled` 三态、单源工程、动态页三件套 |
| A4 打印单据 | 行政 / 财务 / 仓储 | `printPage` 模板、`gen_print` 三态、打印路由 |
| 微服务 / 多模块工程生成 | 架构 / 平台组 | `project_structure` 第三态 micro、多模块收尾 |

### 场景一：从 DDL 一句话建系统

登录后进入建系统向导，选择建模规模，或直接导入已有 DDL。系统完成表配置、命名与路径回落、菜单下发，生成后即可打开管理后台。

![建系统向导](https://dummyimage.com/1200x750/1e293b/ffffff&text=01+Wizard)

![从 DDL 导入](https://dummyimage.com/1200x750/1e293b/ffffff&text=02+DDL+Import)

**价值**：把“建库建表 → 表配置 → 代码生成 → 菜单接入”压缩为一条向导链路，适合新项目冷启动和存量表快速接管。

### 场景二：单表 / 主从表 CRUD 管理后台

在表配置中设置列级控件、校验和字典，支持一主多子表、级联删除三态和详情多 tab。生成物先差异预览，确认后落盘。

![表配置](https://dummyimage.com/1200x750/1e293b/ffffff&text=03+Table+Config)

![主从表详情多 tab](https://dummyimage.com/1200x750/1e293b/ffffff&text=04+Detail+Tabs)

**价值**：常规业务后台不必从零写增删改查，主从表详情页也能直接生成。

### 场景三：生成前差异预览与生成台账

生成前展示“生成物 vs 磁盘现状”的 diff，确认后才写盘；台账记录批次、新建 / 合并 / 跳过 / 失败四态。

![差异预览](https://dummyimage.com/1200x750/1e293b/ffffff&text=05+Diff+Preview)

![生成台账](https://dummyimage.com/1200x750/1e293b/ffffff&text=06+Output+Ledger)

**价值**：避免覆盖已有代码，交付过程可追溯。

### 场景四：批次回滚与产物治理

针对备份覆盖、批次重名和撤销回滚，通过 RBK 机制恢复到生成前状态。

![批次回滚](https://dummyimage.com/1200x750/1e293b/ffffff&text=07+Rollback)

**价值**：生成器敢用、可退，降低“生成一时爽、回滚火葬场”的风险。

### 场景五：模板在线定制与质量护栏

在线编辑 12 套代码模板，保存版本快照并支持回滚；改完模板后跑 `verify-all`，基线快照零 diff 门禁和契约测试会拦截异常产物。

![模板编辑器](https://dummyimage.com/1200x750/1e293b/ffffff&text=08+Template+Editor)

![verify-all 校验](https://dummyimage.com/1200x750/1e293b/ffffff&text=09+verify-all)

**价值**：模板可定制，但改动有护栏，适合平台组长期维护。

### 场景六：工作流待办中心

内置引擎与 Activiti 8.7.0 双引擎，BPMN 由 `GenBpmnBuilder` 翻译，待办中心统一处理审批任务。

![工作流待办中心](https://dummyimage.com/1200x750/1e293b/ffffff&text=10+Workflow+Todo)

**价值**：生成后台不止 CRUD，审批流可直接进入待办闭环。

### 场景七：支付收银台接入

通过 `pay_enabled` 三态控制支付能力，支持微信、支付宝、mock 三渠道，`markPaid` 幂等处理回调。

![支付收银台](https://dummyimage.com/1200x750/1e293b/ffffff&text=11+Pay+Cashier)

**价值**：商业化项目可在生成阶段接入支付骨架，减少重复对接。

### 场景八：移动端 uni-app 单源工程骨架

`mobile_enabled` 三态控制移动端产物，生成 uni-app 单源工程骨架和动态页三件套。

![移动端预览](https://dummyimage.com/1200x750/1e293b/ffffff&text=12+Mobile+Preview)

**价值**：同一套模型同时服务 PC 管理后台与移动端。

### 场景九：A4 打印单据

使用 `printPage` 模板生成 A4 打印页，结合 `gen_print` 三态和打印路由，适用于订单、出库单、对账单等单据。

![A4 打印单据](https://dummyimage.com/1200x750/1e293b/ffffff&text=13+Print+Page)

**价值**：业务单据打印不再单独手写页面。

### 场景十：微服务 / 多模块工程生成

`project_structure` 支持第三态 `micro`，输出多模块 / 微服务工程骨架；默认关闭 Nacos，跨服务接口按需手写。

![微服务工程结构](https://dummyimage.com/1200x750/1e293b/ffffff&text=14+Micro+Structure)

**价值**：提供微服务设施但不夸大“一键微服务”，边界诚实、可按需启用。

---

**替换为真实截图的方法**

1. 把截图放进 `docs/images/scenarios/`，命名与场景表一致（`01-wizard.png` … `14-micro-structure.png`）。
2. 将对应图片地址改为 `./docs/images/scenarios/01-wizard.png`。
3. 执行 `git add docs/images/scenarios && git commit -m "docs: 场景截图" && git push`。
4. Gitee 网页仍不显示时，改用 raw 外链：
   `https://gitee.com/hustxiayuhong/code-survivor-backend/raw/master/docs/images/scenarios/01-wizard.png`

🚀 快速开始
① 初始化数据库
```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql