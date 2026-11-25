# 代码生成器的"最后一公里"：页面级后处理的工程化方案（枚举收敛 / 关联下拉 / 悬空外键清理）

> 生成器能产出 66 张表对应的 Controller 和 Vue 页面，但产出的页面"能用"和"好用"之间还差最后一公里：
> 枚举列显示裸数字、外键是手填数字框、脏数据外键指向已删除的行。这篇文章讲我们如何把这最后一公里做成**可重复、幂等、可验收**的工程化流程。
> 全文代码均来自真实项目，跑过 6 个行业系统、最多一次清理 861 条悬空外键。

> **代码仓库**：Gitee https://gitee.com/hustxiayuhong/CodeGen ｜ GitHub https://github.com/hustxia/CodeGen （觉得有用请点个 Star ⭐）
> **在线演示**：https://hustxia.github.io/CodeGen/ （浏览器直接看真实生成效果，无需安装任何东西）

---

## 一、问题：模板里缺了什么

我们的行业模板（`vuePage.ftl`）拿到的表元数据只有四列：`name / type / comment / pk`。没有 `dictType`（这列是字典还是枚举？取值是什么？），也没有 `refTable`（这列外键指向哪张表的哪个名称列？）。

所以生成产物天然长这样：

- 状态、类型、等级这类 tinyint 字段 → 列表里显示 `2`，表单里是个 `el-input-number` 手填数字框
- `major_id`、`college_id` 这类外键 → 列表里显示裸 ID，表单里还是手填数字框，用户根本不知道该填几

有两条路可以修：

| 路线 | 做法 | 问题 |
|---|---|---|
| 改模板 | 建模时给每张表补 dictType/refTable 元数据 | 每个行业都要重新标一遍 66 张表；模板复杂度暴涨 |
| 生成后处理 | 生成完，用一个脚本按统一配置批量改产物 | 需要解决幂等、原子性、Vue3 兼容等工程问题 |

我们选了**后处理**，理由是：枚举/外键的"展示语义"本来就是业务侧知识（哪个字段是枚举、外键指向哪），把它收敛成一份 JSON 配置，比塞进生成模板更可维护——模板管"结构"，后处理管"语义"，职责分离。

---

## 二、总体设计：生成期标记 + 生成后幂等注入

后处理脚本要往生成的 `.vue` 和 `Controller.java` 里插代码，最容易翻车的点是**重复执行**：跑一遍注入一份 `enumMap`，跑十遍就有十份，前端直接编译报错。

所以我们定了一条铁律：**所有注入必须挂在标记锚点上，重跑先清旧块再重写**。

模板在生成区的末尾预留锚点：

```
// <gen:end key="vue-methods">        ← Vue 页面 methods 生成区末尾
// <gen:end key="controller-body">    ← Controller 类体末尾
```

后处理注入自己的标记块：

```js
// <codegen-polish:data>
enumMap: { status: [...], planStatus: [...] },
vvRefOptions: {}, vvRefLoading: {},
// </codegen-polish:data>

// <codegen-polish:methods>
enumText(field, value) { ... },
vvSearchRef(field, refTable, label, keyword) { ... },
// </codegen-polish:methods>
```

重跑时的收敛流程：

```
收集产物中现有 enumMap / ref 映射（含历史版本裸注入的）
  → 按"配置优先、原生保留"合并
  → 删除全部旧标记块和历史散落注入（按括号平衡精确删，不能只删标记行）
  → 重新写入单一标记块
```

两个细节值得一提：

1. **删除注入代码要用括号平衡匹配，不能正则点到为止**。方法体里有字符串、对象字面量，简单正则会删穿。我们写了个 60 行的 `matching_brace`：跟踪引号状态（含转义）+ 大括号深度，找到真正匹配的 `}`，连尾逗号、换行一起吞掉。
2. **伪幂等比不幂等更隐蔽**。早期版本只删标记行不删缩进，每跑一轮代码整体右移 4 个空格——功能没坏，但 diff 越滚越大，代码评审直接没法看。

---

## 三、枚举收敛："三件套"必须原子化

枚举字段的完整改造是三件事：

```js
// ① data 里有数据源
enumMap: {
  planStatus: [{ value: 1, label: '草稿' }, { value: 2, label: '评审中' },
               { value: 3, label: '已发布' }, { value: 4, label: '执行中' }, { value: 5, label: '废止' }]
},

// ② methods 里有翻译方法
enumText(field, value) {
  const list = (this.enumMap && this.enumMap[field]) || []
  const hit = list.find(d => String(d.value) === String(value))
  return hit ? hit.label : (value === undefined || value === null ? '' : value)
},

// ③ 模板里有调用点
<el-table-column prop="planStatus" label="方案状态"
  :formatter="(r)=>enumText('planStatus', r.planStatus)" />
```

### 翻车实录：64 页表格假空白

