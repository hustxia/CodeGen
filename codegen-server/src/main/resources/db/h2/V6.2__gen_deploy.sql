-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.2__gen_deploy.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_project default_gen_deploy
  [丢弃] information_schema 条件语句/诊断 SELECT

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS default_gen_deploy tinyint DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_deploy` IS '部署物默认:NULL=未配置(关) 1=生成 0=不生成';
