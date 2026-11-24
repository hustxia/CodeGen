-- ============================================================
-- V8.0 产物国际化：系统级 i18n 开关 + 用户译文覆盖表 + 文案翻译菜单
-- 幂等：建表 IF NOT EXISTS、INSERT ON DUPLICATE KEY、加列 information_schema 判定
-- 注意：刻意不写 AFTER <旧列>（坑库 mysql.md V6.5 事故：缺列环境里 AFTER 悬空会阻断后续脚本）
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_i18n_message (
  id bigint NOT NULL COMMENT '主键',
  project_id bigint NOT NULL COMMENT '所属系统（译文按系统隔离）',
  locale varchar(32) NOT NULL COMMENT 'BCP47 语言标签，如 en-US',
  msg_key varchar(128) NOT NULL COMMENT '文案键，如 common.add',
  msg_value text NULL COMMENT '用户译文；NULL/空串=回落到内置精译或默认语言',
  create_by bigint DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by bigint DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_i18n_msg (project_id, locale, msg_key),
  KEY idx_i18n_project_locale (project_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V8.0 用户译文覆盖表';

-- sys_project：产物国际化开关（NULL/0=关，1=开）。可空纯追加，存量系统零 diff。
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'i18n_enabled');
SET @s := IF(@c = 0, 'ALTER TABLE sys_project ADD COLUMN i18n_enabled tinyint NULL COMMENT ''产物国际化开关:NULL/0=关,1=开(V8.0)''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 文案翻译菜单：挂在「代码生成」(1000) 下，与 V3.1__project_menu.sql 同款幂等写法
DELETE FROM sys_menu WHERE path = '/gen/i18n';
INSERT INTO sys_menu (id, parent_id, name, path, component, perms, type, sort, icon, status, deleted)
VALUES (1061, 1000, '文案翻译', '/gen/i18n', 'gen/i18n/index', 'gen:i18n:list', 2, 8, 'el-icon-notebook-2', 1, 0);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1061);

-- 种子数据：4 条中文示例译文，用于验证「用户译文覆盖内置值」。不硬编码 project_id。
INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900001, p.id, 'en-US', 'common.add', 'Add'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);
INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900002, p.id, 'en-US', 'common.saveSuccess', 'Saved successfully'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);
INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900003, p.id, 'ar-SA', 'common.query', 'بحث'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);
INSERT INTO sys_i18n_message (id, project_id, locale, msg_key, msg_value)
SELECT 900004, p.id, 'ja-JP', 'common.delete', '削除'
  FROM sys_project p WHERE p.is_default = 1 AND p.deleted = 0 LIMIT 1
ON DUPLICATE KEY UPDATE msg_value = VALUES(msg_value);
