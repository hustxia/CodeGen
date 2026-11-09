-- ---------- V7.4 空库可部署：sys_oper_log 补齐 BaseEntity 的 4 个审计列 ----------
-- 背景：V2.0__quick_build.sql 建的 sys_oper_log 只有 15 列，没有 create_time /
--       update_time / create_by / update_by；但 SysOperLog 继承 BaseEntity，
--       @TableField(fill=...) + @TableLogic 会在写日志时带上这 4 列。
--       全新部署必报 Unknown column，且被 LogAspect catch 吞成「审计表恒空」。
--       坑库 mysql.md:181 / :306 是同款：开发库手工 ALTER 过，换环境才炸。
-- 幂等写法：字段已存在则 SELECT 1，可重复执行（照 V4.3__oper_log_region.sql）。
-- 注意：V2.0__quick_build.sql 已同步补列，新库靠它；本脚本只负责存量库。

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_oper_log ADD COLUMN create_time datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'create_time');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_oper_log ADD COLUMN update_time datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'update_time');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_oper_log ADD COLUMN create_by bigint NULL COMMENT ''创建人''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'create_by');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_oper_log ADD COLUMN update_by bigint NULL COMMENT ''更新人''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'update_by');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
