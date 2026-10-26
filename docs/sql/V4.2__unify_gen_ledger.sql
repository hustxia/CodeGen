-- ============================================================
-- V4.2 台账合一：gen_output 增列 + 统一字符集
--
-- 背景：gen_output 和 gen_history 两张表功能重叠，统一到 gen_output。
-- gen_output 增加 backup_path（备份路径）和 prev_md5（覆盖前MD5），
-- 写盘时只写 gen_output，gen_history 停止写入。
--
-- 幂等：加列前查 information_schema，字符集转换重复执行不报错。
-- ============================================================

-- 1. gen_output 增列（幂等）
DROP PROCEDURE IF EXISTS `sp_v42_add_gen_output_columns`;

DELIMITER $$
CREATE PROCEDURE `sp_v42_add_gen_output_columns`()
BEGIN
    -- backup_path 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'gen_output'
          AND column_name = 'backup_path'
    ) THEN
        ALTER TABLE gen_output 
        ADD COLUMN `backup_path` varchar(500) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL 
        COMMENT '覆盖前原文件备份路径';
    END IF;
    
    -- prev_md5 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'gen_output'
          AND column_name = 'prev_md5'
    ) THEN
        ALTER TABLE gen_output 
        ADD COLUMN `prev_md5` varchar(32) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL 
        COMMENT '覆盖前原文件MD5，供漂移判断';
    END IF;
END$$
DELIMITER ;

CALL `sp_v42_add_gen_output_columns`();
DROP PROCEDURE IF EXISTS `sp_v42_add_gen_output_columns`;

-- 2. 统一 gen_output 字符集为 utf8mb4_0900_ai_ci（与 gen_history 一致）
-- 必须先改字符集，再回填数据，否则 JOIN 时字符集不匹配
-- 注意：CONVERT TO 会转换所有字符列，重复执行不报错
ALTER TABLE gen_output 
CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 3. 从 gen_history 回填 backup_path（按 batch_no + abs_path 匹配）
-- 只回填 gen_output 中 action 为 OVERWRITTEN/MERGED 且 backup_path 为空的记录
UPDATE gen_output o
INNER JOIN gen_history h 
    ON o.batch_no = h.batch_no 
    AND o.abs_path = h.file_path
    AND o.action IN ('OVERWRITTEN', 'MERGED')
SET o.backup_path = h.backup_path
WHERE o.backup_path IS NULL 
  AND h.backup_path IS NOT NULL;

-- 4. 验证（执行后手动检查）
-- SELECT COUNT(*) FROM gen_output WHERE backup_path IS NOT NULL;
-- SHOW CREATE TABLE gen_output;
