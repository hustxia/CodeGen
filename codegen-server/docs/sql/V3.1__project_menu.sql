-- ============================================================
-- V3.1 系统管理菜单：挂在「代码生成」下
-- 幂等：先删同路径再插（同 V2.1 的写法）
-- ============================================================
DELETE FROM sys_menu WHERE path = '/gen/project';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, status, deleted)
VALUES (1060, 1000, '系统管理', '/gen/project', 'gen/project/index', 'gen:project:list', 2, 0, 1, 0);

-- 超级管理员角色默认可见
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1060);
