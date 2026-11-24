-- V6.8 品牌参数化：sys_project 增加应用显示名 / Logo / 主色
-- 三列全部可空：空值回落「项目名 / 内置 logo / #e1251b」，存量项目零 diff
-- 幂等：逐列 information_schema 判定（2026-11-07 空库冒烟重跑实测：一条 ALTER 加三列，重跑报 1060 'app_name'）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'app_name');
SET @s := IF(@c = 0, 'ALTER TABLE sys_project ADD COLUMN app_name varchar(64) NULL COMMENT ''应用显示名(空=项目名)'' AFTER test_enabled', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'app_logo');
SET @s := IF(@c = 0, 'ALTER TABLE sys_project ADD COLUMN app_logo varchar(255) NULL COMMENT ''Logo图片URL(空=内置logo)'' AFTER app_name', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_project' AND COLUMN_NAME = 'app_theme_color');
SET @s := IF(@c = 0, 'ALTER TABLE sys_project ADD COLUMN app_theme_color varchar(16) NULL COMMENT ''主题主色(空=#e1251b)'' AFTER app_logo', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
