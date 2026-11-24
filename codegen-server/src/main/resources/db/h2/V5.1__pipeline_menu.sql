-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V5.1__pipeline_menu.sql
-- 转换记录:

DELETE FROM sys_menu WHERE path = '/gen/pipeline';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (1070, 1000, '一句话建系统', '/gen/pipeline', 'gen/pipeline/index', 'gen:pipeline:view', 2, 9, 'el-icon-magic-stick', 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1070);
