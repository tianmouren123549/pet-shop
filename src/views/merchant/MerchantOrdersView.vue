<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'

/** 状态筛选 Tab：待支付 / 待发货 与顶栏分色红点对应 */
const STATUS_TABS = [
  { value: '', label: '全部', dot: null },
  { value: 'CREATED', label: '待支付', dot: null },
  { value: 'PAID', label: '待发货', dot: 'ship' },
  { value: 'SHIPPED', label: '已发货', dot: null },
  { value: 'COMPLETED', label: '已完成', dot: null },
  { value: 'CANCELLED', label: '已取消', dot: null },
]

const orders = ref([])
const statusFilter = ref('')
const loading = ref(false)
const errorMsg = ref('')
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const page = ref(1)
const pageSize = ref(10)

/** 是否存在待发货（用于「待发货」Tab 橙点；待支付不做提示） */
const orderTodos = ref({ pendingShipment: false })

let todoPollTimer = null

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

/**
 * @param {string} s
 */
function statusText(s) {
  if (s === 'CREATED') return '待支付'
  if (s === 'PAID') return '待发货'
  if (s === 'SHIPPED') return '已发货'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'CANCELLED') return '已取消'
  return s
}

/**
 * @param {string} s
 */
function statusClass(s) {
  if (s === 'CREATED') return 'status--created'
  if (s === 'PAID') return 'status--ship'
  if (s === 'SHIPPED') return 'status--shipped'
  if (s === 'COMPLETED') return 'status--done'
  if (s === 'CANCELLED') return 'status--cancel'
  return ''
}

/**
 * 同步待处理标识并通知顶栏刷新。
 */
async function loadTodoBadges() {
  const res = await api.merchantOrderTodoBadges(merchantId.value)
  if (res.code === 200 && res.data) {
    orderTodos.value = {
      pendingShipment: Boolean(res.data.pendingShipment),
    }
  }
  window.dispatchEvent(new CustomEvent('petshop-merchant-order-todo-updated'))
}

async function loadOrders() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.merchantGetOrders(merchantId.value, statusFilter.value)
  if (res.code === 200) {
    orders.value = res.data
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
  await loadTodoBadges()
}

/**
 * @param {string} value
 */
function selectFilter(value) {
  statusFilter.value = value
  page.value = 1
  loadOrders()
}

async function updateStatus(orderId, status) {
  const res = await api.merchantUpdateOrderStatus(merchantId.value, orderId, status)
  if (res.code === 200) {
    await loadOrders()
  } else {
    showAppMessage(res.message || '状态更新失败', '提示')
  }
}

onMounted(() => {
  loadOrders()
  todoPollTimer = setInterval(loadTodoBadges, 15000)
})

onUnmounted(() => {
  if (todoPollTimer) clearInterval(todoPollTimer)
  todoPollTimer = null
})
</script>

<template>
  <div class="merchant-page">
    <h2>我的订单管理</h2>
    <p class="desc">
      当前商家ID：{{ merchantId || '-' }}。有待发货订单时，「待发货」筛选与顶栏会显示橙色提示点。
    </p>

    <div class="toolbar">
      <div class="filter-tabs" role="tablist" aria-label="订单状态筛选">
        <button
          v-for="tab in STATUS_TABS"
          :key="tab.value === '' ? 'all' : tab.value"
          type="button"
          class="filter-tab"
          :class="{ 'filter-tab--active': statusFilter === tab.value }"
          role="tab"
          :aria-selected="statusFilter === tab.value"
          @click="selectFilter(tab.value)"
        >
          <span class="filter-tab-label">{{ tab.label }}</span>
          <span
            v-if="tab.dot === 'ship' && orderTodos.pendingShipment"
            class="tab-dot tab-dot--ship"
            title="有待发货订单"
            aria-hidden="true"
          />
        </button>
      </div>
      <button type="button" class="refresh-btn" @click="loadOrders">刷新</button>
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
        <tr
          v-for="item in pagedOrders"
          :key="item.orderId"
          :class="{ 'row--ship': item.status === 'PAID' }"
        >
          <td>{{ item.orderId }}</td>
          <td>{{ item.orderNo }}</td>
          <td>{{ item.userId }}</td>
          <td>{{ item.itemCount ?? '-' }}</td>
          <td>¥{{ item.payAmount }}</td>
          <td>
            <span class="status" :class="statusClass(item.status)">
              <i v-if="item.status === 'PAID'" class="cell-dot cell-dot--ship" aria-hidden="true" />
              {{ statusText(item.status) }}
            </span>
          </td>
          <td>{{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '-' }}</td>
          <td>
            <button v-if="item.status === 'PAID'" type="button" @click="updateStatus(item.orderId, 'SHIPPED')">
              发货
            </button>
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
  line-height: 1.5;
}
.toolbar {
  margin: 10px 0 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.filter-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #ccd7e6;
  background: #fff;
  color: #33465f;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.filter-tab:hover {
  border-color: #0b1630;
  background: #f8fafc;
}

.filter-tab--active {
  background: #0b1630;
  color: #f4f6fb;
  border-color: #0b1630;
}

.filter-tab-label {
  line-height: 1;
}

.tab-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.9);
}

.filter-tab--active .tab-dot {
  box-shadow: 0 0 0 1px #0b1630;
}

.tab-dot--ship {
  background: #ffc069;
}

.filter-tab--active .tab-dot--ship {
  background: #ffd591;
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

.row--ship {
  box-shadow: inset 3px 0 0 #fa8c16;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 700;
}

.cell-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.cell-dot--ship {
  background: #fa8c16;
}

.status--created {
  background: #f0f2f5;
  color: #5c6b7a;
}
.status--ship {
  background: #fff7e6;
  color: #d46b08;
}
.status--shipped {
  background: #e6f7ff;
  color: #0958d9;
}
.status--done {
  background: #f6ffed;
  color: #389e0d;
}
.status--cancel {
  background: #f5f5f5;
  color: #595959;
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
