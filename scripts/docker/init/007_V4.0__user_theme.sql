-- ============================================================
-- V4.0 主题皮肤：用户偏好字段 + 后台主题设置菜单
--
-- 主题 ID 形如 `blue-light-rounded`，由「色相-明暗-形态」三段组成
-- （24 × 2 × 5 = 240 套）。存字符串而不是三个字段：
-- 1. 前端解析一次即可，不用三处拼装；
-- 2. 以后新增维度（如密度）时后缀追加，老数据仍可解析；
-- 3. 主题体系升级要整体换值时，改一列就行。
--
-- 幂等：字段存在则跳过；菜单先删同路径再插。
-- ============================================================

-- 1. sys_user 增加界面主题偏好
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'theme_id'
);
SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN theme_id VARCHAR(64) DEFAULT NULL COMMENT ''界面主题ID（色相-明暗-形态）'' AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 菜单：主题设置挂在「系统管理」下（2000）
DELETE FROM sys_menu WHERE path = '/system/theme';

INSERT INTO sys_menu (id, parent_id, name, path, component, perms, icon, type, sort, status, deleted)
VALUES (2040, 2000, '主题设置', '/system/theme', 'system/theme/index', 'system:theme:list', 'el-icon-brush', 2, 0, 1, 0);

-- 超级管理员角色默认可见
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 2040);
