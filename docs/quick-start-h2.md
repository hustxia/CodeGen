# H2 内存库 demo 模式快速上手

> 目标：**不装 MySQL 也能把平台跑起来体验**。平台库（gen_platform）使用进程内 H2 内存数据库，
> 建表与种子脚本由 `scripts/h2/convert_sql_to_h2.py` 从 `docs/sql/*.sql` 自动转换生成。

## 一、一条命令启动

Maven 方式（仓库根目录）：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

打包方式：

```bash
mvn package -DskipTests
java -jar target/codegen-server*.jar --spring.profiles.active=h2
```

启动时自动按版本号顺序执行 `src/main/resources/db/h2/` 下的 33 个转换脚本 + 1 个演示种子
（H2 MODE=MySQL 兼容方言，内存库每次启动都是全新空库，天然幂等）。

## 二、访问地址与默认账号

| 项 | 值 |
|---|---|
| 服务端口 | http://localhost:8120 |
| 接口清单 | http://localhost:8120/v3/api-docs |
| Swagger UI | http://localhost:8120/swagger-ui.html |
| 健康检查 | http://localhost:8120/actuator/health |
| 默认账号 | `admin` |
| 默认密码 | `admin123`（BCrypt 校验，种子在 V1.0__init.sql） |

登录后可用：代码生成（表配置/批量接管/代码模板/生成台账）、系统管理、多系统台账、
流水线（手动模式）、文档中心、测试中心、验证中心、i18n 等全部菜单。
平台全局代码模板由 `GenTemplateSeeder` 启动时自动从 classpath 落库，默认系统由
`ProjectBootstrap` 自动创建，均无需手工干预。

## 三、脚本转换（可选，仅在 docs/sql 有变更时需要）

```bash
python scripts/h2/convert_sql_to_h2.py
```

幂等可重跑：每次运行先清空 `src/main/resources/db/h2/` 再按**数字版本号**重新生成
（注意排序键是 `V<major>.<minor>` 的数值，V9.15 排在 V9.2 之后，避开文件名字典序陷阱）。
转换规则写在脚本头部注释里；每份产物的头部也带转换记录。

## 四、已知限制（与 MySQL / Docker 模式的差异）

1. **重启即清空**：内存库随进程存活，重启后所有在平台里录入的数据归零（脚本会重建）。
2. **生成目标工程仍需 MySQL**：H2 只替平台自身库；生成出来的目标工程（demo-server）依然按
   MySQL 建表脚本对接目标库，流水线「建库建表」步骤仍需要本机 MySQL。
3. **表探测/字典检测功能降级**：`GenTableServiceImpl` / `DictDetectServiceImpl` /
   `RelationDetectServiceImpl` 等直接查询 MySQL `information_schema`
   （含 `TABLE_SCHEMA=DATABASE()`、`GROUP_CONCAT` 等写法），H2 的同名词典结构不同，
   「从数据库导入表」「字典自动探测」在 demo 模式下结果不完整或报错——不影响其余功能。
4. **AI 能力需自行配置**：`gen.pipeline.ai.enabled` 默认 false（前端降级为手动粘贴 DDL）；
   需要体验 AI 建表/一句话建系统时，把 OpenAI 兼容的 base-url/api-key/model 放进
   `application-local.yml`（与 MySQL 模式完全一致，local 配置可与 h2 profile 叠加生效）。
   产物国际化开关、默认语言等系统级配置同样通过页面/接口设置。
5. **转换脚本丢弃的部分迁移逻辑**（对空库 demo 无影响）：
   - `ALTER TABLE ... CONVERT TO CHARACTER SET`（字符集统一）；
   - `UPDATE ... INNER JOIN` 多表回填（存量数据迁移，空库无数据可迁）；
   - 纯 `SELECT` 诊断语句。
6. **依赖 H2 兼容行为、未实测项**：
   - `INSERT ... ON DUPLICATE KEY UPDATE`（含 `VALUES(col)` 引用新值）——H2 官方文档
     明确支持，但种子数据量大（V7.5 有 100+ 条），首次启动若报错请按报错脚本反馈；
   - 列级 `ON UPDATE CURRENT_TIMESTAMP`——H2 2.x 支持列属性 `ON UPDATE`，
     但与 MySQL 的触发时机存在语义差异（update_time 由应用层填充的表不受影响）；
   - `MEDIUMTEXT/LONGTEXT` 映射为 H2 CLOB，超长模板内容行为可能有细微差异。
7. **与 Docker 模式的差异**：Docker/常规模式用真实 MySQL，数据可持久化、information_schema
   探测完整、性能与生产一致；H2 demo 模式仅用于**无 MySQL 环境下的快速体验与功能演示**，
   不建议作为长期开发环境（表探测、写盘回填等边界功能不可用）。
8. **local 配置冲突提示**：`application.yml` 默认 `spring.profiles.include: local`。
   若你的 `application-local.yml` 覆盖了 `spring.datasource.*`，会与 h2 模式冲突，
   请临时移走该文件或用 `--spring.datasource.url=...` 显式覆盖。

## 五、文件清单

| 文件 | 说明 |
|---|---|
| `pom.xml` | 新增 `com.h2database:h2`（runtime） |
| `src/main/resources/application-h2.yml` | demo 模式数据源 + sql.init 脚本清单 |
| `scripts/h2/convert_sql_to_h2.py` | MySQL→H2 转换脚本（幂等） |
| `scripts/h2/999_demo_seed.sql` | 演示字典种子（登录种子在 V1.0，无需另造用户） |
| `src/main/resources/db/h2/*.sql` | 转换产物（勿手工编辑） |
