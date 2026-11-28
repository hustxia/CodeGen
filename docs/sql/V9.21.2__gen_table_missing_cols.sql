-- V9.21.2 环境补齐：gen_table / gen_converge_item 缺失列
-- 背景：xyh-devbranch 分支实体领先于本地库结构时用于补齐；CI 全新库跑全量脚本时
--       V9.17/V9.18/V9.21 已建过这些列，裸 ADD COLUMN 会报 1060 Duplicate column
--       （2026-11-28 GitHub verify 门禁红：初始化测试库失败），故改为条件 DDL。
-- 幂等： MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，统一用 information_schema + PREPARE。

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'pay_code');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN pay_code varchar(64) DEFAULT NULL COMMENT ''表级支付绑定:非空=该表接入支付能力包''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'child_save_mode');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN child_save_mode tinyint NOT NULL DEFAULT 0 COMMENT ''子表保存模式:0=全量替换(默认) 1=按主键三路合并''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'print_title');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN print_title varchar(100) DEFAULT NULL COMMENT ''打印页标题:NULL/空=回落表注释''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'print_landscape');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN print_landscape tinyint DEFAULT NULL COMMENT ''横版打印:NULL/0=A4纵向 1=A4横向''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'print_barcode');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN print_barcode varchar(64) DEFAULT NULL COMMENT ''打印条码字段名:NULL/空=不渲染''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'print_qr');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN print_qr varchar(64) DEFAULT NULL COMMENT ''打印二维码字段名:NULL/空=不渲染''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- S8 实测补漏：gen_converge_item 漏建审计列（实体继承 BaseEntity，全列查询报 Unknown column 'create_by'）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_converge_item' AND COLUMN_NAME = 'create_by');
SET @s := IF(@c = 0, 'ALTER TABLE gen_converge_item ADD COLUMN create_by bigint DEFAULT NULL COMMENT ''创建人''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_converge_item' AND COLUMN_NAME = 'update_by');
SET @s := IF(@c = 0, 'ALTER TABLE gen_converge_item ADD COLUMN update_by bigint DEFAULT NULL COMMENT ''更新人''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
