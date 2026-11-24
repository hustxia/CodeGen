-- V9.17 打印增强（V4.5 打印能力包三态开关不变）：gen_table 打印页标题覆盖 + 横版打印
-- print_title: NULL/空=回落表注释（printPage.ftl doc-title 默认即表注释，存量产物零 diff）
-- print_landscape: NULL/0=A4 纵向（默认） 1=A4 横向（@page size: A4 landscape + 屏幕预览 1123px 宽）
-- 幂等：重复执行无副作用。执行顺序 V9.15 → 本脚本（AFTER gen_print）

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_table'
      AND COLUMN_NAME = 'print_title'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE gen_table ADD COLUMN print_title varchar(100) DEFAULT NULL COMMENT ''打印页标题:NULL/空=回落表注释'' AFTER gen_print',
    'SELECT ''column print_title already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_table'
      AND COLUMN_NAME = 'print_landscape'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE gen_table ADD COLUMN print_landscape tinyint DEFAULT NULL COMMENT ''横版打印:NULL/0=A4纵向 1=A4横向'' AFTER print_title',
    'SELECT ''column print_landscape already exists'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table'
  AND COLUMN_NAME IN ('print_title', 'print_landscape');
