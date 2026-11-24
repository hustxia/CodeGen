-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V9.2__verification_center.sql
-- 转换记录:

CREATE TABLE IF NOT EXISTS `tc_run` (
  id bigint NOT NULL,
  run_no varchar(64) NOT NULL,
  trigger_type varchar(16) NOT NULL,
  suite_ids varchar(512) NOT NULL,
  status varchar(16) NOT NULL,
  total int NOT NULL DEFAULT 0,
  passed int NOT NULL DEFAULT 0,
  failed int NOT NULL DEFAULT 0,
  skipped int NOT NULL DEFAULT 0,
  duration_ms bigint DEFAULT NULL,
  git_commit varchar(64) DEFAULT NULL,
  summary_json longtext,
  work_dir varchar(512) DEFAULT NULL,
  error_msg varchar(1024) DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT `uk_tc_run_no` UNIQUE (run_no)
);

CREATE INDEX IF NOT EXISTS `idx_tc_run_create_time` ON `tc_run` (create_time);

CREATE INDEX IF NOT EXISTS `idx_tc_run_status` ON `tc_run` (status);

COMMENT ON TABLE `tc_run` IS 'V9.2 验证中心-运行记录';

COMMENT ON COLUMN `tc_run`.`id` IS '主键';

COMMENT ON COLUMN `tc_run`.`run_no` IS 'TC+日期+序号，展示用';

COMMENT ON COLUMN `tc_run`.`trigger_type` IS 'MANUAL / CI / SCHEDULE / RERUN';

COMMENT ON COLUMN `tc_run`.`suite_ids` IS '逗号分隔，按注册表顺序';

COMMENT ON COLUMN `tc_run`.`status` IS 'QUEUED/RUNNING/PASSED/FAILED/CANCELLED/TIMEOUT';

COMMENT ON COLUMN `tc_run`.`git_commit` IS '关联版本，便于追溯';

COMMENT ON COLUMN `tc_run`.`summary_json` IS 'codegen.testcenter/v1 摘要契约';

COMMENT ON COLUMN `tc_run`.`work_dir` IS '本次运行的隔离目录';

CREATE TABLE IF NOT EXISTS `tc_case_result` (
  id bigint NOT NULL,
  run_id bigint NOT NULL,
  suite_id varchar(32) NOT NULL,
  status varchar(16) NOT NULL,
  duration_ms bigint DEFAULT NULL,
  exit_code int DEFAULT NULL,
  metrics_json text,
  log_path varchar(512) DEFAULT NULL,
  error_excerpt text,
  started_at datetime DEFAULT NULL,
  finished_at datetime DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT `uk_tc_run_suite` UNIQUE (run_id, suite_id)
);

CREATE INDEX IF NOT EXISTS `idx_tc_case_run` ON `tc_case_result` (run_id);

COMMENT ON TABLE `tc_case_result` IS 'V9.2 验证中心-套件结果';

COMMENT ON COLUMN `tc_case_result`.`id` IS '主键';

COMMENT ON COLUMN `tc_case_result`.`status` IS 'PASSED/FAILED/SKIPPED/TIMEOUT/CANCELLED';

COMMENT ON COLUMN `tc_case_result`.`metrics_json` IS '结构化基线值，如 312/312、9/9、206/206';

COMMENT ON COLUMN `tc_case_result`.`log_path` IS '相对 runs 根目录，读取时做路径白名单';

COMMENT ON COLUMN `tc_case_result`.`error_excerpt` IS '失败堆栈尾部';

CREATE TABLE IF NOT EXISTS `tc_artifact` (
  id bigint NOT NULL,
  run_id bigint NOT NULL,
  suite_id varchar(32) DEFAULT NULL,
  artifact_type varchar(24) NOT NULL,
  file_name varchar(255) NOT NULL,
  file_path varchar(1024) NOT NULL,
  file_size bigint DEFAULT NULL,
  mime_type varchar(64) DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS `idx_tc_artifact_run` ON `tc_artifact` (run_id);

COMMENT ON TABLE `tc_artifact` IS 'V9.2 验证中心-产物索引';

COMMENT ON COLUMN `tc_artifact`.`id` IS '主键';

COMMENT ON COLUMN `tc_artifact`.`artifact_type` IS 'LOG / REPORT / SCREENSHOT / SUREFIRE / DIFF';

DELETE FROM sys_menu WHERE path = '/tc/center';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (3040, 0, '验证中心', '/tc/center', 'verification/index', 'tc:view', 2, 6, 'el-icon-s-data', 1, 0);

DELETE FROM sys_menu WHERE id IN (3041, 3042, 3043);

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted) VALUES
  (3041, 3040, '运行套件', '', '', 'tc:run', 3, 1, '', 1, 0),
  (3042, 3040, '取消运行', '', '', 'tc:cancel', 3, 2, '', 1, 0),
  (3043, 3040, '导出报告', '', '', 'tc:export', 3, 3, '', 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 3040), (1, 3041), (1, 3042), (1, 3043);
