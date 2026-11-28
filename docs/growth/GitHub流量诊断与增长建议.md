# CodeGen GitHub 流量诊断与增长建议

> 数据采集时间：2026-11-28 17:15 (GMT+8)
> 数据来源：GitHub Trending 周榜、GitHub Search API、仓库 REST API、仓库 README 与 Pages 实测、竞品仓库元数据
> 结论一句话：**项目不缺功能，缺的是被看见的入口。当前选题赛道与 2026 年 GitHub 流量入口严重错位，且冷启动资产为零。**

---

## 1. 数据快照

### 1.1 自己的仓库

| 指标 | 实测值 | 判断 |
| --- | --- | --- |
| Stars / Forks / Watchers / Issues | 0 / 0 / 0 / 0 | 冷启动未完成，GitHub 搜索按 star 排序 → 永远排在长尾 |
| 账号下仓库数 | 5 个，全部 0 star | 无入口资产，无交叉导流 |
| 精确搜索 `Majiang CodeGen` | 命中，第 1 位 | 品牌名只存在于描述里，普通用户不会这样搜 |
| 通用搜索 `code generator spring boot` | 前 8 位未见 | 9 万+ 同类仓库中不可见 |
| 描述语言 | 英文 | 核心用户（CSDN / 中文开发者）读不到 |
| Topics | 20 个，含 `curd` 拼写错误、`spring-boot-2` 过时词 | 无 `mcp` / `ai-agent` / `agent-skills` / `code-generation` |
| Releases | 有，最新 9.15（2026-11-23） | 发布节奏正常 |
| 提交节奏 | 最近 5 天连续提交 | 开发活跃度不是问题 |
| Pages 演示 | HTTP 200，可访问 | 有资产但无转化设计（无 Star CTA） |

### 1.2 GitHub 当前热词（周榜 + 搜索规模）

Trending 周榜高频词：**Agent Skills、MCP server、coding agent、Claude Code / Cursor / Codex、AG-UI、agent ready、personal agent**。典型项目：

- `backnotprop/plannotator` — 1,596 星/周，面向 agent 的计划与评审工作台
- `google-labs-code/stitch-skills` — 2,616 星/周，Agent Skills 开放标准
- `anthropics/skills` — 201,669 星，Agent Skills 官方仓库
- `ChromeDevTools/chrome-devtools-mcp` — 59,664 星，"Chrome DevTools for coding agents"

搜索规模对比（仓库数）：

| 关键词 | 仓库数 | 头部项目 / 星数 |
| --- | ---: | --- |
| MCP server | 200,351 | awslabs/mcp 9,963 |
| agent skills | 174,021 | anthropics/skills 201,669 |
| vibe coding | 66,410 | 2025Emma/vibe-coding-cn 23,241 |
| AI code generator | 5,594 | gofireflyio/aiac 3,781 |
| code generator | 92,652 | mybatis/generator 5,299 |
| Java code generator AI | 98 | liyupi/yu-ai-code-mother 1,998 |
| low-code spring boot | 42 | 最高 114 星 |

**解读**：不是"代码生成器没人要"，而是"传统 CRUD 代码生成器已是红海存量，增量全在 Agent 工具链"。你恰好站在一个 42～98 个仓库的极窄细分里，而且在这个细分里还是 0 星。

### 1.3 竞品打法对照

| 项目 | 星数 | 命名 | 描述语言 | 外部导流 | 热词 |
| --- | ---: | --- | --- | --- | --- |
| moshowgame/SpringBootCodeGenerator | 2,198 | 大狼狗代码生成器（有品牌） | 中文 | CSDN 博客主页 | 传统词 |
| liyupi/yu-ai-code-mother | 1,998 | 鱼皮 AI 代码母机（有品牌+IP） | 中文，长描述塞满技术热词 | 付费课程页 + 编程导航 | ai-agent / langchain4j / SSE / deepseek |
| hustxia/CodeGen | 0 | CodeGen（泛型名） | 英文 | 无（CSDN 稿件只在仓库内） | 传统词，仅 spring-ai 沾边 |

竞品共同点：**有可传播的名字、有中文首屏、有站外内容阵地、描述里告诉读者"这是什么新东西"**。这四件事你的项目目前一件都没有形成闭环。

