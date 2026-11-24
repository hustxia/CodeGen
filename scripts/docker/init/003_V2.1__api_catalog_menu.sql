-- P2-5 接口目录菜单：挂在「代码生成」下，排最后
-- 幂等写法：MySQL 8 对 INSERT ... SELECT ... WHERE NOT EXISTS 的自引用支持不好，
-- 这里用 INSERT IGNORE + 唯一路径判断的变通方案：先删同名的再插。
DELETE FROM sys_menu WHERE path = '/gen/api-catalog';
INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (1050, 1000, '接口目录', '/gen/api-catalog', 'gen/api-catalog/index', 'gen:api-catalog:list', 2, 5, 1, 0);

-- 授权给管理员角色（角色 1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1050);
