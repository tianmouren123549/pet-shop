<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'
import { formatYuan } from '../utils/formatYuan.js'
import { showAppMessage } from '../utils/appMessage'
import PaginationBar from '../components/PaginationBar.vue'
import ConfirmModal from '../components/ConfirmModal.vue'

/** 与商家端 Seller Portal 一致：全部 / 处理中 / 已发货 / 异常 */
const TABLE_TABS = [
  { id: 'all', label: '全部订单' },
  { id: 'processing', label: '处理中' },
  { id: 'shipped', label: '已发货' },
  { id: 'exceptions', label: '异常' },
]

/** 兼容旧链接 ?tab=CREATED 等 */
const LEGACY_TAB_TO_BUCKET = {
  ALL: 'all',
  CREATED: 'processing',
  PAID: 'processing',
  SHIPPED: 'shipped',
  COMPLETED: 'shipped',
  CANCELLED: 'exceptions',
}

const ORDER_PAGE_SIZE = 6

const route = useRoute()
const router = useRouter()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const bulkBannerText = ref('')
const loading = ref(false)
const errorMsg = ref('')
const orders = ref([])
const tableTab = ref('all')
const orderSearch = ref('')
const appliedOrderSearch = ref('')
const page = ref(1)
const pageSize = ref(ORDER_PAGE_SIZE)

const payConfirmOpen = ref(false)
const receiveConfirmOpen = ref(false)
const actionOrder = ref(/** @type {Record<string, unknown> | null} */ (null))

function runOrderSearch() {
  appliedOrderSearch.value = String(orderSearch.value || '').trim()
  page.value = 1
}

const filteredByTab = computed(() => {
  const list = orders.value
  switch (tableTab.value) {
    case 'processing':
      return list.filter((o) => ['CREATED', 'PAID'].includes(o.status))
    case 'shipped':
      return list.filter((o) => ['SHIPPED', 'COMPLETED'].includes(o.status))
    case 'exceptions':
      return list.filter((o) => o.status === 'CANCELLED')
    default:
      return list
  }
})

const filteredOrders = computed(() => {
  const list = filteredByTab.value
  const q = String(appliedOrderSearch.value || '').trim().toLowerCase()
  if (!q) return list
  return list.filter((o) => {
    const no = String(o.orderNo || '').toLowerCase()
    const id = String(o.orderId ?? '')
    return no.includes(q) || id.includes(q)
  })
})

watch(tableTab, () => {
  page.value = 1
})

watch(appliedOrderSearch, () => {
  page.value = 1
})

const total = computed(() => (Array.isArray(filteredOrders.value) ? filteredOrders.value.length : 0))

watch([total, pageSize], () => {
  const tp = Math.max(1, Math.ceil(Number(total.value || 0) / Math.max(1, Number(pageSize.value || 1))))
  if (page.value > tp) page.value = tp
})

