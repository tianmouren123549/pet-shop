<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const errorMsg = ref('')
const detail = ref(null)

function statusText(s) {
  return s === 'CREATED'
    ? '待支付'
    : s === 'PAID'
      ? '待发货'
      : s === 'SHIPPED'
        ? '已发货'
        : s === 'COMPLETED'
          ? '已完成'
          : s === 'CANCELLED'
            ? '已取消'
            : s || '—'
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  const id = route.params.id
  const res = await api.adminGetOrderDetail(id)
  if (res.code === 200) {
    detail.value = res.data
  } else {
    errorMsg.value = res.message || '加载失败'
    detail.value = null
  }
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="head-row">
      <button type="button" class="back-btn" @click="router.push('/admin/orders')">← 返回订单列表</button>
    </div>
    <h2>订单详情</h2>
    <p class="desc">平台查看全站订单：下单用户、涉及商家与商品明细。</p>

    <div v-if="loading" class="panel">加载中...</div>
    <div v-else-if="errorMsg" class="panel err">{{ errorMsg }}</div>
    <template v-else-if="detail">
      <div class="grid-top">
        <section class="card">
          <h3>下单用户</h3>
          <p><span class="label">昵称</span>{{ detail.userNickname || '—' }}</p>
          <p><span class="label">用户 ID</span>{{ detail.userId ?? '—' }}</p>
          <p><span class="label">邮箱</span>{{ detail.userEmail || '—' }}</p>
          <p><span class="label">手机</span>{{ detail.userPhone || '—' }}</p>
        </section>
        <section class="card">
          <h3>订单信息</h3>
          <p><span class="label">订单号</span><strong>{{ detail.orderNo }}</strong></p>
          <p><span class="label">订单 ID</span>{{ detail.orderId }}</p>
          <p><span class="label">实付金额</span>¥{{ detail.payAmount }}</p>
          <p><span class="label">状态</span><span class="status-pill">{{ statusText(detail.status) }}</span></p>
          <p v-if="detail.statusReason" class="reason"><span class="label">备注</span>{{ detail.statusReason }}</p>
          <p><span class="label">下单时间</span>{{ detail.createdAt ? new Date(detail.createdAt).toLocaleString() : '—' }}</p>
          <p><span class="label">更新时间</span>{{ detail.updatedAt ? new Date(detail.updatedAt).toLocaleString() : '—' }}</p>
        </section>
      </div>

      <section v-if="detail.merchants && detail.merchants.length" class="card merchants">
        <h3>本单涉及商家</h3>
        <div class="merchant-chips">
          <div v-for="m in detail.merchants" :key="m.merchantId" class="merchant-chip">
            <div class="m-name">{{ m.shopName }}</div>
            <div class="m-meta">ID: {{ m.merchantId }}</div>
            <div v-if="m.contactName" class="m-meta">联系人：{{ m.contactName }}</div>
            <div v-if="m.phone" class="m-meta">电话：{{ m.phone }}</div>
          </div>
        </div>
      </section>

      <section class="card">
        <h3>商品明细</h3>
        <p class="hint">共 {{ detail.itemCount ?? (detail.items || []).length }} 件商品</p>
        <table class="table">
          <thead>
            <tr>
              <th>商品</th>
              <th>所属店铺</th>
              <th>单价</th>
              <th>数量</th>
              <th>小计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(it, idx) in detail.items || []" :key="`${detail.orderId}-${idx}-${it.productId}`">
              <td>
                <div class="title">{{ it.title }}</div>
                <div class="sub">商品 ID：{{ it.productId }}</div>
              </td>
              <td>
                <div>{{ it.merchantShopName || '—' }}</div>
                <div class="sub" v-if="it.merchantId">商家 ID：{{ it.merchantId }}</div>
              </td>
              <td>¥{{ Number(it.price).toFixed(2) }}</td>
              <td>{{ it.quantity }}</td>
              <td>¥{{ Number(it.subtotal).toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin-page {
  background: #f4f6f9;
  padding: 8px;
  max-width: 1100px;
}
.head-row {
  margin-bottom: 12px;
}
.back-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #cad6e6;
  background: #fff;
  color: #24344f;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}
h2 {
  font-size: 28px;
  color: #1a2740;
  margin-bottom: 6px;
}
h3 {
  font-size: 14px;
  color: #0e1930;
  margin: 0 0 12px;
  font-weight: 800;
}
.desc {
  color: #68788d;
  font-size: 13px;
  margin: 0 0 16px;
}
.panel {
  padding: 16px;
  background: #fff;
  border: 1px solid #dbe3ed;
}
.panel.err {
  color: #a73636;
  border-color: #f0c1c1;
  background: #fff8f8;
}
.grid-top {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}
@media (max-width: 900px) {
  .grid-top {
    grid-template-columns: 1fr;
  }
}
.card {
  background: #fff;
  border: 1px solid #dbe3ed;
  padding: 16px;
  border-radius: 4px;
}
.card.merchants {
  margin-bottom: 12px;
}
.card p {
  margin: 6px 0;
  font-size: 13px;
  color: #26354b;
}
.label {
  display: inline-block;
  min-width: 72px;
  color: #68788d;
  font-size: 12px;
  margin-right: 8px;
}
.reason {
  font-size: 12px;
  color: #5e6e84;
}
.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 2px;
  background: #e9eef6;
  color: #304862;
  font-size: 12px;
  font-weight: 700;
}
.merchant-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.merchant-chip {
  border: 1px solid #dde5ef;
  border-radius: 4px;
  padding: 10px 12px;
  background: #f9fbfe;
  min-width: 200px;
}
.m-name {
  font-weight: 800;
  color: #0e1930;
  margin-bottom: 4px;
}
.m-meta {
  font-size: 12px;
  color: #5e6e84;
}
.hint {
  font-size: 12px;
  color: #68788d;
  margin: 0 0 10px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.table th,
.table td {
  border-bottom: 1px solid #ecf0f5;
  padding: 10px 8px;
  text-align: left;
  vertical-align: top;
}
.table th {
  background: #f1f4f8;
  font-size: 11px;
  color: #5f6d80;
}
.title {
  font-weight: 700;
  color: #0e1930;
}
.sub {
  font-size: 11px;
  color: #8a96a8;
  margin-top: 4px;
}
</style>
