package __PKG__.common.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查单补偿任务：回调不可达兜底（本机/内网部署必然收不到异步通知）。
 *
 * <p>回调只是加速器不是唯一通路——本任务定期扫「待支付且已超 1 分钟」的支付单，
 * 逐单调渠道查单，渠道确认已支付则走同一条 markPaid 幂等链路收状态。
 */
@Component
@ConditionalOnProperty(name = "pay.reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class PayReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(PayReconcileJob.class);

    private final PayOrderMapper orderMapper;
    private final ObjectProvider<PaymentService> paymentService;

    public PayReconcileJob(PayOrderMapper orderMapper, ObjectProvider<PaymentService> paymentService) {
        this.orderMapper = orderMapper;
        this.paymentService = paymentService;
    }

    /** 间隔/开关由 yml pay.reconcile-* 配置（缺省 5 分钟） */
    @Scheduled(fixedDelayString = "${pay.reconcile-interval-ms:300000}", initialDelayString = "${pay.reconcile-interval-ms:300000}")
    public void reconcile() {
        PaymentService svc = paymentService.getIfAvailable();
        if (svc == null) {
            return;
        }
        List<PayOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getPayStatus, PayOrder.STATUS_PENDING)
                .lt(PayOrder::getCreateTime, LocalDateTime.now().minusMinutes(1))
                .last("LIMIT 50"));
        for (PayOrder order : pending) {
            try {
                svc.query(order.getOutTradeNo());
            } catch (Exception e) {
                // 单渠道失败不阻断本轮对账，下一轮重试
                log.warn("查单补偿失败（下一轮重试）: {} {}", order.getOutTradeNo(), e.getMessage());
            }
        }
        if (!pending.isEmpty()) {
            log.info("查单补偿完成：本轮处理 {} 笔待支付单", pending.size());
        }
    }
}