const pagedOrders = computed(() => {
  const list = Array.isArray(filteredOrders.value) ? filteredOrders.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
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

function fulfillmentLabel(s) {
  if (s === 'CREATED') return '待支付'
  if (s === 'PAID') return '待发货'
  if (s === 'SHIPPED') return '运输中'
  if (s === 'COMPLETED') return '已送达'
  if (s === 'CANCELLED') return '已取消'
  return String(s || '—')
}

function fulfillmentClass(s) {
  if (s === 'CREATED') return 'fulfill--muted'
  if (s === 'PAID') return 'fulfill--process'
  if (s === 'SHIPPED') return 'fulfill--transit'
  if (s === 'COMPLETED') return 'fulfill--done'
  if (s === 'CANCELLED') return 'fulfill--exception'
  return ''
}

function logisticsCarrierShort(item) {
  const no = String(item.logisticsNo || '').trim()
  if (!no) return ''
  if (/^sf/i.test(no)) return '顺丰'
  if (/^(yt|yuantong)/i.test(no)) return '圆通'
  if (/^zto/i.test(no)) return '中通'
  if (/^sto/i.test(no)) return '申通'
  if (/^jd/i.test(no)) return '京东'
  if (/^(ems|邮政)/i.test(no)) return 'EMS'
  return '快递'
}

function formatOrderDate(item) {
  const raw = item.createdAt
  if (!raw) return '—'
  try {
    return new Date(raw).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return '—'
  }
}

function formatStatInt(n) {
  return Number(n || 0).toLocaleString('zh-CN')
}

const statMax = computed(() => {
  const list = orders.value
  const pendingPay = list.filter((o) => o.status === 'CREATED').length
  const toShip = list.filter((o) => o.status === 'PAID').length
  const shippedDone = list.filter((o) => ['SHIPPED', 'COMPLETED'].includes(o.status)).length
  const cancelled = list.filter((o) => o.status === 'CANCELLED').length
  return Math.max(1, pendingPay, toShip, shippedDone, cancelled)
})

const summaryCards = computed(() => {
  const list = orders.value
  const pendingPay = list.filter((o) => o.status === 'CREATED').length
  const toShip = list.filter((o) => o.status === 'PAID').length
  const shippedDone = list.filter((o) => ['SHIPPED', 'COMPLETED'].includes(o.status)).length
  const cancelled = list.filter((o) => o.status === 'CANCELLED').length
  const max = statMax.value
  const totalN = Math.max(1, list.length)

  const pct = (n) => Math.min(100, Math.round((n / max) * 100))
  const share = (n) => Math.round((n / totalN) * 100)

  return [
    {
      key: 'pay',
      title: '待支付',
      value: pendingPay,
      badge: pendingPay > 0 ? `占全部 ${share(pendingPay)}%` : null,
      badgeKind: 'blue',
      hint: null,
      barClass: 'stat-fill--ink',
      pct: pct(pendingPay),
    },
    {
      key: 'ship',
      title: '待发货',
      value: toShip,
      badge: toShip > 0 ? '待处理' : null,
      badgeKind: 'amber',
      hint: null,
      barClass: 'stat-fill--cocoa',
      pct: pct(toShip),
    },
    {
      key: 'shipped',
      title: '已发货',
      value: shippedDone,
      badge: null,
      badgeKind: '',
      hint: '含运输中与已送达',
      barClass: 'stat-fill--ink',
      pct: pct(shippedDone),
    },
    {
      key: 'ex',
      title: '异常订单',
      value: cancelled,
      badge: cancelled > 0 ? '已取消' : null,
      badgeKind: 'danger',
      hint: null,
      barClass: 'stat-fill--rose',
      pct: pct(cancelled),
    },
  ]
})

const totalOrderCount = computed(() => (orders.value || []).length)
const pendingPayCount = computed(() => (orders.value || []).filter((o) => o.status === 'CREATED').length)
const pendingShipCount = computed(() => (orders.value || []).filter((o) => o.status === 'PAID').length)
const currentTabLabel = computed(() => TABLE_TABS.find((t) => t.id === tableTab.value)?.label ?? '')

const listRangeLabel = computed(() => {
  const n = filteredOrders.value.length
  if (!n) return ''
  const head = currentTabLabel.value ? `${currentTabLabel.value}，` : ''
  const q = String(appliedOrderSearch.value || '').trim()
  const ps = Math.max(1, Number(pageSize.value || 1))
  const tp = Math.max(1, Math.ceil(n / ps))
  const p = Math.max(1, Math.min(Number(page.value || 1), tp))
  const start = n ? (p - 1) * ps + 1 : 0
  const end = Math.min(p * ps, n)
  if (q) return `${head}共 ${n} 条匹配 · 本页 ${start}–${end} · 第 ${p}/${tp} 页`
  return `${head}本页 ${start}–${end}，共 ${n} 笔 · 第 ${p}/${tp} 页`
})

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

function applyOrderTabFromQuery() {
  const raw = String(route.query.tab || '').toUpperCase()
  const mapped = LEGACY_TAB_TO_BUCKET[raw]
  if (mapped) tableTab.value = mapped
}

watch(
  () => route.query.tab,
  () => applyOrderTabFromQuery(),
)

function dismissBulkBanner() {
  bulkBannerText.value = ''
}

function openPayModal(o) {
  actionOrder.value = o
  payConfirmOpen.value = true
}

async function submitPay() {
  if (!actionOrder.value) return
  const id = Number(actionOrder.value.orderId)
  const res = await api.userPayOrder(id, userId.value)
  payConfirmOpen.value = false
  actionOrder.value = null
  if (res.code === 200) {
    await loadOrders()
  } else {
    showAppMessage(res.message || '支付失败', '提示')
  }
}

function openReceiveModal(o) {
  actionOrder.value = o
  receiveConfirmOpen.value = true
}

async function submitReceive() {
  if (!actionOrder.value) return
  const id = Number(actionOrder.value.orderId)
  const res = await api.userConfirmOrder(id, userId.value)
  receiveConfirmOpen.value = false
  actionOrder.value = null
  if (res.code === 200) {
    await loadOrders()
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

onMounted(async () => {
  if (!userId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  applyOrderTabFromQuery()
  await loadOrders()
  if (route.query.from === 'bulk-checkout' && route.query.n) {
    bulkBannerText.value = '结算已完成。请在下方列表中查看刚生成的订单并完成支付。'
    router.replace({ path: '/orders', query: {} })
  }
})
</script>

<template>
  <div class="orders-dash">
    <div v-if="bulkBannerText" class="orders-bulk-banner" role="status">
      <span class="orders-bulk-banner-text">{{ bulkBannerText }}</span>
      <button type="button" class="orders-bulk-dismiss" @click="dismissBulkBanner">知道了</button>
    </div>

    <header class="mo-hero">
      <div class="mo-toolbar" role="toolbar" aria-label="订单概览与搜索">
        <div class="mo-hero-main">
          <div class="mo-hero-chips" aria-label="我的订单概览">
            <span class="mo-chip">我的订单</span>
            <span class="mo-chip">共 {{ totalOrderCount }} 笔</span>
            <span v-if="pendingPayCount > 0" class="mo-chip mo-chip--amber">待支付 {{ pendingPayCount }}</span>
            <span v-if="pendingShipCount > 0" class="mo-chip mo-chip--warn">待发货 {{ pendingShipCount }}</span>
          </div>
        </div>
        <div class="mo-hero-aside">
          <div class="mo-search mo-search--hero" role="search">
            <svg class="mo-search-ic" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                d="M11 19a8 8 0 100-16 8 8 0 000 16zm10 2l-4.35-4.35"
              />
            </svg>
            <input
              v-model.trim="orderSearch"
              type="search"
              class="mo-search-input"
              placeholder="搜索订单号、订单 ID"
              autocomplete="off"
              enterkeyhint="search"
              @keyup.enter="runOrderSearch"
            />
          </div>
          <button type="button" class="mo-btn-refresh mo-btn-search" title="按关键字筛选列表" @click="runOrderSearch">
            搜索
          </button>
          <button type="button" class="mo-btn-refresh" title="刷新列表" @click="loadOrders">刷新</button>
        </div>
      </div>
    </header>

    <section class="stat-grid" aria-label="订单数据概览">
      <div v-for="card in summaryCards" :key="card.key" class="stat-card">
        <div class="stat-card-head">
          <span class="stat-card-title">{{ card.title }}</span>
        </div>
        <div class="stat-card-mid">
          <span class="stat-card-value">{{ formatStatInt(card.value) }}</span>
          <div class="stat-card-meta">
            <span v-if="card.badge" class="stat-pill" :class="`stat-pill--${card.badgeKind}`">{{ card.badge }}</span>
            <span v-else-if="card.hint" class="stat-hint">{{ card.hint }}</span>
          </div>
        </div>
        <div class="stat-card-bar">
          <div class="stat-card-bar-fill" :class="card.barClass" :style="{ width: `${card.pct}%` }" />
        </div>
      </div>
    </section>

    <section class="mo-panel">
      <div class="mo-panel-head">
        <div class="mo-toolbar-actions">
          <RouterLink to="/cart" class="btn btn-outline btn-with-icon" title="前往购物车">
            <svg class="btn-icon-svg" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M6 6h15l-1.5 9h-12L6 6zm0 0L5 3H2"
              />
              <circle cx="9" cy="20" r="1" fill="currentColor" />
              <circle cx="18" cy="20" r="1" fill="currentColor" />
            </svg>
            购物车
          </RouterLink>
          <RouterLink to="/products" class="btn btn-outline btn-with-icon" title="去商城选购">
            <svg class="btn-icon-svg" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M3 9l9-6 9 6v10a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"
              />
            </svg>
            去选购
          </RouterLink>
        </div>
        <nav class="mo-table-tabs" aria-label="列表筛选">
          <button
            v-for="tab in TABLE_TABS"
            :key="tab.id"
            type="button"
            class="mo-table-tab"
            :class="{ 'mo-table-tab--active': tableTab === tab.id }"
            @click="tableTab = tab.id"
          >
            {{ tab.label }}
          </button>
        </nav>
      </div>

      <div v-if="loading" class="state-msg mo-panel-inset">加载中…</div>
      <div v-else-if="errorMsg" class="state-msg state-msg--err mo-panel-inset">{{ errorMsg }}</div>
      <div v-else-if="!filteredOrders.length" class="orders-empty-block mo-panel-inset">
        <p class="orders-empty-title">暂无订单</p>
        <p class="orders-empty-desc">还没有符合当前筛选的订单记录。</p>
        <div class="orders-empty-actions">
          <RouterLink to="/products" class="btn btn-primary-link">去选购</RouterLink>
          <button v-if="appliedOrderSearch || tableTab !== 'all'" type="button" class="btn btn-outline" @click="tableTab = 'all'; appliedOrderSearch = ''; orderSearch = ''">
            查看全部
          </button>
        </div>
      </div>

      <div v-else class="mo-panel-body">
        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>日期</th>
                <th class="th-num">金额</th>
                <th>履约状态</th>
                <th class="th-carrier">物流</th>
                <th class="th-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in pagedOrders"
                :key="item.orderId"
                :class="{ 'tr--urgent': item.status === 'CREATED' || item.status === 'PAID' }"
              >
                <td>
                  <RouterLink class="order-link" :to="`/order/${item.orderId}`" :title="String(item.orderNo || item.orderId)">
                    #{{ item.orderNo || item.orderId }}
                  </RouterLink>
                  <div class="order-id-sub">共 {{ item.itemCount ?? '—' }} 件 · {{ statusText(item.status) }}</div>
                </td>
                <td class="td-date">{{ formatOrderDate(item) }}</td>
                <td class="td-num">¥{{ formatYuan(item.payAmount) }}</td>
                <td>
                  <span class="fulfill" :class="fulfillmentClass(item.status)">
                    <i class="fulfill-dot" />
                    {{ fulfillmentLabel(item.status) }}
                  </span>
                </td>
                <td class="td-carrier">
                  <span v-if="logisticsCarrierShort(item)" class="carrier-chip">{{ logisticsCarrierShort(item) }}</span>
                  <span v-else class="carrier-dash">—</span>
                </td>
                <td class="td-actions">
                  <div class="row-actions" role="group" :aria-label="`订单 ${item.orderId} 操作`">
                    <RouterLink class="btn-row btn-row--link" :to="`/order/${item.orderId}`">详情</RouterLink>
                    <button
                      v-if="item.status === 'CREATED'"
                      type="button"
                      class="btn-row btn-row--primary"
                      @click.stop="openPayModal(item)"
                    >
                      支付
                    </button>
                    <button
                      v-else-if="item.status === 'SHIPPED'"
                      type="button"
                      class="btn-row btn-row--primary"
                      @click.stop="openReceiveModal(item)"
                    >
                      收货
                    </button>
                    <span v-else class="row-actions__spacer" aria-hidden="true" />
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="mo-panel-footer mo-panel-footer--stack">
          <span class="mo-panel-footer__meta">{{ listRangeLabel }}</span>
          <div class="orders-pagination-wrap">
            <PaginationBar
              :page="page"
              :page-size="pageSize"
              :total="total"
              :show-range-meta="false"
              @update:page="page = $event"
            />
          </div>
        </footer>
      </div>
    </section>

    <ConfirmModal
      :open="payConfirmOpen"
      title="确认支付"
      confirm-label="确认支付"
      @update:open="payConfirmOpen = $event"
      @confirm="submitPay"
    >
      <p>确认支付本订单？当前为平台内支付流程，不会产生银行卡或第三方支付平台的真实扣款。确定继续吗？</p>
    </ConfirmModal>

    <ConfirmModal
      :open="receiveConfirmOpen"
      title="确认收货"
      confirm-label="确认收货"
      @update:open="receiveConfirmOpen = $event"
      @confirm="submitReceive"
    >
      <p>确认已收到全部货物吗？</p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.orders-dash {
  min-height: 100%;
  padding: 0 20px 24px;
  max-width: 1600px;
  margin: 0 auto;
  background: #f8f9fa;
  box-sizing: border-box;
}

.orders-bulk-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 14px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.06);
  color: #475569;
  font-size: 14px;
  line-height: 1.55;
}

.orders-bulk-banner-text {
  flex: 1;
  min-width: 200px;
  font-weight: 600;
}

.orders-bulk-dismiss {
  flex-shrink: 0;
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  border: 1px solid #0f172a;
  background: #0f172a;
  color: #f8fafc;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  min-width: 0;
  background: #ffffff;
  border-radius: 14px;
  padding: 18px 20px 16px;
  border: 1px solid #eceff3;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}

.stat-card-head {
  margin-bottom: 10px;
}

.stat-card-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #64748b;
  text-transform: none;
}

.stat-card-mid {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  min-height: 40px;
}

.stat-card-value {
  font-size: clamp(26px, 2.4vw, 32px);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
  line-height: 1.05;
}

.stat-card-meta {
  flex-shrink: 0;
  text-align: right;
  max-width: 46%;
}

.stat-pill {
  display: inline-block;
  font-size: 11px;
  font-weight: 800;
  padding: 4px 10px;
  border-radius: 999px;
  line-height: 1.2;
}

.stat-pill--blue {
  background: #e0f2fe;
  color: #0369a1;
}

.stat-pill--amber {
  background: #fef3c7;
  color: #b45309;
}

.stat-pill--danger {
  background: #ffe4e6;
  color: #be123c;
}

.stat-hint {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  line-height: 1.35;
}

.stat-card-bar {
  height: 6px;
  border-radius: 999px;
  background: #eef2f7;
  overflow: hidden;
}

.stat-card-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.35s ease;
}

.stat-fill--ink {
  background: #0f172a;
}

.stat-fill--cocoa {
  background: #92400e;
}

.stat-fill--rose {
  background: #e11d48;
}

.mo-hero {
  padding: 0;
  margin-bottom: 14px;
  border-bottom: none;
}

.mo-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 16px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.06);
}

