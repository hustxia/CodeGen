-- V6.6 文件能力包：sys_project 文件基座系统级开关
-- 三态可空（V4.4 教训）：NULL=未配置(关) 1=生成文件基座 0=不生成
-- 回落唯一实现 GenTemplateModel.resolveFile，硬编码 0 兜底
-- 幂等：重复执行无副作用。执行顺序 V6.5 → 本脚本（AFTER mobile_enabled 依赖 V6.5 脚本）

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'file_enabled'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_project ADD COLUMN file_enabled tinyint DEFAULT NULL COMMENT ''文件能力包:NULL=未配置(关) 1=生成上传下载基座 0=不生成'' AFTER mobile_enabled',
    'SELECT ''column file_enabled already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'file_enabled';
