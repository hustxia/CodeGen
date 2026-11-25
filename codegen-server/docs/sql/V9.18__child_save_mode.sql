-- ============================================================
-- V9.18 增量保存：gen_table 新增 child_save_mode（主子保存策略）
--
-- 背景：V4.4 backlog P1-1「子表按主键三路合并替代全量替换」。
--       现存行为是全量替换（先按外键 delete 再逐行 insert），会丢主键、
--       触发审计字段重置；本列让主表可显式切到「增量合并」。
--
-- 取值：0=全量替换（默认，存量逐字节零 diff）
--       1=按主键三路合并（新增 insert / 变更 update / 缺失 delete）
--       另：一对一子表按外键 upsert、多对多中间表按关联键差集增删，
--       都由 1 开启（0 分支绝不产生新代码，这是产物零 diff 红线）。
--
-- 幂等：加列前查 information_schema，重复执行不报错（V6.3/V9.17 同款写法）。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'child_save_mode');
SET @s := IF(@c = 0,
    'ALTER TABLE gen_table ADD COLUMN child_save_mode tinyint NOT NULL DEFAULT 0 COMMENT ''子表保存模式:0=全量替换(默认) 1=按主键三路合并(含一对一 upsert/多对多差集)'' AFTER delete_cascade_mode',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 验证
SELECT COUNT(*) AS gen_table_total,
       SUM(child_save_mode = 1) AS child_save_mode_1
FROM gen_table;
