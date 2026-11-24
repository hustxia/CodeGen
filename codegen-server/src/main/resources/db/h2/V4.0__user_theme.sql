-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V4.0__user_theme.sql
-- 转换记录:
  [丢弃] information_schema 条件语句/诊断 SELECT
  [条件DDL] ADD COLUMN IF NOT EXISTS sys_user theme_id

ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS theme_id VARCHAR(64) DEFAULT NULL;

COMMENT ON COLUMN `sys_user`.`theme_id` IS '界面主题ID（色相-明暗-形态）';

DELETE FROM sys_menu WHERE path = '/system/theme';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, icon, type, sort, status, deleted)
VALUES (2040, 2000, '主题设置', '/system/theme', 'system/theme/index', 'system:theme:list', 'el-icon-brush', 2, 0, 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2040);
