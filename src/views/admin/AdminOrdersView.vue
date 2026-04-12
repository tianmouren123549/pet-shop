<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'

const orders = ref([])
const statusFilter = ref('')
const orderNoKeyword = ref('')
const appliedOrderNo = ref('')
const loading = ref(false)
const errorMsg = ref('')
const page = ref(1)
const pageSize = ref(10)
const router = useRouter()
const route = useRoute()

function syncStatusFromRoute(query) {
  const raw = String(query?.status || '').toUpperCase()
  const ok = ['CREATED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'].includes(raw)
  statusFilter.value = ok ? raw : ''
}

function onStatusFilterChange() {
  router.replace({
    path: '/admin/orders',
    query: statusFilter.value ? { status: statusFilter.value } : {},
  })
}

const stats = computed(() => {
  const m = { CREATED: 0, PAID: 0, SHIPPED: 0, COMPLETED: 0, CANCELLED: 0 }
  for (const o of orders.value || []) {
    const s = String(o?.status || '')
    if (s in m) m[s] += 1
  }
  return m
})

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
            : s
}

async function loadOrders() {
  loading.value = true
  const res = await api.adminGetOrders(statusFilter.value)
  if (res.code === 200) {
    orders.value = res.data
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
  page.value = 1
}

function runOrderNoSearch() {
  appliedOrderNo.value = orderNoKeyword.value
  page.value = 1
}

function resetOrderNoSearch() {
  orderNoKeyword.value = ''
  appliedOrderNo.value = ''
  page.value = 1
}

const filteredOrders = computed(() => {
  const kw = appliedOrderNo.value.trim().toLowerCase()
  if (!kw) return orders.value || []
  return (orders.value || []).filter((o) => {
    const orderNo = String(o?.orderNo || '').toLowerCase()
    const orderId = String(o?.orderId ?? '').toLowerCase()
    return orderNo.includes(kw) || orderId.includes(kw)
  })
})

watch(statusFilter, () => {
  page.value = 1
})

const total = computed(() => (Array.isArray(filteredOrders.value) ? filteredOrders.value.length : 0))
const pagedOrders = computed(() => {
  const list = Array.isArray(filteredOrders.value) ? filteredOrders.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function setPageSize(n) {
  pageSize.value = Number(n || 10)
  page.value = 1
}

async function updateStatus(orderId, status) {
  const res = await api.adminUpdateOrderStatus(orderId, { status })
  if (res.code === 200) {
    await loadOrders()
  } else {
    showAppMessage(res.message || '状态更新失败', '提示')
  }
}

onMounted(async () => {
  syncStatusFromRoute(route.query)
  await loadOrders()
})

onBeforeRouteUpdate(async (to) => {
  syncStatusFromRoute(to.query)
  await loadOrders()
})
</script>

<template>
  <div class="admin-page">
    <h2>订单管理中心</h2>
    <p class="desc">查看全站订单与流转进度；平台可在纠纷等场景协助取消订单。发货与履约由对应商家处理。</p>

    <div class="stats">
      <div class="stat"><span>待支付</span><strong>{{ stats.CREATED }}</strong></div>
      <div class="stat"><span>待发货</span><strong>{{ stats.PAID }}</strong></div>
      <div class="stat"><span>已发货</span><strong>{{ stats.SHIPPED }}</strong></div>
      <div class="stat"><span>已完成</span><strong>{{ stats.COMPLETED }}</strong></div>
      <div class="stat muted"><span>已取消</span><strong>{{ stats.CANCELLED }}</strong></div>
    </div>

    <div class="toolbar">
      <select v-model="statusFilter" @change="onStatusFilterChange">
        <option value="">全部状态</option>
        <option value="CREATED">待支付</option>
        <option value="PAID">待发货</option>
        <option value="SHIPPED">已发货</option>
        <option value="COMPLETED">已完成</option>
        <option value="CANCELLED">已取消</option>
      </select>
      <input
        v-model="orderNoKeyword"
        class="filter-input"
        placeholder="输入订单号/订单ID查询"
        @keyup.enter="runOrderNoSearch"
      />
      <button class="refresh-btn" @click="runOrderNoSearch">查询</button>
      <button class="ghost-btn" @click="resetOrderNoSearch">清空</button>
      <button class="refresh-btn" @click="loadOrders">刷新</button>
    </div>
    <div v-if="loading">加载中...</div>
    <div v-else-if="errorMsg">{{ errorMsg }}</div>
    <div v-else-if="orders.length === 0">暂无订单</div>
    <div v-else-if="filteredOrders.length === 0">没有匹配的订单</div>
    <table v-else class="table">
      <thead>
        <tr>
          <th>订单号</th>
          <th>用户昵称</th>
          <th>商品数</th>
          <th>金额</th>
          <th>状态</th>
          <th>下单时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in pagedOrders" :key="item.orderId">
          <td>
            <button type="button" class="link-order" @click="router.push(`/admin/orders/${item.orderId}`)">
              {{ item.orderNo }}
            </button>
            <div class="id-sub">ID {{ item.orderId }}</div>
          </td>
          <td>
            <span class="nick">{{ item.userNickname || `用户${item.userId}` }}</span>
          </td>
          <td>{{ item.itemCount ?? '-' }}</td>
          <td>¥{{ item.payAmount }}</td>
          <td><span class="status">{{ statusText(item.status) }}</span></td>
          <td>{{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '-' }}</td>
          <td>
            <div class="btn-group">
              <button type="button" class="btn ghost" @click="router.push(`/admin/orders/${item.orderId}`)">详情</button>
              <button class="btn danger" v-if="item.status === 'CREATED' || item.status === 'PAID'" @click="updateStatus(item.orderId, 'CANCELLED')">取消</button>
            </div>
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
.admin-page { background: #f4f6f9; padding: 8px; }
h2 { font-size: 34px; color: #1a2740; margin-bottom: 8px; }
.desc { color: #68788d; font-size: 13px; margin: 0 0 12px; }

.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 10px 0 14px;
}
.stat {
  padding: 14px;
  border-radius: 4px;
  background: #ffffff;
  border: 1px solid #dde5ef;
  min-height: 86px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.stat span { color: #6d7d91; font-size: 11px; letter-spacing: 0.6px; font-weight: 800; }
.stat strong { color: #0e1930; font-size: 34px; line-height: 1; }
.stat.muted { background: #f7f9fc; }

.toolbar { margin: 10px 0; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
select { height: 32px; padding: 0 8px; border: 1px solid #ccd7e6; background: #fff; color: #33465f; border-radius: 2px; }
.filter-input {
  height: 32px;
  padding: 0 10px;
  border: 1px solid #ccd7e6;
  background: #fff;
  color: #33465f;
  border-radius: 2px;
  font-size: 12px;
  min-width: 220px;
}
.refresh-btn { height: 32px; padding: 0 10px; border: 1px solid #cad6e6; background: #f9fbff; color: #24344f; border-radius: 2px; cursor: pointer; font-size: 12px; font-weight: 700; }
.ghost-btn { height: 32px; padding: 0 10px; border: 1px solid #cad6e6; background: #fff; color: #24344f; border-radius: 2px; cursor: pointer; font-size: 12px; font-weight: 700; }
.table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #dbe3ed; }
.table th, .table td { border-bottom: 1px solid #ecf0f5; padding: 12px 10px; font-size: 13px; color: #26354b; }
.table th { background: #f1f4f8; text-align: left; font-size: 11px; color: #5f6d80; letter-spacing: 0.6px; }
.status { display: inline-block; padding: 2px 8px; border-radius: 2px; background: #e9eef6; color: #304862; font-size: 11px; font-weight: 700; }
.btn-group { display: flex; gap: 8px; flex-wrap: wrap; }
.btn { height: 28px; padding: 0 10px; border: 1px solid #cad6e6; background: #f9fbff; color: #24344f; border-radius: 2px; cursor: pointer; font-size: 12px; font-weight: 700; }
.btn.danger { border-color: #ff4d4f; background: #fff1f1; color: #a73636; }
.btn.ghost { border-color: #c9d4e4; background: #f8fbff; color: #23344f; }
.link-order {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: #16355f;
  font-weight: 800;
  font-size: 13px;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.link-order:hover { color: #0b1630; }
.id-sub { font-size: 11px; color: #8a96a8; margin-top: 4px; }
.nick { font-weight: 600; color: #26354b; }

@media (max-width: 980px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
}
</style>

