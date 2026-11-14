# 代码生成器 · 版本路线图（Roadmap）

> 当前版本：**V9.3** — 生成产物自动化测试（S10/S11/S12 + GEN 触发），基于 V9.2 测试中心 + 验证中心；V8.0 全页面国际化 → V8.1 质量加固 → V9.0 生成物 Vue3 现代化 → V9.1 巨型类拆分 → V9.2 测试中心/验证中心 → V9.3 产物自动化测试，均已落地并通过验证  
> 路线图建立：2026-11-12　|　最近更新：2026-11-14  
> 状态：V9.3 已完成生成产物自动化测试；12 步全流程实测通过，真实产物 GEN 验证 PASSED（S10 20/20 用例、S11/S12 如实 SKIP、三层落库齐全）。

本文档承接项目评估结论，把近期必须推进的三件事拆成可执行的版本里程碑：  
**P0 前端升级 Vue 3 + Element Plus**、**P1 i18n 自动化测试**、**P1 README 诚实化与翻译共建**。

---

## 一、当前状态与技术栈

| 层    | 现状                                                         | 备注                                      |
| ---- | ---------------------------------------------------------- | --------------------------------------- |
| 后端   | Spring Boot 3 / Java 21 / MyBatis-Plus / Freemarker 2.3.32 | 较新，健康                                   |
| 前端   | **Vue 2.7 + Element UI**                                   | ⚠️ Vue 2 已于 2023-12 EOL；Element UI 已停维护 |
| i18n | 双轨制：`I18nTextRewriter` 全局替换 + `vuePage.ftl` 条件包裹           | OFF 逐字节零 diff，ON emit `$t(key,'中文')`    |
| 语言覆盖 | 框架支持 **206** 种 / **精译 8** 种（中英日韩法德西阿）                      | 其余回落中文                                  |
| 体量   | 约 7.3 万行源码（后端 57.9k / 前端 15.1k）                            | 9 种生成模式                                 |
| 测试   | 仅离线等价校验 + 手动截图                                             | 无 CI 卡点                                 |

### 已知技术债（按优先级）

1. **前端栈老化**（P0）：Vue 2 / Element UI 停更，安全补丁断供、招人难、与新工具链摩擦增大。
2. **测试缺失**（P1）：i18n 正确性靠人肉回归，无法防回归。
3. **宣传口径**（P1）：「206 语言」对外未区分「框架支持 / 精译覆盖」，易被质疑夸大。
4. **体验门槛**（P2）：依赖 Maven + 数据库才能启动，demo 不随手。

---

## 二、版本规划

### V8.1 — 质量加固与诚实化（P1）

**目标**：把 i18n 正确性固化成自动化保障，并校准对外表述，不改动功能行为。

**任务清单**

- [x] **`vuePage.ftl` 快照测试**
  - 用 9 种生成模式（simple / relation / master-detail / master-multichild / multi / micro / flow / pay / file）的固定数据模型渲染两遍：`i18nEnabled=0` 与 `i18nEnabled=1`。
  - OFF 与 v8.0 基线**逐字节零 diff**（契约保护）；ON 断言含 `$t('crud.<Class>.field...', '中文')` 且第二参恒为中文回落。
- [x] **`i18n.js` 回落链单测（Node）**
  - 断言顺序：当前语言包 → `zh-CN` → 内联中文；`ar-SA` 自动 RTL；缺失 key 不空白。
  - 覆盖 `resolveLocales` 206 项里 8 精译 + 回落样本。
- [x] **CI 卡点（GitHub Actions）**
  - PR 必须跑 OFF/ON 双快照，任一非预期 diff 即阻断合并。
  - 轻量 harness：用 Freemarker 独立渲染 ftl（不依赖完整 Spring 启动），喂入 `GenTemplateModel` 样例，做快照比对——避免「缺 Maven+DB 无法验证」的老问题。
- [x] **README 诚实拆分 + 翻译共建入口**
  - 顶部「语言支持」明确两数字：`框架支持 206 种语言切换与回落` / `当前精译 8 种（zh-CN, en-US, ja-JP, ko-KR, fr-FR, de-DE, es-ES, ar-SA）`。
  - 在「多语言项目介绍/」新增 `CONTRIBUTING.md`：说明如何贡献翻译（提交 `src/locales/<loc>.json` + 在 `languages.json` 置 `enabled=true`），并附 key 覆盖校验脚本。

