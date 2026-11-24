-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V3.1__project_menu.sql
-- 转换记录:

DELETE FROM sys_menu WHERE path = '/gen/project';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (1060, 1000, '系统管理', '/gen/project', 'gen/project/index', 'gen:project:list', 2, 0, 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1060);
