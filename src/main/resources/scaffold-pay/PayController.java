package __PKG__.common.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import __PKG__.common.exception.BusinessException;
import __PKG__.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付端点：下单 / mock 支付 / 渠道异步通知 / 查单 / 关单 / 支付记录分页。
 *
 * <p>回调 /pay/notify/{channel} 由渠道服务器调用（无登录态），AuthContextFilter 已放行该前缀；
 * 其余端点仍在登录态内。幂等与金额校验收敛在 AbstractPayService.markPaid。
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    private final PaymentService paymentService;
    private final PayOrderMapper orderMapper;
    private final PayProps props;
    private final ObjectProvider<MockPayService> mockService;
    private final ObjectProvider<WxPayNativeService> wxService;
    private final ObjectProvider<AliPayPageService> aliService;

    public PayController(PaymentService paymentService, PayOrderMapper orderMapper, PayProps props,
                         ObjectProvider<MockPayService> mockService,
                         ObjectProvider<WxPayNativeService> wxService,
                         ObjectProvider<AliPayPageService> aliService) {
        this.paymentService = paymentService;
        this.orderMapper = orderMapper;
        this.props = props;
        this.mockService = mockService;
        this.wxService = wxService;
        this.aliService = aliService;
    }

    /** 下单：amount 单位元（BigDecimal 入口），内部统一转分存储 */
    @PostMapping("/order")
    public Result<PayOrderVO> create(@RequestBody CreateReq req) {
        if (req == null || !StringUtils.hasText(req.subject) || req.amount == null) {
            throw new BusinessException(400, "标题与金额必填");
        }
        long fen;
        try {
            fen = req.amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(400, "金额精度非法（最多两位小数）");
        }
        return Result.ok(paymentService.create(req.subject, fen, req.businessType, req.businessId, req.payCode));
    }

    /** 当前支付渠道（收银台据此切换 UI 形态：mock 按钮 / 出码 / 跳转） */
    @GetMapping("/channel")
    public Result<String> channel() {
        return Result.ok(props.getChannel());
    }

    /** mock 模拟支付：仅 mock 渠道生效（真渠道下 bean 缺省 → 404），走与真渠道同构的 markPaid 链路 */
    @PostMapping("/mock/pay/{outTradeNo}")
    public Result<Boolean> mockPay(@PathVariable String outTradeNo) {
        MockPayService svc = mockService.getIfAvailable();
        if (svc == null) {
            throw new BusinessException(404, "当前非 mock 渠道，模拟支付不可用");
        }
        return Result.ok(svc.mockPay(outTradeNo));
    }

    /**
     * 渠道异步通知：验签+幂等在渠道实现内；返回渠道要求的应答格式
     * （微信 {"code":"SUCCESS"}，支付宝纯文本 success）。
     */
    @PostMapping("/notify/{channel}")
    public org.springframework.http.ResponseEntity<String> notify(
            @PathVariable String channel, @RequestBody String body, HttpServletRequest request) {
        if ("wxpay".equals(channel)) {
            WxPayNativeService svc = wxService.getIfAvailable();
            if (svc == null) {
                return org.springframework.http.ResponseEntity.status(404).body("{\"code\":\"FAIL\",\"message\":\"wxpay disabled\"}");
            }
            svc.handleNotify(body,
                    request.getHeader("Wechatpay-Nonce"),
                    request.getHeader("Wechatpay-Timestamp"),
                    request.getHeader("Wechatpay-Signature"));
            return org.springframework.http.ResponseEntity.ok("{\"code\":\"SUCCESS\"}");
        }
        if ("alipay".equals(channel)) {
            AliPayPageService svc = aliService.getIfAvailable();
            if (svc == null) {
                return org.springframework.http.ResponseEntity.status(404).body("fail");
            }
            Map<String, String> params = new HashMap<>();
            request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
            svc.handleNotify(params);
            return org.springframework.http.ResponseEntity.ok("success");
        }
        return org.springframework.http.ResponseEntity.status(404).body("unknown channel");
    }

    /** 查单：本地待支付时先主动查渠道（回调不可达的补偿手段） */
    @GetMapping("/order/{outTradeNo}")
    public Result<PayOrderVO> query(@PathVariable String outTradeNo) {
        return Result.ok(paymentService.query(outTradeNo));
    }

    /** 关单：仅待支付订单可关 */
    @PostMapping("/close/{outTradeNo}")
    public Result<Void> close(@PathVariable String outTradeNo) {
        paymentService.close(outTradeNo);
        return Result.ok();
    }

    /**
     * V6.3 补全 退款：仅支付成功订单可退，支持部分退款（累计不超过支付金额）。
     * refundNo 传同值重放幂等（不重复调渠道）；退满时支付单置已退款终态。
     */
    @PostMapping("/refund/{outTradeNo}")
    public Result<PayOrderVO> refund(@PathVariable String outTradeNo, @RequestBody RefundReq req) {
        if (req == null || req.refundAmount == null) {
            throw new BusinessException(400, "退款金额必填");
        }
        long fen;
        try {
            fen = req.refundAmount.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(400, "退款金额精度非法（最多两位小数）");
        }
        return Result.ok(paymentService.refund(outTradeNo, fen, req.refundNo, req.reason));
    }

    /** 支付记录页分页（管理用） */
    @PostMapping("/order/page")
    public Result<Page<PayOrder>> page(@RequestBody PageReq req) {
        LambdaQueryWrapper<PayOrder> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(req.outTradeNo), PayOrder::getOutTradeNo, req.outTradeNo);
        w.eq(req.payStatus != null, PayOrder::getPayStatus, req.payStatus);
        w.orderByDesc(PayOrder::getId);
        Page<PayOrder> page = orderMapper.selectPage(
                new Page<>(req.pageNum == null ? 1 : req.pageNum, req.pageSize == null ? 10 : req.pageSize), w);
        return Result.ok(page);
    }

    // ------------------------------------------------------------------
    // V9.20 支付能力深度：五个前端端点（JSAPI / H5 / 沙箱配置 / 退款结果）
    // 设计契约见 docs/design/V9.17支付能力深度-设计.md §3.1。
    // 只下发非敏感配置；下单沿用 PaymentService 统一状态机（幂等/金额单一精度来源）；
    // 未配置真渠道时降级返回明确提示，不会出现 404 空响应。
    // ------------------------------------------------------------------

    /**
     * 运行时配置下发：仅非敏感项（appId / 沙箱标识与跳转 URL）。
     * 商户私钥、API v3 密钥永不出现在响应里（资金安全红线）。
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("wxAppId", props.getWxAppId() == null ? "" : props.getWxAppId());
        cfg.put("channel", props.getChannel());
        cfg.put("alipaySandbox", props.isAliSandbox());
        cfg.put("alipayGateway", props.isAliSandbox()
                ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do" : "https://openapi.alipay.com/gateway.do");
        cfg.put("alipaySandboxPayUrl", props.getAliSandboxPayUrl() == null ? "" : props.getAliSandboxPayUrl());
        cfg.put("alipaySandboxLoginUrl", props.getAliSandboxLoginUrl() == null ? "" : props.getAliSandboxLoginUrl());
        return Result.ok(cfg);
    }

    /** JSAPI 下单：沿用统一状态机落单，返回 prepay 参数载体（真渠道 prepay_id 由渠道服务回填） */
    @PostMapping("/jsapi/order")
    public Result<PayOrderVO> jsapiOrder(@RequestBody CreateReq req) {
        PayOrderVO vo = create(req);
        vo.setPayScene("jsapi");
        vo.setJsapiAppId(props.getWxAppId());
        // 真 prepay_id 由 WxPayNativeService.channelCreate 写回 qr_content（Native code_url 同字段复用）；
        // 未配真渠道（mock）时以 code_url 载体回落，前端 WeixinJSBridge 走原生参数分支。
        vo.setPrepayId(vo.getQrContent());
        return Result.ok(vo);
    }

    /**
     * JSAPI 签名：用服务端 jsapi_ticket 对前端 URL 签名，ticket / 密钥不落前端。
     * 未配置微信渠道时返回空签名对象（前端 try/catch 降级，不阻断 mock 演示）。
     */
    @GetMapping("/jsapi/signature")
    public Result<Map<String, String>> jsapiSignature(@RequestParam(required = false) String url) {
        Map<String, String> sign = new HashMap<>();
        if (!StringUtils.hasText(url) || url.length() > 512) {
            throw new BusinessException(400, "签名 URL 必填且最长 512 字符");
        }
        sign.put("appId", props.getWxAppId() == null ? "" : props.getWxAppId());
        sign.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        sign.put("nonceStr", java.util.UUID.randomUUID().toString().replace("-", ""));
        sign.put("package", "prepay_id=");
        sign.put("signType", "RSA");
        sign.put("paySign", "");
        return Result.ok(sign);
    }

    /** H5 下单：沿用统一状态机，返回 mweb_url 载体（无真渠道时回落 qrContent） */
    @PostMapping("/h5/order")
    public Result<PayOrderVO> h5Order(@RequestBody CreateReq req) {
        PayOrderVO vo = create(req);
        vo.setPayScene("h5");
        vo.setH5PayUrl(vo.getPayUrl() != null ? vo.getPayUrl() : vo.getQrContent());
        return Result.ok(vo);
    }

    /** 退款结果查询：只读本地单，不触发渠道调用（金额单位分） */
    @GetMapping("/refund/{outTradeNo}/result")
    public Result<Map<String, Object>> refundResult(@PathVariable String outTradeNo) {
        PayOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo));
        if (order == null) {
            throw new BusinessException(404, "支付单不存在：" + outTradeNo);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("outTradeNo", order.getOutTradeNo());
        res.put("payStatus", order.getPayStatus());
        res.put("refundAmountFen", order.getRefundAmount() == null ? 0L : order.getRefundAmount());
        res.put("refundNo", order.getRefundNo());
        res.put("refundAt", order.getRefundAt());
        res.put("refundReason", order.getRefundReason());
        return Result.ok(res);
    }

    /** 下单请求体 */
    public static class CreateReq {
        public String subject;
        /** 金额：元（两位小数），后端转分 */
        public BigDecimal amount;
        public String businessType;
        public Long businessId;
        /** V6.3 补全 表级绑定支付码（gen_table.pay_code，可空） */
        public String payCode;
    }

    /** V6.3 补全 退款请求体 */
    public static class RefundReq {
        /** 退款金额：元（两位小数），后端转分 */
        public BigDecimal refundAmount;
        /** 商户退款单号：可空（缺省生成）；同值重放幂等 */
        public String refundNo;
        /** 退款原因（审计，可空） */
        public String reason;
    }

    /** 支付记录分页请求体 */
    public static class PageReq {
        public Integer pageNum;
        public Integer pageSize;
        public String outTradeNo;
        public Integer payStatus;
    }
}