**验收标准**

- CI 绿；新增代码有测试覆盖且可本地一键运行。
- README 顶部两数字清晰；存在翻译贡献指引。

---

### V9.0 — 前端现代化（P0，破坏性升级）

**目标**：**生成物**前端 Vue 2.7 + Element UI → Vue 3 + Element Plus + Vite，功能与 i18n 能力等价（平台控制台 `codegen-web` 仍是 Vue 2.7 + Element UI，不在本次升级范围）。

**任务清单**

- [x] **依赖迁移**
  - 入：`vue@^3.4`、`element-plus`、`@element-plus/icons-vue`、`vite`、`@vitejs/plugin-vue`、`vue-i18n@9`（可选，或保留自研 `i18n.js` 并桥接）。
  - 出：`vue@2.7`、`vue-template-compiler`、`element-ui`、webpack / vue-cli 相关。
- [x] **应用入口改造**
  - `createApp(App).use(ElementPlus)`；i18n 桥接：`app.config.globalProperties.$t = t`，保证模板内 `$t` 仍可调用（兼容生成代码）。
- [x] **语法迁移（生成模板需同步改）**
  - 过滤器 `filter` → 计算属性 / 方法（Vue 3 已移除 filter）。
  - `v-model` 自定义组件 → `v-model:arg` + `update:arg` 事件。
  - 事件总线 `$on/$off/$emit` → `mitt`。
  - 全局 API `Vue.prototype.x` → `app.config.globalProperties.x`；`slot` → `v-slot`。
- [x] **`vuePage.ftl` 适配 Vue 3 SFC**
  - 输出 `<script setup>`（或 `defineComponent`）结构；组件用法对齐 Element Plus（el-table / el-form 等部分 prop 差异）。
  - **关键契约**：`i18nEnabled=0` 时生成的代码必须与「Vue 3 无 i18n 基线」逐字节零 diff（i18n 开关的 OFF 契约在 Vue 3 下继续成立，不能被升级破坏）。
- [x] **构建迁移**：webpack → Vite；devServer proxy / alias / 环境变量迁移。
- [x] **回归复验**（2026-11-13 完成，证据见「五」补记）
  - 8 语言截图（UI 全量译文、ar-SA RTL 镜像、回落不空白）：14 张 Chrome Headless 实拍，`ar-SA` RTL 表格渲染正常。
  - 构建验证：生成物 `npm install` + `vite build` 成功（Vite 5.4.21，14.4s，dist 完整产出）。

**验收标准**

- 生成系统前后端启动正常；i18n 全链路等价（与 V8.0 行为一致）。
- CI 新增 Vue 3 OFF 快照基线，零 diff        卡点生效。

---

### V9.1 — 巨型类拆分（P0 可维护性）

**目标**：把 `GenScaffoldServiceImpl`（5072 行，单文件承载全部产物字符串）按职责拆成可独立维护的 Writer 集合，**产物逐字节不变**。

**任务清单**

- [x] **按职责切分 8 个类**（`com.codegen.service.impl`，包内可见 `final class`，方法全部静态化）
  - `ScaffoldFlowWriter`（905 行）：Flow/Activiti/BPMN 服务、流程部署器、流程 Controller、流程注册 SQL。
  - `ScaffoldOpsWriter`（280 行）：Dockerfile / compose / CI 模板 / 移动端工程 / 支付与文件能力包索引。
  - `ScaffoldPomWriter`（765 行）：pom 依赖树、启动类、`application*.yml`、后端 README。
  - `ScaffoldCommonWriter`（1132 行）：Result / 异常 / 分页 / MyBatis-Plus / 数据权限 / 操作日志等 22 个基座类。
  - `ScaffoldAuthWriter`（850 行）：认证过滤器 / TokenStore / 登录 / 权限校验 / 字典 / 登录日志。
  - `ScaffoldSeedWriter`（144 行）：`sys_user`/`sys_role`/`sys_menu`/`sys_session` 种子 SQL。
  - `ScaffoldFrontendWriter`（533 行）：`package.json` / `vite.config.js` / `main.js` / layout / login / router / store。
  - `ScaffoldTextSupport`（39 行）：classpath 资源读取等共享工具（各 Writer 静态导入）。
