package __PKG__.common.pay;

import java.time.LocalDateTime;

/** 支付单视图对象：收银台/支付记录页取数载体 */
public class PayOrderVO {

    private String outTradeNo;
    private String subject;
    /** 金额：分 */
    private Long amountFen;
    /** 金额：元（前端直接展示） */
    private String amountYuan;
    private String payChannel;
    private Integer payStatus;
    private String statusText;
    private LocalDateTime paidAt;
    /** 跳转支付地址（alipay 等） */
    private String payUrl;
    /** 二维码内容（wxpay code_url，前端 qrcode 出码） */
    private String qrContent;
    /** alipay pageExecute 自动提交表单 HTML */
    private String payForm;
    private String businessType;
    private Long businessId;

    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String v) { this.outTradeNo = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long v) { this.amountFen = v; }
    public String getAmountYuan() { return amountYuan; }
    public void setAmountYuan(String v) { this.amountYuan = v; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String v) { this.payChannel = v; }
    public Integer getPayStatus() { return payStatus; }
    public void setPayStatus(Integer v) { this.payStatus = v; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String v) { this.statusText = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { this.paidAt = v; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String v) { this.payUrl = v; }
    public String getQrContent() { return qrContent; }
    public void setQrContent(String v) { this.qrContent = v; }
    public String getPayForm() { return payForm; }
    public void setPayForm(String v) { this.payForm = v; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String v) { this.businessType = v; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long v) { this.businessId = v; }
}
