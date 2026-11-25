-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V7.4__oper_log_audit_columns.sql
-- 转换记录:
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_oper_log create_time
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_oper_log update_time
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_oper_log create_by
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_oper_log update_by

ALTER TABLE `sys_oper_log` ADD COLUMN IF NOT EXISTS create_time datetime NULL DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN `sys_oper_log`.`create_time` IS '创建时间';

ALTER TABLE `sys_oper_log` ADD COLUMN IF NOT EXISTS update_time datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

COMMENT ON COLUMN `sys_oper_log`.`update_time` IS '更新时间';

ALTER TABLE `sys_oper_log` ADD COLUMN IF NOT EXISTS create_by bigint NULL;

COMMENT ON COLUMN `sys_oper_log`.`create_by` IS '创建人';

ALTER TABLE `sys_oper_log` ADD COLUMN IF NOT EXISTS update_by bigint NULL;

COMMENT ON COLUMN `sys_oper_log`.`update_by` IS '更新人';