- [x] **主类回归编排职责**：`scaffold()`（写盘、备份、台账、受保护跳过）、`check()`（体检）、`writeI18nResources()`（依赖 `I18nMessageService` 实例状态，保留在主类），5072 → **914 行**。
- [x] **反射兼容桩**：`seedSql()` / `dictControllerJava(String)` / `mobileResources(String,int,String)` 等 9 个被 `getDeclaredMethod` 锁定的旧入口保留，实现委托到 Writer，基线测试零改动。
- [x] **测试债清偿**（拆分验证时暴露的 13 个历史红灯）
  - `gen-baseline` 23 个快照按 V9.0 现状重生成（diff 全部是 `slot-scope`→`#default`、`.sync`→`v-model:`、`.native` 去除、图标组件化）。
  - `I18nTextRewriterTest.模板源码零改动`：断言从「模板不得出现 `$t(`」升级为「`$t(` 必须处于 `<#if i18nEnabled>` 守卫分支内」（小状态机跟踪 `<#if>/<#else>/</#if>` 栈），与 V8.0 双轨制一致。
  - `HistogramConfigProbeTest`：本机 Micrometer 连独立 `SimpleMeterRegistry` 的 `histogramCounts()` 也恒为 0（仅 SLO 桶有效），改为锁定配置契约（属性绑定 + MeterFilter 打开 histogram），不再依赖运行时桶数量。

**验收标准**

- 主类 ≤ 1000 行；新增类单一职责、无实例状态。
- 全量单测 0 失败；离线 i18n 契约 9/9。
- **同一 DTO 下新旧 jar 生成物逐字节相等**（含 i18n 开/关两种变体）。

---

### V9.2+ — 体验与生态（P2，待排期）

- [ ] H2 内存库一键 demo / Docker 镜像，降低上手门槛。
- [ ] 社区翻译贡献流程：PR 审核 + 自动校验 key 覆盖，推动精译从 8 → 逐步扩展。
- [ ] 精译语言扩展计划（优先 ru-RU / pt-BR / id-ID 等高覆盖语种）。

---

## 三、风险与里程碑依赖

| 风险                                   | 影响                             | 缓解                                |
| ------------------------------------ | ------------------------------ | --------------------------------- |
| Vue3 + Element Plus 组件 API 与 Vue2 差异 | 生成模板需逐一适配，工作量集中在 `vuePage.ftl` | V8.1 先建测试网，升级时即改即验                |
| 升级破坏「OFF 零 diff」契约                   | 既有用户生成物变更                      | CI 增加 Vue 3 OFF 快照基线卡点            |
| 测试 harness 需 Maven+DB                | CI 资源重                         | 采用 Freemarker 离线独立渲染，不拉起完整 Spring |
| 206 宣传口径未校准                          | 口碑风险                           | V8.1 即拆分两数字 + 贡献入口                |

**依赖顺序**：V8.1（测试 + 文档）应先于 V9.0。测试网是前端破坏性升级的安全绳——没有它，Vue3 改造无法防回归。

---

## 四、决策记录

- **2026-11-12**：确立近期路线——先 V8.1（P1 测试与文档诚实化），后 V9.0（P0 前端升级）。核心理由：用测试网为破坏性升级兜底，且先校准对外表述。
- 关键约束锁定：**i18n 开关 OFF = 逐字节零 diff** 的契约在 Vue 2 与 Vue 3 两条线上都必须保持，作为 CI 永久卡点。

---

## 五、进度与验证状态（截至 2026-11-12）

### V8.1 — 完成
- 离线契约 harness：`scripts/i18n_snapshot/VuePageI18nContract.java`，Freemarker 独立渲染 9 种生成模式，断言 `i18nEnabled=0` 与 v8.0 基线逐字节零 diff、`i18nEnabled=1` emit `$t(key,'中文')`。回归结果 **9/9 通过**。
- 回落链单测：`codegen-web/tests/i18n-core.test.mjs` 覆盖 `resolveText/interpolate/isRtl`，**9 passed**；纯函数核心抽到 `scaffold-i18n/i18n-core.js`（V9.0 不变）。
- CI 卡点：`.github/workflows/i18n-contract.yml`（后端 FTL 快照 / 前端 i18n-core / gate 三 job），覆盖 main/master/develop 的 push 与 PR，任一非预期 diff 即阻断合并。
- 文档诚实化：`README.md` 顶部拆「框架支持 206 / 精译 8」；`多语言项目介绍/CONTRIBUTING.md` 提供翻译贡献指引；`scripts/check-locale-keys.mjs` 已落地（生成物自动产出到 `scripts/`），并通过 fixture 单测覆盖缺失键 / 阈值 / 边界。

