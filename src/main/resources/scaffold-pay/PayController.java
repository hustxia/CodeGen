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
        return Result.ok(paymentService.create(req.subject, fen, req.businessType, req.businessId));
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

    /** 下单请求体 */
    public static class CreateReq {
        public String subject;
        /** 金额：元（两位小数），后端转分 */
        public BigDecimal amount;
        public String businessType;
        public Long businessId;
    }

    /** 支付记录分页请求体 */
    public static class PageReq {
        public Integer pageNum;
        public Integer pageSize;
        public String outTradeNo;
        public Integer payStatus;
    }
}
