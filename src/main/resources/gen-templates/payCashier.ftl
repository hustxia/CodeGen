<template>
  <div class="cashier">
    <el-card shadow="never">
      <template #header><div class="hd">
        <span>收银台</span>
        <el-tag size="small" type="info">{{ channelText }}</el-tag>
      </div></template>

      <el-form :model="form" label-width="90px" style="max-width:480px">
        <el-form-item label="订单标题">
          <el-input v-model="form.subject" placeholder="如：培训缴费" />
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input-number v-model="form.amountYuan" :min="0.01" :precision="2" :step="1" style="width:200px" />
          <span class="tip">数据库按「分」存储，此处以元输入</span>
        </el-form-item>
        <el-form-item v-if="form.businessType" label="关联业务">
          <el-tag size="small">{{ form.businessType }} {{ '#' + form.businessId }}</el-tag>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="createOrder">生成支付单</el-button>
        </el-form-item>
      </el-form>

      <el-divider v-if="order" />

      <div v-if="order" class="pay-area">
        <div class="line">商户单号：<b>{{ order.outTradeNo }}</b>
          <el-tag size="small" :type="statusTagType(order.payStatus)">{{ statusText(order.payStatus) }}</el-tag>
        </div>

        <!-- mock 渠道：一键模拟支付（走与真渠道同构的状态流转链路） -->
        <template v-if="channel === 'mock' && order.payStatus === 0">
          <el-button type="success" :loading="mocking" @click="mockPayNow">模拟支付（立即成功）</el-button>
          <div class="tip">mock 渠道用于本地演示与流水线自检；接入真实商户后改 yml pay.channel 即切真渠道，零代码改动</div>
        </template>

        <!-- wxpay：前端 qrcode 出码（code_url） -->
        <template v-if="channel === 'wxpay' && order.payStatus === 0">
          <img v-if="qrDataUrl" :src="qrDataUrl" class="qr" alt="微信支付二维码">
          <div class="tip">请使用微信扫码支付；本页每 2 秒自动查单，成功后自动跳转</div>
        </template>

        <!-- alipay：pageExecute 返回的表单落空文档提交跳转 -->
        <template v-if="channel === 'alipay' && order.payStatus === 0">
          <el-button type="primary" @click="gotoAli">跳转支付宝支付</el-button>
          <div class="tip">本页每 2 秒自动查单，成功后自动跳转</div>
        </template>

<#-- V9.17：以下三块均为三态开关能力（NULL/0 关、1 开），缺省关闭时模板指令行被剔除，产物与 V6.3 逐字节一致 -->
<#if (payJsapiEnabled!0) == 1>
        <!-- V9.17 JSAPI：微信内置浏览器走 WeixinJSBridge，appId 由后端配置下发（前端零硬编码） -->
        <template v-if="channel === 'wxpay' && isWeixin && order.payStatus === 0">
          <el-button type="primary" :loading="paying" :disabled="!jsapiReady" @click="payInWeixin">微信内支付</el-button>
          <div class="tip">签名由后端用 jsapi_ticket 生成；appId/商户号从 pay 配置读取，前端不落密钥</div>
        </template>

</#if>
<#if (payH5Enabled!0) == 1>
        <!-- V9.17 H5：非微信内置浏览器跳微信 H5 支付链接 -->
        <template v-if="channel === 'wxpay' && !isWeixin && order.payStatus === 0 && order.payUrl">
          <el-button type="primary" @click="gotoH5">跳转微信 H5 支付</el-button>
          <div class="tip">微信外打开走 H5 链接；本页每 2 秒自动查单</div>
        </template>

</#if>
<#if (payAlipaySandboxEnabled!0) == 1>
        <!-- V9.17 支付宝沙箱：后端 alipay.sandbox=true 切沙箱网关，前端给环境提示与扫码登录入口 -->
        <template v-if="channel === 'alipay' && sandbox">
          <div class="tip">当前为支付宝沙箱环境，资金为虚拟；扫码请使用沙箱买家账号</div>
          <el-button size="small" @click="gotoSandboxLogin">沙箱扫码/登录</el-button>
        </template>

