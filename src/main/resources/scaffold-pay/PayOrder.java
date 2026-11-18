package __PKG__.common.pay;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 支付单（pay_order）。
 *
 * <p>金额一律「分」（bigint）存储——decimal 浮点误差是支付资损经典坑；
 * 展示层（收银台/支付记录页）负责分↔元转换。
 */
@TableName("pay_order")
public class PayOrder {

    /** 0 待支付 1 成功 2 已关闭 3 已退款 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_CLOSED = 2;
    public static final int STATUS_REFUNDED = 3;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商户单号（幂等键，全局唯一） */
    private String outTradeNo;

    /** 订单标题 */
    private String subject;

    /** 金额：分 */
    private Long amount;

    /** 渠道：mock / wxpay / alipay */
    private String payChannel;

    /** 0 待支付 1 成功 2 已关闭 3 已退款 */
    private Integer payStatus;

    /** 二维码内容（wxpay code_url，前端 qrcode 出码） */
    private String qrContent;

    /** 跳转支付地址（alipay 等） */
    private String payUrl;

    /** 渠道返回的自动提交表单 HTML（alipay pageExecute） */
    private String payForm;

    /** 业务表名（支付成功联动其 pay_status 列，白名单校验后使用） */
    private String businessType;

    /** 业务主键 */
    private Long businessId;

    /** 回调原始报文（审计） */
    private String notifyRaw;

    /** 支付成功时间 */
    private LocalDateTime paidAt;

    private Integer deleted;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String v) { this.outTradeNo = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }
    public Long getAmount() { return amount; }
    public void setAmount(Long v) { this.amount = v; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String v) { this.payChannel = v; }
    public Integer getPayStatus() { return payStatus; }
    public void setPayStatus(Integer v) { this.payStatus = v; }
    public String getQrContent() { return qrContent; }
    public void setQrContent(String v) { this.qrContent = v; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String v) { this.payUrl = v; }
    public String getPayForm() { return payForm; }
    public void setPayForm(String v) { this.payForm = v; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String v) { this.businessType = v; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long v) { this.businessId = v; }
    public String getNotifyRaw() { return notifyRaw; }
    public void setNotifyRaw(String v) { this.notifyRaw = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { this.paidAt = v; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer v) { this.deleted = v; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long v) { this.createBy = v; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long v) { this.updateBy = v; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
