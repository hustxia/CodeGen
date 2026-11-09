-- ============================================================
-- V3.0 多系统支持：系统台账 sys_project
--
-- 背景：现在 backend_root / frontend_root / root_package / gateway_prefix
-- 散落在 application.yml 和每张 gen_table 上，改一个系统的包名要 UPDATE 全表，
-- 也没有任何地方能回答「这个系统有哪些表」。
--
-- 引入 sys_project 后 gen_table 只存 project_id + module 引用，
-- 生成时按「表级覆盖 → 所属系统 → 全局 yml」三级回落。
--
-- 幂等：全部走 information_schema / INSERT ... WHERE NOT EXISTS 判断。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_project
(
    id                  bigint       NOT NULL COMMENT '主键',
    project_code        varchar(64)  NOT NULL COMMENT '系统编码（英文小写，生成物与字典前缀用）',
    project_name        varchar(64)  NOT NULL COMMENT '系统名称',
    backend_root        varchar(255) NULL COMMENT '后端工程根绝对路径',
    frontend_root       varchar(255) NULL COMMENT '前端工程根绝对路径',
    backend_path        varchar(255) NULL COMMENT '后端相对路径（如 demo-server/src/main/java/com/demo）',
    frontend_path       varchar(255) NULL COMMENT '前端相对路径（如 demo-web）',
    root_package        varchar(128) NULL COMMENT '根包名（如 com.demo）',
    gateway_prefix      varchar(64)  NULL COMMENT '网关/接口前缀（如 demo）',
    selfcheck_classpath varchar(512) NULL COMMENT '该系统的依赖 classpath（支持 dir/* 通配符）',
    is_default          tinyint      NOT NULL DEFAULT 0 COMMENT '1 为默认系统，新建表时预选',
    enabled             tinyint      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    sort                int          NOT NULL DEFAULT 0 COMMENT '排序',
    remark              varchar(255) NULL COMMENT '备注',
    create_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by           bigint       NULL,
    update_by           bigint       NULL,
    deleted             tinyint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (project_code, deleted),
    KEY idx_default (is_default)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统台账（多系统输出的根配置）';

-- ------------------------------------------------------------
-- 给 gen_table 挂上系统
-- ------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE gen_table ADD COLUMN project_id bigint NULL COMMENT ''所属系统 sys_project.id，空表示沿用全局 yml 配置''',
              'SELECT ''gen_table.project_id 已存在，跳过'' AS msg')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_table'
      AND COLUMN_NAME = 'project_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE gen_table ADD INDEX idx_project (project_id)',
              'SELECT ''idx_project 已存在，跳过'' AS msg')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_table'
      AND INDEX_NAME = 'idx_project'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 生成台账也挂系统：131 条历史记录现在分不清属于哪个系统
-- ------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE gen_output ADD COLUMN project_id bigint NULL COMMENT ''生成时所属系统''',
              'SELECT ''gen_output.project_id 已存在，跳过'' AS msg')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_output'
      AND COLUMN_NAME = 'project_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 用当前 yml 的默认值建一条「默认系统」，把存量表全部挂过去。
--
-- 取值刻意不写死绝对路径：这里写的是占位，真正的路径由后端
-- ProjectBootstrap 启动时按 GenOutputProperties 覆盖一次，
-- 这样换机器 / 改 application-local.yml 也能自动跟上。
-- 只在没有默认系统时插入，避免每次启动改回用户配置。
-- ------------------------------------------------------------
INSERT INTO sys_project (id, project_code, project_name, is_default, enabled, sort, remark)
SELECT 1, 'default', '默认系统', 1, 1, 0, 'V3.0 迁移时自动创建，配置由启动引导按 application.yml 同步'
WHERE NOT EXISTS (SELECT 1 FROM sys_project WHERE project_code = 'default' AND deleted = 0);

-- 存量表回填：还没有归属的，一律挂默认系统
UPDATE gen_table
SET project_id = (SELECT id FROM sys_project WHERE project_code = 'default' AND deleted = 0 LIMIT 1)
WHERE project_id IS NULL;

-- 存量生成台账同样回填
UPDATE gen_output
SET project_id = (SELECT id FROM sys_project WHERE project_code = 'default' AND deleted = 0 LIMIT 1)
WHERE project_id IS NULL;
