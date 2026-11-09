-- V6.1 工作流双引擎：两列新增，无新表
-- 幂等：空库/重跑都安全（2026-11-07 空库冒烟重跑实测：裸 ADD COLUMN 报 1060 Duplicate column 'flow_engine'）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'flow_engine');
SET @s := IF(@c = 0, 'ALTER TABLE sys_project ADD COLUMN flow_engine varchar(16) DEFAULT NULL COMMENT ''产物流程引擎：null/builtin=内置简易流，activiti=Activiti 8 引擎'' AFTER default_gen_print', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_flow_def' AND COLUMN_NAME = 'bpmn_xml');
SET @s := IF(@c = 0, 'ALTER TABLE sys_flow_def ADD COLUMN bpmn_xml mediumtext NULL COMMENT ''V6.1 自动翻译的 BPMN 2.0 XML（部署审计用）'' AFTER nodes', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
