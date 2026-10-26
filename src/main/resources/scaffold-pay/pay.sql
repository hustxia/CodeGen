-- V6.3 支付能力包：支付单表（幂等键 out_trade_no 唯一；金额一律「分」，杜绝浮点资损）
CREATE TABLE IF NOT EXISTS pay_order (
    id             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    out_trade_no   varchar(64)  NOT NULL COMMENT '商户单号（幂等键）',
    subject        varchar(128) NOT NULL COMMENT '订单标题',
    amount         bigint       NOT NULL COMMENT '金额：分',
    pay_channel    varchar(16)  DEFAULT NULL COMMENT '渠道：mock/wxpay/alipay',
    pay_status     tinyint      NOT NULL DEFAULT 0 COMMENT '0待支付 1成功 2关闭 3退款',
    qr_content     varchar(255) DEFAULT NULL COMMENT '二维码内容（wxpay code_url，前端出码）',
    pay_url        varchar(512) DEFAULT NULL COMMENT '跳转支付地址（alipay 等）',
    pay_form       mediumtext   COMMENT '渠道返回的自动提交表单 HTML（alipay pageExecute）',
    business_type  varchar(64)  DEFAULT NULL COMMENT '业务表名（支付成功联动其 pay_status）',
    business_id    bigint       DEFAULT NULL COMMENT '业务主键',
    notify_raw     mediumtext   COMMENT '回调原始报文（审计）',
    paid_at        datetime     DEFAULT NULL COMMENT '支付成功时间',
    deleted        tinyint      NOT NULL DEFAULT 0,
    create_by      bigint       DEFAULT NULL,
    create_time    datetime     DEFAULT NULL,
    update_by      bigint       DEFAULT NULL,
    update_time    datetime     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';
