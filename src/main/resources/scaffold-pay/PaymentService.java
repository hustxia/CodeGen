package __PKG__.common.pay;

/**
 * 支付渠道抽象：业务代码只面对本接口（下单/查单/关单），
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

    /** 查单：本地待支付时先主动查渠道（回调不可达的补偿手段），再返回本地状态 */
    PayOrderVO query(String outTradeNo);

    /** 关单：仅待支付订单可关（渠道侧关单 + 本地置已关闭） */
    void close(String outTradeNo);
}
