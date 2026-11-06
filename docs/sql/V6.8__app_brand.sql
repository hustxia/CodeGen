-- V6.8 品牌参数化：sys_project 增加应用显示名 / Logo / 主色
-- 三列全部可空：空值回落「项目名 / 内置 logo / #e1251b」，存量项目零 diff
-- 已在 gen_platform 与 gen_platform_test 执行（2026-10-28）

ALTER TABLE `sys_project`
  ADD COLUMN `app_name` varchar(64) NULL COMMENT '应用显示名(空=项目名)' AFTER `test_enabled`,
  ADD COLUMN `app_logo` varchar(255) NULL COMMENT 'Logo图片URL(空=内置logo)' AFTER `app_name`,
  ADD COLUMN `app_theme_color` varchar(16) NULL COMMENT '主题主色(空=#e1251b)' AFTER `app_logo`;
