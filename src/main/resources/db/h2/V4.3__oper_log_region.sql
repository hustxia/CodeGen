-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V4.3__oper_log_region.sql
-- 转换记录:
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_oper_log province
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_oper_log city

ALTER TABLE `sys_oper_log` ADD COLUMN IF NOT EXISTS province varchar(32) NULL;

COMMENT ON COLUMN `sys_oper_log`.`province` IS '省份（IP 解析）';

ALTER TABLE `sys_oper_log` ADD COLUMN IF NOT EXISTS city varchar(32) NULL;

COMMENT ON COLUMN `sys_oper_log`.`city` IS '城市（IP 解析）';
