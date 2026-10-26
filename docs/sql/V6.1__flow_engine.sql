-- V6.1 工作流双引擎：两列新增，无新表
ALTER TABLE sys_project
    ADD COLUMN flow_engine varchar(16) DEFAULT NULL COMMENT '产物流程引擎：null/builtin=内置简易流，activiti=Activiti 8 引擎' AFTER default_gen_print;

ALTER TABLE sys_flow_def
    ADD COLUMN bpmn_xml mediumtext NULL COMMENT 'V6.1 自动翻译的 BPMN 2.0 XML（部署审计用）' AFTER nodes;
