-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V2.1__api_catalog_menu.sql
-- 转换记录:

DELETE FROM sys_menu WHERE path = '/gen/api-catalog';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (1050, 1000, '接口目录', '/gen/api-catalog', 'gen/api-catalog/index', 'gen:api-catalog:list', 2, 5, 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1050);