早期版本把这三件事**分两轮跑**：第一轮注入调用点，第二轮才注入方法本体。结果第二轮只处理了"配置里显式声明的枚举页"——其余 **64 个页面**调了 `enumText` 却没有这个方法，渲染函数抛 `_ctx.enumText is not a function`，Element Plus 把渲染异常吞掉，表象是 **Total 显示 8、表格体全白、console 没有报错**。

这是最阴险的一类 bug：页面"看起来加载正常"，数据接口也 200，就是表格空白。截图脚本跑完 66 页，靠 DOM 检查才逮到。

**修法固化成原则：三件套一次注入，绝不分轮。** 哪个页面有枚举调用点，同一轮就必须有 data 和方法；没有就 build 阶段报错拦下。

### 业务枚举错配：enumText('status', row.planStatus)

还有一个更隐蔽的坑：生成器对**所有** tinyint 枚举列统一生成 `enumText('status', row.xxx)`——把业务枚举指到了通用的 `status` 映射（正常/停用）。业务值 2/3/4 在 status 的 0/1 值域里找不到，回退显示原始数字。

**修法**：枚举映射按 **camelCase 字段名**建立独立条目（`planStatus`、`batchStatus`、`executionStatus`……），调用点和搜索下拉的选项来源（`enumMap.planStatus`）同步指向同名字段。一句话总结：**一个业务枚举一个值域一份映射，绝不共用通用映射兜底**。

---

## 四、关联下拉：远程搜索 + 通用 /options 端点

外键字段（`major_id`、`teacher_id`、`base_id`……）的改造同样三件套：

```js
// 表单：手填数字框 → 远程搜索下拉
<el-select v-model="form.majorId" clearable filterable remote style="width: 100%"
  placeholder="输入关键字搜索"
  :remote-method="kw => vvSearchRef('majorId', 'edu_major', 'major_name', kw)"
  :loading="vvRefLoading['majorId']">
  <el-option v-for="o in vvRefOptions['majorId'] || []" :key="o.id" :label="o.label" :value="o.id" />
</el-select>

// 列表：裸 ID → 关联名称
:formatter="(r)=>refText('majorId', r.majorId)"

// created 时预加载全部关联（页面打开下拉就是满的）
vvLoadRefs() {
  const all = { majorId: ['edu_major', 'major_name'], teacherId: ['edu_teacher', 'teacher_name'] }
  Object.keys(all).forEach(f => this.vvSearchRef(f, all[f][0], all[f][1], ''))
}
```

### 后端：一个通用端点服务所有关联

不给每张表单独写 options 接口，而是给每个 Controller 注入同一个通用端点：

```java
@GetMapping("/options")
public Result<List<Map<String, Object>>> options(@RequestParam("refTable") String refTable,
                                                 @RequestParam(value = "label", required = false) String label,
                                                 @RequestParam(value = "keyword", required = false) String keyword) {
    // ① 表名白名单：只允许字母数字下划线，杜绝注入
    if (refTable == null || !refTable.matches("[A-Za-z0-9_]+")) {
        throw new BusinessException(400, "非法关联表名");
    }
    String labelCol = label == null || !label.matches("[A-Za-z0-9_]+") ? "name" : label;
    String base = "SELECT id, " + labelCol + " AS label FROM " + refTable;
    String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    int limit = like == null ? 500 : 20;   // ② 预加载 500 条 / 搜索 20 条
    String sql = base + " WHERE deleted = 0" + (like == null ? "" : " AND " + labelCol + " LIKE ?")
            + " ORDER BY id DESC LIMIT " + limit;
    ...
}
```

三个安全/工程细节：

- **表名列名过正则白名单**才拼 SQL，参数化查询 + 白名单双保险
- **预加载 500 条、关键字搜索 20 条**：下拉打开即有值，搜索走远程不卡顿
- **软删除过滤**（`deleted = 0`）：已被删除的学院不再出现在下拉里

### 翻车实录：request 没导入，错误被 try/catch 吞掉

注入的 `vvSearchRef` 里用了 `request`，但部分模板版本的页面只用具名 API 函数、不 import 它。`ReferenceError` 被 `try/catch` 静默吞掉——表象是**无 JS 报错、无 options 请求、下拉永远空、列表显示裸 ID**，四样凑齐才能定位。

**修法**：注入前检查文件头部有没有 `import request`，没有就补一行。经验固化：**注入代码引用的每个外部符号，注入前都必须验证依赖存在**。

---

## 五、label 列校验 + 悬空外键清理

### 配置写错了怎么办：先校验再应用

配置里写 `majorId → edu_major.major_name`，但如果列名拼错，下拉永远是空的（SQL 查不到 label 列，接口返回空数组，还不报错）。所以应用前先跑校验：

```bash
python polish_enums_refs.py --config cfg.json --check-labels
# 坏引用 majorId -> edu_major.majr_name 列不存在
# label 列校验完成，坏引用: 1
```

直接查 `information_schema.columns`，坏引用退出的 exit code 非 0，可以挂进 CI。

### 悬空外键：LEFT JOIN 找出来，置 NULL 修掉

