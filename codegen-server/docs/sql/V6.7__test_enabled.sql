-- V6.7 产物测试出厂：sys_project 测试骨架系统级开关
-- 三态可空（V4.4 教训）：NULL=未配置(开) 1=生成测试骨架 0=不生成
-- 回落唯一实现 GenTemplateModel.resolveTest，硬编码 1 兜底
--   （与 file/pay 的兜底 0 不同：测试只加 src/test 目录，零运行时侵入，默认开收益最大）
-- 幂等：重复执行无副作用。执行顺序 V6.6 → 本脚本（AFTER file_enabled 依赖 V6.6 脚本）

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'test_enabled'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_project ADD COLUMN test_enabled tinyint DEFAULT NULL COMMENT ''测试骨架:NULL=未配置(开) 1=生成集成测试骨架 0=不生成'' AFTER file_enabled',
    'SELECT ''column test_enabled already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'test_enabled';
