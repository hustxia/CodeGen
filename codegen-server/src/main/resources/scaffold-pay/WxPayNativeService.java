package __PKG__.common.pay;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 微信 Native 扫码支付（wechatpay-java，APIv3）。
 *
 * <p>凭据全部环境变量占位符（PAY_WX_*），未配置时本 bean 不会被装配
 * （pay.channel=mock 缺省）——代码编译期可用但运行时零激活。
 * 证书/私钥由 RSAAutoCertificateConfig 自动管理（平台证书自动更新）。
 */
@Service
@ConditionalOnProperty(name = "pay.channel", havingValue = "wxpay")
public class WxPayNativeService extends AbstractPayService {

    private volatile RSAAutoCertificateConfig config;
    private volatile NativePayService nativePayService;

    public WxPayNativeService(PayOrderMapper orderMapper, JdbcTemplate jdbcTemplate, PayProps props) {
        super(orderMapper, jdbcTemplate, props);
    }

    @Override
    protected String channel() {
        return "wxpay";
    }

    @Override
    protected void channelCreate(PayOrder order) {
        PrepayRequest req = new PrepayRequest();
        req.setAppid(props.getWxAppId());
        req.setMchid(props.getWxMchId());
        req.setDescription(order.getSubject());
        req.setOutTradeNo(order.getOutTradeNo());
        req.setNotifyUrl(props.getWxNotifyUrl());
        Amount amount = new Amount();
        amount.setTotal(order.getAmount().intValue());
        req.setAmount(amount);
        // Native 下单返回 code_url，前端 qrcode 出码（后端零图像依赖）
        order.setQrContent(nativePayService().prepay(req).getCodeUrl());
    }

    @Override
    protected boolean channelQueryPaid(PayOrder order) {
        QueryOrderByOutTradeNoRequest req = new QueryOrderByOutTradeNoRequest();
        req.setMchid(props.getWxMchId());
        req.setOutTradeNo(order.getOutTradeNo());
        Transaction t = nativePayService().queryOrderByOutTradeNo(req);
        return t.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
    }

    @Override
    protected void channelClose(PayOrder order) {
        CloseOrderRequest req = new CloseOrderRequest();
        req.setMchid(props.getWxMchId());
        req.setOutTradeNo(order.getOutTradeNo());
        nativePayService().closeOrder(req);
    }

    /**
     * 异步通知处理：平台证书验签（NotificationParser）→ 金额二次校验 → markPaid。
     * 验签失败抛异常 → 控制层返回非 SUCCESS，微信按重试机制重发。
     * 注：wechatpay-java 0.2.x 的 RequestParam 无 serialNo 字段（证书自动更新模式不需要）。
     */
    public void handleNotify(String body, String nonce, String timestamp, String signature) {
        RequestParam param = new RequestParam.Builder()
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
        Transaction t = new NotificationParser(config()).parse(param, Transaction.class);
        if (t.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            return; // 未支付成功（如已关闭）不流转状态
        }
        PayOrder order = mustGet(t.getOutTradeNo());
        markPaid(order, t.getAmount().getTotal(), body);
    }

    private synchronized RSAAutoCertificateConfig config() {
        if (config == null) {
            config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(props.getWxMchId())
                    .privateKeyFromPath(props.getWxPrivateKeyPath())
                    .merchantSerialNumber(props.getWxSerialNo())
                    .apiV3Key(props.getWxApiV3Key())
                    .build();
        }
        return config;
    }

    private NativePayService nativePayService() {
        if (nativePayService == null) {
            nativePayService = new NativePayService.Builder().config(config()).build();
        }
        return nativePayService;
    }
}
