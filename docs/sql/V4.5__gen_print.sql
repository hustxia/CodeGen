-- ============================================================
-- V4.5 A4 打印单据：gen_table / sys_project 各加 1 列
--
-- 背景：打印单据开关（1=生成 A4 打印页 print.vue），默认关（存量表零影响）。
-- 回落：表级 → sys_project.default_gen_print → 硬编码 0（关），
--       独立实现 GenTemplateModel.resolveGenPrint，不进 GenSwitchDefaults 17 键
--       （默认值是 0 而非 1，塞进 17 键要改 resolve 兜底语义）。
--
-- 幂等：加列前查 information_schema，重复执行不报错。
-- ============================================================

-- gen_table.gen_print
-- 注意：必须可空三态（NULL=跟随系统默认、1=开、0=强制关）。
-- 若设 NOT NULL DEFAULT 0：系统默认开成 1 后，表级永远是 0 短路回落，
-- 系统默认层变死配置——V4.4 已在坑库 mysql.md 记过同类翻车。
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gen_table'
    AND COLUMN_NAME = 'gen_print'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE gen_table ADD COLUMN gen_print TINYINT NULL DEFAULT NULL COMMENT ''打印单据:NULL=跟随系统默认 1=生成 0=不生成'' AFTER delete_cascade_mode',
  'SELECT ''gen_table.gen_print 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_project.default_gen_print
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_project'
    AND COLUMN_NAME = 'default_gen_print'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_project ADD COLUMN default_gen_print TINYINT NULL DEFAULT NULL COMMENT ''打印单据默认:NULL=未配置(关) 1=默认生成 0=默认不生成''',
  'SELECT ''sys_project.default_gen_print 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证：全 NULL 才是正确初始状态（默认关）
SELECT COUNT(*) AS gen_table_total,
       SUM(gen_print IS NULL) AS print_null_count,
       SUM(gen_print = 1) AS print_on_count
FROM gen_table;
