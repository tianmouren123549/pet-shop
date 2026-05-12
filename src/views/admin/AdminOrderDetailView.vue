<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'

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

function statusTone(s) {
  const k = String(s || '').toLowerCase()
  if (k === 'created') return 'created'
  if (k === 'paid') return 'paid'
  if (k === 'shipped') return 'shipped'
  if (k === 'completed') return 'completed'
  if (k === 'cancelled') return 'cancelled'
  return ''
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
    <header class="page-header">
      <button type="button" class="back-btn" @click="router.push('/admin/orders')">← 返回订单列表</button>
      <div class="page-header-main">
        <h2>订单详情</h2>
      </div>
    </header>

    <div v-if="loading" class="panel">加载中...</div>
    <div v-else-if="errorMsg" class="panel err">{{ errorMsg }}</div>
    <template v-else-if="detail">
      <div class="grid-top">
        <section class="card">
          <h3>下单用户</h3>
          <dl class="kv-list">
            <div class="kv-row">
              <dt>昵称</dt>
              <dd>{{ detail.userNickname || '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>用户 ID</dt>
              <dd>{{ detail.userId ?? '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>邮箱</dt>
              <dd>{{ detail.userEmail || '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>手机</dt>
              <dd>{{ detail.userPhone || '—' }}</dd>
            </div>
          </dl>
        </section>
        <section class="card">
          <h3>订单信息</h3>
          <dl class="kv-list">
            <div class="kv-row">
              <dt>订单号</dt>
              <dd class="dd-strong">{{ detail.orderNo }}</dd>
            </div>
            <div class="kv-row">
              <dt>订单 ID</dt>
              <dd>{{ detail.orderId }}</dd>
            </div>
            <div class="kv-row">
              <dt>实付金额</dt>
              <dd class="dd-amount">¥{{ formatYuan(detail.payAmount) }}</dd>
            </div>
            <div class="kv-row">
              <dt>状态</dt>
              <dd>
                <span :class="['status-pill', statusTone(detail.status)]">{{ statusText(detail.status) }}</span>
              </dd>
            </div>
            <div v-if="detail.statusReason" class="kv-row kv-row--block">
              <dt>备注</dt>
              <dd class="dd-muted">{{ detail.statusReason }}</dd>
            </div>
            <div class="kv-row">
              <dt>下单时间</dt>
              <dd>{{ detail.createdAt ? new Date(detail.createdAt).toLocaleString() : '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>更新时间</dt>
              <dd>{{ detail.updatedAt ? new Date(detail.updatedAt).toLocaleString() : '—' }}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section v-if="detail.merchants && detail.merchants.length" class="card merchants-card">
        <div class="merchants-head">
          <h3>本单涉及商家</h3>
          <span class="merchants-count">共 {{ detail.merchants.length }} 家</span>
        </div>
        <div class="merchant-grid">
          <article v-for="m in detail.merchants" :key="m.merchantId" class="merchant-tile">
            <div class="merchant-tile-head">
              <span class="merchant-tile-icon">店</span>
              <div class="merchant-tile-titles">
                <div class="m-name">{{ m.shopName }}</div>
                <div class="m-id">商家 ID {{ m.merchantId }}</div>
              </div>
            </div>
            <dl class="merchant-mini-kv">
              <div v-if="m.contactName" class="mini-row">
                <dt>联系人</dt>
                <dd>{{ m.contactName }}</dd>
              </div>
              <div v-if="m.phone" class="mini-row">
                <dt>电话</dt>
                <dd>{{ m.phone }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </section>

      <section class="card card-table">
        <div class="table-section-head">
          <h3>商品明细</h3>
          <span class="hint">共 {{ detail.itemCount ?? (detail.items || []).length }} 件商品</span>
        </div>
        <div class="table-scroll">
          <table class="detail-table">
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
              <td>¥{{ formatYuan(it.price) }}</td>
              <td>{{ it.quantity }}</td>
              <td>¥{{ formatYuan(it.subtotal) }}</td>
            </tr>
          </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 100%;
  width: 100%;
  padding: 4px 0 8px;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.page-header-main {
  flex: 1;
  min-width: 220px;
}

.back-btn {
  height: 38px;
  padding: 0 16px;
  border: 1px solid #c5d2e6;
  background: linear-gradient(180deg, #ffffff 0%, #f4f7fb 100%);
  color: #1a2d48;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(12, 28, 52, 0.06);
}

.back-btn:hover {
  border-color: #0b1630;
  color: #0b1630;
}

h2 {
  font-size: 26px;
  color: #0f1a2e;
  margin: 0;
  font-weight: 800;
}

h3 {
  font-size: 15px;
  color: #0f1a2e;
  margin: 0;
  font-weight: 800;
}

.panel {
  padding: 20px;
  background: #fff;
  border: 1px solid #dbe3ed;
  border-radius: 14px;
}

.panel.err {
  color: #a73636;
  border-color: #f0c1c1;
  background: #fff8f8;
}

.grid-top {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .grid-top {
    grid-template-columns: 1fr;
  }
}

.card {
  background: #fff;
  border: 1px solid #dbe3ed;
  padding: 18px 20px;
  border-radius: 14px;
  box-shadow: 0 4px 14px rgba(12, 24, 48, 0.05);
}

.card-table {
  margin-top: 0;
}

.kv-list {
  margin: 0;
  padding: 0;
}

.kv-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 8px 14px;
  align-items: baseline;
  padding: 10px 0;
  border-bottom: 1px solid #eef2f7;
}

.kv-row:last-child {
  border-bottom: none;
}

.kv-row--block {
  align-items: start;
}

.kv-row dt {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #6b7c90;
}

.kv-row dd {
  margin: 0;
  font-size: 14px;
  color: #14233d;
  font-weight: 600;
  word-break: break-word;
}

.dd-strong {
  font-weight: 800;
  letter-spacing: 0.02em;
}

.dd-amount {
  font-size: 18px;
  font-weight: 900;
  color: #9b4500;
}

.dd-muted {
  font-weight: 500;
  color: #4a5d78;
  line-height: 1.5;
}

.status-pill {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 800;
  border: 1px solid #d9e1ec;
  background: #eef2f7;
  color: #304862;
}

.status-pill.created {
  background: #fff7e6;
  border-color: #ffd591;
  color: #ad6800;
}

.status-pill.paid {
  background: #e6f7ff;
  border-color: #91d5ff;
  color: #096dd9;
}

.status-pill.shipped {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #237804;
}

.status-pill.completed {
  background: #f0f5ff;
  border-color: #adc6ff;
  color: #1d39c4;
}

.status-pill.cancelled {
  background: #fff1f1;
  border-color: #f0c1c1;
  color: #a73636;
}

.merchants-card {
  margin-bottom: 16px;
}

.merchants-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.merchants-count {
  font-size: 13px;
  font-weight: 700;
  color: #5a6b82;
  padding: 4px 12px;
  border-radius: 999px;
  background: #f0f4fa;
  border: 1px solid #dbe3ef;
}

.merchant-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.merchant-tile {
  border: 1px solid #dbe3ef;
  border-radius: 14px;
  padding: 14px 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  min-height: 120px;
  box-sizing: border-box;
}

.merchant-tile-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.merchant-tile-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #0f1a2e 0%, #243a5c 100%);
  color: #f4f7fc;
  font-size: 14px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.merchant-tile-titles {
  min-width: 0;
}

.m-name {
  font-weight: 800;
  color: #0f1a2e;
  font-size: 16px;
  line-height: 1.3;
  margin-bottom: 4px;
}

.m-id {
  font-size: 12px;
  font-weight: 600;
  color: #6b7c90;
}

.merchant-mini-kv {
  margin: 0;
  padding: 0;
}

.mini-row {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px solid #eef2f7;
  font-size: 13px;
}

.mini-row:first-of-type {
  border-top: none;
  padding-top: 0;
}

.mini-row dt {
  margin: 0;
  color: #6b7c90;
  font-weight: 600;
}

.mini-row dd {
  margin: 0;
  color: #14233d;
  font-weight: 600;
  word-break: break-all;
}

.table-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.hint {
  font-size: 13px;
  color: #6b7c90;
  font-weight: 600;
  margin: 0;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid #dbe3ed;
}

.detail-table {
  width: 100%;
  min-width: 640px;
  border-collapse: collapse;
  font-size: 14px;
  background: #fff;
}

.detail-table th,
.detail-table td {
  border-bottom: 1px solid #ecf0f5;
  padding: 12px 14px;
  text-align: left;
  vertical-align: top;
}

.detail-table th {
  background: linear-gradient(180deg, #f2f6fc 0%, #e8eef7 100%);
  font-size: 13px;
  font-weight: 800;
  color: #1f2d44;
}

.detail-table tbody tr:hover {
  background: #fafbfd;
}

.title {
  font-weight: 800;
  color: #0f1a2e;
}

.sub {
  font-size: 12px;
  color: #7a8a9e;
  margin-top: 6px;
  font-weight: 500;
}
</style>
