-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V2.2__flow_code.sql
-- 转换记录:
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table flow_code

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS flow_code varchar(64) DEFAULT NULL;

COMMENT ON COLUMN `gen_table`.`flow_code` IS 'P3-3 绑定的审批流程编码，空为不启用';
