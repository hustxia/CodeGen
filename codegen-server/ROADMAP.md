# 代码生成器 · 版本路线图（Roadmap）

> 当前版本：**V8.0** — 全页面国际化，206 语言框架支持（双轨制 i18n）  
> 路线图建立：2026-11-12  
> 状态：V8.1 已落地（测试网 + 文档诚实化，CI 卡点生效）；V9.0 生成物 Vue3 改造已完成并通过离线「OFF 零 diff」契约，运行时端到端验证（Maven+DB+浏览器）待真实环境。

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

**目标**：Vue 2.7 + Element UI → Vue 3 + Element Plus + Vite，功能与 i18n 能力等价。

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
- [ ] **回归复验**
  - 8 语言截图（UI 全量译文、ar-SA RTL 镜像、回落不空白）。
  - 构建耗时对比（预期下降）。

**验收标准**

- 生成系统前后端启动正常；i18n 全链路等价（与 V8.0 行为一致）。
- CI 新增 Vue 3 OFF 快照基线，零 diff        卡点生效。

---

### V9.1+ — 体验与生态（P2，待排期）

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