演示数据和迁移数据常见一种脏数据：外键有值，但指向的行已经物理删除。前端 `refText` 查不到 label，列里显示裸数字，数据看板一打开就很廉价。

清理逻辑两步走：

```sql
-- ① 找悬空：左连接目标表，连不上的就是悬空
SELECT COUNT(*) FROM edu_practice_plan a
LEFT JOIN edu_internship_base b ON a.base_id = b.id
WHERE a.base_id IS NOT NULL AND b.id IS NULL;

-- ② 悬空置 NULL（不删业务行，只清引用）
UPDATE edu_practice_plan a
LEFT JOIN edu_internship_base b ON a.base_id = b.id
SET a.base_id = NULL
WHERE a.base_id IS NOT NULL AND b.id IS NULL;
```

脚本按"每张页面表 × 每个外键字段"全量扫描执行。实战数据：学校人才培养方案系统一次性清理 **861 条悬空外键**（流水线早期测试数据残留），清完 66 页列表再无一个裸 ID。

注意一个坑：Java 字段到数据库列名的驼峰反推（`baseId → base_id`）对含随机 slug 的表名不可逆（`v94_1a9iwwc_main_id` 这种），这类系统必须在配置里显式给 `refColumns` 映射。

---

## 六、Vue3 兼容：this.$set 是颗哑雷

从 Vue2 模板迁移过来的生成逻辑里有 `this.$set(this.dictMap, key, value)`。Vue3 移除了这个 API，运行时直接 `TypeError`——而且它发生在数据加载回调里，被外层 catch 吞掉，表象又是"页面正常、数据不显示"。

**修法**：后处理阶段全局替换为直接赋值（Vue3 Proxy 响应式不需要 $set）：

```js
// ❌ Vue2 写法，Vue3 抛 TypeError 且被 catch 吞掉
this.$set(this.dictMap, key, value)

// ✅ Vue3：对象新增属性直接赋值即可响应
this.dictMap[key] = value
```

连同类的哑雷还有：注入方法时生成区末方法**没有尾逗号**（`import` 语法错误）、`dictText('sys_status', ...)` 旧字典 API 残留。全部收进后处理的清理清单，一次跑完。

---

## 七、工程化闭环：四条命令 + 三道验收

后处理最终收敛成一个脚本、四种模式：

```bash
python polish_enums_refs.py --config cfg.json --check          # 只统计不改（dry run）
python polish_enums_refs.py --config cfg.json --check-labels   # 校验关联配置的 label 列存在
python polish_enums_refs.py --config cfg.json                  # 应用到产物
python polish_enums_refs.py --config cfg.json --fix-dangling   # 悬空外键置 NULL
```

配套三道验收，缺一不可：

1. **`npm run build`**：语法层拦截（尾逗号、未定义引用、模板解析错误）
2. **逐页截图 + console 检查**：66 页全部截图，断言 console 错误数为 0——专抓"假空白"这类无报错渲染异常
3. **抽查视觉**：枚举列是否中文、外键列是否名称、下拉是否远程搜索，抽 3-5 页人工确认

### 实战数据（学校人才培养方案系统，66 表 / 66 页）

| 指标 | 数值 |
|---|---|
| 关联下拉注入 | 130 处 |
| Controller 补 /options 端点 | 63 个 |
| 悬空外键清理 | 861 条 |
| 业务枚举独立映射 | 12 个（planStatus / batchStatus / executionStatus 等） |
| 验收结果 | 66 页截图，console 错误 0 |

---

## 八、总结：三条可复用的设计原则

这套"最后一公里"方案跑过 6 个行业系统后，沉淀出三条与具体框架无关的原则：

1. **语义后置，结构前置**：生成器管表结构和页面骨架，枚举/关联等业务语义收敛成独立 JSON 配置，由后处理落地——改语义不用重跑流水线
2. **幂等是注入类工具的生命线**：标记锚点 + 括号平衡清理 + 合并重写，保证脚本跑一遍和跑十遍结果一致
3. **无报错的失败最危险**：try/catch 吞掉的 ReferenceError、Element Plus 吞掉的渲染异常，都要求验收必须包含"逐页 DOM/console 断言"，只看接口 200 是不够的

下一篇计划写：**12 步生成流水线的失败定位设计**（failReason 如何做到每一步可归因），感兴趣可以关注。

---

## 如果这个项目帮到了你

- **点个 Star** ⭐：Gitee https://gitee.com/hustxiayuhong/CodeGen （无需代理，顺手一点）｜ GitHub https://github.com/hustxia/CodeGen —— 让更多开发者少写一天 CRUD
- **国内克隆**：直接用 Gitee 镜像：`git clone https://gitee.com/hustxiayuhong/CodeGen.git`
- **先看效果再决定**：[在线演示](https://hustxia.github.io/CodeGen/) 展示真实生成系统的首页与数据看板
- **遇到问题**：提 [Discussions](https://github.com/hustxia/CodeGen/discussions) 或 Issue，我会回复
