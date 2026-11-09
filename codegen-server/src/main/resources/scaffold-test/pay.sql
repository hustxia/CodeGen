-- V6.3 支付能力包：pay_order（H2 测试版，与 db/pay.sql 同源）
-- PayReconcileJob 启动 5 分钟后才首次对账，测试上下文一般跑不到；
-- 这里建表兜底，避免长测试/手工触发时 H2 缺表 500
CREATE TABLE IF NOT EXISTS pay_order (
    id             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    out_trade_no   varchar(64)  NOT NULL COMMENT '商户单号（幂等键）',
    subject        varchar(128) NOT NULL COMMENT '订单标题',
    amount         bigint       NOT NULL COMMENT '金额：分',
    pay_channel    varchar(16)  DEFAULT NULL COMMENT '渠道：mock/wxpay/alipay',
    pay_status     tinyint      NOT NULL DEFAULT 0 COMMENT '0待支付 1成功 2关闭 3退款',
    qr_content     varchar(255) DEFAULT NULL COMMENT '二维码内容',
    pay_url        varchar(512) DEFAULT NULL COMMENT '跳转支付地址',
    pay_form       text         COMMENT '渠道返回的自动提交表单 HTML',
    business_type  varchar(64)  DEFAULT NULL COMMENT '业务表名',
    business_id    bigint       DEFAULT NULL COMMENT '业务主键',
    notify_raw     text         COMMENT '回调原始报文（审计）',
    paid_at        datetime     DEFAULT NULL COMMENT '支付成功时间',
    deleted        tinyint      NOT NULL DEFAULT 0,
    create_by      bigint       DEFAULT NULL,
    create_time    datetime     DEFAULT NULL,
    update_by      bigint       DEFAULT NULL,
    update_time    datetime     DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_out_trade_no UNIQUE (out_trade_no)
);
