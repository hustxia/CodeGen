-- ============================================================
-- V5.1 一句话建系统菜单（修复：此前漏建，仅手工/运行时存在，重放迁移或重启后丢失）
-- 背景：V5.0 只建了 gen_pipeline 两张表，未建对应平台菜单；
--       代码生成模块（1000）下的子菜单由迁移统一维护（1060 系统管理 / 1061 文案翻译 / 1070 一句话建系统）。
-- 幂等：先按 path 删除再插入（同 V2.1/V3.1 写法）；角色授权用 INSERT IGNORE 防重。
-- 顺序：排在 V5.0 之后、V5.5 之前，符合 docs/sql 字典序。
-- ============================================================

DELETE FROM sys_menu WHERE path = '/gen/pipeline';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (1070, 1000, '一句话建系统', '/gen/pipeline', 'gen/pipeline/index', 'gen:pipeline:view', 2, 9, 'el-icon-magic-stick', 1, 0);

-- 超级管理员角色默认可见
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1070);
