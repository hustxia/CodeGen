-- H2 MODE=MySQL 版本（demo 模式用），由 convert_sql_to_h2.py 自动生成
-- 原始脚本: docs/sql/V4.1__project_gen_defaults.sql
-- 转换记录:
-- [存储过程] 按 17 列白名单展开 ADD COLUMN IF NOT EXISTS -> sys_project

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_entity` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_entity` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_mapper` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_mapper` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_service` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_service` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_log` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_log` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_controller` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_controller` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_page_api` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_page_api` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_detail_api` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_detail_api` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_add_api` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_add_api` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_edit_api` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_edit_api` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_delete_api` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_delete_api` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_frontend` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_frontend` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_api_js` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_api_js` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_vue_page` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_vue_page` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_query_form` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_query_form` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_add_btn` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_add_btn` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_edit_btn` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_edit_btn` IS '生成开关默认值：NULL 表示未配置，回落 1';

ALTER TABLE `sys_project` ADD COLUMN IF NOT EXISTS `default_gen_delete_btn` TINYINT DEFAULT NULL;

COMMENT ON COLUMN `sys_project`.`default_gen_delete_btn` IS '生成开关默认值：NULL 表示未配置，回落 1';

UPDATE sys_project
SET default_gen_entity     = IFNULL(default_gen_entity, 1),
    default_gen_mapper     = IFNULL(default_gen_mapper, 1),
    default_gen_service    = IFNULL(default_gen_service, 1),
    default_gen_log        = IFNULL(default_gen_log, 1),
    default_gen_controller = IFNULL(default_gen_controller, 1),
    default_gen_page_api   = IFNULL(default_gen_page_api, 1),
    default_gen_detail_api = IFNULL(default_gen_detail_api, 1),
    default_gen_add_api    = IFNULL(default_gen_add_api, 1),
    default_gen_edit_api   = IFNULL(default_gen_edit_api, 1),
    default_gen_delete_api = IFNULL(default_gen_delete_api, 1),
    default_gen_frontend   = IFNULL(default_gen_frontend, 1),
    default_gen_api_js     = IFNULL(default_gen_api_js, 1),
    default_gen_vue_page   = IFNULL(default_gen_vue_page, 1),
    default_gen_query_form = IFNULL(default_gen_query_form, 1),
    default_gen_add_btn    = IFNULL(default_gen_add_btn, 1),
    default_gen_edit_btn   = IFNULL(default_gen_edit_btn, 1),
    default_gen_delete_btn = IFNULL(default_gen_delete_btn, 1)
WHERE is_default = 1 AND deleted = 0;
