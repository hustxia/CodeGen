-- ============================================================
-- V9.27.2：文档中心 + 字典配置 归并到「系统管理」下
--
-- 背景（2026-11-28 功能菜单全量对账）：
-- - 文档中心（3000）原为顶层独立菜单（V6.9），现按用户要求归入系统管理（2000）
-- - 字典配置（/system/dict）页面与后端接口齐全，但历史上从未有菜单种子，
--   本次新建菜单行并授权给超级管理员（role_id=1）
--
-- 幂等：菜单行先删后插；授权 INSERT IGNORE；文档中心用 UPDATE 幂等改父级。
-- 前端零改动：菜单由 /auth/routers 下发，regroupMenus 自动合并。
-- ============================================================

-- 1. 文档中心：从顶层（parent_id=0）改挂到系统管理（2000）
UPDATE sys_menu SET parent_id = 2000, sort = 4
WHERE path = '/doc' AND parent_id <> 2000;

-- 2. 字典配置：新建菜单行（页面 src/views/system/dict.vue，接口 /dict/*）
DELETE FROM sys_menu WHERE path = '/system/dict';
INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (2050, 2000, '字典配置', '/system/dict', 'system/dict', 'system:dict:list', 2, 5, 'el-icon-collection', 1, 0);

-- 3. 超级管理员授权（文档中心授权行原已存在，字典配置新建）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3000), (1, 2050);
