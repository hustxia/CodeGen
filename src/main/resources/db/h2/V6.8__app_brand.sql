-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.8__app_brand.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_project app_name
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_project app_logo
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_project app_theme_color

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS app_name varchar(64) NULL;

COMMENT ON COLUMN `sys_project`.`app_name` IS '应用显示名(空=项目名)';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS app_logo varchar(255) NULL;

COMMENT ON COLUMN `sys_project`.`app_logo` IS 'Logo图片URL(空=内置logo)';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS app_theme_color varchar(16) NULL;

COMMENT ON COLUMN `sys_project`.`app_theme_color` IS '主题主色(空=#e1251b)';
