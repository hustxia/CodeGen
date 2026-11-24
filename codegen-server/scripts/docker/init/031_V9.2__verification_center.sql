-- ============================================================
-- V9.2 验证中心（tc_* 三表）+ 菜单与按钮权限
--
-- 与 V9.2__test_center.sql 的关系：同一个 V9.2 的两个子模块。
--   /test/*      = 用例中心（test_* 七表，接口/UI 自动化用例资产）
--   /tc/center   = 验证中心（tc_* 三表，把平台自身验证资产收拢成可追溯运行）
-- 文件名刻意分开，避免覆盖用例中心的迁移脚本；两个脚本都幂等，可任意顺序执行。
--
-- 审计字段沿用平台 BaseEntity 口径（create_by/update_by 为 bigint）。
-- ============================================================

CREATE TABLE IF NOT EXISTS tc_run (
  id bigint NOT NULL COMMENT '主键',
  run_no varchar(64) NOT NULL COMMENT 'TC+日期+序号，展示用',
  trigger_type varchar(16) NOT NULL COMMENT 'MANUAL / CI / SCHEDULE / RERUN',
  suite_ids varchar(512) NOT NULL COMMENT '逗号分隔，按注册表顺序',
  status varchar(16) NOT NULL COMMENT 'QUEUED/RUNNING/PASSED/FAILED/CANCELLED/TIMEOUT',
  total int NOT NULL DEFAULT 0,
  passed int NOT NULL DEFAULT 0,
  failed int NOT NULL DEFAULT 0,
  skipped int NOT NULL DEFAULT 0,
  duration_ms bigint DEFAULT NULL,
  git_commit varchar(64) DEFAULT NULL COMMENT '关联版本，便于追溯',
  summary_json longtext COMMENT 'codegen.testcenter/v1 摘要契约',
  work_dir varchar(512) DEFAULT NULL COMMENT '本次运行的隔离目录',
  error_msg varchar(1024) DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tc_run_no (run_no),
  KEY idx_tc_run_create_time (create_time),
  KEY idx_tc_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V9.2 验证中心-运行记录';

CREATE TABLE IF NOT EXISTS tc_case_result (
  id bigint NOT NULL COMMENT '主键',
  run_id bigint NOT NULL,
  suite_id varchar(32) NOT NULL,
  status varchar(16) NOT NULL COMMENT 'PASSED/FAILED/SKIPPED/TIMEOUT/CANCELLED',
  duration_ms bigint DEFAULT NULL,
  exit_code int DEFAULT NULL,
  metrics_json text COMMENT '结构化基线值，如 312/312、9/9、206/206',
  log_path varchar(512) DEFAULT NULL COMMENT '相对 runs 根目录，读取时做路径白名单',
  error_excerpt text COMMENT '失败堆栈尾部',
  started_at datetime DEFAULT NULL,
  finished_at datetime DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tc_run_suite (run_id, suite_id),
  KEY idx_tc_case_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V9.2 验证中心-套件结果';

CREATE TABLE IF NOT EXISTS tc_artifact (
  id bigint NOT NULL COMMENT '主键',
  run_id bigint NOT NULL,
  suite_id varchar(32) DEFAULT NULL,
  artifact_type varchar(24) NOT NULL COMMENT 'LOG / REPORT / SCREENSHOT / SUREFIRE / DIFF',
  file_name varchar(255) NOT NULL,
  file_path varchar(1024) NOT NULL,
  file_size bigint DEFAULT NULL,
  mime_type varchar(64) DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tc_artifact_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V9.2 验证中心-产物索引';

-- 菜单 id 段 3040（用例中心占 5000–5040，系统监控占 3030），互不冲突。
-- 前端 require.context 动态加载 src/views/verification/index.vue，不改 router/index.js。
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
