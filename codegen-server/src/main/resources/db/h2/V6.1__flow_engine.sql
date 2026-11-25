-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V6.1__flow_engine.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_project flow_engine
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_flow_def bpmn_xml

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS flow_engine varchar(16) DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`flow_engine` IS '产物流程引擎：null/builtin=内置简易流，activiti=Activiti 8 引擎';

ALTER TABLE `sys_flow_def` ADD COLUMN IF NOT EXISTS bpmn_xml mediumtext NULL;

COMMENT ON COLUMN `sys_flow_def`.`bpmn_xml` IS 'V6.1 自动翻译的 BPMN 2.0 XML（部署审计用）';
