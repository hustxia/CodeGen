<template>
  <div class="app-container">
    <el-card shadow="never">
      <!-- 查询区 -->
      <el-form :inline="true" :model="query" size="small" @submit.native.prevent>
        <el-form-item label="商户单号">
          <el-input v-model="query.outTradeNo" placeholder="精确匹配" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.payStatus" placeholder="全部" clearable style="width:120px">
            <el-option label="待支付" :value="0" />
            <el-option label="成功" :value="1" />
            <el-option label="已关闭" :value="2" />
            <el-option label="已退款" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="load(1)">查询</el-button>
          <el-button icon="el-icon-refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table v-loading="loading" :data="rows" border size="small">
        <el-table-column prop="outTradeNo" label="商户单号" width="220" show-overflow-tooltip />
        <el-table-column prop="subject" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="金额（元）" width="110" align="right">
          <template slot-scope="{ row }">{{ fenToYuan(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="payChannel" label="渠道" width="90" />
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="statusTagType(row.payStatus)">{{ statusText(row.payStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务" width="160" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.businessType ? row.businessType + ' #' + row.businessId : '—' }}</template>
        </el-table-column>
        <el-table-column prop="paidAt" label="支付时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" size="mini" @click="doQuery(row)">查单</el-button>
            <el-button v-if="row.payStatus === 0" type="text" size="mini" class="danger-link" @click="doClose(row)">关单</el-button>
            <el-button v-if="row.payStatus === 0" type="text" size="mini" @click="goPay(row)">去支付</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top:12px;text-align:right"
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        :current-page.sync="query.pageNum"
        :page-size.sync="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="load()"
        @size-change="load(1)"
      />
    </el-card>
  </div>
</template>

<script>
import { payOrderPage, getPayOrder, closePayOrder } from '@/api/pay'

export default {
  name: 'PayOrderPage',
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, outTradeNo: '', payStatus: undefined },
      rows: [],
      total: 0,
      loading: false
    }
  },
  mounted() { this.load(1) },
  methods: {
    async load(page) {
      if (page) this.query.pageNum = page
      this.loading = true
      try {
        const data = (await payOrderPage(this.query)).data
        this.rows = data.records || []
        this.total = Number(data.total || 0)
      } finally {
        this.loading = false
      }
    },
    reset() {
      this.query.outTradeNo = ''
      this.query.payStatus = undefined
      this.load(1)
    },
    async doQuery(row) {
      const data = (await getPayOrder(row.outTradeNo)).data
      Object.assign(row, data)
      this.$message.success('已同步渠道最新状态：' + this.statusText(data.payStatus))
    },
    doClose(row) {
      this.$confirm('关闭后该支付单不可再支付，确认？', '关单', { type: 'warning' })
        .then(async() => {
          await closePayOrder(row.outTradeNo)
          this.$message.success('已关闭')
          this.load()
        }).catch(() => {})
    },
    goPay(row) {
      this.$router.push({ path: '/pay/cashier', query: {
        subject: row.subject, amount: this.fenToYuan(row.amount), redirect: '/pay/order'
      } })
    },
    fenToYuan(fen) { return (Number(fen || 0) / 100).toFixed(2) },
    statusText(s) { return { 0: '待支付', 1: '成功', 2: '已关闭', 3: '已退款' }[s] || s },
    statusTagType(s) { return { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }[s] || 'info' }
  }
}
</script>

<style scoped>
.danger-link { color: #f56c6c; }
</style>
