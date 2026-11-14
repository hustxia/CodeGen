-- ============================================================
-- V9.2 测试中心：7 张表 + 菜单/按钮权限
-- 形态：CodeGen 控制台内嵌模块（非独立平台）
-- 设计：docs/design/V9.2测试中心-设计.md
-- 幂等：表 IF NOT EXISTS；菜单先删同路径再插（同 V6.9/V8.0 写法）
-- 权限码：test:list / test:env:edit / test:case:edit / test:case:generate / test:run
-- ============================================================

-- ---------------------------- 1. 测试环境 ----------------------------
CREATE TABLE IF NOT EXISTS `test_env` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '所属系统(sys_project.id)',
  `name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '环境名，如 test / 预发',
  `base_url` varchar(300) COLLATE utf8mb4_general_ci NOT NULL COMMENT '接口根地址',
  `headers_json` text COLLATE utf8mb4_general_ci COMMENT '公共请求头',
  `secret_ref` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密钥引用(环境变量名/配置键)，不存明文',
  `is_prod` tinyint NOT NULL DEFAULT '0' COMMENT '生产环境:1是(只读，禁写操作)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用:0否1是',
  `remark` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_env_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试环境';

-- ---------------------------- 2. 测试套件 ----------------------------
CREATE TABLE IF NOT EXISTS `test_suite` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '所属系统',
  `table_id` bigint DEFAULT NULL COMMENT '来源业务表(gen_table.id)，手工套件为空',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '套件名，默认取表注释',
  `type` varchar(8) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'api' COMMENT '类型:api/ui',
  `source` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '来源:generated/manual',
  `module_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '模块名',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用:0否1是',
  `remark` varchar(500) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_suite_project` (`project_id`),
  KEY `idx_suite_table` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试套件';

-- ---------------------------- 3. 测试用例 ----------------------------
CREATE TABLE IF NOT EXISTS `test_case` (
  `id` bigint NOT NULL COMMENT '主键',
  `suite_id` bigint NOT NULL COMMENT '所属套件',
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用例名',
  `source` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '来源:generated/manual',
  `method` varchar(10) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'GET' COMMENT 'HTTP 方法',
  `path` varchar(300) COLLATE utf8mb4_general_ci NOT NULL COMMENT '接口路径(支持 ${var} 占位)',
  `headers_json` text COLLATE utf8mb4_general_ci COMMENT '请求头',
  `body_json` text COLLATE utf8mb4_general_ci COMMENT '请求体',
  `extract_json` text COLLATE utf8mb4_general_ci COMMENT '变量提取:{"name":"$.data.id"}',
  `asserts_json` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '断言数组',
  `cleanup_sql` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行后清理语句(生产环境禁执行)',
  `order_no` int NOT NULL DEFAULT '0' COMMENT '执行顺序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用:0否1是',
  `fingerprint` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成内容哈希，判定是否被手工修改',
  `last_result` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最近结果:pass/fail/skip',
  `last_run_time` datetime DEFAULT NULL COMMENT '最近执行时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_case_suite` (`suite_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试用例';

-- ---------------------------- 4. 用例数据集 ----------------------------
CREATE TABLE IF NOT EXISTS `test_dataset` (
  `id` bigint NOT NULL COMMENT '主键',
  `case_id` bigint NOT NULL COMMENT '所属用例',
  `name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名',
  `rows_json` longtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '行数组，每行一组变量',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用:0否1是',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dataset_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例数据集';

-- ---------------------------- 5. 执行批次 ----------------------------
CREATE TABLE IF NOT EXISTS `test_run` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '所属系统',
  `env_id` bigint NOT NULL COMMENT '执行环境',
  `trigger_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '触发:manual/schedule/ci',
  `suite_ids` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '本轮套件 ID 列表',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'running' COMMENT '状态:running/success/failed/aborted',
  `total` int NOT NULL DEFAULT '0' COMMENT '用例总数',
  `passed` int NOT NULL DEFAULT '0' COMMENT '通过数',
  `failed` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `skipped` int NOT NULL DEFAULT '0' COMMENT '跳过数',
  `duration_ms` bigint DEFAULT NULL COMMENT '总耗时',
  `started_at` datetime NOT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `error_msg` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '编排级错误',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_project` (`project_id`),
  KEY `idx_run_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试执行批次';

-- ---------------------------- 6. 用例结果 ----------------------------
CREATE TABLE IF NOT EXISTS `test_result` (
  `id` bigint NOT NULL COMMENT '主键',
  `run_id` bigint NOT NULL COMMENT '所属批次',
  `case_id` bigint NOT NULL COMMENT '用例 ID',
  `case_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用例名快照(防改名后报告失真)',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '结果:pass/fail/skip',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时',
  `request_snapshot` mediumtext COLLATE utf8mb4_general_ci COMMENT '请求快照',
  `response_snapshot` mediumtext COLLATE utf8mb4_general_ci COMMENT '响应快照(超 64KB 截断并标记)',
  `screenshot_path` varchar(300) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '截图相对路径',
  `failed_assert_json` text COLLATE utf8mb4_general_ci COMMENT '失败断言明细',
  `error_msg` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '异常摘要',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_result_run` (`run_id`),
  KEY `idx_result_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试用例结果';

-- ---------------------------- 7. 定时回归 ----------------------------
CREATE TABLE IF NOT EXISTS `test_schedule` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL DEFAULT '0' COMMENT '所属系统',
  `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名',
  `env_id` bigint NOT NULL COMMENT '执行环境',
  `suite_ids` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '套件 ID 列表',
  `cron` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron 表达式(6 段)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用:0否1是',
  `last_run_id` bigint DEFAULT NULL COMMENT '最近批次',
  `notify_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通知方式:none/webhook',
  `notify_target` varchar(300) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通知目标',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除:0否1是',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sched_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试定时任务';

-- ---------------------------- 菜单 + 按钮权限 ----------------------------
-- 幂等：按钮菜单 path 为 NULL，只按 path='/test' 删会残留 5010–5040，
-- 重跑时整条多值 INSERT 因主键冲突失败（ERROR 1062），连主菜单 5000 一起回滚。
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

SELECT 'V9.2 test center tables ready' AS info,
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'test\_%') AS test_table_count;

