<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../../utils/request'
import PaginationBar from '../../components/PaginationBar.vue'

const orders = ref([])
const statusFilter = ref('')
const loading = ref(false)
const errorMsg = ref('')
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const page = ref(1)
const pageSize = ref(10)

watch(statusFilter, () => {
  page.value = 1
})

const total = computed(() => (Array.isArray(orders.value) ? orders.value.length : 0))
const pagedOrders = computed(() => {
  const list = Array.isArray(orders.value) ? orders.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function setPageSize(n) {
  pageSize.value = Number(n || 10)
  page.value = 1
}

function statusText(s) {
  return s === 'CREATED'
    ? '待支付'
    : s === 'PAID'
      ? '已支付'
      : s === 'SHIPPED'
        ? '已发货'
        : s === 'COMPLETED'
          ? '已完成'
          : s === 'CANCELLED'
            ? '已取消'
            : s
}

async function loadOrders() {
  loading.value = true
  const res = await api.merchantGetOrders(merchantId.value, statusFilter.value)
  if (res.code === 200) {
    orders.value = res.data
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
}

async function updateStatus(orderId, status) {
  const res = await api.merchantUpdateOrderStatus(merchantId.value, orderId, status)
  if (res.code === 200) {
    await loadOrders()
  } else {
    alert(res.message || '状态更新失败')
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="merchant-page">
    <h2>我的订单管理</h2>
    <p class="desc">当前商家ID：{{ merchantId || '-' }}；可对已支付订单执行发货操作。</p>
    <div class="toolbar">
      <select v-model="statusFilter" @change="loadOrders">
        <option value="">全部状态</option>
        <option value="CREATED">待支付</option>
        <option value="PAID">已支付</option>
        <option value="SHIPPED">已发货</option>
        <option value="COMPLETED">已完成</option>
        <option value="CANCELLED">已取消</option>
      </select>
      <button class="refresh-btn" @click="loadOrders">刷新</button>
    </div>

    <div v-if="loading">加载中...</div>
    <div v-else-if="errorMsg">{{ errorMsg }}</div>
    <div v-else-if="orders.length === 0">暂无订单</div>

    <table v-else class="table">
      <thead>
        <tr>
          <th>订单ID</th>
          <th>订单号</th>
          <th>用户ID</th>
          <th>商品数</th>
          <th>金额</th>
          <th>状态</th>
          <th>下单时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in pagedOrders" :key="item.orderId">
          <td>{{ item.orderId }}</td>
          <td>{{ item.orderNo }}</td>
          <td>{{ item.userId }}</td>
          <td>{{ item.itemCount ?? '-' }}</td>
          <td>¥{{ item.payAmount }}</td>
          <td><span class="status">{{ statusText(item.status) }}</span></td>
          <td>{{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '-' }}</td>
          <td>
            <button v-if="item.status === 'PAID'" @click="updateStatus(item.orderId, 'SHIPPED')">发货</button>
          </td>
        </tr>
      </tbody>
    </table>

    <PaginationBar
      :page="page"
      :page-size="pageSize"
      :total="total"
      :page-size-options="[8, 10, 20, 50]"
      @update:page="page = $event"
      @update:page-size="setPageSize"
    />
  </div>
</template>

<style scoped>
.merchant-page {
  background: #f4f6f9;
  padding: 8px;
}
h2 {
  font-size: 34px;
  color: #1a2740;
  margin-bottom: 10px;
}
.desc {
  color: #68788d;
  font-size: 13px;
  margin: 0 0 12px;
}
.toolbar {
  margin: 10px 0;
  display: flex;
  gap: 8px;
  align-items: center;
}
select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid #ccd7e6;
  background: #fff;
  color: #33465f;
  border-radius: 2px;
}
.refresh-btn {
  height: 32px;
  padding: 0 10px;
  border: 1px solid #cad6e6;
  background: #f9fbff;
  color: #24344f;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}
.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid #dbe3ed;
}
.table th,
.table td {
  border-bottom: 1px solid #ecf0f5;
  padding: 12px 10px;
  font-size: 13px;
  color: #26354b;
}
.table th {
  background: #f1f4f8;
  text-align: left;
  font-size: 11px;
  color: #5f6d80;
  letter-spacing: 0.6px;
}
.status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 2px;
  background: #e9eef6;
  color: #304862;
  font-size: 11px;
  font-weight: 700;
}
button {
  margin-right: 6px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #cad6e6;
  background: #f9fbff;
  color: #24344f;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
}
</style>