.mo-hero-main {
  flex: 1 1 200px;
  min-width: 0;
}

.mo-hero-aside {
  flex: 1 1 260px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  max-width: 520px;
  min-width: 0;
}

.mo-hero-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mo-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  background: #fff;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.mo-chip--amber {
  color: #9a3412;
  background: #fffbeb;
  border-color: #fde68a;
}

.mo-chip--warn {
  color: #c2410c;
  background: #fff7ed;
  border-color: #fed7aa;
}

.mo-btn-refresh {
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  border: 1px solid #0f172a;
  background: #0f172a;
  font-size: 13px;
  font-weight: 800;
  color: #f8fafc;
  cursor: pointer;
  flex-shrink: 0;
}

.mo-btn-refresh.mo-btn-search {
  background: #fff;
  color: #0f172a;
  border-color: #cbd5e1;
}

.mo-btn-refresh.mo-btn-search:hover {
  background: #f8fafc;
  border-color: #94a3b8;
}

.mo-search--hero {
  flex: 1;
  min-width: 0;
  height: 42px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}

.mo-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px 0 14px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  min-width: min(280px, 100%);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.mo-search-ic {
  flex-shrink: 0;
  color: #64748b;
}

.mo-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  outline: none;
}

.mo-search-input::placeholder {
  color: #94a3b8;
  font-weight: 500;
}

