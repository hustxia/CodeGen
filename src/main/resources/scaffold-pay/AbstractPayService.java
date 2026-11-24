package __PKG__.common.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务公共骨架：下单落库 / 幂等状态流转 / 业务联动，渠道差异留给 hook。
 *
 * <p>幂等核心是 {@link #markPaid} 的条件 UPDATE（pay_status=0 才置 1）：
 * 回调重放、mock 重复支付、补偿任务三路并发天然安全，不需要分布式锁。
 * mock 与真渠道走同一条链路，测试等价（V6.3 定案）。
 */
public abstract class AbstractPayService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(AbstractPayService.class);

    protected final PayOrderMapper orderMapper;
    protected final JdbcTemplate jdbcTemplate;
    protected final PayProps props;

    protected AbstractPayService(PayOrderMapper orderMapper, JdbcTemplate jdbcTemplate, PayProps props) {
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
    }

    /** 渠道标识：mock / wxpay / alipay */
    protected abstract String channel();

    /** 渠道下单 hook：回填 qrContent / payUrl / payForm 之一 */
    protected abstract void channelCreate(PayOrder order);

    /** 渠道侧查单 hook：渠道确认已支付返回 true（补偿链路据此 markPaid） */
    protected abstract boolean channelQueryPaid(PayOrder order);

    /** 渠道侧关单 hook */
    protected abstract void channelClose(PayOrder order);

    /**
     * 渠道侧退款 hook：refundNo 是渠道幂等键（同号重放渠道侧也不会二次退），
     * 校验失败（余额不足/单不可退）抛 BusinessException，成功无返回。
     */
    protected abstract void channelRefund(PayOrder order, long refundFen, String refundNo);

    @Override
    public PayOrderVO create(String subject, long amountFen, String businessType, Long businessId) {
        return create(subject, amountFen, businessType, businessId, null);
    }

    @Override
    public PayOrderVO create(String subject, long amountFen, String businessType, Long businessId, String payCode) {
        if (amountFen <= 0) {
            throw new __PKG__.common.exception.BusinessException(400, "金额必须大于 0");
        }
        if (!StringUtils.hasText(subject)) {
            throw new __PKG__.common.exception.BusinessException(400, "订单标题必填");
        }
        if (StringUtils.hasText(businessType)) {
            validateBusinessTable(businessType);
        }
        PayOrder order = new PayOrder();
        order.setOutTradeNo("P" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100, 1000));
        order.setSubject(subject);
        order.setAmount(amountFen);
        order.setPayChannel(channel());
        order.setPayStatus(PayOrder.STATUS_PENDING);
        order.setBusinessType(businessType);
        order.setBusinessId(businessId);
        order.setPayCode(payCode);
        orderMapper.insert(order);
        channelCreate(order);
        orderMapper.updateById(order);
        return toVo(order);
    }

    @Override
    public PayOrderVO query(String outTradeNo) {
        PayOrder order = mustGet(outTradeNo);
        if (order.getPayStatus() == PayOrder.STATUS_PENDING && channelQueryPaid(order)) {
            // 渠道确认已付，金额以本地单为准（查询链路无回调报文）
            markPaid(order, order.getAmount(), null);
            order = mustGet(outTradeNo);
        }
        return toVo(order);
    }

    @Override
    public void close(String outTradeNo) {
        PayOrder order = mustGet(outTradeNo);
        if (order.getPayStatus() != PayOrder.STATUS_PENDING) {
            throw new __PKG__.common.exception.BusinessException(400, "仅待支付订单可关闭");
        }
        channelClose(order);
        order.setPayStatus(PayOrder.STATUS_CLOSED);
        orderMapper.updateById(order);
    }

    /**
     * V6.3 补全 退款状态机唯一入口：金额校验 → 渠道退款 → 条件 UPDATE 累加 → 退满置终态。
     *
     * <p>与 {@link #markPaid} 同款幂等思路，三重防护：
     * <ol>
     *   <li>余额校验：本次退款 + 已累计退款 ≤ 支付金额，超出直接 400；</li>
     *   <li>同 refund_no 重放：直接返回当前状态，不再调渠道（渠道侧 out_refund_no 同号也幂等，双保险）；</li>
     *   <li>条件 UPDATE：pay_status=1 且余额足够才累加（并发退款天然收敛，无需分布式锁）。</li>
     * </ol>
     * 部分退款保持 pay_status=1（由 refund_amount 区分）；累计退满 → pay_status=3 并联动业务表 pay_status=3。
     */
    @Override
    public PayOrderVO refund(String outTradeNo, long refundFen, String refundNo, String reason) {
        if (refundFen <= 0) {
            throw new __PKG__.common.exception.BusinessException(400, "退款金额必须大于 0");
        }
        PayOrder order = mustGet(outTradeNo);
        if (order.getPayStatus() == PayOrder.STATUS_REFUNDED) {
            throw new __PKG__.common.exception.BusinessException(400, "订单已全额退款，不可重复退款");
        }
        if (order.getPayStatus() != PayOrder.STATUS_PAID) {
            throw new __PKG__.common.exception.BusinessException(400, "仅支付成功的订单可退款");
        }
        long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
        if (refundFen + refunded > order.getAmount()) {
            throw new __PKG__.common.exception.BusinessException(400,
                    "退款金额超过可退余额（已退 " + fenToYuan(refunded) + " 元）");
        }
        if (StringUtils.hasText(refundNo) && refundNo.equals(order.getRefundNo())) {
            return toVo(order); // 幂等重放：同 refund_no 已受理过
        }
        String effectiveRefundNo = StringUtils.hasText(refundNo)
                ? refundNo
                : "R" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                        + ThreadLocalRandom.current().nextInt(100, 1000);
        channelRefund(order, refundFen, effectiveRefundNo);
        // refundFen 已是校验过的 long，条件里仅做数值拼接（无注入面）；余额不足或状态已变时影响行数=0
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo)
                .eq(PayOrder::getPayStatus, PayOrder.STATUS_PAID)
                .apply("IFNULL(refund_amount, 0) + {0} <= amount", refundFen)
                .setSql("refund_amount = IFNULL(refund_amount, 0) + " + refundFen)
                .set(PayOrder::getRefundNo, effectiveRefundNo)
                .set(PayOrder::getRefundAt, LocalDateTime.now())
                .set(StringUtils.hasText(reason), PayOrder::getRefundReason, reason));
        if (updated == 0) {
            throw new __PKG__.common.exception.BusinessException(400, "退款失败：订单状态已变化或可退余额不足");
        }
        PayOrder latest = mustGet(outTradeNo);
        if (latest.getRefundAmount() != null && latest.getRefundAmount().equals(latest.getAmount())) {
            // 累计退满：1 → 3 终态（条件 UPDATE，与并发退款互斥），业务表 pay_status 联动置 3
            orderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                    .eq(PayOrder::getOutTradeNo, outTradeNo)
                    .eq(PayOrder::getPayStatus, PayOrder.STATUS_PAID)
                    .set(PayOrder::getPayStatus, PayOrder.STATUS_REFUNDED));
            linkBusiness(latest, 3);
            latest = mustGet(outTradeNo);
        }
        return toVo(latest);
    }

    /**
     * 唯一状态流转入口：回调重放/mock 重复支付/补偿任务全部经此收敛。
     *
     * <p>条件 UPDATE（pay_status=0 才置 1）影响行数=0 即已被处理，直接返回 false——幂等；
     * 金额二次校验在此统一做（回调金额必须等于支付单金额，防篡改）。
     *
     * @return true=本次流转生效（首次支付成功）；false=此前已处理（幂等重放）
     */
    public boolean markPaid(PayOrder order, long channelAmountFen, String raw) {
        if (channelAmountFen != order.getAmount()) {
            throw new __PKG__.common.exception.BusinessException(400,
                    "回调金额与支付单不一致（防篡改校验）：out_trade_no=" + order.getOutTradeNo());
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, order.getOutTradeNo())
                .eq(PayOrder::getPayStatus, PayOrder.STATUS_PENDING)
                .set(PayOrder::getPayStatus, PayOrder.STATUS_PAID)
                .set(PayOrder::getPaidAt, LocalDateTime.now())
                .set(raw != null, PayOrder::getNotifyRaw, raw));
        if (updated == 0) {
            return false;
        }
        linkBusiness(order, 1);
        return true;
    }

    /**
     * 业务联动：支付成功更新业务表 pay_status=1；V6.3 补全：全额退款联动置 3。
     *
     * <p>business_type 是动态表名，必须三重校验防 SQL 注入：
     * 正则白名单 → 表存在性（information_schema）→ pay_status 列存在性。
     */
    private void linkBusiness(PayOrder order, int targetStatus) {
        String table = order.getBusinessType();
        if (!StringUtils.hasText(table) || order.getBusinessId() == null) {
            return;
        }
        if (!table.matches("[a-z][a-z0-9_]{0,63}")) {
            log.warn("business_type 非法，跳过业务联动: {}", table);
            return;
        }
        Long tableCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class, table);
        if (tableCnt == null || tableCnt == 0) {
            log.warn("business_type 表不存在，跳过业务联动: {}", table);
            return;
        }
        Long colCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                        + " AND table_name = ? AND column_name = 'pay_status'",
                Long.class, table);
        if (colCnt == null || colCnt == 0) {
            return; // 业务表没有 pay_status 列 = 未接入支付约定，静默跳过
        }
        jdbcTemplate.update("UPDATE " + table + " SET pay_status = ? WHERE id = ?",
                targetStatus, order.getBusinessId());
    }

    /** 下单前校验业务表名（同 linkBusiness 的白名单与存在性口径） */
    private void validateBusinessTable(String table) {
        if (!table.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new __PKG__.common.exception.BusinessException(400, "business_type 非法（限小写字母/数字/下划线）");
        }
    }

    protected PayOrder mustGet(String outTradeNo) {
        PayOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo)
                .last("LIMIT 1"));
        if (order == null) {
            throw new __PKG__.common.exception.BusinessException(404, "支付单不存在：" + outTradeNo);
        }
        return order;
    }

    protected PayOrderVO toVo(PayOrder order) {
        PayOrderVO vo = new PayOrderVO();
        vo.setOutTradeNo(order.getOutTradeNo());
        vo.setSubject(order.getSubject());
        vo.setAmountFen(order.getAmount());
        vo.setAmountYuan(fenToYuan(order.getAmount()));
        vo.setPayChannel(order.getPayChannel());
        vo.setPayStatus(order.getPayStatus());
        vo.setStatusText(switch (order.getPayStatus()) {
            case PayOrder.STATUS_PAID -> "成功";
            case PayOrder.STATUS_CLOSED -> "已关闭";
            case PayOrder.STATUS_REFUNDED -> "已退款";
            default -> "待支付";
        });
        vo.setPaidAt(order.getPaidAt());
        vo.setPayUrl(order.getPayUrl());
        vo.setQrContent(order.getQrContent());
        vo.setPayForm(order.getPayForm());
        vo.setBusinessType(order.getBusinessType());
        vo.setBusinessId(order.getBusinessId());
        // V6.3 补全：退款进度字段（前端展示「已退 x 元 / 剩余可退」）
        vo.setPayCode(order.getPayCode());
        vo.setRefundAmountFen(order.getRefundAmount());
        vo.setRefundAmountYuan(fenToYuan(order.getRefundAmount()));
        vo.setRefundAt(order.getRefundAt());
        return vo;
    }

    protected String fenToYuan(Long fen) {
        return BigDecimal.valueOf(fen == null ? 0L : fen).movePointLeft(2).toPlainString();
    }
}
