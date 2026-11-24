-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.7__test_enabled.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_project test_enabled
  [丢弃] information_schema 条件语句/诊断 SELECT

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS test_enabled tinyint DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`test_enabled` IS '测试骨架:NULL=未配置(开) 1=生成集成测试骨架 0=不生成';
