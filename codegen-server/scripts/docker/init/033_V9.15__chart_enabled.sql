-- V9.15 数据可视化图表：sys_project 图表能力包系统级开关
-- 三态可空（V4.4 教训）：NULL=未配置(关) 1=生成图表能力包 0=不生成
-- 回落唯一实现 GenTemplateModel.resolveChart，硬编码 0 兜底
-- 幂等：重复执行无副作用。执行顺序 V6.8 → 本脚本（AFTER i18n_enabled）

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'chart_enabled'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_project ADD COLUMN chart_enabled tinyint DEFAULT NULL COMMENT ''数据可视化图表能力包:NULL=未配置(关) 1=生成看板与聚合接口 0=不生成'' AFTER i18n_enabled',
    'SELECT ''column chart_enabled already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'chart_enabled';
