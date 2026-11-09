package __PKG__.common.pay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * mock 渠道：本地假支付，解决「无商户资质/无公网回调」死锁。
 *
 * <p>模拟支付（{@link #mockPay}）与真渠道回调走同一条 markPaid 状态流转链路——
 * mock 跑通的全流程对真渠道测试等价（V6.3 定案）。
 */
@Service
@ConditionalOnProperty(name = "pay.channel", havingValue = "mock", matchIfMissing = true)
public class MockPayService extends AbstractPayService {

    public MockPayService(PayOrderMapper orderMapper, JdbcTemplate jdbcTemplate, PayProps props) {
        super(orderMapper, jdbcTemplate, props);
    }

    @Override
    protected String channel() {
        return "mock";
    }

    @Override
    protected void channelCreate(PayOrder order) {
        // mock 无外部渠道：收银台凭 outTradeNo 调 /pay/mock/pay/{outTradeNo} 模拟支付成功
    }

    @Override
    protected boolean channelQueryPaid(PayOrder order) {
        // mock 状态本地即真相，无需外查
        return false;
    }

    @Override
    protected void channelClose(PayOrder order) {
        // 无渠道侧动作
    }

    /**
     * 模拟渠道回调：读单 → 金额校验 → markPaid（与真渠道回调同构）。
     *
     * @return true=本次支付生效；false=此前已处理（幂等重放）
     */
    public boolean mockPay(String outTradeNo) {
        PayOrder order = mustGet(outTradeNo);
        if (order.getPayStatus() != PayOrder.STATUS_PENDING) {
            return false;
        }
        return markPaid(order, order.getAmount(), "{\"mock\":true}");
    }
}
