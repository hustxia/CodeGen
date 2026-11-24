-- H2 MODE=MySQL 版本（demo 模式用）
-- 原始脚本: docs/sql/V9.18__child_save_mode.sql
-- 转换记录:
--   [条件DDL] ADD COLUMN IF NOT EXISTS gen_table child_save_mode
--   [丢弃] information_schema 条件语句/验证 SELECT（H2 内存库每次启动都是新库）

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS child_save_mode tinyint NOT NULL DEFAULT 0;

COMMENT ON COLUMN `gen_table`.`child_save_mode` IS '子表保存模式:0=全量替换(默认) 1=按主键三路合并(含一对一 upsert/多对多差集)';
