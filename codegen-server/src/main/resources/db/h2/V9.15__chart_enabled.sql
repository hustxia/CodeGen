-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V9.15__chart_enabled.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_project chart_enabled
-- [丢弃] information_schema 条件语句/诊断 SELECT

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS chart_enabled tinyint DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`chart_enabled` IS '数据可视化图表能力包:NULL=未配置(关) 1=生成看板与聚合接口 0=不生成';
