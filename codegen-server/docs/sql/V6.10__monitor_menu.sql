-- ============================================================
-- V6.10 平台可观测性：系统监控菜单
-- 幂等：先删同路径再插（同 V2.1 / V6.9 写法）
-- 权限码：monitor:view（菜单 / 页面 / 后端 /monitor/* 共用同一个码）
-- 前端走 require.context 动态路由，component 写 monitor/index 即可，不改 router/index.js
-- id 段：V6.9 已占 3000/3010/3020，本版用 3030
-- ============================================================
DELETE FROM sys_menu WHERE path = '/monitor';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (3030, 0, '系统监控', '/monitor', 'monitor/index', 'monitor:view', 2, 4, 'monitor', 1, 0);

-- 超级管理员角色默认可见
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3030);
