package __PKG__.common.pay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付调度开关：@EnableScheduling 随能力包开启（查单补偿任务的调度前提）。
 * 独立成类避免改动启动类签名——能力包关闭时产物与旧版逐字节一致。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pay.reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class PayScheduleConfig {
}
