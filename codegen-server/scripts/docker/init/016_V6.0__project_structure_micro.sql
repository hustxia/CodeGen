-- V6.0 微服务设施：project_structure 增值 'micro'（第三态）
-- 无新列、无语义变化，仅更新列注释使其描述完整取值域；NULL/multi 行为零变化
-- 幂等：重复执行无副作用（MySQL 18 前用动态 SQL 判断注释是否已是新值）

SET @need_update = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'project_structure'
      AND COLUMN_COMMENT NOT LIKE '%micro%'
);
SET @ddl = IF(@need_update > 0,
    'ALTER TABLE sys_project MODIFY COLUMN project_structure varchar(20) NULL COMMENT ''工程结构：NULL=单模块，multi=多模块，micro=微服务设施（五模块+Nacos/Gateway/Feign）''',
    'SELECT ''comment already up to date'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'project_structure';
