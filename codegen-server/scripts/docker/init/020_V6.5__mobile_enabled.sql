-- V6.5 移动端支持：sys_project 移动端工程开关（补录：V6.5 交付时设计文档写了 DDL，脚本未落文件）
-- 三态可空（V4.4 教训）：NULL=未配置(关) 1=生成 uni-app 工程 0=不生成；回落唯一实现 GenTemplateModel.resolveMobile
-- 幂等：重复执行无副作用。执行顺序 V6.3 → 本脚本 → V6.6（V6.6 的 AFTER mobile_enabled 依赖本列）

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'mobile_enabled'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_project ADD COLUMN mobile_enabled tinyint DEFAULT NULL COMMENT ''移动端工程:NULL=未配置(关) 1=生成 uni-app 单源 H5+微信小程序'' AFTER pay_enabled',
    'SELECT ''column mobile_enabled already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'mobile_enabled';
