-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V9.17__print_enhance.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS gen_table print_title
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS gen_table print_landscape
  [丢弃] information_schema 条件语句/诊断 SELECT

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS print_title varchar(100) DEFAULT NULL;

COMMENT ON COLUMN `gen_table`.`print_title` IS '打印页标题:NULL/空=回落表注释';

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS print_landscape tinyint DEFAULT NULL;

COMMENT ON COLUMN `gen_table`.`print_landscape` IS '横版打印:NULL/0=A4纵向 1=A4横向';
