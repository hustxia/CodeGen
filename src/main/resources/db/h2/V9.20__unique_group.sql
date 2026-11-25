-- H2 MODE=MySQL 版本（demo 模式用）
-- 原始脚本: docs/sql/V9.20__unique_group.sql
ALTER TABLE `gen_table_column` ADD COLUMN IF NOT EXISTS unique_group tinyint NOT NULL DEFAULT 0;

COMMENT ON COLUMN `gen_table_column`.`unique_group` IS '唯一组编号:0=默认组 1..N=独立唯一组';
