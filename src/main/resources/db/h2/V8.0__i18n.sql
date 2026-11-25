-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V8.0__i18n.sql
-- 转换记录:
-- [丢弃] information_schema 条件语句/诊断 SELECT
-- [条件DDL] ADD COLUMN IF NOT EXISTS sys_project i18n_enabled

CREATE TABLE IF NOT EXISTS `sys_i18n_message` (
  id bigint NOT NULL,
  project_id bigint NOT NULL,
  locale varchar(32) NOT NULL,
  msg_key varchar(128) NOT NULL,
  msg_value text NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT `uk_i18n_msg` UNIQUE (project_id, locale, msg_key)
);

CREATE INDEX IF NOT EXISTS `idx_i18n_project_locale` ON `sys_i18n_message` (project_id, locale);

COMMENT ON TABLE `sys_i18n_message` IS 'V8.0 用户译文覆盖表';

COMMENT ON COLUMN `sys_i18n_message`.`id` IS '主键';

COMMENT ON COLUMN `sys_i18n_message`.`project_id` IS '所属系统（译文按系统隔离）';

COMMENT ON COLUMN `sys_i18n_message`.`locale` IS 'BCP47 语言标签，如 en-US';

COMMENT ON COLUMN `sys_i18n_message`.`msg_key` IS '文案键，如 common.add';

COMMENT ON COLUMN `sys_i18n_message`.`msg_value` IS '用户译文；NULL/空串=回落到内置精译或默认语言';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS i18n_enabled tinyint NULL;

COMMENT ON COLUMN `sys_project`.`i18n_enabled` IS '产物国际化开关:NULL/0=关,1=开(V8.0)';

DELETE FROM sys_menu WHERE path = '/gen/i18n';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (1061, 1000, '文案翻译', '/gen/i18n', 'gen/i18n/index', 'gen:i18n:list', 2, 8, 'el-icon-notebook-2', 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1061);

INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900001, p.id, 'en-US', 'common.add', 'Add'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);

INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900002, p.id, 'en-US', 'common.saveSuccess', 'Saved successfully'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);

INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900003, p.id, 'ar-SA', 'common.query', 'بحث'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);

INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900004, p.id, 'ja-JP', 'common.delete', '削除'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);
