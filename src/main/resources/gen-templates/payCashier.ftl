<template>
  <div class="cashier">
    <el-card shadow="never">
      <div slot="header" class="hd">
        <span>收银台</span>
        <el-tag size="mini" type="info">{{ channelText }}</el-tag>
      </div>

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
          <el-tag size="mini" :type="statusTagType(order.payStatus)">{{ statusText(order.payStatus) }}</el-tag>
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

        <div v-if="order.payStatus === 1" class="done">
          <i class="el-icon-success" style="color:#67c23a;font-size:42px" />
          <div>支付成功{{ redirect ? '，即将跳转…' : '' }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import QRCode from 'qrcode'
import { createPayOrder, getPayOrder, mockPay, getPayChannel } from '@/api/pay'

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
    }
  },
  computed: {
    channelText() {
      return { mock: '模拟支付', wxpay: '微信支付（扫码）', alipay: '支付宝' }[this.channel] || this.channel
    }
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
      if (this.order.payForm) {
        // pageExecute 返回自动提交表单：落空文档即跳转支付宝
        const w = window.open('', '_blank')
        w.document.write(this.order.payForm)
        w.document.close()
      } else if (this.order.payUrl) {
        window.location.href = this.order.payUrl
      }
    },
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
