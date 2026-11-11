# 数据库初始化脚本

本目录存放 CodeGen 平台（码匠代码生成器）的 MySQL 数据库导出文件，用于本地一键还原开发环境。

## 文件说明

| 文件 | 内容 | 大小 |
|---|---|---|
| `codegen.sql` | `gen_platform` 库全量结构 + 数据（24 张表） | 约 8.5 MB |

**导出信息**

- 数据库：`gen_platform`
- 导出工具：`mysqldump 10.13`（MySQL Server 8.0.28）
- 导出时间：2026-11-11 11:35
- 字符集：`utf8mb4`

## 表结构分组

| 分组 | 表 |
|---|---|
| 代码生成核心 | `gen_table`、`gen_table_column`、`gen_template`、`gen_template_history`、`gen_history`、`gen_output`、`gen_pipeline`、`gen_pipeline_step` |
| 平台与系统 | `sys_user`、`sys_role`、`sys_role_menu`、`sys_menu`、`sys_dict_type`、`sys_dict_data`、`sys_oper_log`、`sys_doc`、`sys_project`、`sys_flow_def`、`sys_flow_task` |
| 历史备份快照 | `gen_table_bak_20261025`、`gen_table_column_bak_20261025`、`gen_template_bak_20261025`、`gen_output_bak_20261025` |

> `*_bak_20261025` 是 2026-10-25 的库内备份快照，仅供追溯，恢复后可按需删除。

## 恢复步骤

```bash
# 1. 建库（若不存在）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS gen_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2. 导入（8.5 MB，通常几秒内完成）
mysql -uroot -p gen_platform < database/codegen.sql

# 3. 验证
mysql -uroot -p -e "USE gen_platform; SHOW TABLES;" | head -30
```

导入后按 `codegen-server` 的 `application.yml` 配置数据源（`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` 环境变量）即可启动后端。

## ⚠️ 安全说明（重要）

**本 dump 已脱敏**：`sys_user` 表中 `admin` 账号的 BCrypt 密码哈希已替换为占位串
`$2a$10$SANITIZED_SANITIZED_SANITIZED_SANITIZED_SANITIZED_AB1`，该串不是合法 BCrypt 哈希，**无法用于登录**。

首次登录前必须先重置 admin 密码，二选一：

1. **用应用自带的密码加密器生成新哈希**（推荐）：写一段小测试调用 `BCryptPasswordEncoder().encode("你的新密码")`，把结果写回库：

   ```sql
   UPDATE `sys_user`
      SET `password` = '$2a$10$这里换成新生成的哈希'
    WHERE `username` = 'admin';
   ```

2. **临时开放注册**：通过系统的注册/用户管理接口新建一个账号并授予管理员角色，登录后修改 admin 密码。

除 admin 账号外，dump 内不含任何生产密钥、访问令牌或个人真实信息（邮箱域名为 `demo.local`）。

## 重新导出（保持最新）

数据结构或初始化数据变更后，用同样的命令更新本文件并提交：

```bash
mysqldump -uroot -p --single-transaction --routines --triggers \
  gen_platform > database/codegen.sql
```

导出后务必检查是否带入新的密码哈希等敏感内容。
