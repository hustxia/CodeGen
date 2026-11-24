-- V5.5 多模块工程生成：sys_project 增加工程结构配置
-- 三态可空（NULL=单模块 mono，'multi'=多模块），延续 V4.4 教训：NOT NULL DEFAULT 会杀死回落层
-- 幂等：重复执行无副作用

-- 1. 加列（先查 information_schema）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'project_structure'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_project ADD COLUMN project_structure varchar(20) NULL COMMENT ''工程结构：NULL=单模块，multi=多模块（微服务就绪）'' AFTER default_gen_print',
    'SELECT ''column project_structure already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 校验
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'project_structure';
