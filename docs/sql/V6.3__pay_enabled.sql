-- V6.3 支付能力包：系统级开关（可空三态，V4.4 坑库教训：NOT NULL DEFAULT 会杀死回落层）
-- 回落唯一实现：GenTemplateModel.resolvePay（项目级 → 硬编码 0=关）
ALTER TABLE sys_project
    ADD COLUMN pay_enabled tinyint DEFAULT NULL COMMENT '支付能力包:NULL=未配置(关) 1=生成 0=不生成' AFTER default_gen_deploy;
