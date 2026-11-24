# Docker 一键快速开始

本文描述如何用 `docker compose up` 一条命令把 codegen-server（后端 + MySQL 8）完整跑起来。

## 前置条件

- Docker 20.10+ 与 Docker Compose v2（`docker compose version` 能跑通即可）
- 构建期需联网拉取 Maven 依赖（国内网络慢可在 `Dockerfile` 中启用阿里云镜像，见其注释）
- 首次构建约需几分钟（取决于网络与是否命中依赖缓存）

## 一条命令启动

在仓库根目录执行：

```bash
docker compose up -d --build
```

启动过程说明：

1. `mysql8` 容器先启动，首次初始化时自动按 `001` ~ `033` 的顺序执行
   `scripts/docker/init/` 下的建表与种子脚本（由 `scripts/docker/build_init_sql.py`
   按**数字版本序**生成，避免了字典序 `V9.15 < V9.2` 的坑）。
2. `codegen-server` 通过 `depends_on: condition: service_healthy` 等 MySQL 健康检查
   通过后才启动。
3. 平台自身健康检查端点为 `/actuator/health`（已在鉴权白名单内，无需 token）。

查看启动日志：

```bash
docker compose logs -f codegen-server
```

## 访问地址

| 内容 | 地址 |
| --- | --- |
| 后端服务 | http://localhost:8120 |
| 健康检查 | http://localhost:8120/actuator/health |
| 接口文档（Swagger） | http://localhost:8120/swagger-ui.html |

## 默认账号

| 字段 | 值 |
| --- | --- |
| 用户名 | `admin` |
| 密码 | `admin123` |

账号由 `docs/sql/V1.0__init.sql` 种子数据写入（BCrypt 密文），仅用于本地体验，
生产环境请第一时间改密。

## 常用运维命令

```bash
# 查看整体状态（health 字段）
docker compose ps

# 跟踪日志
docker compose logs -f codegen-server

# 重启平台（不重建镜像）
docker compose restart codegen-server

# 重新编译部署最新代码
docker compose up -d --build

# 停止（保留数据卷，数据库不丢）
docker compose down

# 彻底重置（删除数据库数据卷，下次启动重新导库）
docker compose down -v
```

数据库宿主机直连：`mysql -h127.0.0.1 -uroot -proot gen_platform`
（3306 被占用时先改 `docker-compose.yml` 的端口映射，如 `13306:3306`）。

## 环境变量

支持在 `docker compose up` 前用环境变量或 `.env` 文件覆盖：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_USERNAME` | `root` | 平台连接 MySQL 的用户名 |
| `DB_PASSWORD` | `root` | 平台连接 MySQL 的密码（root 口令同值） |
| `JWT_SECRET` | 仓库内置默认值 | **生产必须覆盖**，否则 token 可被伪造（启动会打 WARN） |
| `JWT_EXPIRE_HOURS` | `24` | token 有效期 |

其余可覆盖项见 `src/main/resources/application.yml`（`SPRINGDOC_*`、`DOC_*`、
`GEN_OUTPUT_*`、`MONITOR_SAMPLE_*` 等），全部通过 Spring Boot 标准环境变量绑定生效。

## AI 配置说明

`gen.pipeline.ai.enabled` 默认 `false`：不配置 AI 时流水线自动降级为
「手动粘贴 DDL」，其余功能不受影响。需要启用时，通过环境变量
`GEN_PIPELINE_AI_ENABLED=true` 等标准绑定方式注入 `base-url / api-key / model`，
不要把密钥写进镜像。

## 与 H2 模式的差异（占位）

> TODO：本仓库暂无 H2 内嵌模式分支，此处预留说明位——待 H2 模式落地后补充
> 两者的建库方式、数据持久化位置与兼容性差异。

## 已知注意事项

- `application-local.yml`（本机 AI 配置）已通过 `.dockerignore` 排除，不会打进镜像；
  该文件缺失时 `local` profile 静默跳过，不影响启动。
- 代码生成产物写盘路径默认相对工作目录，容器内挂载在 `codegen_output` 卷；
  流水线打包/部署功能依赖容器内 java/maven/npm，目前仅保证平台核心功能可用。
- 首次启动 MySQL 初始化约需 30~60 秒（33 个脚本顺序导入），期间平台会等待。
