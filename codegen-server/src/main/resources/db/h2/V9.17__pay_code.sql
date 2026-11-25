-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V9.17__pay_code.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table pay_code

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS pay_code varchar(64) DEFAULT NULL;

COMMENT ON COLUMN `gen_table`.`pay_code` IS '表级支付绑定(pay_code):非空=该表接入支付能力包';
