-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V9.2__test_center.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT

CREATE TABLE IF NOT EXISTS `test_env` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL DEFAULT '0',
  `name` varchar(64) NOT NULL,
  `base_url` varchar(300) NOT NULL,
  `headers_json` text,
  `secret_ref` varchar(200) DEFAULT NULL,
  `is_prod` tinyint NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) NOT NULL DEFAULT '',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_env_project` ON `test_env` (`project_id`);

COMMENT ON TABLE `test_env` IS '测试环境';

COMMENT ON COLUMN `test_env`.`id` IS '主键';

COMMENT ON COLUMN `test_env`.`project_id` IS '所属系统(sys_project.id)';

COMMENT ON COLUMN `test_env`.`name` IS '环境名，如 test / 预发';

COMMENT ON COLUMN `test_env`.`base_url` IS '接口根地址';

COMMENT ON COLUMN `test_env`.`headers_json` IS '公共请求头';

COMMENT ON COLUMN `test_env`.`secret_ref` IS '密钥引用(环境变量名/配置键)，不存明文';

COMMENT ON COLUMN `test_env`.`is_prod` IS '生产环境:1是(只读，禁写操作)';

COMMENT ON COLUMN `test_env`.`enabled` IS '启用:0否1是';

COMMENT ON COLUMN `test_env`.`remark` IS '备注';

COMMENT ON COLUMN `test_env`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_env`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_env`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_env`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_env`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `test_suite` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL DEFAULT '0',
  `table_id` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(8) NOT NULL DEFAULT 'api',
  `source` varchar(16) NOT NULL DEFAULT 'manual',
  `module_name` varchar(50) NOT NULL DEFAULT '',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) NOT NULL DEFAULT '',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_suite_project` ON `test_suite` (`project_id`);

CREATE INDEX IF NOT EXISTS `idx_suite_table` ON `test_suite` (`table_id`);

COMMENT ON TABLE `test_suite` IS '测试套件';

COMMENT ON COLUMN `test_suite`.`id` IS '主键';

COMMENT ON COLUMN `test_suite`.`project_id` IS '所属系统';

COMMENT ON COLUMN `test_suite`.`table_id` IS '来源业务表(gen_table.id)，手工套件为空';

COMMENT ON COLUMN `test_suite`.`name` IS '套件名，默认取表注释';

COMMENT ON COLUMN `test_suite`.`type` IS '类型:api/ui';

COMMENT ON COLUMN `test_suite`.`source` IS '来源:generated/manual';

COMMENT ON COLUMN `test_suite`.`module_name` IS '模块名';

COMMENT ON COLUMN `test_suite`.`enabled` IS '启用:0否1是';

COMMENT ON COLUMN `test_suite`.`remark` IS '备注';

