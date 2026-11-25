package __PKG__.common.pay;

/**
 * 支付渠道抽象：业务代码只面对本接口（下单/查单/关单/退款），
 * 渠道实现由 yml pay.channel 单选装配（mock/wxpay/alipay），可插拔零代码切换。
 */
public interface PaymentService {

    /**
     * 创建支付单：落库 → 渠道下单 → 回填支付载体（二维码内容/跳转地址/表单）。
     *
     * @param subject      订单标题
     * @param amountFen    金额：分（入口统一转分，渠道侧不做浮点运算）
     * @param businessType 业务表名（联动 pay_status，白名单校验后使用，可空）
     * @param businessId   业务主键（可空）
     */
    PayOrderVO create(String subject, long amountFen, String businessType, Long businessId);

    /**
     * V6.3 补全 表级绑定入口：payCode 为 gen_table.pay_code 业务标识（可空），
     * 随支付单落 pay_order.pay_code，供按业务码对账/检索。其余语义同上。
     */
    PayOrderVO create(String subject, long amountFen, String businessType, Long businessId, String payCode);

    /** 查单：本地待支付时先主动查渠道（回调不可达的补偿手段），再返回本地状态 */
    PayOrderVO query(String outTradeNo);

    /** 关单：仅待支付订单可关（渠道侧关单 + 本地置已关闭） */
    void close(String outTradeNo);

    /**
     * V6.3 补全 退款：仅支付成功（pay_status=1）订单可退，支持部分退款（累计不超过支付金额）。
     *
     * @param outTradeNo 原支付单商户单号
     * @param refundFen  本次退款金额：分（>0，且 ≤ 可退余额）
     * @param refundNo   商户退款单号：可空（缺省生成）；传同值重放幂等，不重复调渠道
     * @param reason     退款原因（审计，可空）
     * @return 退款后的最新支付单状态（退满时 pay_status=3）
     */
    PayOrderVO refund(String outTradeNo, long refundFen, String refundNo, String reason);
}
