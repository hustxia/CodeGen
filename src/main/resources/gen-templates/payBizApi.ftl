package ${pkg}.controller;

<#-- V6.3 补全 表级支付绑定：gen_table.pay_code 非空的业务表生成 {Class}PayController。
     表名/单号列/金额列全部生成期固化为常量（chartStats 白名单同款防注入思路），运行期零拼接用户输入；
     支付/退款的状态流转与幂等收敛在 ${rootPkg}.common.pay.AbstractPayService，本类只做业务行定位与参数换算。 -->
import ${rootPkg}.common.exception.BusinessException;
import ${rootPkg}.common.pay.PayOrder;
import ${rootPkg}.common.pay.PayOrderMapper;
import ${rootPkg}.common.pay.PayOrderVO;
import ${rootPkg}.common.pay.PaymentService;
import ${rootPkg}.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
<#if (payRefundQueryEnabled!0) == 1>
import java.util.LinkedHashMap;
</#if>

/**
 * ${comment} 支付绑定接口（V6.3 补全 表级绑定 pay_code=${payCodeSafe} 自动生成）。
 *
 * <p>最小端点三件：按业务单号发起支付 / 查询支付状态 / 退款（沿用 /pay 全局端点的
 * PaymentService 状态机，业务表名固化进 business_type → 支付成功联动本表 pay_status）。
 * 单号列 = ${payBizNoColumn}<#if !payBizNoIsId>（命名约定推导）</#if><#if payBizNoIsId>（无命名约定单号列，回落主键 id，"业务单号"即主键值）</#if>；
 * <#if payBizAmountColumn?has_content>金额列 = ${payBizAmountColumn}（发起支付缺省取它）<#else>本表无约定金额列：发起支付必须显式传 amount（元）</#if>。
 */
@RestController
@RequestMapping("/${moduleName}/pay")
public class ${className}PayController {

    /** 表级绑定支付码：随支付单落 pay_order.pay_code（业务侧标识/对账检索用） */
    private static final String PAY_CODE = "${payCodeSafe}";

    /** 生成期固化（防注入）：业务表名与单号列，运行期只以参数绑定传值 */
    private static final String BIZ_TABLE = "${tableName}";
    private static final String NO_COLUMN = "${payBizNoColumn}";
<#if payBizAmountColumn?has_content>
    /** 生成期固化：金额列与单位（Long 整型视为分，其余数值按元换算） */
    private static final String AMOUNT_COLUMN = "${payBizAmountColumn}";
    private static final boolean AMOUNT_IS_FEN = ${payBizAmountIsFen?c};
</#if>

    private final PaymentService paymentService;
    private final PayOrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;