---

## 2. 六条诊断（按影响权重排序）

### D1 · 赛道与热词错位（根因，权重最高）

你的 topics 是 `mybatis-plus`、`mysql`、`maven`、`spring-boot-2`、`curd`（还拼错了）。这些是 2018—2022 年的流量入口。2026 年开发者在 GitHub 上搜的是 MCP、Agent Skill、coding agent、vibe coding。项目里**实际已经有 MCP Streamable HTTP Server**（`McpServerConfig`，5 个工具：`codegen_spec_save` / `codegen_spec_confirm` / `codegen_converge_run` / `codegen_pipeline_run` / `codegen_pipeline_status`），但这件事在 README 首屏、仓库描述、topics 里一个字都没提。等于手里拿着热门门票没检票。

### D2 · 零冷启动资产形成死循环

5 个仓库全 0 star。GitHub 的搜索、topic 页、related repositories 都重度依赖 star 作为排序信号。0 star → 搜索不可见 → 没人点 → 没有 star。这个循环靠"继续加功能"打不破，必须靠站外流量强制注入第一波 star。

### D3 · 名字不可传播

`CodeGen` 在 92,652 个结果里是通用词。别人想安利时只能说"那个 hustxia 的 codegen"，记不住、搜不到。竞品"大狼狗""AI 代码母机"虽然土，但能口口相传。"Majiang CodeGen / 码匠 CodeGen"这个品牌只藏在描述开头，没有成为正式展示名。

### D4 · 首屏叙事没有差异点

README 第一句是 "turns a database schema into a complete Spring Boot + Vue business system"，和 9 万个 generator 的表达没有区别。真正有杀伤力的差异点——**表驱动而非提示词驱动、真 `javac` 自检、四态台账一键回滚、marker-block 合并保留手写代码、206 语言**——放在 `<details>` 折叠块里，读者不会展开。

### D5 · 信任信号自相矛盾

- 徽章 `tests 486 passing`，本地全量实测是 **620**；
- 徽章写 `License: Apache-2.0`，GitHub API 识别为 **MulanPSL-2.0**；
- 正文写"当前版本 V9.19"，实际已到 **V9.27.3**；
- README 中英文混杂，英文首屏下面又出现中文介绍段落和中文目录锚点；
- topics 里 `curd` 是 `crud` 的拼写错误。

技术读者看到这些不一致，会怀疑整个项目是否维护认真。这是最低成本、最高性价比的修复项。

### D6 · 有内容资产但没有分发闭环

`docs/blog/` 里有 5 篇可直接发布的 CSDN HTML（智慧警务、服装 ERP、智慧 HR、制造业 MES、多行业落地），但只是躺在仓库里；README 的文档地图把它们当内部文件列出，没有对外发布链接。Pages 演示页只有仓库链接，没有 Star 按钮或"30 秒试玩"路径。内容→仓库→Star 的链路是断的。

---

## 3. 行动建议

### P0 · 一周内做完（解决"被看见"）

**P0-1 品牌化命名**
- 仓库名改为 `majiang-codegen`（0 star 改名成本极低，GitHub 旧地址自动重定向）；若担心链接变动，至少把 README 主标题改为 `# Majiang CodeGen · 码匠代码生成器`，并全文统一。

**P0-2 重写仓库描述（中英双版，直接可用）**
- 英文：`Majiang CodeGen — database-driven code generator for Spring Boot 3 + Vue 3. DDL → compilable CRUD, menus, RBAC and i18n, with an MCP server that lets coding agents generate whole systems.`
- 中文：`码匠 CodeGen — 表驱动代码生成器：DDL 一键生成可运行的 Spring Boot 3 + Vue 3 业务系统，含菜单/权限/多语言，并提供 MCP Server 让 AI 编程助手直接调用。`
- 描述必须包含用户搜索时会用的词（database-driven / MCP / Spring Boot 3 / Vue 3 / DDL），而不是自造概念。

**P0-3 Topics 换血**
- 删除：`curd`（拼写错误）、`spring-boot-2`（已迁 3）、`maven`、`mysql`（低区分度）。
- 保留：`spring-boot-3`、`java`、`vue3`、`mybatis-plus`、`low-code`、`generator`、`i18n`、`developer-tools`。
- 新增（能力已核实，不算蹭热点）：`mcp`、`ai-agent`、`code-generation`、`spring-ai`。

