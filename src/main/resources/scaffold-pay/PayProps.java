package __PKG__.common.pay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付配置：密钥全部环境变量占位符（application.yml 里零明文），
 * 商户私钥/ APIv3 密钥泄露 = 资金风险，严禁提交仓库。
 */
@Component
public class PayProps {

    @Value("${pay.channel:mock}")
    private String channel;

    @Value("${pay.wxpay.app-id:}")
    private String wxAppId;
    @Value("${pay.wxpay.mch-id:}")
    private String wxMchId;
    @Value("${pay.wxpay.merchant-serial-no:}")
    private String wxSerialNo;
    @Value("${pay.wxpay.api-v3-key:}")
    private String wxApiV3Key;
    @Value("${pay.wxpay.private-key-path:}")
    private String wxPrivateKeyPath;
    @Value("${pay.wxpay.notify-url:}")
    private String wxNotifyUrl;

    @Value("${pay.alipay.app-id:}")
    private String aliAppId;
    @Value("${pay.alipay.private-key:}")
    private String aliPrivateKey;
    @Value("${pay.alipay.alipay-public-key:}")
    private String aliPublicKey;
    @Value("${pay.alipay.notify-url:}")
    private String aliNotifyUrl;

    public String getChannel() { return channel == null || channel.isBlank() ? "mock" : channel; }
    public String getWxAppId() { return wxAppId; }
    public String getWxMchId() { return wxMchId; }
    public String getWxSerialNo() { return wxSerialNo; }
    public String getWxApiV3Key() { return wxApiV3Key; }
    public String getWxPrivateKeyPath() { return wxPrivateKeyPath; }
    public String getWxNotifyUrl() { return wxNotifyUrl; }
    public String getAliAppId() { return aliAppId; }
    public String getAliPrivateKey() { return aliPrivateKey; }
    public String getAliPublicKey() { return aliPublicKey; }
    public String getAliNotifyUrl() { return aliNotifyUrl; }
}