### V9.0 — 模板与构建改造完成，运行时验证待真实环境
- 生成物依赖与构建升级：`package.json` → Vue3 + Element Plus + Vite；`vite.config.js` 替代 `vue.config.js`；`main.js` 用 `createApp` + 全局注册图标 + `$t` 桥接；`router`/`store` 升 `vue-router@4`/`vuex@4`；`request.js` 用 `import.meta.env` + `ElMessage`；env 由 `VUE_APP_*` → `VITE_APP_*`。
- `i18n.js` 适配层：`Vue.observable` → `reactive`（i18n-core 不变）。
- `vuePage.ftl` 与 web 栈辅助模板（pay / print / flow / scaffold-file / layout / LanguageSwitch）Vue2→Vue3 机械改造：`.sync`→`v-model`、命名插槽 `slot=`→`#`/`#default`、`slot-scope`→`#default`、`.native` 移除、字体图标 `<i class="el-icon-*">`→`<el-icon><Xxx/></el-icon>`、`size="mini"`→`small`。
- **关键契约保持**：OFF 零 diff 在 Vue3 下仍成立（harness 9/9 通过，OFF 长度与 v8.0 基线一致）；i18n 仍是纯条件包裹，Vue3 改造无副作用。
- ⏳ 待办（真实环境，本机无 Maven+DB+浏览器，未谎报完成）：Maven 编译 codegen-server → 跑生成 → `npm install` → `npm run dev` → 浏览器验证 8 精译语言 i18n 切换与 Element Plus 渲染、`ar-SA` RTL 镜像、回落不空白。

### 2026-11-12 运行时验证补记（Maven + MySQL + Chrome 本机齐备后执行）
- **验证方式**：打包 jar 起后端（8120）→ API 驱动一键建工程产出 V9.0 前端 → 脚本断言 40 项（`verify-v81-v90.mjs`）→ Chrome(Headless) 登录系统页面截图（`shot-v9.cjs`）。
- **结果**：40/40 通过；生成物 `npm install` 后 **`vite build` 成功**（Vite 5，产物可构建即 entry/依赖/语法三层全对）。
- **运行时才暴露并已修复的 2 个真实缺陷**（离线 harness 抓不到，正是验证的价值）：
  1. `GenScaffoldServiceImpl` 登录页内联串残留 2 处 `.native`（`@submit.native.prevent` / `@keyup.enter.native`）→ Vue3 移除该修饰符，已改 `@submit.prevent` / `@keyup.enter`；`payOrderPage.ftl` 同类 1 处已清。
  2. `index.html` 仍按 vue-cli 惯例写 `public/`，且缺 Vite 入口脚本 → `vite build` 报 `Could not resolve entry module "index.html"`。已改写到工程根目录并补 `<script type="module" src="/src/main.js">`，favicon 加根路径 `/`。
  3. 路由用 `@/layout/index.vue` 别名但 `vite.config.js` 未声明（vue-cli 隐式内置，Vite 必须显式配）→ Rollup resolve 失败。已补 `resolve.alias`（`fileURLToPath(new URL('./src', import.meta.url))`）。
- 验证脚本已加严：B20 拆成 B20/B20a/B20b（根目录入口 HTML、module script、禁止 public/index.html），B7 增加 `@` 别名断言，防回归。
- **最终结果**：修复后重新生成产物，`vite build` 成功（Vite 5.4.21，14.4s，dist 完整产出），verify 40/40 通过。

