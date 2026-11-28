-- H2 MODE=MySQL 版本（demo 模式用）
-- 原始脚本: docs/sql/V9.24__quality_gate.sql

CREATE TABLE IF NOT EXISTS `gen_quality_report` (
  `id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `table_ids` varchar(1000) DEFAULT '',
  `engine` varchar(20) NOT NULL DEFAULT 'DET',
  `llm_state` varchar(20) NOT NULL DEFAULT 'DISABLED',
  `status` varchar(20) NOT NULL DEFAULT 'PASS',
  `blocked` tinyint NOT NULL DEFAULT 0,
  `blocker_count` int NOT NULL DEFAULT 0,
  `warn_count` int NOT NULL DEFAULT 0,
  `issue_count` int NOT NULL DEFAULT 0,
  `file_count` int NOT NULL DEFAULT 0,
  `rule_count` int NOT NULL DEFAULT 0,
  `degraded` tinyint NOT NULL DEFAULT 0,
  `degraded_reason` varchar(500) DEFAULT '',
  `message` varchar(500) DEFAULT '',
  `elapsed_ms` bigint NOT NULL DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_quality_proj` ON `gen_quality_report` (`project_id`, `create_time`);

CREATE TABLE IF NOT EXISTS `gen_quality_issue` (
  `id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `rule_id` varchar(20) NOT NULL,
  `category` varchar(20) DEFAULT '',
  `engine` varchar(10) NOT NULL DEFAULT 'DET',
  `severity` varchar(20) NOT NULL,
  `file_path` varchar(500) DEFAULT '',
  `line_no` bigint NOT NULL DEFAULT 0,
  `message` varchar(1000) DEFAULT '',
  `bad_snippet` text,
  `fix_snippet` text,
  `source_ref` varchar(300) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_quality_issue` ON `gen_quality_issue` (`report_id`, `severity`);
