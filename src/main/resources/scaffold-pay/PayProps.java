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

    /** V9.20：微信 JSAPI 下单需要用户 openid 场景值（公众号 appid 复用 wxAppId） */
    @Value("${pay.wxpay.jsapi-openid:}")
    private String wxJsapiOpenid;

    /** V9.20：微信 H5 下单的场景信息（WAP 网站域名，微信支付要求） */
    @Value("${pay.wxpay.h5-scene-info:}")
    private String wxH5SceneInfo;

    /** V9.20：支付宝沙箱开关 —— 只影响运行时网关与配置下发，不触碰密钥 */
    @Value("${pay.alipay.sandbox:false}")
    private boolean aliSandbox;

    /** V9.20：支付宝沙箱扫码登录 URL（前端跳转用，可空） */
    @Value("${pay.alipay.sandbox-login-url:}")
    private String aliSandboxLoginUrl;

    /** V9.20：支付宝沙箱收银台 URL（前端跳转用，可空） */
    @Value("${pay.alipay.sandbox-pay-url:}")
    private String aliSandboxPayUrl;

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
    public String getWxJsapiOpenid() { return wxJsapiOpenid; }
    public String getWxH5SceneInfo() { return wxH5SceneInfo; }
    public boolean isAliSandbox() { return aliSandbox; }
    public String getAliSandboxLoginUrl() { return aliSandboxLoginUrl; }
    public String getAliSandboxPayUrl() { return aliSandboxPayUrl; }
    public String getAliAppId() { return aliAppId; }
    public String getAliPrivateKey() { return aliPrivateKey; }
    public String getAliPublicKey() { return aliPublicKey; }
    public String getAliNotifyUrl() { return aliNotifyUrl; }
}