### 2026-11-13 语言支持全量核验（206 种）
- `/language/list` 返回 **206** 种语言，与静态清单 `i18n/languages.json` 完全一致；每条均含 code / ISO639-2 / locale / 中英文名 / 原文名 / 书写方向 / 启用状态，**0 缺字段、0 重复、0 禁用**。
- `/language/options` 下拉项 **204**（BCP47 主语言去重，符合设计）；**12 种 RTL**（ar-SA、fa-IR、ur-PK、he-IL、ar-EG、dv-MV、ks-IN、ku-IQ、ps-AF、sd-PK、ug-CN、yi-IL）。
- **36 个主要语区抽样全覆盖**（zh/en/ja/ko/fr/de/es/ru/pt-BR/hi-IN/th-TH/vi-VN/id-ID/tr-TR/it-IT/nl-NL/pl-PL/sv-SE/da-DK/fi-FI/nb-NO/el-GR/he-IL/hu-HU/cs-CZ/ro-RO/uk-UA/ms-MY/bn-BD/ta-IN/te-IN/fa-IR/ur-PK/sw-KE/af-ZA → 36/36）。
- 词库 `i18n/messages/` 共 **204** 个 JSON；8 精译语言词库与 `zh-CN` 基线**逐键一致**（各 60 键，偏差 0）。
- 口径重申：**框架支持 206 / 精译 8**；未精译语言按回落链（目标语言 → 中文内联兜底）展示，不出现空白。验证明细存档 `.screenshots/v9/lang-verify-result.json`。

### 2026-11-13 V9.1 拆分验证记录（可复现）
- **拆分口径**：以「成员定义 + 其 javadoc」为切分单元，直接搬运代码文本、仅把 `private` 改为 `static`，不做任何字符串内容改动——从机制上保证产物不变。
- **等价性验证（最强证据）**：`git stash` 前后各打一个 jar（`old.jar` 无 Writer 类 / `new.jar` 含 8 个新类），两服务并行启动，同一 DTO 各生成两套工程：
  - 变体 A：i18n 开（zh-CN + ar-SA + ja-JP）+ 支付 + 文件 + 移动端 + 测试骨架 + 部署物 → **119 个文件**
  - 变体 B：微服务（gateway）+ i18n 关 → **62 个文件**
  - `diff -r` 两套均为 **0 差异**（含二进制资源与 `.gitignore` 等隐藏文件）。
- **测试**：`mvn test` → **312 passed / 0 failed / 0 error**（拆分前基线为 13 failed，本次一并清偿）。
- **契约**：`VuePageI18nContract` 9 模式 **9/9 PASS**（OFF 与 V8.0 基线字节相等、ON 含 `$t`）。
- **结构**：主类 931 行（914 行编排 + 17 行兼容桩）；最大单文件 `ScaffoldCommonWriter` 1132 行（此前 5072 行）；新增类均无实例字段、无 Spring 注解，可被单测直接静态调用。
- **代价与取舍**：为兼容 9 个反射入口保留薄委托桩（约 20 行），与 V7.5 移动端旧入口同款策略；不修改任何基线测试的断言口径（除两个确已过期者，见上）。

---

## V9.2 — 测试中心 + 验证中心（已实现，2026-11-13）

> 用户拍板「两套合并」：工作区既有的接口/UI 用例中心（`test_*` 七表、`/test/*`）与本轮新增的
> 验证资产收拢（`tc_*` 三表、`/tc/center`）作为同一版本的两个子模块交付，路由与菜单 id 互不冲突。
> 完整需求基线见 `docs/代码生成器-V9.2-规划.md`；设计文档见 `docs/design/V9.2测试中心-设计.md`。

**交付**
- 用例中心：`docs/sql/V9.2__test_center.sql`（7 张表 + 菜单 5000–5040）、Controller/Service/执行器/断言引擎、前端 `views/test/index.vue`。
- 验证中心：`docs/sql/V9.2__verification_center.sql`（tc_run / tc_case_result / tc_artifact + 菜单 3040–3043）、
  `TcSuite` 注册表 S1–S9、`TestCenterExecutor`（CAS 单运行锁 / 串行 / 超时 kill / 取消 / 失败不短路）、
  `TestCenterService` + `/tc/*` REST、前端 `views/verification/index.vue`、`codegen.testcenter/v1` 摘要契约。
- 门禁对齐：`scripts/verify.sh` 升到 7 步（`MIN_TESTS` 240 → 312、新增 i18n 快照契约与 206 语言键），
  日志改落 `logs/testcenter/`（不再写 `/tmp`），末尾由 `scripts/testcenter-summary.mjs` 输出同一份 JSON 摘要。
- 驱动脚本：`scripts/testcenter-suites.mjs`（9 套件，Windows `.cmd` / NODE_OPTIONS / JAVA_HOME 差异收敛）、
  `scripts/check-all-locales.mjs`（206/206 键覆盖 + 8 精译 + 12 RTL）。

