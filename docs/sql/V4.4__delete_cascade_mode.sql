-- ============================================================
-- V4.4 一主多子与级联删除：gen_table / sys_project 各加 1 列
--
-- 背景：级联删除三档模式（0=阻止 1=级联 2=不处理），
--       默认 1（级联）为 2026-10-23 用户决定。
-- 回落：表级 → sys_project.default_delete_cascade_mode → 硬编码 1，
--       独立实现 support/GenCascadeMode，不进 GenSwitchDefaults 17 键。
--
-- 幂等：加列前查 information_schema，重复执行不报错。
-- ============================================================

-- gen_table.delete_cascade_mode
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gen_table'
    AND COLUMN_NAME = 'delete_cascade_mode'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE gen_table ADD COLUMN delete_cascade_mode TINYINT NOT NULL DEFAULT 1 COMMENT ''删除级联模式：0=阻止(有子行拒删) 1=级联(同删全部子表) 2=不处理(现状)'' AFTER last_gen_by',
  'SELECT ''gen_table.delete_cascade_mode 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_project.default_delete_cascade_mode
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_project'
    AND COLUMN_NAME = 'default_delete_cascade_mode'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_project ADD COLUMN default_delete_cascade_mode TINYINT NULL COMMENT ''系统级默认删除级联模式，空=1(级联)''',
  'SELECT ''sys_project.default_delete_cascade_mode 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧 serviceImpl 种子行停用（V4.4 模板分道后由三个新模板接替；仅停用系统种子行，用户自定义行不动）
UPDATE gen_template
SET status = 0,
    remark = '全局种子模板（V4.4 分道后停用，由 serviceImplSimple/Tree/Masterdetail 接替）'
WHERE tpl_code = 'serviceImpl' AND table_id = 0 AND status = 1
  AND remark LIKE '全局种子模板%';

-- 验证
SELECT COUNT(*) AS gen_table_total,
       SUM(delete_cascade_mode = 1) AS mode1_count
FROM gen_table;
