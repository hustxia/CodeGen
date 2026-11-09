package __PKG__.common.pay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝电脑网站支付（alipay-sdk-java，RSA2）。
 *
 * <p>凭据全部环境变量占位符（PAY_ALI_*），未配置时本 bean 不会被装配
 * （pay.channel=mock 缺省）——代码编译期可用但运行时零激活。
 */
@Service
@ConditionalOnProperty(name = "pay.channel", havingValue = "alipay")
public class AliPayPageService extends AbstractPayService {

    private static final String GATEWAY = "https://openapi.alipay.com/gateway";

    private volatile AlipayClient client;

    public AliPayPageService(PayOrderMapper orderMapper, JdbcTemplate jdbcTemplate, PayProps props) {
        super(orderMapper, jdbcTemplate, props);
    }

    @Override
    protected String channel() {
        return "alipay";
    }

    @Override
    protected void channelCreate(PayOrder order) {
        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        req.setNotifyUrl(props.getAliNotifyUrl());
        // bizContent 金额单位是元（字符串），这里以本地单的分换算，保持单一精度来源
        req.setBizContent("{\"out_trade_no\":\"" + order.getOutTradeNo() + "\""
                + ",\"total_amount\":\"" + fenToYuan(order.getAmount()) + "\""
                + ",\"subject\":\"" + order.getSubject().replace("\"", "'") + "\""
                + ",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
        // pageExecute 返回自动提交表单 HTML：前端落空文档即跳支付宝（收银台 payForm 字段）
        try {
            AlipayTradePagePayResponse resp = client().pageExecute(req);
            order.setPayForm(resp.getBody());
        } catch (com.alipay.api.AlipayApiException e) {
            throw new RuntimeException("支付宝下单失败：" + e.getMessage(), e);
        }
    }

    @Override
    protected boolean channelQueryPaid(PayOrder order) {
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        req.setBizContent("{\"out_trade_no\":\"" + order.getOutTradeNo() + "\"}");
        try {
            AlipayTradeQueryResponse resp = client().execute(req);
            // 单不存在等场景 alipay 返回错误码而非异常，按未支付处理
            return resp != null && resp.isSuccess()
                    && ("TRADE_SUCCESS".equals(resp.getTradeStatus()) || "TRADE_FINISHED".equals(resp.getTradeStatus()));
        } catch (com.alipay.api.AlipayApiException e) {
            throw new __PKG__.common.exception.BusinessException(500, "支付宝查单失败：" + e.getMessage());
        }
    }

    @Override
    protected void channelClose(PayOrder order) {
        AlipayTradeCloseRequest req = new AlipayTradeCloseRequest();
        req.setBizContent("{\"out_trade_no\":\"" + order.getOutTradeNo() + "\"}");
        try {
            AlipayTradeCloseResponse resp = client().execute(req);
            if (resp != null && !resp.isSuccess()) {
                // 已支付/单不存在的关单失败不阻断本地置关，查单链路会收敛最终状态
                org.slf4j.LoggerFactory.getLogger(AliPayPageService.class)
                        .warn("支付宝关单未成功（不阻断）：{} {}", order.getOutTradeNo(), resp.getSubMsg());
            }
        } catch (com.alipay.api.AlipayApiException e) {
            throw new __PKG__.common.exception.BusinessException(500, "支付宝关单失败：" + e.getMessage());
        }
    }

    /**
     * 异步通知处理：RSA2 验签 → 状态确认 → 金额二次校验 → markPaid。
     * 参数表单编码，notify_url 收到的 query/form 参数原样传入。
     */
    public void handleNotify(Map<String, String> params) {
        boolean signOk;
        try {
            signOk = AlipaySignature.rsaCheckV1(params, props.getAliPublicKey(), "UTF-8", "RSA2");
        } catch (com.alipay.api.AlipayApiException e) {
            throw new __PKG__.common.exception.BusinessException(401, "支付宝回调验签异常");
        }
        if (!signOk) {
            throw new __PKG__.common.exception.BusinessException(401, "支付宝回调验签失败");
        }
        String status = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) {
            return;
        }
        PayOrder order = mustGet(params.get("out_trade_no"));
        markPaid(order, yuanToFen(params.get("total_amount")), params.toString());
    }

    private synchronized AlipayClient client() {
        if (client == null) {
            client = new DefaultAlipayClient(GATEWAY, props.getAliAppId(), props.getAliPrivateKey(),
                    "json", "UTF-8", props.getAliPublicKey(), "RSA2");
        }
        return client;
    }

    private long yuanToFen(String yuan) {
        return new BigDecimal(yuan).movePointRight(2).longValueExact();
    }
}
