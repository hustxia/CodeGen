-- P3-3 审批流：表配置绑定流程编码
-- 为空表示该表不启用审批；填了就必须在 sys_flow_def 里存在同名 flow_code
--
-- 幂等：MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，直接用 ALTER 二次执行会报
-- 1060 Duplicate column。这里查 information_schema，只有列不存在时才执行 DDL。
-- 用预处理语句而不是存储过程：DROP PROCEDURE 需要额外权限，脚本可以重复跑更重要。
SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE gen_table ADD COLUMN flow_code varchar(64) DEFAULT NULL COMMENT ''P3-3 绑定的审批流程编码，空为不启用''',
        'SELECT ''flow_code 已存在，跳过'' AS msg'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_table'
      AND COLUMN_NAME = 'flow_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