**验证**
- `mvn -o test` **373/373 全绿**（V9.2 新增 50 个单测：注册表 / 摘要契约 / 请求安全 / 路径白名单 / 执行器状态机 / 报告 / 存储 / 服务层）。
- i18n 快照契约 **9/9**、i18n-core vitest **9 passed**、206 语言键 **206/206**、生成物 JS 1024 组合 0 坏、控制台 `npm run build` 成功。
- 端到端：本机 MySQL 落两套 DDL（幂等重跑 2 次）、服务 8125 启动、登录 → `/tc/suites` 9 个 → `POST /tc/runs` 跑通 S4
  → run/case/artifact 三层落库 → 并发第二次触发 **HTTP 409** → 夹带 `command` 字段 **HTTP 403**。
- 修掉两个真问题：`ReentrantLock` 同线程可重入导致 409 失效（改 CAS）；`overview.activeRunId` 用 JSON number
  下发 19 位雪花 ID 导致前端精度丢失、取消 404（改字符串，与 `BaseEntity` 口径一致）。
- 产物零 diff：本版未改 `gen-templates` 与 8 个 Writer，新旧 jar 生成物对比沿用 V9.1 结论。

**未做（按需求基线留到后续版本）**：S8 截图/构建自动化、S9 新旧 jar `diff -r` 自动化（P1，默认不勾选）、
相邻 run 基线 diff、定时执行、把结果接成合并阻断门禁。

---

## V9.3 — 生成产物自动化测试（已实现，2026-11-14）

> 主题由用户指定为「生成产物自动化测试」。在 V9.2 验证中心（S1–S9）之上扩展产物类套件，
> 不重写测试中心；方案与实现记录见 `docs/代码生成器-V9.3-规划.md`。

**交付**
- 套件：`TcSuite` 新增 **S10 产物单测**（对真实产物跑 `mvn test` + Surefire 解析）、**S11 产物接口测试**
  （Docker/TestContainers，缺失如实 SKIPPED）、**S12 产物 E2E**（Playwright，缺失如实 SKIPPED）；
  `GEN_PRODUCT_SUITES` / `isGenProductSuite()` 统一标识，前端按 `TcSuiteVO.genProduct` 渲染，不硬编码套件号。
- 触发：`TestCenterExecutor` 新增 `GEN` trigger 与 `TC_GEN_ROOT`（经 `TcProcessSpec.env` 注入子进程）；
  `autoVerifyGen` 默认 `false` 控制写盘后自动钩子；`POST /tc/runs/gen` 手动触发忽略该开关。
- 前端：`views/verification/index.vue` 新增「验证生成产物」操作区，`api/verification.js` 新增 `triggerGen()`，
  运行历史的 GEN 触发类型高亮。
- 安全：`TcGenRunRequest` 只接 `genRoot`，不接收命令 / path 黑名单字段。

**验证**
- `TcSuiteRegistryTest` 通过（12 套件 / 默认勾选 8 个）；`mvn -o compile` / `package`、控制台 `npm run build` 均通过。
- 端到端：新建「设备资产管理系统」（`v93_asset_demo`，4 张表）真跑 12 步流水线 `status=success`、
  `failReason=None`、后端 8140 / 前端 9040 / health 200。
- 真实产物 GEN 验证：`TC20261114-00147`、`TC20261114-00109` 均 **PASSED**；S10 真跑产物自带的
  4 个 ControllerTest **20/20 用例全过**，S11/S12 如实 SKIPPED，`tc_run` / `tc_case_result` /
  `tc_artifact` 三层落库核对无误（1 行 / 3 行 / 3 个日志）。
- 全流程实测修掉 4 个流水线缺陷（肥 jar 自检 classpath、子进程相对路径、Vite 与 vue-cli 启动串台、
  MyBatis-Plus NOT_NULL 导致 `fail_reason` 残留）与生成产物 Vue3 页面 3 处 Vue2 遗留
  （`gen:end` 尾部标记、`GroupView` 的 `h(RouterView)`、`>>>` 深度选择器），已沉淀坑 65–69。

**未做**：S11/S12 真实接入（需 Docker + Playwright 且产物内置 TestContainers / RestAssured /
Playwright 测试骨架）；`autoVerifyGen` 保持默认关闭，避免日常生成刷测试记录。