</#if>
        <div v-if="order.payStatus === 1" class="done">
          <el-icon style="color:#67c23a;font-size:42px"><SuccessFilled /></el-icon>
          <div>支付成功{{ redirect ? '，即将跳转…' : '' }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import QRCode from 'qrcode'
import { createPayOrder, getPayOrder, mockPay, getPayChannel } from '@/api/pay'
<#if (payJsapiEnabled!0) == 1 || (payAlipaySandboxEnabled!0) == 1>
import { getPayConfig } from '@/api/pay'
</#if>
<#if (payJsapiEnabled!0) == 1>
import { createJsapiOrder, getJsapiSignature } from '@/api/pay'
</#if>
<#if (payH5Enabled!0) == 1>
import { createH5Order } from '@/api/pay'
</#if>

export default {
  name: 'PayCashier',
  data() {
    return {
      channel: 'mock',
      form: { subject: '', amountYuan: null, businessType: '', businessId: '', redirect: '' },
      order: null,
      qrDataUrl: '',
      creating: false,
      mocking: false,
      timer: null
<#if (payJsapiEnabled!0) == 1 || (payAlipaySandboxEnabled!0) == 1>
      , payConfig: {}
      , sandbox: false
</#if>
<#if (payJsapiEnabled!0) == 1>
      , paying: false
      , jsapiParams: null
      , jsapiReady: false
</#if>
    }
  },
  computed: {
    channelText() {
      return { mock: '模拟支付', wxpay: '微信支付（扫码）', alipay: '支付宝' }[this.channel] || this.channel
    }
<#if (payJsapiEnabled!0) == 1 || (payH5Enabled!0) == 1>
    , isWeixin() {
      return /MicroMessenger/i.test(navigator.userAgent || '')
    }
</#if>
  },
  async mounted() {
    const q = this.$route.query || {}
    this.form.subject = q.subject || ''
    this.form.amountYuan = q.amount ? Number(q.amount) : null
    this.form.businessType = q.businessType || ''
    this.form.businessId = q.businessId || ''
    this.form.redirect = q.redirect || ''
    try {
      // 拦截器按 code 判成败后 return res（整个 body），渠道字符串在 res.data
      const res = await getPayChannel()
      this.channel = res.data || 'mock'
    } catch (e) { this.channel = 'mock' }
<#if (payJsapiEnabled!0) == 1 || (payAlipaySandboxEnabled!0) == 1>
    try {
      // V9.17：appId / 沙箱标识等运行时配置由产物后端从 yml 读取后下发
      const cfg = (await getPayConfig()).data || {}
      this.payConfig = cfg
      this.sandbox = cfg.alipaySandbox === true || cfg.alipaySandbox === 1
    } catch (e) { /* 后端未部署 /pay/config 时保持默认（非沙箱），不影响原有渠道流程 */ }
</#if>
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    async createOrder() {
      if (!this.form.subject || !this.form.amountYuan) {
        this.$message.warning('请填写订单标题与金额')
        return
      }
      this.creating = true
      try {
<#if (payJsapiEnabled!0) == 1>
        if (this.channel === 'wxpay' && this.isWeixin) {
          const data = (await createJsapiOrder({
            subject: this.form.subject, amount: this.form.amountYuan,
            businessType: this.form.businessType || undefined,
            businessId: this.form.businessId || undefined,
            redirect: this.form.redirect || undefined,
            payScene: 'jsapi'
          })).data
          this.order = data
          await this.applyJsapiConfig(data)
          if (data.payStatus === 0) this.startPolling()
          return
        }
</#if>
<#if (payH5Enabled!0) == 1>
        if (this.channel === 'wxpay' && !this.isWeixin) {
          const data = (await createH5Order({
            subject: this.form.subject, amount: this.form.amountYuan,
            businessType: this.form.businessType || undefined,
            businessId: this.form.businessId || undefined,
            redirect: this.form.redirect || undefined,
            payScene: 'h5'
          })).data
          this.order = data
          if (data.payUrl) window.location.href = data.payUrl
          if (data.payStatus === 0) this.startPolling()
          return
        }
</#if>
        const data = (await createPayOrder({
          subject: this.form.subject,
          amount: this.form.amountYuan,
          businessType: this.form.businessType || undefined,
          businessId: this.form.businessId || undefined,
          redirect: this.form.redirect || undefined
        })).data
        this.order = data
        if (data.qrContent) {
          this.qrDataUrl = await QRCode.toDataURL(data.qrContent, { width: 200 })
        }
        if (data.payStatus === 0) this.startPolling()
      } finally {
        this.creating = false
      }
    },
    async mockPayNow() {
      this.mocking = true
      try {
        await mockPay(this.order.outTradeNo)
        await this.refreshOnce()
      } finally {
        this.mocking = false
      }
    },
    gotoAli() {
<#if (payAlipaySandboxEnabled!0) == 1>
      // V9.17 沙箱：后端按 alipay.sandbox 切到沙箱网关并下发 payUrl，前端不拼网关域名
      if (this.sandbox && this.payConfig.alipaySandboxPayUrl) {
        window.location.href = this.payConfig.alipaySandboxPayUrl
        return
      }
</#if>
      if (this.order.payForm) {
        // pageExecute 返回自动提交表单：落空文档即跳转支付宝
        const w = window.open('', '_blank')
        w.document.write(this.order.payForm)
        w.document.close()
      } else if (this.order.payUrl) {
        window.location.href = this.order.payUrl
      }
    },
<#if (payAlipaySandboxEnabled!0) == 1>
    gotoSandboxLogin() {
      const url = this.payConfig.alipaySandboxLoginUrl
      if (!url) { this.$message.warning('后端未提供沙箱登录地址（检查 alipay.sandbox 配置）'); return }
      window.open(url, '_blank')
    },
</#if>
<#if (payJsapiEnabled!0) == 1>
    async applyJsapiConfig(data) {
      try {
        let sign = data && data.jsapiSign
        if (!sign) {
          sign = (await getJsapiSignature(window.location.href.split('#')[0])).data
        }
        const appId = (this.payConfig && this.payConfig.wxAppId) || (sign && sign.appId)
        if (!appId) { this.$message.warning('后端未下发微信 appId，检查 pay 配置'); return }
        this.jsapiParams = {
          appId,
          timeStamp: String(sign.timestamp),
          nonceStr: sign.nonceStr,
          package: sign.package || ('prepay_id=' + (data.prepayId || '')),
          signType: sign.signType || 'RSA',
          paySign: sign.paySign
        }
        this.jsapiReady = true
      } catch (e) { this.$message.error('JSAPI 签名获取失败') }
    },
    payInWeixin() {
      if (!window.WeixinJSBridge || !this.jsapiParams) {
        this.$message.warning('请在微信内置浏览器打开，并确认 JSAPI 签名已就绪')
        return
      }
      this.paying = true
      window.WeixinJSBridge.invoke('getBrandWCPayRequest', this.jsapiParams, (res) => {
        this.paying = false
        if (res && res.err_msg === 'get_brand_wcpay_request:ok') this.refreshOnce()
      })
    },
</#if>
<#if (payH5Enabled!0) == 1>
    gotoH5() {
      if (this.order && this.order.payUrl) window.location.href = this.order.payUrl
      else this.$message.warning('后端未返回 H5 支付链接')
    },
</#if>
    startPolling() {
      if (this.timer) clearInterval(this.timer)
      this.timer = setInterval(() => this.refreshOnce(), 2000)
    },
    async refreshOnce() {
      try {
        const data = (await getPayOrder(this.order.outTradeNo)).data
        this.order = Object.assign({}, this.order, data)
        if (data.payStatus === 1) {
          if (this.timer) clearInterval(this.timer)
          this.$message.success('支付成功')
          if (this.form.redirect) setTimeout(() => { this.$router.push(this.form.redirect) }, 1500)
        }
      } catch (e) { /* 查单失败静默，下一轮重试 */ }
    },
    statusText(s) { return { 0: '待支付', 1: '成功', 2: '已关闭', 3: '已退款' }[s] || s },
    statusTagType(s) { return { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }[s] || 'info' }
  }
}
</script>

<style scoped>
.cashier { padding: 16px; }
.hd { display: flex; align-items: center; gap: 8px; }
.tip { color: #909399; font-size: 12px; margin-top: 8px; }
.line { margin-bottom: 12px; }
.qr { width: 200px; height: 200px; border: 1px solid #ebeef5; border-radius: 4px; }
.done { text-align: center; padding: 20px 0; }
</style>
