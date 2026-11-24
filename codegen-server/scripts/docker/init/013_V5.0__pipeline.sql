-- ---------- V5.0 流水线建表（V7.4 补交付） ----------
-- 背景：V5.0「一句话建系统」的两张表此前只存在于设计文档
--       docs/design/V5.0一句话建系统-设计.md，docs/sql/ 从 V4.5 直接跳到 V5.5，
--       空库顺序执行全部脚本后流水线功能直接 Table doesn't exist（台账 F-08）。
-- 字段口径：以 src/test/resources/schema-test.sql 的 gen_pipeline 为准（含 seed_mode），
--           它是实际运行库结构的快照；设计文档那份少一列，不再复制。
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，存量库（已手工建过表）重复执行不受影响。
-- 顺序：V5.0 排在 V4.5 之后、V5.5 之前，符合 docs/sql 文件名的字典序。

CREATE TABLE IF NOT EXISTS gen_pipeline (
  id             bigint       NOT NULL COMMENT '主键ID',
  pipeline_no    varchar(32)  NOT NULL COMMENT '流水线号 PPL+时间戳',
  project_id     bigint       NULL COMMENT '关联 sys_project',
  system_name    varchar(64)  NOT NULL COMMENT '录入的系统名称',
  system_desc    varchar(512) NULL COMMENT '一句话业务描述',
  db_name        varchar(64)  NOT NULL COMMENT '目标数据库名',
  backend_port   int          NULL COMMENT '后端端口',
  frontend_port  int          NULL COMMENT '前端端口',
  mode           varchar(16)  NOT NULL DEFAULT 'ai' COMMENT 'ai/manual',
  seed_mode      varchar(10)  NULL COMMENT '数据初始化：rule/ai/off，NULL=off',
  status         varchar(16)  NOT NULL DEFAULT 'draft',
  current_step   varchar(32)  NULL,
  fail_reason    varchar(1024) NULL,
  frontend_url   varchar(256) NULL,
  backend_pid    bigint       NULL,
  frontend_pid   bigint       NULL,
  deleted        tinyint      NOT NULL DEFAULT 0,
  create_by      bigint       NULL,
  create_time    datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      bigint       NULL,
  update_time    datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_no (pipeline_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V5.0 一句话建系统流水线';

CREATE TABLE IF NOT EXISTS gen_pipeline_step (
  id             bigint       NOT NULL COMMENT '主键ID',
  pipeline_id    bigint       NOT NULL COMMENT 'gen_pipeline.id',
  step_key       varchar(32)  NOT NULL COMMENT '步骤key：model/confirm/db/scaffold/gen/package/deploy/health',
  step_name      varchar(64)  NOT NULL COMMENT '步骤中文名',
  step_order     int          NOT NULL COMMENT '执行顺序',
  side           varchar(16)  NOT NULL DEFAULT 'pipeline' COMMENT 'pipeline/backend/frontend',
  status         varchar(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed/skipped',
  message        varchar(1024) NULL COMMENT '结果/错误摘要',
  log_excerpt    text         NULL COMMENT '日志摘要',
  started_at     datetime     NULL,
  finished_at    datetime     NULL,
  deleted        tinyint      NOT NULL DEFAULT 0,
  create_by      bigint       NULL,
  create_time    datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      bigint       NULL,
  update_time    datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pipe_step (pipeline_id, step_key),
  KEY idx_pipeline (pipeline_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V5.0 流水线步骤明细';
