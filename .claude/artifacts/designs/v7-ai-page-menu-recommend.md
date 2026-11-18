# V7.0 AI 页面与菜单推荐 Spec

> Status: DRAFT
> Author: xiayuhong
> Last updated: 2026-11-04

## Background

生成器已有 AI 建模（`AiModelService.model/modules`，OpenAI 兼容接口，`pipeline.ai` 配置，失败降级「手动粘贴 DDL」），但表结构就绪后的页面配置仍全靠人工：列级的查询/列表/表单/字典开关来自 V6.6 列名语义推断（纯规则），表级的模块分组/树形/主子识别全靠手填，菜单目录组织靠 `moduleName` 约定。V7.0 让 AI 在「建模之后、生成之前」自动推荐这三层配置，用户预览确认后应用。V6.9 版本号已被未提交的「文档中心」占用，本版顺延 V7.0。

## In scope

- `AiRecommendService`：输入本系统全部表与列元数据（按模块分批调用），输出三层建议 JSON——
  - 列级：`is_query / query_type / is_list / is_form / html_type / dict_type / sort`
  - 表级：`module_name`（模块分组）、`tree_parent_column / tree_label_column`（树形识别）、主子表与中间表识别（`master_table_id / relation_type`）
  - 菜单组织：目录层级、菜单名称、图标候选、排序（作用于菜单注册参数）
- pipeline 新增独立步骤 `ai_recommend`：表结构就绪后、模板生成前**自动执行**，无手动入口
- 建议暂存于 pipeline 步骤产物（JSON），不落 `gen_table / gen_table_column`
- 前端 diff 预览页（当前值 vs 建议值），用户勾选后一键应用，**应用时才写库**
- 服务端白名单校验：枚举值非法、引用的列名/表名不存在 → 该项建议丢弃并回落 V6.6 规则默认值
- 失败降级：`ai.enabled=false`、超时（沿用 30s）、调用失败 → 步骤标记 degraded/skipped，pipeline 用规则默认继续，不阻断（沿用 `AiModelService` 既有原则）

## Out of scope

- 产物侧 AI 能力包（生成系统自带 AI 对话/RAG）
- 对话式迭代建模（多轮改表）
- AI 审查 DDL / 命名规范
- AI 种子数据、AI 修复生成错误
- 表配置页手动「AI 推荐」按钮（本期仅自动触发）
- 模型配置 UI（沿用 `pipeline.ai` yml 配置）

## Assumptions

- 模型走 OpenAI 兼容接口，复用 `pipeline.ai`（base-url / api-key / model / timeout-seconds）
- AI 输出为结构化 JSON（Function Calling 或 JSON Schema 约束），不做自由文本解析
- 菜单组织建议不直接写 `sys_menu`，仅作为生成后菜单注册环节的参数来源
- 版本号默认 V7.0（V6.9 = 文档中心，未提交）；用户未显式确认，可在评审时改

## Solution

建模/表导入完成 → pipeline 触发 `ai_recommend` → 按模块分批调用 AI（沿用 model/modules 两段式控制单次输出预算）→ 服务端校验与清洗 → 建议 JSON 暂存于步骤产物 → 前端预览页 diff 呈现 → 用户勾选应用 → 写 `gen_table / gen_table_column`，菜单组织参数随写盘后的菜单注册生效 → pipeline 继续生成。任何一环 AI 不可用即回落 V6.6 规则推断，产物与 V6.8 行为一致。

## Edge cases & risks

| Category | Notes |
|---|---|
| Boundary conditions | 单表系统与 30+ 表完整系统都要覆盖；空注释列（无 comment）建议质量下降，允许 AI 输出「不建议」 |
| Failure modes | AI 超时/返回截断 JSON/枚举越界/引用不存在的列；预览未应用时用户直接触发生成 |
| Risks | token 成本随表数增长；建议与手改冲突（用户已手动调过的字段被 AI 再建议） |
| Mitigation | 分批调用 + 输出预算上限；白名单校验逐项丢弃；已手改字段（与规则默认不一致）在预览中标记「已手改」默认不勾选 |

## Acceptance criteria

- AC-1 `ai.enabled=false` 时执行 pipeline → `ai_recommend` 步骤标记 skipped，生成产物与 V6.8 完全一致
- AC-2 AI 返回非法 `html_type` 或不存在的树形列名 → 该项建议被丢弃、回落规则默认值，pipeline 不中断，步骤日志记录丢弃原因
- AC-3 预览页未点「应用」→ `gen_table / gen_table_column` 无任何 UPDATE（应用前后行内容一致）
- AC-4 勾选应用后 → 被选中项字段值与建议一致写库，重新生成的 vue 页面与菜单随之变化（如某列从不可查变为下拉查询）
- AC-5 AI 调用超时 → 步骤标记 failed-degraded，pipeline 用规则默认完成生成，整体状态为成功（带降级标记）
- AC-6 40 张表系统分批调用 → 单批输出不超模型预算，全部建议完整暂存，无截断 JSON

## Open questions

- 版本号 V7.0 待用户最终确认（当前默认）

## Core entities (ontology)

| Entity | Type | Key fields | Relationship |
|---|---|---|---|
| AiRecommend | 步骤产物 JSON | tableId, layer(column/table/menu), field, currentValue, suggestedValue, applied | 挂在 GenPipelineStep 下 |
| GenTableColumn | 既有实体 | isQuery/queryType/isList/isForm/htmlType/dictType/sort | 列级建议应用目标 |
| GenTable | 既有实体 | moduleName/treeParentColumn/treeLabelColumn/masterTableId/relationType | 表级建议应用目标 |
| SysMenu | 既有实体 | 目录层级/名称/图标/排序 | 菜单组织建议间接生效 |

## Interview metadata

- Mode: default
- Waves: 4
- Final ambiguity: ~20%
- Status: PASSED

### Clarity breakdown

| Dimension | Score | Weight | Weighted |
|---|---|---|---|
| Goal | 0.85 | 0.40 | 0.34 |
| Scope | 0.80 | 0.25 | 0.20 |
| AC | 0.70 | 0.25 | 0.175 |
| Context | 0.85 | 0.10 | 0.085 |
