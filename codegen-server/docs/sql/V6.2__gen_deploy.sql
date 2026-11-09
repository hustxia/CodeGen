-- V6.2 部署物能力包：sys_project 部署物默认开关（补录：V6.2 交付时只改了实体与测试库 schema，漏了迁移脚本）
-- 三态可空（V4.4 教训）：NULL=未配置(关) 1=生成 0=不生成；回落唯一实现 GenTemplateModel.resolveGenDeploy
-- 幂等：重复执行无副作用。执行顺序 V6.1 → 本脚本 → V6.3（V6.3 的 AFTER default_gen_deploy 依赖本列）

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_project'
      AND COLUMN_NAME = 'default_gen_deploy'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_project ADD COLUMN default_gen_deploy tinyint DEFAULT NULL COMMENT ''部署物默认:NULL=未配置(关) 1=生成 0=不生成'' AFTER flow_engine',
    'SELECT ''column default_gen_deploy already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'default_gen_deploy';