    public ${className}PayController(PaymentService paymentService, PayOrderMapper orderMapper,
                                     JdbcTemplate jdbcTemplate) {
        this.paymentService = paymentService;
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 发起支付：amount 单位元（两位小数，后端转分）。缺省取业务行金额列；
     * 业务表名固化进 business_type → 支付成功后联动本表 pay_status=1。
     */
    @PostMapping("/order")
    public Result<PayOrderVO> create(@RequestBody PayBizReq req) {
        if (req == null || !StringUtils.hasText(req.bizNo)) {
            throw new BusinessException(400, "业务单号必填");
        }
        Map<String, Object> bizRow = findBizRow(req.bizNo);
        long amountFen = resolveAmountFen(req.amount, bizRow);
        return Result.ok(paymentService.create("${comment} " + req.bizNo, amountFen, BIZ_TABLE,
                ((Number) bizRow.get("id")).longValue(), PAY_CODE));
    }

    /** 查询支付状态：返回该业务单最新一笔支付单（含退款进度 refundAmount，单位分） */
    @GetMapping("/status/{bizNo}")
    public Result<PayOrder> status(@PathVariable String bizNo) {
        PayOrder latest = latestOrder(bizNo);
        if (latest == null) {
            throw new BusinessException(404, "该业务单还没有支付记录：" + bizNo);
        }
        return Result.ok(latest);
    }

    /**
     * 退款：对业务单最新一笔支付成功的支付单退款。
     * amount 缺省全额退（剩余可退）；refundNo 可传幂等键（同值重放不二次退款）。
     */
    @PostMapping("/refund/{bizNo}")
    public Result<PayOrderVO> refund(@PathVariable String bizNo,
                                     @org.springframework.web.bind.annotation.RequestBody(required = false) RefundBizReq req) {
        PayOrder latest = latestOrder(bizNo);
        if (latest == null || latest.getPayStatus() != PayOrder.STATUS_PAID) {
            throw new BusinessException(400, "该业务单没有可退款的支付单：" + bizNo);
        }
        long refundFen;
        if (req != null && req.refundAmount != null) {
            try {
                refundFen = req.refundAmount.movePointRight(2).longValueExact();
            } catch (ArithmeticException e) {
                throw new BusinessException(400, "退款金额精度非法（最多两位小数）");
            }
        } else {
            long refunded = latest.getRefundAmount() == null ? 0L : latest.getRefundAmount();
            refundFen = latest.getAmount() - refunded;
            if (refundFen <= 0) {
                throw new BusinessException(400, "该支付单已全额退款");
            }
        }
        return Result.ok(paymentService.refund(latest.getOutTradeNo(), refundFen,
                req == null ? null : req.refundNo, req == null ? null : req.reason));
    }

<#--
    V9.17 新增能力（三态：NULL/0=关、1=开；缺省关 → 产物与 V6.3 补全版逐字节零 diff）。
    消费来自 model Map 的可选 key，不新增 DTO：
      payRefundQueryEnabled=1 → 追加退款结果查询端点；payNo 由表级 PAY_CODE 推导，不硬编码 order_no。
      payJsapiEnabled / payH5Enabled / payAlipaySandboxEnabled → 全局共享模板 payApi / payCashier 消费。
-->
<#if (payRefundQueryEnabled!0) == 1>
    /** V9.17 退款结果查询：按业务单号拿到最新支付单的退款进度（金额单位分） */
    @GetMapping("/refund/{bizNo}/result")
    public Result<Map<String, Object>> refundResult(@PathVariable String bizNo) {
        PayOrder latest = latestOrder(bizNo);
        if (latest == null || latest.getRefundAmount() == null || latest.getRefundAmount() <= 0) {
            throw new BusinessException(404, "该业务单没有退款记录：" + bizNo);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payNo", payNo(bizNo));
        data.put("outTradeNo", latest.getOutTradeNo());
        data.put("refundNo", latest.getRefundNo());
        data.put("refundAmount", latest.getRefundAmount());
        data.put("refundAt", latest.getRefundAt());
        data.put("refundReason", latest.getRefundReason());
        data.put("payStatus", latest.getPayStatus());
        return Result.ok(data);
    }

    /** V9.17 pay_code 表级绑定：业务侧支付单号按 pay_code 生成，替代硬编码 order_no */
    private String payNo(String bizNo) {
        return PAY_CODE + "_" + bizNo;
    }

</#if>
    // ------------------------------------------------------------------
    // 内部：单号定位业务行 / 最新支付单（列名全部生成期常量，值全部参数绑定）
    // ------------------------------------------------------------------

    private Map<String, Object> findBizRow(String bizNo) {
        String sql = "SELECT id<#if payBizAmountColumn?has_content>, " + AMOUNT_COLUMN + " AS amount_val</#if>"
                + " FROM " + BIZ_TABLE + " WHERE " + NO_COLUMN + " = ? AND deleted = 0 ORDER BY id DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, bizNo);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "业务单不存在：" + bizNo);
        }
        return rows.get(0);
    }

    private PayOrder latestOrder(String bizNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM " + BIZ_TABLE + " WHERE " + NO_COLUMN + " = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                bizNo);
        if (rows.isEmpty()) {
            return null;
        }
        Long bizId = ((Number) rows.get(0).get("id")).longValue();
        return orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getBusinessType, BIZ_TABLE)
                .eq(PayOrder::getBusinessId, bizId)
                .orderByDesc(PayOrder::getId)
                .last("LIMIT 1"));
    }

    private long resolveAmountFen(BigDecimal explicit, Map<String, Object> bizRow) {
<#if payBizAmountColumn?has_content>
        if (explicit == null) {
            Object val = bizRow.get("amount_val");
            if (val == null) {
                throw new BusinessException(400, "业务行金额列为空，必须显式传 amount（元）");
            }
            if (AMOUNT_IS_FEN) {
                return ((Number) val).longValue();
            }
            return new BigDecimal(String.valueOf(val)).movePointRight(2).longValueExact();
        }
</#if>
        if (explicit == null) {
            throw new BusinessException(400, "本表未固化金额列，必须显式传 amount（元）");
        }
        try {
            return explicit.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(400, "金额精度非法（最多两位小数）");
        }
    }

    /** 发起支付请求体 */
    public static class PayBizReq {
        /** 业务单号（生成期固化单号列的值） */
        public String bizNo;
        /** 支付金额：元（两位小数）<#if payBizAmountColumn?has_content>；缺省取业务行金额列</#if> */
        public BigDecimal amount;
    }

    /** 退款请求体 */
    public static class RefundBizReq {
        /** 退款金额：元（两位小数）；缺省全额退（剩余可退） */
        public BigDecimal refundAmount;
        /** 商户退款单号：可空；同值重放幂等 */
        public String refundNo;
        /** 退款原因（审计，可空） */
        public String reason;
    }
}
