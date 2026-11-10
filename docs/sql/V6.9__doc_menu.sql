-- ============================================================
-- V6.9 平台文档中心：菜单 + 按钮权限
-- 幂等：先删同路径再插（同 V2.1/V3.1 写法）
-- 权限码：doc:list（菜单）/ doc:upload / doc:remove（按钮）
-- 后端 DocController 用 PermissionService 校验同一套码，不靠前端隐藏按钮
-- ============================================================
DELETE FROM sys_menu WHERE path = '/doc';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES
  (3000, 0,    '文档中心', '/doc',        'doc/index',   'doc:list',   2, 3, 'documentation', 1, 0),
  (3010, 3000, '上传文档', NULL,          NULL,          'doc:upload', 3, 1, NULL,            1, 0),
  (3020, 3000, '删除文档', NULL,          NULL,          'doc:remove', 3, 2, NULL,            1, 0);

-- 超级管理员角色默认可见
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3000), (1, 3010), (1, 3020);