**P0-4 README 首屏重排**
1. 第一行放品牌 + 一句话差异：`表驱动，不是提示词驱动 —— DDL 到可运行系统，代码能编译、能回滚。`
2. 保留现有 8 系统动图，但把对比表从 `<details>` 里拿出来直接展示。
3. 加一条"30 秒试玩"：H2 内存模式，不用装 MySQL（项目已有 `application-h2.yml`）。
4. 顶部放明确的 Star CTA，措辞从"如果你省了一天"改成"Star 是唯一能让更多人看到它的方式"。

**P0-5 修正信任信号**
- 徽章 `tests 486` → `620`，或改成指向 Actions 的动态徽章；
- License 徽章与 `LICENSE` 文件统一为 MulanPSL-2.0；
- 版本描述改为动态 Release 徽章，不再手写 V9.19；
- 清理 README 里的中英重复段落。

### P1 · 2～4 周（解决"被传播"）

**P1-1 拆一个卫星仓库：`majiang-codegen-mcp`**
把已有的 5 个 MCP tools 抽成一个独立、极简的仓库：README 只需"是什么 / 怎么配 / 3 个调用示例 / 回主仓库"。给 Claude Code、Cursor、Codex 的可复制配置片段。这是当前 GitHub 最大的流量入口，而且内容真实——你不是蹭 AI，是把已有能力包装成别人能直接用的形态。两个仓库互链，卫星仓库给主仓库导流。

**P1-2 把 5 篇 CSDN 稿真发出去**
标题带热词和具体数字，例如"我用 MCP + Spring Boot 3 做了个一句话生成 68 页系统的代码生成器"。每篇文末固定三件套：仓库链接、在线演示、Star 请求。每周一篇，连续 5 周。

**P1-3 演示页做转化**
Pages 当前是静态 showcase。加醒目的 Star 按钮、"30 秒试玩"命令块、以及从演示页直达仓库的锚点。README 顶部 GIF 保留。

**P1-4 把 0 issue 变成社区入口**
置顶一个 Roadmap / 场景征集 issue，列出"你最想生成的行业系统"，给读者一个留言的理由。Issue 数本身就是活跃信号。

### P2 · 1～3 个月（解决"规模化"）

- 围绕争议话题做内容："Agent 时代，表驱动代码生成还有没有价值？"——这类话题在掘金 / B站 / 公众号有自然传播力。
- 找 5～10 个真实用户试用，收集截图和反馈，用真实案例替代自说自话。
- 保持每周发版 + Release Notes，把更新做成可订阅的节奏。
- 明确流量目标：要 Star、要试用、还是要商业线索？不同目标对应不同的 CTA 落地页，别混在一起。

---

## 4. 不建议做的事

- **不要买 star 或互赞群**：GitHub 会定期清理，且虚假 star 不带来任何试用和反馈。
- **不要堆无关热词**：把 `agent-skills` 贴上去但仓库里没有 skill，读者进来 3 秒就走，跳出率比不贴更差。
- **不要推翻项目重做成纯 AI Agent**：你已经有 MCP Server、AI 建模、SDD/Converge 流水线，这是真实差异化，应该放大而不是重写。
- **不要全量机翻成英文**：中文开发者是基本盘，正确做法是中文首屏 + 英文摘要，或清晰的双语导航。

---

## 5. 验证指标

| 时间 | 指标 | 目标 |
| --- | --- | --- |
| 7 天 | Star 数 | 0 → 20+ |
| 7 天 | `Majiang CodeGen` 搜索 | 第 1 位（已达成） |
| 14 天 | `code generator spring boot` 搜索 | 进入前 3 页 |
| 30 天 | Star / Fork / Issue | 100+ / 20+ / 10+ |
| 30 天 | 站外发布内容 | 5 篇 CSDN + 1 个卫星仓库 |
| 持续 | GitHub Insights → Traffic | 看 unique visitors 和 referrers 来源，比 star 更能反映真实流量 |

> 关键提醒：**先修 P0-5 的信任信号**，因为它只需要改几行字，却决定了后面所有引流动作的转化率。
