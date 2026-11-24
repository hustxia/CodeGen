-- V6.3 补全 表级支付绑定：业务表可绑定 pay_code（非空 = 接入支付能力包）
-- 生成 {Class}PayController（按业务单号发起支付/查状态/退款），pay_code 随支付单落 pay_order.pay_code
-- 回落唯一实现：GenTemplateModel.hasPayBiz（pay_code 非空 + 系统 pay_enabled=1 + gen_controller=1 + 非从表）
-- 幂等：V6.3 pay_enabled 同款条件 DDL 写法（裸 ADD COLUMN 重跑报 1060）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gen_table' AND COLUMN_NAME = 'pay_code');
SET @s := IF(@c = 0, 'ALTER TABLE gen_table ADD COLUMN pay_code varchar(64) DEFAULT NULL COMMENT ''表级支付绑定(pay_code):非空=该表接入支付能力包'' AFTER flow_code', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
