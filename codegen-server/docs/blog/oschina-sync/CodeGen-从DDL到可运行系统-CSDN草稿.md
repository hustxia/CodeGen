# 从 DDL 到可运行系统：我用代码生成器把 79 张表变成了一个真实警务平台

> CSDN 草稿 · 对应仓库：https://github.com/hustxia/CodeGen （觉得有用请点个 Star ⭐）
> 在线演示：https://hustxia.github.io/CodeGen/ （浏览器直接看真实生成效果，无需安装）
> 发布前请：替换本地截图为图床链接；首尾钩子块不可删（见 docs/blog/文章钩子模板-每篇必加.md）；提交后把 GitHub 主页 Homepage 指到本文。

## 开篇：为什么又写一个代码生成器

CRUD 生成器很多，但真正让人头疼的不是"生成几张页面的代码"，而是生成之后：

- 前后端字段对不上，联调半天；
- 二次生成把手写逻辑冲掉；
- 生成物编译不过，所谓"可运行"只是演示；
- 生成一次容易，持续迭代难。

我做的 CodeGen 想解决的正是后半段：**把数据库表结构变成可编译、可运行、带菜单权限的 Spring Boot 3 + Vue3 工程，并且能安全地重复生成**。

## 30 秒看效果

先看四套由它端到端生成的真实系统首页（非示意图，列表里都是真实数据）：

- 智慧警务：64 页 / 79 表，接处警、重点场所、辖区人口
- 服装 ERP：SKU 矩阵、动销分析
- 智慧 HR：组织、考勤、薪酬
- 制造 MES：工单、批次追溯

在线演示首页：https://hustxia.github.io/CodeGen/

![村务系统首页](images/01-home.png)

## 核心设计：表驱动，而不是提示词驱动

输入是 DDL 或数据库连接，不是一段自然语言描述。所有产物从同一份元数据渲染：

```
DDL / 数据库表
  └─ 字段与开关配置
      ├─ 后端 Entity / Mapper / Service / Controller / DTO / VO
      ├─ 前端 api.js / Vue 页面 / 路由 / 菜单
      └─ 可选：种子数据 / 测试骨架 / 国际化 / 图表看板
```

前后端同源，天然不会漂移。

## 三个真正区别于普通生成器的点

**1. 真实编译自检。** 生成后跑真实 `javac`，不是模板渲染成功就算完成。486 个后端测试覆盖流水线、合并、回滚、i18n 与图表能力包。

**2. 标记块合并 + 四态账本。** 手写代码放在标记块里，重新生成不会覆盖；每次写盘前自动备份，可按批回滚。这是"敢在已有工程上反复生成"的前提。

**3. V9.15 数据可视化能力包。** `chart_enabled` 三态开关，默认关闭时产物与旧版逐字节一致；开启后按列元数据自动推导饼图/折线/柱状/仪表盘，并生成每表 `/stats` 聚合接口（字段白名单，防注入）。图卡用 ECharts 按需引入，颜色跟随主题主色。

## 60 秒跑起来

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
mvn package -DskipTests
java -jar target/codegen-server.jar
```

前端：`cd ../codegen-web && npm install && npm run serve`，打开 `http://localhost:9530`，`admin / admin123`。

## 适合谁

- 手上有一批表，想快速得到可编译的业务骨架；
- 需要在已有工程上反复生成，又怕覆盖手写代码；
- 想研究"代码生成器如何做到可重复交付"这一工程问题。

## 结尾

---

## 如果这个项目帮到了你

- **点个 Star** ⭐：https://github.com/hustxia/CodeGen —— 让更多开发者少写一天 CRUD
- **先看效果再决定**：[在线演示](https://hustxia.github.io/CodeGen/) 展示四套真实生成系统的首页与 V9.15 数据看板
- **遇到问题**：提 [Discussions](https://github.com/hustxia/CodeGen/discussions) 或 Issue，我会回复