.mo-panel {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.mo-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px 14px;
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 100%);
  border-bottom: 1px solid #eef2f7;
}

.mo-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.mo-panel-inset {
  margin: 0 18px 16px;
}

.mo-panel-body {
  padding: 0 18px 16px;
}

.mo-panel-footer {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding-top: 14px;
  margin-top: 2px;
  border-top: 1px solid #eef2f7;
}

.mo-panel-footer--stack {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.mo-panel-footer__meta {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.02em;
}

.orders-pagination-wrap :deep(.pw-pagination) {
  justify-content: center;
  border: none !important;
  background: transparent !important;
  border-radius: 0 !important;
  padding: 8px 0 0 !important;
  margin-top: 0;
  box-shadow: none !important;
}

.orders-pagination-wrap :deep(.pw-pagination--no-meta .pw-pagination-right) {
  justify-content: center;
}

.orders-pagination-wrap :deep(.pw-page-btn),
.orders-pagination-wrap :deep(.pw-page-num) {
  min-width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 700;
}

.orders-pagination-wrap :deep(.pw-page-num.active) {
  border-color: #0f172a;
  background: #0f172a;
  color: #fff;
}

.btn {
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.btn-outline {
  background: #fff;
  color: #334155;
  border-color: #cbd5e1;
}

.btn-outline:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.btn-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn-icon-svg {
  flex-shrink: 0;
  opacity: 0.85;
}

.mo-table-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-left: auto;
  justify-content: flex-end;
  flex-shrink: 0;
  padding: 4px;
  border-radius: 12px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.mo-table-tab {
  padding: 8px 14px;
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  color: #64748b;
  cursor: pointer;
  border-radius: 10px;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.mo-table-tab:hover {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.85);
}

.mo-table-tab--active {
  color: #0f172a;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.state-msg {
  padding: 28px 18px;
  text-align: center;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.state-msg--err {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.orders-empty-block {
  padding: 32px 20px;
  text-align: center;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.orders-empty-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.orders-empty-desc {
  margin: 0 0 20px;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  line-height: 1.55;
}

.orders-empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.btn-primary-link {
  height: 42px;
  padding: 0 22px;
  border-radius: 12px;
  border: 1px solid #0f172a;
  background: #0f172a;
  color: #f8fafc;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-primary-link:hover {
  filter: brightness(1.06);
}

.table-scroll {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  margin-bottom: 0;
}

.data-table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table tbody tr {
  transition: background 0.12s ease;
}

.data-table tbody tr:hover {
  background: #f8fafc;
}

.data-table th,
.data-table td {
  padding: 13px 14px;
  border-bottom: 1px solid #eef2f7;
  text-align: left;
  vertical-align: middle;
  color: #1e293b;
}

.data-table th {
  font-size: 12px;
  font-weight: 800;
  color: #475569;
  letter-spacing: 0.02em;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
}

.th-num,
.td-num {
  text-align: right;
  font-weight: 800;
  color: #0f172a;
  white-space: nowrap;
}

.th-actions,
.td-actions {
  width: 1%;
  min-width: 176px;
  text-align: right;
  vertical-align: middle;
  white-space: nowrap;
}

.tr--urgent {
  box-shadow: inset 3px 0 0 #f97316;
}

.order-link {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  font-weight: 900;
  font-size: 13px;
  color: #0f172a;
  text-decoration: none;
}

.order-link:hover {
  color: #2563eb;
  text-decoration: underline;
}

.order-id-sub {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-top: 3px;
}

.td-date {
  color: #334155;
  font-weight: 600;
  white-space: nowrap;
}

.fulfill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.fulfill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.fulfill--muted {
  background: #f1f5f9;
  color: #64748b;
}
.fulfill--process {
  background: #ffedd5;
  color: #c2410c;
}
.fulfill--transit {
  background: #dbeafe;
  color: #1d4ed8;
}
.fulfill--done {
  background: #dcfce7;
  color: #15803d;
}
.fulfill--exception {
  background: #fee2e2;
  color: #b91c1c;
}

.th-carrier,
.td-carrier {
  white-space: nowrap;
}

.carrier-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  padding: 5px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #475569;
  background: #fff;
}

.carrier-dash {
  color: #94a3b8;
  font-weight: 700;
}

.row-actions {
  --action-w: 78px;
  display: grid;
  grid-template-columns: var(--action-w) var(--action-w);
  gap: 10px;
  justify-content: end;
  margin-left: auto;
  width: fit-content;
  align-items: center;
}

.row-actions__spacer {
  width: 100%;
  min-height: 36px;
  border-radius: 10px;
  visibility: hidden;
  pointer-events: none;
  box-sizing: border-box;
}

.btn-row {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  text-decoration: none;
  box-sizing: border-box;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.btn-row:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.btn-row--link:hover {
  border-color: #cbd5e1;
  color: #0f172a;
  background: #f8fafc;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.btn-row--primary {
  border-color: #0f172a;
  background: #0f172a;
  color: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.12);
}

.btn-row--primary:active {
  transform: translateY(0.5px);
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .mo-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .mo-hero-aside {
    justify-content: stretch;
    max-width: none;
    width: 100%;
  }

  .mo-search--hero {
    max-width: none;
  }

  .mo-panel-head {
    flex-direction: column;
    align-items: stretch;
  }

  .mo-toolbar-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .mo-table-tabs {
    margin-left: 0;
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
