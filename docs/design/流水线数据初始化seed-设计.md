# 流水线数据初始化（seed 步）设计

> 2026-10-25。需求见 `docs/requirements/流水线数据初始化seed-需求.md`；本期 P0。

## 一、表结构

`gen_pipeline` 加 1 列（已执行，2026-10-25）：

```sql
ALTER TABLE gen_platform.gen_pipeline
  ADD COLUMN seed_mode varchar(10) DEFAULT NULL
    COMMENT '数据初始化：rule规则生成/off跳过（NULL=off）' AFTER mode;
-- 验证：information_schema.columns COUNT(*) = 1
```

沿用 V4.4 坑库教训：可空三态，NULL=off，不用 NOT NULL DEFAULT（会杀死回落语义）。
`gen_pipeline_step` 不加列不加行——seed 步沿用现有步骤行结构，`seedSteps()` 种子插入即可。
`schema-test.sql` 无 gen_pipeline 快照（契约测试不触碰流水线表），无需同步。

## 二、功能设计

| 编号 | 位置 | 设计 |
|---|---|---|
| F-01 | `GenPipelineCreateDTO.seedMode` / `GenPipeline.seedMode` | 合法值 `rule`/`off`（其他值按 off）；向导 radio，默认 rule |
| F-02 | `GenPipelineServiceImpl.seedSteps` + `PipelineExecutor.STEP_KEYS` | `{"seed","数据初始化","pipeline"}` 插在 db(order 2) 之后，后续步骤 order 顺延；旧流水线无此步行，天然跳过 |
| F-03 | `support/GenSeedGenerator`（新类，唯一实现） | 见下 |
| F-04 | `GenSeedGenerator` | 每表 `SELECT COUNT(*)`，>0 跳过；生成 INSERT 落盘 `logs/pipeline/{no}/seed.sql`（UTF-8） |
| F-05 | `GenPipelineController POST /{id}/seed` + `GenPipelineService.seedExisting` | 状态校验（await_confirm/failed/success/stopped 均可跑），只补台账中 0 行的表 |
| F-06 | `stepSeed` 返回 message | 汇总"共 N 表 M 行"+ 跳过清单（截断保护） |

**GenSeedGenerator 规则引擎**（纯函数内核 `buildInserts(schema, tables)` 便于单测）：

1. **表集**：`gen_table`（projectId）∩ 目标库 information_schema 真实表；列取 information_schema.columns。
2. **跳过列**：`auto_increment`；有非空默认值；`id` 主键（有默认/自增）；二进制类型（blob/binary/json 本期给 NULL）。
3. **外键拓扑**：`KEY_COLUMN_USAGE`（referenced 在表集内）Kahn 排序；成环降级台账顺序 + 告警。
4. **行数策略**：`mdm_/sys_/base_` 前缀 12 行；有外键指向表集内父表的"明细表"跟随首个父表（父行数 × 2~4，≤24）；其余 8 行。单步总上限 2000 行，超限表记待补数。
5. **取值优先级**：枚举（复用 `GenTemplateModel.parseEnumOptions`，升 public——注释「1启用0停用」等）→ 语义词库（列名/注释关键词：姓名→人名、公司/客户/供应商→公司名、城市/地址→地名、手机→手机号、邮箱、金额/价格→金额、数量/库存、部门、备注…）→ 类型兜底（varchar 截长度、int/bigint 范围、decimal 按精度、datetime/date 时间偏移、text 短语）。
6. **唯一列**：`column_key=UNI` 或单列唯一索引 → 语义值+序号后缀（长度截断），撞库重试 3 次后弃表。
7. **执行**：INSERT（显式列名，多行 VALUES，每 50 行一条）→ `ddlService.execute`（scaffold 种子同路径，自带语句切分与库上下文）。
8. **NOT NULL 无默认列必须生成值**；可空列同样填充（演示数据更真实）。

## 三、接口设计

| 接口 | 方法 | 路径 | 请求体 | 响应 |
|---|---|---|---|---|
| 创建任务（改） | POST | `/gen/pipeline` | `{systemName, dbName, mode, tables/ddlText, seedMode}` | GenPipelineVO（+seedMode） |
| 独立补数（新） | POST | `/gen/pipeline/{id}/seed` | — | `{code,msg,data:{insertedTables, totalRows, skipped:[{table,reason}], warn}}` |

- 补数接口复用登录态（平台统一鉴权），无新白名单
- 错误也是 HTTP 200 + body.code（项目约定）

## 四、明确不做（本期）

- P1：AI 语义生成（seed_mode=ai）、明细-主表金额一致、向导粘贴自定义 INSERT
- P2：CSV 导入、单表"再造一批"、词库可配置

## 五、验证记录

（2026-10-25 实测回填）

**第 1 层 编译**
- `mvn -o compile` → BUILD SUCCESS
- `mvn -o test` → **Tests run: 137, Failures: 0, Errors: 0**（含新增 GenSeedGeneratorTest 14 例：拓扑排序 3 / 枚举解析 2 / 截断 / 唯一后缀 / 编号前缀 / decimal 精度 / 数量范围 / datetime 格式 / 引号转义 / fillable 3 例 / 行数策略）
- 前端：`vue-cli-service lint` → No lint errors；`npx vitest run` → 20/20

**第 2 层 接口**（冒烟脚本 v52-seed-smoke.mjs，手工 DDL 三表：customer[UNIQUE+枚举] / goods / order[FK→customer+枚举]）
- 创建流水线带 `seedMode=rule` → 落库；步骤种子 10 步：model→db→**seed**→scaffold→…→health（seed 第 3 位）
- `POST /{id}/seed` #1：**共 3 表 32 行**（customer 8 / goods 8 / order 16=父 8×2）
- DB 抽查：level 取值 {1,2,3}、order_status {0,1,2}（枚举不越界）；外键孤儿行 0；唯一列 0 重复（唯一后缀生效：`华信科技有限公司-0001`）；中文语义数据正常（人名/公司/城市/手机号/金额/单号）
- 幂等：#2 复跑 → 0 表 0 行，跳过 3（已有数据）
- seed.sql 落盘 `logs/pipeline/{单号}/seed.sql`

**第 3 层 UI**
- dev server 9530 热更新 200；ESLint 零错误；vitest 20/20
- 向导第②步新增「数据初始化」radio（rule 默认/off）；进度步骤卡片通用渲染自动展示 seed 节点

**验证过程中发现的既有缺陷（不在本期修，已记坑库）**
1. `GenDdlPatcher` 判 id 列用 `body.contains("id ")` 子串——`customer_id bigint` 误命中导致不注入 id 主键，`importTables` 拒收该表（冒烟中以显式 id 绕开）
2. DDL 导入不支持 CREATE TABLE 内联 `CONSTRAINT ... FOREIGN KEY`（表建了但台账缺）；且 `gen_table.uk_table_name` 全局唯一，跨系统导入同名表整批失败（异常被吞，仅 statements 里一条 FAILED）
