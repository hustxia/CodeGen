# 代码生存器后端（CodeGen Server）

面向 Spring Boot + Vue 项目的**多工程代码生成器**。从数据库表结构出发，产出可编译、可运行、带菜单权限的前后端代码。

## 能做什么

| 阶段 | 能力 |
|---|---|
| 接表 | DDL 解析、库表直连导入、批量接管、列配置自动推导（控件/校验/字典/关联） |
| 生成 | 12 个模板产出 Entity/Mapper/Service/Controller/DTO/VO + api.js + Vue 页面；标记块合并保留手写代码 |
| 校验 | 内存编译自检（真实 javac）、结构契约检查、权限一致性检查、与磁盘 diff 预览 |
| 落地 | 写盘 + `.gen-backup` 批次备份、`gen_output` 四态台账、历史回滚 |
| 配套 | 菜单权限双出口（artifact SQL / internal 直写）、前端路由自动重建、模板版本化管理 |

支持主子表（同事务保存）、树形表、多模块输出、关联下拉、Excel 导入导出。

## 快速开始

### 1. 建库

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

初始化脚本会创建 13 张表，并写入默认账号 `admin / admin123` 与基础菜单。

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

### 3. 启动

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

服务默认端口 **8120**，接口文档 `/swagger-ui/index.html`。

## 软件架构

```
controller/      接口层
service/impl/    核心实现（Gen* 生成链路 / Sys* 系统管理）
util/            模板引擎、模型装配、合并、校验
config/          输出配置、自检配置、模板种子器
common/          Result / BaseEntity / 异常 / 审计
resources/gen-templates/   12 个 FreeMarker 模板
```

技术栈：Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8

## 目标工程必须自带的基座能力

生成器只产出业务代码，以下基础件需由目标工程提供，**缺失会导致静默故障**：

1. **MyBatis-Plus 分页插件**（`PaginationInnerInterceptor`）
   没有它时 `service.page()` 只做 limit 不做 count，`Page.total` 恒为 0——列表数据正常但分页器算不出总页数。
2. **`Result` / `BaseEntity` / `PageQuery`** 等基座类，包名前缀与 `root-package` 一致。
3. **`utils/request.js` 的 `toPageData`**，把后端 `Page{records,total}` 转成页面的 `{list,total}`。

另需 pom 依赖 `springdoc-openapi-starter-webmvc-ui`（生成物用 `@Operation`）与 `poi-ooxml`（Excel 导入导出）。

## 配置说明

| 配置 | 说明 |
|---|---|
| `gen.output.*` | 生成物写盘路径、包名、模块映射 |
| `gen.selfcheck.classpath` | 自检内存编译的依赖 classpath，支持 `dir/*` |
| `gen.menu.outlet` | `artifact` 只产出菜单 SQL 与路由片段；`internal` 同时写入平台自身 `sys_menu` |

## 版本

当前版本 **V6.10（平台可观测性）**：接入 Actuator + Micrometer，5 秒采样写入内存环形缓冲（保留 30 分钟窗口），并新增内嵌「系统监控」页——后端 `/monitor/*` 聚合接口 + 前端 ECharts 面板。端点仅白名单放行 `health/info/metrics`，`/actuator/health` 免 token，`metrics` 等需鉴权；`env/beans/heapdump/threaddump/loggers` 永不暴露。采样窗口仅进程内、重启即丢、多实例不共享。详见 `docs/可观测性说明.md`。

| 版本 | 主题 |
|---|---|
| V6.9 | 平台文档中心（后端基座，前端收尾同批推进） |
| V6.10 | 平台可观测性：Actuator + 内存指标窗口 + 内嵌监控页 |
