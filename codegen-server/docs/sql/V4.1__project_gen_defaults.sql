-- ============================================================
-- V4.1 系统级生成开关默认值
--
-- 背景：17 个生成开关（gen_entity ... gen_delete_btn）此前只能是
-- 「每张表一行一个值」，新系统如果整批不需要操作日志 / 不需要前端，
-- 只能逐表取消勾选。这里给 sys_project 补一份默认值，
-- 「从数据库导入」按它填表，表配置弹窗也能一键恢复。
--
-- 默认值列允许 NULL，NULL 表示「未配置」，回落硬编码 1。
-- 不用 NOT NULL DEFAULT 1：那样无法区分「系统显式要求关闭」和「没配过」，
-- 以后想改硬编码默认值时会分不清哪些行是人为设置。
--
-- 幂等：MySQL 8 的 ADD COLUMN IF NOT EXISTS 在部分分支不可用，
-- 统一用 information_schema + PREPARE 条件执行。
-- ============================================================

DROP PROCEDURE IF EXISTS `sp_v41_add_project_gen_defaults`;

DELIMITER $$
CREATE PROCEDURE `sp_v41_add_project_gen_defaults`()
BEGIN
    DECLARE col_name VARCHAR(64);
    DECLARE done INT DEFAULT 0;

    DECLARE cur CURSOR FOR
        SELECT c.name FROM (
            SELECT 'default_gen_entity' AS name UNION ALL
            SELECT 'default_gen_mapper' UNION ALL
            SELECT 'default_gen_service' UNION ALL
            SELECT 'default_gen_log' UNION ALL
            SELECT 'default_gen_controller' UNION ALL
            SELECT 'default_gen_page_api' UNION ALL
            SELECT 'default_gen_detail_api' UNION ALL
            SELECT 'default_gen_add_api' UNION ALL
            SELECT 'default_gen_edit_api' UNION ALL
            SELECT 'default_gen_delete_api' UNION ALL
            SELECT 'default_gen_frontend' UNION ALL
            SELECT 'default_gen_api_js' UNION ALL
            SELECT 'default_gen_vue_page' UNION ALL
            SELECT 'default_gen_query_form' UNION ALL
            SELECT 'default_gen_add_btn' UNION ALL
            SELECT 'default_gen_edit_btn' UNION ALL
            SELECT 'default_gen_delete_btn'
        ) c
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'sys_project'
              AND column_name = c.name
        );

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    add_loop: LOOP
        FETCH cur INTO col_name;
        IF done = 1 THEN
            LEAVE add_loop;
        END IF;
        -- 列名来自上面的白名单常量，不拼接外部输入
        SET @ddl = CONCAT('ALTER TABLE sys_project ADD COLUMN `', col_name,
                          '` TINYINT DEFAULT NULL COMMENT ''生成开关默认值：NULL 表示未配置，回落 1''');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL `sp_v41_add_project_gen_defaults`();
DROP PROCEDURE IF EXISTS `sp_v41_add_project_gen_defaults`;

-- 默认系统按「全开」显式落一次值，让管理页能看到基线，而不是满屏留空
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
