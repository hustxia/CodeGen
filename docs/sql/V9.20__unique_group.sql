-- V9.20 多组独立唯一键：gen_table_column.unique_group
-- 与 unique_flag=1 配合使用：0/空=默认组（存量行为不变：所有 unique_flag=1 列组成一组联合唯一）
-- 1..N = 独立唯一组；同组列联合唯一，不同组互不干扰
-- 幂等：重复执行无副作用
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_table_column'
      AND COLUMN_NAME = 'unique_group'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE gen_table_column ADD COLUMN unique_group tinyint NOT NULL DEFAULT 0 COMMENT ''唯一组编号:0=默认组 1..N=独立唯一组'' AFTER unique_flag',
    'SELECT ''column unique_group already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
