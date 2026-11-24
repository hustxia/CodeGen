-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V5.0__pipeline.sql
-- 转换记录:

CREATE TABLE IF NOT EXISTS `gen_pipeline` (
  id bigint NOT NULL,
  pipeline_no varchar(32) NOT NULL,
  project_id bigint NULL,
  system_name varchar(64) NOT NULL,
  system_desc varchar(512) NULL,
  db_name varchar(64) NOT NULL,
  backend_port int NULL,
  frontend_port int NULL,
  mode varchar(16) NOT NULL DEFAULT 'ai',
  seed_mode varchar(10) NULL,
  status varchar(16) NOT NULL DEFAULT 'draft',
  current_step varchar(32) NULL,
  fail_reason varchar(1024) NULL,
  frontend_url varchar(256) NULL,
  backend_pid bigint NULL,
  frontend_pid bigint NULL,
  deleted tinyint NOT NULL DEFAULT 0,
  create_by bigint NULL,
  create_time datetime NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint NULL,
  update_time datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT `uk_no` UNIQUE (pipeline_no)
);

CREATE INDEX IF NOT EXISTS `idx_status` ON `gen_pipeline` (status);

COMMENT ON TABLE `gen_pipeline` IS 'V5.0 一句话建系统流水线';

COMMENT ON COLUMN `gen_pipeline`.`id` IS '主键ID';

COMMENT ON COLUMN `gen_pipeline`.`pipeline_no` IS '流水线号 PPL+时间戳';

COMMENT ON COLUMN `gen_pipeline`.`project_id` IS '关联 sys_project';

COMMENT ON COLUMN `gen_pipeline`.`system_name` IS '录入的系统名称';

COMMENT ON COLUMN `gen_pipeline`.`system_desc` IS '一句话业务描述';

COMMENT ON COLUMN `gen_pipeline`.`db_name` IS '目标数据库名';

COMMENT ON COLUMN `gen_pipeline`.`backend_port` IS '后端端口';

COMMENT ON COLUMN `gen_pipeline`.`frontend_port` IS '前端端口';

COMMENT ON COLUMN `gen_pipeline`.`mode` IS 'ai/manual';

COMMENT ON COLUMN `gen_pipeline`.`seed_mode` IS '数据初始化：rule/ai/off，NULL=off';

CREATE TABLE IF NOT EXISTS `gen_pipeline_step` (
  id bigint NOT NULL,
  pipeline_id bigint NOT NULL,
  step_key varchar(32) NOT NULL,
  step_name varchar(64) NOT NULL,
  step_order int NOT NULL,
  side varchar(16) NOT NULL DEFAULT 'pipeline',
  status varchar(16) NOT NULL DEFAULT 'pending',
  message varchar(1024) NULL,
  log_excerpt text NULL,
  started_at datetime NULL,
  finished_at datetime NULL,
  deleted tinyint NOT NULL DEFAULT 0,
  create_by bigint NULL,
  create_time datetime NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint NULL,
  update_time datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT `uk_pipe_step` UNIQUE (pipeline_id, step_key)
);

CREATE INDEX IF NOT EXISTS `idx_pipeline` ON `gen_pipeline_step` (pipeline_id);

COMMENT ON TABLE `gen_pipeline_step` IS 'V5.0 流水线步骤明细';

COMMENT ON COLUMN `gen_pipeline_step`.`id` IS '主键ID';

COMMENT ON COLUMN `gen_pipeline_step`.`pipeline_id` IS 'gen_pipeline.id';

COMMENT ON COLUMN `gen_pipeline_step`.`step_key` IS '步骤key：model/confirm/db/scaffold/gen/package/deploy/health';

COMMENT ON COLUMN `gen_pipeline_step`.`step_name` IS '步骤中文名';

COMMENT ON COLUMN `gen_pipeline_step`.`step_order` IS '执行顺序';

COMMENT ON COLUMN `gen_pipeline_step`.`side` IS 'pipeline/backend/frontend';

COMMENT ON COLUMN `gen_pipeline_step`.`status` IS 'pending/running/success/failed/skipped';

COMMENT ON COLUMN `gen_pipeline_step`.`message` IS '结果/错误摘要';

COMMENT ON COLUMN `gen_pipeline_step`.`log_excerpt` IS '日志摘要';