COMMENT ON COLUMN `test_suite`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_suite`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_suite`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_suite`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_suite`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `test_case` (
  `id` bigint NOT NULL,
  `suite_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `source` varchar(16) NOT NULL DEFAULT 'manual',
  `method` varchar(10) NOT NULL DEFAULT 'GET',
  `path` varchar(300) NOT NULL,
  `headers_json` text,
  `body_json` text,
  `extract_json` text,
  `asserts_json` text NOT NULL,
  `cleanup_sql` varchar(1000) DEFAULT NULL,
  `order_no` int NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `fingerprint` varchar(64) DEFAULT NULL,
  `last_result` varchar(16) DEFAULT NULL,
  `last_run_time` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_case_suite` ON `test_case` (`suite_id`);

COMMENT ON TABLE `test_case` IS '测试用例';

COMMENT ON COLUMN `test_case`.`id` IS '主键';

COMMENT ON COLUMN `test_case`.`suite_id` IS '所属套件';

COMMENT ON COLUMN `test_case`.`name` IS '用例名';

COMMENT ON COLUMN `test_case`.`source` IS '来源:generated/manual';

COMMENT ON COLUMN `test_case`.`method` IS 'HTTP 方法';

COMMENT ON COLUMN `test_case`.`path` IS '接口路径(支持 ${var} 占位)';

COMMENT ON COLUMN `test_case`.`headers_json` IS '请求头';

COMMENT ON COLUMN `test_case`.`body_json` IS '请求体';

COMMENT ON COLUMN `test_case`.`extract_json` IS '变量提取:{"name":"$.data.id"}';

COMMENT ON COLUMN `test_case`.`asserts_json` IS '断言数组';

COMMENT ON COLUMN `test_case`.`cleanup_sql` IS '执行后清理语句(生产环境禁执行)';

COMMENT ON COLUMN `test_case`.`order_no` IS '执行顺序';

COMMENT ON COLUMN `test_case`.`enabled` IS '启用:0否1是';

COMMENT ON COLUMN `test_case`.`fingerprint` IS '生成内容哈希，判定是否被手工修改';

COMMENT ON COLUMN `test_case`.`last_result` IS '最近结果:pass/fail/skip';

COMMENT ON COLUMN `test_case`.`last_run_time` IS '最近执行时间';

COMMENT ON COLUMN `test_case`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_case`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_case`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_case`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_case`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `test_dataset` (
  `id` bigint NOT NULL,
  `case_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `rows_json` longtext NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_dataset_case` ON `test_dataset` (`case_id`);

COMMENT ON TABLE `test_dataset` IS '用例数据集';

COMMENT ON COLUMN `test_dataset`.`id` IS '主键';

COMMENT ON COLUMN `test_dataset`.`case_id` IS '所属用例';

COMMENT ON COLUMN `test_dataset`.`name` IS '数据集名';

COMMENT ON COLUMN `test_dataset`.`rows_json` IS '行数组，每行一组变量';

COMMENT ON COLUMN `test_dataset`.`enabled` IS '启用:0否1是';

COMMENT ON COLUMN `test_dataset`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_dataset`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_dataset`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_dataset`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_dataset`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `test_run` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL DEFAULT '0',
  `env_id` bigint NOT NULL,
  `trigger_type` varchar(16) NOT NULL DEFAULT 'manual',
  `suite_ids` varchar(500) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'running',
  `total` int NOT NULL DEFAULT '0',
  `passed` int NOT NULL DEFAULT '0',
  `failed` int NOT NULL DEFAULT '0',
  `skipped` int NOT NULL DEFAULT '0',
  `duration_ms` bigint DEFAULT NULL,
  `started_at` datetime NOT NULL,
  `finished_at` datetime DEFAULT NULL,
  `error_msg` varchar(1000) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_run_project` ON `test_run` (`project_id`);

CREATE INDEX IF NOT EXISTS `idx_run_started` ON `test_run` (`started_at`);

COMMENT ON TABLE `test_run` IS '测试执行批次';

COMMENT ON COLUMN `test_run`.`id` IS '主键';

COMMENT ON COLUMN `test_run`.`project_id` IS '所属系统';

COMMENT ON COLUMN `test_run`.`env_id` IS '执行环境';

COMMENT ON COLUMN `test_run`.`trigger_type` IS '触发:manual/schedule/ci';

COMMENT ON COLUMN `test_run`.`suite_ids` IS '本轮套件 ID 列表';

COMMENT ON COLUMN `test_run`.`status` IS '状态:running/success/failed/aborted';

COMMENT ON COLUMN `test_run`.`total` IS '用例总数';

COMMENT ON COLUMN `test_run`.`passed` IS '通过数';

COMMENT ON COLUMN `test_run`.`failed` IS '失败数';

COMMENT ON COLUMN `test_run`.`skipped` IS '跳过数';

COMMENT ON COLUMN `test_run`.`duration_ms` IS '总耗时';

COMMENT ON COLUMN `test_run`.`started_at` IS '开始时间';

COMMENT ON COLUMN `test_run`.`finished_at` IS '结束时间';

COMMENT ON COLUMN `test_run`.`error_msg` IS '编排级错误';

COMMENT ON COLUMN `test_run`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_run`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_run`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_run`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_run`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `test_result` (
  `id` bigint NOT NULL,
  `run_id` bigint NOT NULL,
  `case_id` bigint NOT NULL,
  `case_name` varchar(128) NOT NULL,
  `status` varchar(16) NOT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `request_snapshot` mediumtext,
  `response_snapshot` mediumtext,
  `screenshot_path` varchar(300) DEFAULT NULL,
  `failed_assert_json` text,
  `error_msg` varchar(1000) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_result_run` ON `test_result` (`run_id`);

CREATE INDEX IF NOT EXISTS `idx_result_case` ON `test_result` (`case_id`);

COMMENT ON TABLE `test_result` IS '测试用例结果';

COMMENT ON COLUMN `test_result`.`id` IS '主键';

COMMENT ON COLUMN `test_result`.`run_id` IS '所属批次';

COMMENT ON COLUMN `test_result`.`case_id` IS '用例 ID';

COMMENT ON COLUMN `test_result`.`case_name` IS '用例名快照(防改名后报告失真)';

COMMENT ON COLUMN `test_result`.`status` IS '结果:pass/fail/skip';

COMMENT ON COLUMN `test_result`.`duration_ms` IS '耗时';

COMMENT ON COLUMN `test_result`.`request_snapshot` IS '请求快照';

COMMENT ON COLUMN `test_result`.`response_snapshot` IS '响应快照(超 64KB 截断并标记)';

COMMENT ON COLUMN `test_result`.`screenshot_path` IS '截图相对路径';

COMMENT ON COLUMN `test_result`.`failed_assert_json` IS '失败断言明细';

COMMENT ON COLUMN `test_result`.`error_msg` IS '异常摘要';

COMMENT ON COLUMN `test_result`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_result`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_result`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_result`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_result`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `test_schedule` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL DEFAULT '0',
  `name` varchar(100) NOT NULL,
  `env_id` bigint NOT NULL,
  `suite_ids` varchar(500) NOT NULL,
  `cron` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `last_run_id` bigint DEFAULT NULL,
  `notify_type` varchar(32) DEFAULT NULL,
  `notify_target` varchar(300) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_sched_project` ON `test_schedule` (`project_id`);

COMMENT ON TABLE `test_schedule` IS '测试定时任务';

COMMENT ON COLUMN `test_schedule`.`id` IS '主键';

COMMENT ON COLUMN `test_schedule`.`project_id` IS '所属系统';

COMMENT ON COLUMN `test_schedule`.`name` IS '任务名';

COMMENT ON COLUMN `test_schedule`.`env_id` IS '执行环境';

COMMENT ON COLUMN `test_schedule`.`suite_ids` IS '套件 ID 列表';

COMMENT ON COLUMN `test_schedule`.`cron` IS 'cron 表达式(6 段)';

COMMENT ON COLUMN `test_schedule`.`enabled` IS '启用:0否1是';

COMMENT ON COLUMN `test_schedule`.`last_run_id` IS '最近批次';

COMMENT ON COLUMN `test_schedule`.`notify_type` IS '通知方式:none/webhook';

COMMENT ON COLUMN `test_schedule`.`notify_target` IS '通知目标';

COMMENT ON COLUMN `test_schedule`.`deleted` IS '逻辑删除:0否1是';

COMMENT ON COLUMN `test_schedule`.`create_by` IS '创建人';

COMMENT ON COLUMN `test_schedule`.`create_time` IS '创建时间';

COMMENT ON COLUMN `test_schedule`.`update_by` IS '更新人';

COMMENT ON COLUMN `test_schedule`.`update_time` IS '更新时间';

DELETE FROM sys_menu WHERE path = '/test' OR id BETWEEN 5000 AND 5040;

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES
  (5000, 0,    '测试中心', '/test',     'test/index',   'test:list',            2, 5, 'el-icon-odometer', 1, 0),
  (5010, 5000, '环境配置', NULL,        NULL,           'test:env:edit',        3, 1, NULL,               1, 0),
  (5020, 5000, '用例编辑', NULL,        NULL,           'test:case:edit',       3, 2, NULL,               1, 0),
  (5030, 5000, '生成用例', NULL,        NULL,           'test:case:generate',   3, 3, NULL,               1, 0),
  (5040, 5000, '执行用例', NULL,        NULL,           'test:run',             3, 4, NULL,               1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 5000), (1, 5010), (1, 5020), (1, 5030), (1, 5040);
