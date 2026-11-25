-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V3.0__multi_project.sql
-- 转换记录:
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_table project_id
-- [条件DDL] CREATE INDEX IF NOT EXISTS idx_project
-- [条件DDL] ADD COLUMN IF NOT EXISTS gen_output project_id

CREATE TABLE IF NOT EXISTS `sys_project` (
  id bigint NOT NULL,
  project_code varchar(64) NOT NULL,
  project_name varchar(64) NOT NULL,
  backend_root varchar(255) NULL,
  frontend_root varchar(255) NULL,
  backend_path varchar(255) NULL,
  frontend_path varchar(255) NULL,
  root_package varchar(128) NULL,
  gateway_prefix varchar(64) NULL,
  selfcheck_classpath varchar(512) NULL,
  is_default tinyint NOT NULL DEFAULT 0,
  enabled tinyint NOT NULL DEFAULT 1,
  sort int NOT NULL DEFAULT 0,
  remark varchar(255) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by bigint NULL,
  update_by bigint NULL,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT `uk_project_code` UNIQUE (project_code, deleted)
);

CREATE INDEX IF NOT EXISTS `idx_default` ON `sys_project` (is_default);

COMMENT ON TABLE `sys_project` IS '系统台账（多系统输出的根配置）';

COMMENT ON COLUMN `sys_project`.`id` IS '主键';

COMMENT ON COLUMN `sys_project`.`project_code` IS '系统编码（英文小写，生成物与字典前缀用）';

COMMENT ON COLUMN `sys_project`.`project_name` IS '系统名称';

COMMENT ON COLUMN `sys_project`.`backend_root` IS '后端工程根绝对路径';

COMMENT ON COLUMN `sys_project`.`frontend_root` IS '前端工程根绝对路径';

COMMENT ON COLUMN `sys_project`.`backend_path` IS '后端相对路径（如 demo-server/src/main/java/com/demo）';

COMMENT ON COLUMN `sys_project`.`frontend_path` IS '前端相对路径（如 demo-web）';

COMMENT ON COLUMN `sys_project`.`root_package` IS '根包名（如 com.demo）';

COMMENT ON COLUMN `sys_project`.`gateway_prefix` IS '网关/接口前缀（如 demo）';

COMMENT ON COLUMN `sys_project`.`selfcheck_classpath` IS '该系统的依赖 classpath（支持 dir/* 通配符）';

COMMENT ON COLUMN `sys_project`.`is_default` IS '1 为默认系统，新建表时预选';

COMMENT ON COLUMN `sys_project`.`enabled` IS '1 启用 0 停用';

COMMENT ON COLUMN `sys_project`.`sort` IS '排序';

COMMENT ON COLUMN `sys_project`.`remark` IS '备注';

ALTER TABLE `gen_table` ADD COLUMN IF NOT EXISTS project_id bigint NULL;

COMMENT ON COLUMN `gen_table`.`project_id` IS '所属系统 sys_project.id，空表示沿用全局 yml 配置';

CREATE INDEX IF NOT EXISTS `idx_project` ON `gen_table` (project_id);

ALTER TABLE `gen_output` ADD COLUMN IF NOT EXISTS project_id bigint NULL;

COMMENT ON COLUMN `gen_output`.`project_id` IS '生成时所属系统';

INSERT INTO sys_project (id, project_code, project_name, is_default, enabled, sort, remark)
SELECT 1, 'default', '默认系统', 1, 1, 0, 'V3.0 迁移时自动创建，配置由启动引导按 application.yml 同步'
WHERE NOT EXISTS (SELECT 1 FROM sys_project WHERE project_code = 'default' AND deleted = 0);

UPDATE gen_table
SET project_id = (SELECT id FROM sys_project WHERE project_code = 'default' AND deleted = 0 LIMIT 1)
WHERE project_id IS NULL;

UPDATE gen_output
SET project_id = (SELECT id FROM sys_project WHERE project_code = 'default' AND deleted = 0 LIMIT 1)
WHERE project_id IS NULL;
