-- V9.21.2 环境补齐：gen_table 缺失列（xyh-devbranch 分支实体 V6.3/V4.5/V9.20 领先于本地库结构）
-- 注意：MySQL 8 ADD COLUMN 不支持 IF NOT EXISTS，重复执行会报 Duplicate column（无害，跳过即可）
ALTER TABLE gen_table ADD COLUMN pay_code        varchar(64)  DEFAULT NULL COMMENT '表级支付绑定:非空=该表接入支付能力包';
ALTER TABLE gen_table ADD COLUMN child_save_mode tinyint      NOT NULL DEFAULT 0 COMMENT '子表保存模式:0=全量替换(默认) 1=按主键三路合并';
ALTER TABLE gen_table ADD COLUMN print_title     varchar(100) DEFAULT NULL COMMENT '打印页标题:NULL/空=回落表注释';
ALTER TABLE gen_table ADD COLUMN print_landscape tinyint      DEFAULT NULL COMMENT '横版打印:NULL/0=A4纵向 1=A4横向';
ALTER TABLE gen_table ADD COLUMN print_barcode   varchar(64)  DEFAULT NULL COMMENT '打印条码字段名:NULL/空=不渲染';
ALTER TABLE gen_table ADD COLUMN print_qr        varchar(64)  DEFAULT NULL COMMENT '打印二维码字段名:NULL/空=不渲染';

-- S8 实测补漏：gen_converge_item 漏建审计列（实体继承 BaseEntity，全列查询报 Unknown column 'create_by'）
ALTER TABLE gen_converge_item ADD COLUMN create_by bigint DEFAULT NULL COMMENT '创建人';
ALTER TABLE gen_converge_item ADD COLUMN update_by bigint DEFAULT NULL COMMENT '更新人';
