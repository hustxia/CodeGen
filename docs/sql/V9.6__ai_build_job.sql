-- V9.6 AI 建表分批任务：大型/超大型系统不再单请求串行建模，改为「任务 + 批次」落库，
-- 由调度器多 worker 认领执行，支持进度查询、失败重试、重启续跑。
-- 幂等：空库全量脚本与重复执行都必须通过（verify.sh 空库冒烟会重跑一遍）。

CREATE TABLE IF NOT EXISTS `gen_ai_build_job` (
  `id`            bigint       NOT NULL COMMENT '主键（雪花）',
  `job_no`        varchar(40)  DEFAULT NULL COMMENT '任务号（可读，AIJ+时间戳）',
  `job_type`      varchar(16)  NOT NULL DEFAULT 'plan' COMMENT 'plan=建模 confirm=落库建表',
  `status`        varchar(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/partial/failed/canceled',
  `system_name`   varchar(200) DEFAULT NULL COMMENT '系统名称',
  `system_desc`   varchar(1000) DEFAULT NULL COMMENT '系统描述（原文）',
  `scope`         varchar(16)  DEFAULT 'new' COMMENT 'new/supplement',
  `project_id`    bigint       DEFAULT NULL COMMENT '补表目标系统',
  `scale`         varchar(16)  DEFAULT NULL COMMENT 'small/large/huge',
  `deploy`        varchar(16)  DEFAULT NULL COMMENT 'mono/micro',
  `total_batch`   int          NOT NULL DEFAULT 0 COMMENT '总批次数（拆模块阶段会从 1 增长）',
  `done_batch`    int          NOT NULL DEFAULT 0 COMMENT '成功批次数',
  `failed_batch`  int          NOT NULL DEFAULT 0 COMMENT '失败批次数',
  `total_table`   int          NOT NULL DEFAULT 0 COMMENT '已产出表数（滚动累计）',
  `payload`       mediumtext   COMMENT '任务上下文 JSON：dto+已有表+descForAi',
  `result`        mediumtext   COMMENT '最终结果 JSON（GenAiBuildPlanVO）',
  `error`         varchar(1000) DEFAULT NULL COMMENT '任务级错误',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`     bigint       DEFAULT NULL,
  `update_by`     bigint       DEFAULT NULL,
  `deleted`       tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_job_no` (`job_no`),
  KEY `idx_ai_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 建表分批任务';

CREATE TABLE IF NOT EXISTS `gen_ai_build_batch` (
  `id`            bigint       NOT NULL COMMENT '主键（雪花）',
  `job_id`        bigint       NOT NULL COMMENT '所属任务',
  `seq`           int          NOT NULL DEFAULT 0 COMMENT '顺序（0=拆模块批次）',
  `kind`          varchar(16)  NOT NULL DEFAULT 'module' COMMENT 'split=拆模块 module=模块建模 confirm=建表批次',
  `module_name`   varchar(200) DEFAULT NULL COMMENT '模块名',
  `status`        varchar(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed/skipped',
  `retry_count`   int          NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `tables`        mediumtext   COMMENT '该批产出的表结构 JSON 数组',
  `table_count`   int          NOT NULL DEFAULT 0,
  `pipeline_id`   bigint       DEFAULT NULL COMMENT 'confirm 批次对应的流水线',
  `error`         varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `started_at`    datetime     DEFAULT NULL,
  `finished_at`   datetime     DEFAULT NULL,
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`     bigint       DEFAULT NULL,
  `update_by`     bigint       DEFAULT NULL,
  `deleted`       tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ai_batch_job` (`job_id`, `seq`),
  KEY `idx_ai_batch_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 建表分批任务-批次';
