-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V9.6__ai_build_job.sql
-- 转换记录:

CREATE TABLE IF NOT EXISTS `gen_ai_build_job` (
  `id` bigint NOT NULL,
  `job_no` varchar(40) DEFAULT NULL,
  `job_type` varchar(16) NOT NULL DEFAULT 'plan',
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `system_name` varchar(200) DEFAULT NULL,
  `system_desc` varchar(1000) DEFAULT NULL,
  `scope` varchar(16) DEFAULT 'new',
  `project_id` bigint DEFAULT NULL,
  `scale` varchar(16) DEFAULT NULL,
  `deploy` varchar(16) DEFAULT NULL,
  `total_batch` int NOT NULL DEFAULT 0,
  `done_batch` int NOT NULL DEFAULT 0,
  `failed_batch` int NOT NULL DEFAULT 0,
  `total_table` int NOT NULL DEFAULT 0,
  `payload` mediumtext,
  `result` mediumtext,
  `error` varchar(1000) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_ai_job_no` UNIQUE (`job_no`)
);

CREATE INDEX IF NOT EXISTS `idx_ai_job_status` ON `gen_ai_build_job` (`status`);

COMMENT ON TABLE `gen_ai_build_job` IS 'AI 建表分批任务';

COMMENT ON COLUMN `gen_ai_build_job`.`id` IS '主键（雪花）';

COMMENT ON COLUMN `gen_ai_build_job`.`job_no` IS '任务号（可读，AIJ+时间戳）';

COMMENT ON COLUMN `gen_ai_build_job`.`job_type` IS 'plan=建模 confirm=落库建表';

COMMENT ON COLUMN `gen_ai_build_job`.`status` IS 'pending/running/success/partial/failed/canceled';

COMMENT ON COLUMN `gen_ai_build_job`.`system_name` IS '系统名称';

COMMENT ON COLUMN `gen_ai_build_job`.`system_desc` IS '系统描述（原文）';

COMMENT ON COLUMN `gen_ai_build_job`.`scope` IS 'new/supplement';

COMMENT ON COLUMN `gen_ai_build_job`.`project_id` IS '补表目标系统';

COMMENT ON COLUMN `gen_ai_build_job`.`scale` IS 'small/large/huge';

COMMENT ON COLUMN `gen_ai_build_job`.`deploy` IS 'mono/micro';

COMMENT ON COLUMN `gen_ai_build_job`.`total_batch` IS '总批次数（拆模块阶段会从 1 增长）';

COMMENT ON COLUMN `gen_ai_build_job`.`done_batch` IS '成功批次数';

COMMENT ON COLUMN `gen_ai_build_job`.`failed_batch` IS '失败批次数';

COMMENT ON COLUMN `gen_ai_build_job`.`total_table` IS '已产出表数（滚动累计）';

COMMENT ON COLUMN `gen_ai_build_job`.`payload` IS '任务上下文 JSON：dto+已有表+descForAi';

COMMENT ON COLUMN `gen_ai_build_job`.`result` IS '最终结果 JSON（GenAiBuildPlanVO）';

COMMENT ON COLUMN `gen_ai_build_job`.`error` IS '任务级错误';

CREATE TABLE IF NOT EXISTS `gen_ai_build_batch` (
  `id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  `seq` int NOT NULL DEFAULT 0,
  `kind` varchar(16) NOT NULL DEFAULT 'module',
  `module_name` varchar(200) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `retry_count` int NOT NULL DEFAULT 0,
  `tables` mediumtext,
  `table_count` int NOT NULL DEFAULT 0,
  `pipeline_id` bigint DEFAULT NULL,
  `error` varchar(1000) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_ai_batch_job` ON `gen_ai_build_batch` (`job_id`, `seq`);

CREATE INDEX IF NOT EXISTS `idx_ai_batch_status` ON `gen_ai_build_batch` (`status`);

COMMENT ON TABLE `gen_ai_build_batch` IS 'AI 建表分批任务-批次';

COMMENT ON COLUMN `gen_ai_build_batch`.`id` IS '主键（雪花）';

COMMENT ON COLUMN `gen_ai_build_batch`.`job_id` IS '所属任务';

COMMENT ON COLUMN `gen_ai_build_batch`.`seq` IS '顺序（0=拆模块批次）';

COMMENT ON COLUMN `gen_ai_build_batch`.`kind` IS 'split=拆模块 module=模块建模 confirm=建表批次';

COMMENT ON COLUMN `gen_ai_build_batch`.`module_name` IS '模块名';

COMMENT ON COLUMN `gen_ai_build_batch`.`status` IS 'pending/running/success/failed/skipped';

COMMENT ON COLUMN `gen_ai_build_batch`.`retry_count` IS '已重试次数';

COMMENT ON COLUMN `gen_ai_build_batch`.`tables` IS '该批产出的表结构 JSON 数组';

COMMENT ON COLUMN `gen_ai_build_batch`.`pipeline_id` IS 'confirm 批次对应的流水线';

COMMENT ON COLUMN `gen_ai_build_batch`.`error` IS '失败原因';
