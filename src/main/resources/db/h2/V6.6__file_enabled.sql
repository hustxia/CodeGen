-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.6__file_enabled.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_project file_enabled
  [丢弃] information_schema 条件语句/诊断 SELECT

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS file_enabled tinyint DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`file_enabled` IS '文件能力包:NULL=未配置(关) 1=生成上传下载基座 0=不生成';
