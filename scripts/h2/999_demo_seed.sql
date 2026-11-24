-- ============================================================
-- H2 demo 模式最小种子（演示字典，非登录所需）
--
-- 登录种子（admin/admin123、角色、菜单）已由 V1.0__init.sql 提供；
-- 平台模板由 GenTemplateSeeder 启动时从 classpath:gen-templates 落库；
-- 默认系统由 ProjectBootstrap 启动引导创建。
-- 这里只补一批演示字典，方便在 H2 环境里体验字典联动功能。
-- 固定 id 与转换脚本生成的 RESTART WITH 序列不冲突（sys_dict_type 从 8 起）。
-- ============================================================

INSERT INTO sys_dict_type (id, dict_code, dict_name, status, deleted, created_at, updated_at) VALUES
(1, 'demo_gender',   '演示-性别',   1, 0, NOW(), NOW()),
(2, 'demo_priority', '演示-优先级', 1, 0, NOW(), NOW());

INSERT INTO sys_dict_data (id, dict_code, dict_label, dict_value, sort_no, status, created_at, updated_at) VALUES
(1, 'demo_gender',   '男',   'M', 1, 1, NOW(), NOW()),
(2, 'demo_gender',   '女',   'F', 2, 1, NOW(), NOW()),
(3, 'demo_priority', '高',   'high',   1, 1, NOW(), NOW()),
(4, 'demo_priority', '中',   'medium', 2, 1, NOW(), NOW()),
(5, 'demo_priority', '低',   'low',    3, 1, NOW(), NOW());
