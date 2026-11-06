# 码匠后端 (CodeGen Server)

代码生成平台的服务端 — Spring Boot + MySQL，默认监听 http://127.0.0.1:8120，为 [codegen-web](https://gitee.com/hustxiayuhong/code-survivor-frontend) 前端控制台提供全部业务接口。

## 简介

码匠后端是代码生成平台的核心引擎，承载 **建表建模 → 模板渲染 → 差异比对 → 产物落盘 → 台账回滚** 的完整流水线，并对外提供鉴权、菜单下发、字典、模板版本、批次台账等能力。

前端侧边栏菜单由本服务的 `/auth/routers` 接口下发，前端按 component 字符串动态解析视图 —— 新增页面只需在后端配置菜单，无需改动前端路由文件。

## 技术栈

| 分类 | 选型 |
|------|------|
| 语言 / 框架 | Java + Spring Boot |
| 构建工具 | Maven（`mvn package -DskipTests`） |
| 数据库 | MySQL（库名 gen_platform，字符集 utf8mb4） |
| 鉴权 | Token 持久化 + BCrypt 密码加密 + 登录防护 |
| 模板引擎 | 12 套可在线编辑的代码模板，支持版本历史与回滚 |
| 工作流 | 双引擎：内置引擎 / Activiti 8.7.0（GenBpmnBuilder 翻译） |
| 交付形态 | 可执行 JAR（target/codegen-server.jar） |

## 功能模块

| 模块 | 说明 |
|------|------|
| 代码生成 | 表配置、列级控件/校验/字典、批量接管、路径回落、命名 |
| 差异预览 | 生成物与磁盘现状 diff 计算，确认后才执行落盘 |
| 自检 | 内存编译检查、结构与权限一致性问题清单 |
| 生成台账 | 批次历史、产出文件四态（新建/合并/跳过/失败）、备份覆盖与撤销回滚 |
| 代码模板 | 12 个模板的在线编辑、版本快照与回滚 |
| 系统管理 | 用户、角色、菜单、字典 |
| 权限安全 | 菜单下发、接口权限后端强制、登录防护 |
| 工作流 | 双引擎待办中心，BPMN 自动翻译 |
| 支付能力 | pay_enabled 三态 + 微信/支付宝/mock 三渠道 + markPaid 幂等 |
| 移动端 | mobile_enabled 三态 + uni-app 单源工程骨架 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 5.7+

### 配置数据库

```sql
-- 创建数据库
CREATE DATABASE gen_platform DEFAULT CHARACTER SET utf8mb4;

-- 执行初始化脚本
source docs/sql/V1.0__init.sql
```

### 配置修改

复制配置示例文件并修改数据库连接：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

修改 `application-local.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gen_platform?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
```

### 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/codegen-server.jar --spring.profiles.active=local
```

服务启动后访问 http://127.0.0.1:8120

## 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 主要接口

### 认证接口

| 接口 | 说明 |
|------|------|
| `POST /auth/login` | 用户登录 |
| `GET /auth/info` | 获取当前用户信息 |
| `GET /auth/routers` | 获取动态路由（菜单） |

### 代码生成接口

| 接口 | 说明 |
|------|------|
| `POST /gen/table` | 保存表配置 |
| `POST /gen/apply` | 一键生成（自检→备份→写盘→编译→注册菜单） |
| `POST /gen/diff` | 差异预览 |
| `POST /gen/history/rollback` | 回滚某个批次 |
| `POST /gen/template` | 创建模板 |

### 字典接口

| 接口 | 说明 |
|------|------|
| `GET /dict/{code}` | 查询字典项 |
| `POST /dict/type/save` | 保存字典类型 |
| `POST /dict/data/save` | 保存字典项 |

### 工作流接口

| 接口 | 说明 |
|------|------|
| `POST /flow/submit` | 提交审批 |
| `POST /flow/approve` | 审批通过 |
| `POST /flow/reject` | 驳回 |

## 项目结构

```
src/
├── main/
│   ├── java/com/codegen/
│   │   ├── common/           # 公共组件（异常处理、日志、数据权限等）
│   │   ├── config/           # 配置类
│   │   ├── controller/       # 控制器
│   │   ├── dto/              # 数据传输对象
│   │   ├── entity/           # 实体类
│   │   ├── mapper/           # MyBatis Mapper
│   │   ├── service/          # 服务接口及实现
│   │   ├── support/          # 工具类与支持类
│   │   ├── util/             # 通用工具
│   │   └── vo/               # 视图对象
│   └── resources/
│       ├── gen-templates/    # 代码生成模板
│       ├── scaffold-file/    # 文件能力脚手架
│       ├── scaffold-mobile/  # 移动端脚手架
│       └── scaffold-pay/     # 支付能力脚手架
└── test/                     # 单元测试
    └── java/com/codegen/
```

## 许可证

MIT License