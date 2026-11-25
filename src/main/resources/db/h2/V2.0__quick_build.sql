-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V2.0__quick_build.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table_column mask_type
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table_column unique_flag
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table_column link_rule
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table_column data_scope
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table_column relation_key
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table master_depth
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table relation_type
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_role data_scope
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_user dept_id
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table gen_log

ALTER TABLE `gen_table_column` ADD COLUMN IF NOT EXISTS mask_type varchar(16) NULL;

COMMENT ON COLUMN `gen_table_column`.`mask_type` IS '脱敏类型';

ALTER TABLE `gen_table_column` ADD COLUMN IF NOT EXISTS unique_flag tinyint NOT NULL DEFAULT 0;

COMMENT ON COLUMN `gen_table_column`.`unique_flag` IS '是否参与唯一性校验:0否1是';

ALTER TABLE `gen_table_column` ADD COLUMN IF NOT EXISTS link_rule varchar(512) NULL;

COMMENT ON COLUMN `gen_table_column`.`link_rule` IS '表单联动规则 JSON';

ALTER TABLE `gen_table_column` ADD COLUMN IF NOT EXISTS data_scope varchar(16) NULL;

COMMENT ON COLUMN `gen_table_column`.`data_scope` IS '数据权限标记';

ALTER TABLE `gen_table_column` ADD COLUMN IF NOT EXISTS relation_key tinyint NOT NULL DEFAULT 0;

COMMENT ON COLUMN `gen_table_column`.`relation_key` IS '是否中间表外键列:0否1是';

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS master_depth tinyint NOT NULL DEFAULT 0;

COMMENT ON COLUMN `gen_table`.`master_depth` IS '主子表层级深度';

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS relation_type varchar(16) NULL;

COMMENT ON COLUMN `gen_table`.`relation_type` IS '表关系类型：middle 中间表';

ALTER TABLE `sys_role` ADD COLUMN IF NOT EXISTS data_scope varchar(16) NOT NULL DEFAULT 'all';

COMMENT ON COLUMN `sys_role`.`data_scope` IS '数据权限范围:all/dept/self';

ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS dept_id bigint NULL;

COMMENT ON COLUMN `sys_user`.`dept_id` IS '所属部门ID';

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS gen_log tinyint NOT NULL DEFAULT 1;

COMMENT ON COLUMN `gen_table`.`gen_log` IS '开关:生成操作日志@Log';

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  id bigint NOT NULL,
  title varchar(64) NOT NULL,
  oper_type varchar(16) NOT NULL,
  method varchar(200) NULL,
  request_uri varchar(255) NULL,
  request_method varchar(10) NULL,
  oper_param text NULL,
  json_result text NULL,
  status tinyint NOT NULL DEFAULT 1,
  error_msg text NULL,
  oper_user_id bigint NULL,
  oper_name varchar(64) NULL,
  oper_ip varchar(64) NULL,
  cost_ms bigint NULL,
  oper_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  create_by bigint NULL,
  create_time datetime NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint NULL,
  update_time datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS `idx_oper_time` ON `sys_oper_log` (oper_time);

CREATE INDEX IF NOT EXISTS `idx_oper_user` ON `sys_oper_log` (oper_user_id);

COMMENT ON TABLE `sys_oper_log` IS '操作日志';

COMMENT ON COLUMN `sys_oper_log`.`id` IS '主键';

COMMENT ON COLUMN `sys_oper_log`.`title` IS '模块标题';

COMMENT ON COLUMN `sys_oper_log`.`oper_type` IS 'INSERT/UPDATE/DELETE/IMPORT/EXPORT/OTHER';

COMMENT ON COLUMN `sys_oper_log`.`method` IS '方法名';

COMMENT ON COLUMN `sys_oper_log`.`request_uri` IS '请求地址';

COMMENT ON COLUMN `sys_oper_log`.`request_method` IS 'HTTP 方法';

COMMENT ON COLUMN `sys_oper_log`.`oper_param` IS '请求参数（敏感字段已脱敏）';

COMMENT ON COLUMN `sys_oper_log`.`json_result` IS '返回结果';

COMMENT ON COLUMN `sys_oper_log`.`status` IS '1成功0失败';

COMMENT ON COLUMN `sys_oper_log`.`error_msg` IS '错误消息';

COMMENT ON COLUMN `sys_oper_log`.`oper_user_id` IS '操作人 ID';

COMMENT ON COLUMN `sys_oper_log`.`oper_name` IS '操作人用户名';

COMMENT ON COLUMN `sys_oper_log`.`oper_ip` IS '操作 IP';

COMMENT ON COLUMN `sys_oper_log`.`cost_ms` IS '耗时毫秒';

COMMENT ON COLUMN `sys_oper_log`.`oper_time` IS '操作时间';

COMMENT ON COLUMN `sys_oper_log`.`create_by` IS '创建人';

COMMENT ON COLUMN `sys_oper_log`.`create_time` IS '创建时间';

COMMENT ON COLUMN `sys_oper_log`.`update_by` IS '更新人';

COMMENT ON COLUMN `sys_oper_log`.`update_time` IS '更新时间';

CREATE TABLE IF NOT EXISTS `sys_flow_def` (
  id bigint NOT NULL,
  flow_code varchar(64) NOT NULL,
  flow_name varchar(64) NOT NULL,
  table_name varchar(64) NOT NULL,
  status_col varchar(64) NOT NULL DEFAULT 'status',
  nodes varchar(1024) NOT NULL,
  enabled tinyint NOT NULL DEFAULT 1,
  remark varchar(255) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by bigint NULL,
  update_by bigint NULL,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT `uk_flow_code` UNIQUE (flow_code, deleted)
);

COMMENT ON TABLE `sys_flow_def` IS '审批流程定义';

COMMENT ON COLUMN `sys_flow_def`.`id` IS '主键';

COMMENT ON COLUMN `sys_flow_def`.`flow_code` IS '流程编码';

COMMENT ON COLUMN `sys_flow_def`.`flow_name` IS '流程名称';

COMMENT ON COLUMN `sys_flow_def`.`table_name` IS '绑定业务表名';

COMMENT ON COLUMN `sys_flow_def`.`status_col` IS '单据状态列';

COMMENT ON COLUMN `sys_flow_def`.`nodes` IS '状态机 JSON [{from,to,action,roleCode}]';

CREATE TABLE IF NOT EXISTS `sys_flow_task` (
  id bigint NOT NULL PRIMARY KEY,
  flow_code varchar(64) NOT NULL,
  biz_table varchar(64) NOT NULL,
  biz_id bigint NOT NULL,
  from_status varchar(32) NULL,
  to_status varchar(32) NOT NULL,
  action varchar(16) NOT NULL,
  comment varchar(512) NULL,
  oper_user_id bigint NULL,
  oper_name varchar(64) NULL,
  oper_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS `idx_biz` ON `sys_flow_task` (biz_table, biz_id);

CREATE INDEX IF NOT EXISTS `idx_flow` ON `sys_flow_task` (flow_code);

COMMENT ON TABLE `sys_flow_task` IS '审批任务流水';

COMMENT ON COLUMN `sys_flow_task`.`action` IS 'SUBMIT/APPROVE/REJECT';
