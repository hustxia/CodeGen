-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V5.5__project_structure.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_project project_structure
  [丢弃] information_schema 条件语句/诊断 SELECT

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS project_structure varchar(20) NULL;

COMMENT ON COLUMN `sys_project`.`project_structure` IS '工程结构：NULL=单模块，multi=多模块（微服务就绪）';
