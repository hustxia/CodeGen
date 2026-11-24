-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V4.5__gen_print.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS gen_table gen_print
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_project default_gen_print
  [丢弃] 纯 SELECT 诊断语句

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS gen_print TINYINT NULL DEFAULT NULL;

COMMENT ON COLUMN `gen_table`.`gen_print` IS '打印单据:NULL=跟随系统默认 1=生成 0=不生成';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS default_gen_print TINYINT NULL DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_print` IS '打印单据默认:NULL=未配置(关) 1=默认生成 0=默认不生成';
