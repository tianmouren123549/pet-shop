<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import PaginationBar from '../components/PaginationBar.vue'

const route = useRoute()
const router = useRouter()
const userId = ref(Number(localStorage.getItem('userId') || 0))
/** 从购物车「一键结算全部」跳转后的说明条 */
const bulkBannerText = ref('')
const loading = ref(false)
const errorMsg = ref('')
const orders = ref([])
const tab = ref('ALL') // ALL | CREATED | PAID | SHIPPED | COMPLETED | CANCELLED
const page = ref(1)
const pageSize = ref(8)

const filteredOrders = computed(() => {
  if (tab.value === 'ALL') return orders.value
  return orders.value.filter((o) => o.status === tab.value)
})

watch(tab, () => {
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
  pageSize.value = Number(n || 8)
  page.value = 1
}

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
  errorMsg.value = ''
  const res = await api.userGetOrders(userId.value)
  if (res.code === 200) {
    orders.value = res.data || []
  } else {
    errorMsg.value = res.message || '订单加载失败'
    orders.value = []
  }
  loading.value = false
}

onMounted(async () => {
  if (!userId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  await loadOrders()
  const n = route.query.n
  if (route.query.from === 'bulk-checkout' && n) {
    bulkBannerText.value = `结算已完成。请在下方「我的订单」中查看刚生成的订单并完成支付。`
    router.replace({ path: '/orders', query: {} })
  }
})

function dismissBulkBanner() {
  bulkBannerText.value = ''
}
</script>

<template>
  <div class="pw-page orders-page">
    <section class="pw-hero orders-hero">
      <h1 class="pw-title">我的订单</h1>
      <p class="pw-lead">查看下单记录与订单状态（待支付、待发货、已完成等）。</p>
    </section>

    <div v-if="bulkBannerText" class="orders-bulk-banner" role="status">
      <span class="orders-bulk-banner-text">{{ bulkBannerText }}</span>
      <button type="button" class="orders-bulk-dismiss pw-btn-ghost pw-btn-sm" @click="dismissBulkBanner">
        知道了
      </button>
    </div>

    <section class="pw-section orders-tabs">
      <div class="pw-toolbar pw-toolbar--tight">
        <button type="button" class="tab-btn" :class="{ active: tab === 'ALL' }" @click="tab = 'ALL'">全部</button>
        <button type="button" class="tab-btn" :class="{ active: tab === 'CREATED' }" @click="tab = 'CREATED'">待支付</button>
        <button type="button" class="tab-btn" :class="{ active: tab === 'PAID' }" @click="tab = 'PAID'">待发货</button>
        <button type="button" class="tab-btn" :class="{ active: tab === 'SHIPPED' }" @click="tab = 'SHIPPED'">已发货</button>
        <button type="button" class="tab-btn" :class="{ active: tab === 'COMPLETED' }" @click="tab = 'COMPLETED'">已完成</button>
        <button type="button" class="tab-btn" :class="{ active: tab === 'CANCELLED' }" @click="tab = 'CANCELLED'">已取消</button>
        <div class="orders-tabs-spacer"></div>
        <button type="button" class="pw-btn-ghost pw-btn-sm" @click="loadOrders">刷新</button>
      </div>
    </section>

    <div v-if="loading" class="pw-state">订单加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">
      <div class="orders-error">{{ errorMsg }}</div>
      <button type="button" class="pw-btn pw-btn-sm" @click="loadOrders">重试</button>
    </div>
    <div v-else-if="filteredOrders.length === 0" class="pw-state pw-state--empty">
      <div class="orders-empty-title">暂无订单</div>
      <button type="button" class="pw-btn pw-btn-sm" @click="$router.push('/products')">去选购</button>
    </div>

    <div v-else class="list">
      <div v-for="o in pagedOrders" :key="o.orderId" class="card" @click="$router.push(`/order/${o.orderId}`)">
        <div class="row">
          <div class="left">
            <div class="no">订单号：{{ o.orderNo }}</div>
            <div class="meta">
              <span>商品数：{{ o.itemCount }}</span>
              <span>下单时间：{{ new Date(o.createdAt).toLocaleString() }}</span>
            </div>
          </div>
          <div class="right">
            <div class="amount">¥{{ o.payAmount }}</div>
            <span class="status" :class="o.status.toLowerCase()">{{ statusText(o.status) }}</span>
          </div>
        </div>
        <div class="hint">点击查看订单详情</div>
      </div>
    </div>

    <PaginationBar
      :page="page"
      :page-size="pageSize"
      :total="total"
      :page-size-options="[6, 8, 12, 20]"
      @update:page="page = $event"
      @update:page-size="setPageSize"
    />
  </div>
</template>

<style scoped>
.orders-page { width: 100%; margin: 0 auto; }
.orders-hero { margin-bottom: 14px; }

.orders-bulk-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 14px;
  padding: 12px 14px;
  background: #e8f4ff;
  border: 1px solid #91caff;
  border-radius: 4px;
  color: #0958d9;
  font-size: 13px;
  line-height: 1.55;
}
.orders-bulk-banner-text { flex: 1; min-width: 200px; font-weight: 600; }
.orders-bulk-dismiss { flex-shrink: 0; }

.orders-tabs { padding: 12px 12px; }
.orders-tabs-spacer { flex: 1; }
.tab-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid transparent;
  background: transparent;
  color: #5e6e84;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}
.tab-btn.active {
  background: #0b1630;
  border-color: #0b1630;
  color: #f4f6fb;
}

.list { display: flex; flex-direction: column; gap: 12px; }
.card {
  background: #f9fbfe;
  border: 1px solid #dce4ef;
  border-radius: 2px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.card:hover { transform: translateY(-1px); box-shadow: 0 10px 24px rgba(10, 28, 54, 0.08); }
.row { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.no { font-weight: 800; color: #0e1d33; margin-bottom: 6px; }
.meta { display: flex; flex-wrap: wrap; gap: 14px; color: #6c7d93; font-size: 12px; }
.right { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.amount { font-size: 20px; font-weight: 800; color: #0b1730; }
.status { padding: 2px 10px; border-radius: 2px; font-size: 11px; font-weight: 800; border: 1px solid #d9e1ec; background: #eef2f7; color: #304862; }
.status.created { background: #fff7e6; border-color: #ffd591; color: #ad6800; }
.status.paid { background: #e6f7ff; border-color: #91d5ff; color: #096dd9; }
.status.shipped { background: #f6ffed; border-color: #b7eb8f; color: #237804; }
.status.completed { background: #f0f5ff; border-color: #adc6ff; color: #1d39c4; }
.status.cancelled { background: #fff1f1; border-color: #f0c1c1; color: #a73636; }
.hint { margin-top: 10px; color: #8b97aa; font-size: 12px; }
</style>

