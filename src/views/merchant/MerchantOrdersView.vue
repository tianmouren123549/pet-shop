<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'
import { showAppMessage } from '../../utils/appMessage'

/** 与 Seller Portal 一致：全部 / 处理中 / 已发货 / 异常 */
const TABLE_TABS = [
  { id: 'all', label: '全部订单' },
  { id: 'processing', label: '处理中' },
  { id: 'shipped', label: '已发货' },
  { id: 'exceptions', label: '异常' },
]

const orders = ref([])
/** 输入框草稿；筛选使用 {@link appliedOrderSearch}，须按 Enter 或点「搜索」生效 */
const orderSearch = ref('')
const appliedOrderSearch = ref('')
const tableTab = ref('all')
const loading = ref(false)
const errorMsg = ref('')
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const orderTodos = ref({ pendingShipment: false })
const selectedIds = ref(/** @type {Set<number>} */ (new Set()))
const batchSubmitting = ref(false)
const headerCheckboxRef = ref(/** @type {HTMLInputElement | null} */ (null))

let todoPollTimer = null

/**
 * 始终拉取本店相关全部订单，统计与 Tab 在前端完成（单次请求、与参考稿统计卡片一致）。
 */
async function loadOrders() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.merchantGetOrders(merchantId.value, '')
  if (res.code === 200) {
    orders.value = Array.isArray(res.data) ? res.data : []
  } else {
    errorMsg.value = res.message || '加载失败'
    orders.value = []
  }
  loading.value = false
  await loadTodoBadges()
}

async function loadTodoBadges() {
  const res = await api.merchantOrderTodoBadges(merchantId.value)
  if (res.code === 200 && res.data) {
    orderTodos.value = {
      pendingShipment: Boolean(res.data.pendingShipment),
    }
  }
  window.dispatchEvent(new CustomEvent('petshop-merchant-order-todo-updated'))
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

/**
 * @param {Record<string, unknown>} item
 */
function customerName(item) {
  return String(item.buyerNickname || item.userNickname || '').trim() || `用户 #${item.userId ?? '—'}`
}

/**
 * @param {Record<string, unknown>} item
 */
function customerInitials(item) {
  const name = customerName(item)
  if (!name || name.startsWith('用户')) {
    const id = String(item.userId ?? '?')
    return id.slice(-1)
  }
  if (/^[a-z]/i.test(name)) return name.slice(0, 2).toUpperCase()
  return name.slice(0, 1)
}

function runOrderSearch() {
  appliedOrderSearch.value = String(orderSearch.value || '').trim()
}

/** Tab + 顶栏搜索（订单号 / ID / 买家） */
const filteredOrders = computed(() => {
  const list = filteredByTab.value
  const q = String(appliedOrderSearch.value || '').trim().toLowerCase()
  if (!q) return list
  return list.filter((o) => {
    const no = String(o.orderNo || '').toLowerCase()
    const id = String(o.orderId ?? '')
    const name = customerName(o).toLowerCase()
    return no.includes(q) || id.includes(q) || name.includes(q)
  })
})

/** 进度条比例：相对四类中的最大值，避免全为 0 时除零 */
const statMax = computed(() => {
  const list = orders.value
  const pendingPay = list.filter((o) => o.status === 'CREATED').length
  const toShip = list.filter((o) => o.status === 'PAID').length
  const shippedDone = list.filter((o) => ['SHIPPED', 'COMPLETED'].includes(o.status)).length
  const cancelled = list.filter((o) => o.status === 'CANCELLED').length
  return Math.max(1, pendingPay, toShip, shippedDone, cancelled)
})

/**
 * 参考稿风格：标题 + 大数字 + 角标/说明 + 底部分段条（中文文案）
 */
const summaryCards = computed(() => {
  const list = orders.value
  const pendingPay = list.filter((o) => o.status === 'CREATED').length
  const toShip = list.filter((o) => o.status === 'PAID').length
  const shippedDone = list.filter((o) => ['SHIPPED', 'COMPLETED'].includes(o.status)).length
  const cancelled = list.filter((o) => o.status === 'CANCELLED').length
  const max = statMax.value
  const total = Math.max(1, list.length)

  const pct = (n) => Math.min(100, Math.round((n / max) * 100))
  const share = (n) => Math.round((n / total) * 100)

  return [
    {
      key: 'pay',
      title: '待支付',
      value: pendingPay,
      badge: pendingPay > 0 ? `占全店 ${share(pendingPay)}%` : null,
      badgeKind: 'blue',
      hint: null,
      barClass: 'stat-fill--ink',
      pct: pct(pendingPay),
    },
    {
      key: 'ship',
      title: '待发货',
      value: toShip,
      badge: orderTodos.value.pendingShipment ? '加急' : toShip > 0 ? '待处理' : null,
      badgeKind: orderTodos.value.pendingShipment ? 'urgent' : 'amber',
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

/**
 * @param {number} n
 */
function formatStatInt(n) {
  return Number(n || 0).toLocaleString('zh-CN')
}

/**
 * @param {string} s
 */
function fulfillmentLabel(s) {
  if (s === 'CREATED') return '待支付'
  if (s === 'PAID') return '待发货'
  if (s === 'SHIPPED') return '运输中'
  if (s === 'COMPLETED') return '已送达'
  if (s === 'CANCELLED') return '已取消'
  return String(s || '—')
}

/**
 * @param {string} s
 */
function fulfillmentClass(s) {
  if (s === 'CREATED') return 'fulfill--muted'
  if (s === 'PAID') return 'fulfill--process'
  if (s === 'SHIPPED') return 'fulfill--transit'
  if (s === 'COMPLETED') return 'fulfill--done'
  if (s === 'CANCELLED') return 'fulfill--exception'
  return ''
}

/**
 * 有物流单号时展示承运简称；无单号返回空（表格显示 —）
 * @param {Record<string, unknown>} item
 */
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

/**
 * @param {Record<string, unknown>} item
 */
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

const totalOrderCount = computed(() => (orders.value || []).length)

const pendingPayCount = computed(() => (orders.value || []).filter((o) => o.status === 'CREATED').length)

const pendingShipCount = computed(() => (orders.value || []).filter((o) => o.status === 'PAID').length)

const currentTabLabel = computed(() => TABLE_TABS.find((t) => t.id === tableTab.value)?.label ?? '')

const listRangeLabel = computed(() => {
  const n = filteredOrders.value.length
  if (!n) return ''
  const tab = currentTabLabel.value
  const head = tab ? `${tab}，` : ''
  const q = String(appliedOrderSearch.value || '').trim()
  if (q) return `${head}共 ${n} 条匹配`
  return `${head}显示第 1–${n} 笔，共 ${n} 笔订单`
})

const allPageSelected = computed(() => {
  const rows = filteredOrders.value
  return rows.length > 0 && rows.every((o) => selectedIds.value.has(Number(o.orderId)))
})

const somePageSelected = computed(() => {
  const rows = filteredOrders.value
  return rows.some((o) => selectedIds.value.has(Number(o.orderId)))
})

watch([allPageSelected, somePageSelected, filteredOrders], () => {
  nextTick(() => {
    const el = headerCheckboxRef.value
    if (!el) return
    el.indeterminate = somePageSelected.value && !allPageSelected.value
  })
})

function toggleSelectAll(checked) {
  const next = new Set(selectedIds.value)
  for (const o of filteredOrders.value) {
    const id = Number(o.orderId)
    if (checked) next.add(id)
    else next.delete(id)
  }
  selectedIds.value = next
}

/**
 * @param {number} orderId
 * @param {boolean} checked
 */
function toggleRow(orderId, checked) {
  const next = new Set(selectedIds.value)
  const id = Number(orderId)
  if (checked) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}

/**
 * @param {number} orderId
 */
function isRowSelected(orderId) {
  return selectedIds.value.has(Number(orderId))
}

async function updateStatus(orderId, status) {
  const res = await api.merchantUpdateOrderStatus(merchantId.value, orderId, status)
  if (res.code === 200) {
    await loadOrders()
  } else {
    showAppMessage(res.message || '状态更新失败', '提示')
  }
}

async function batchShip() {
  const ids = [...selectedIds.value].filter((id) => {
    const o = orders.value.find((x) => Number(x.orderId) === id)
    return o && o.status === 'PAID'
  })
  if (!ids.length) {
    showAppMessage('请先勾选「待发货」订单', '提示')
    return
  }
  if (batchSubmitting.value) return
  batchSubmitting.value = true
  for (const id of ids) {
    const res = await api.merchantUpdateOrderStatus(merchantId.value, id, 'SHIPPED')
    if (res.code !== 200) {
      showAppMessage(res.message || `订单 ${id} 发货失败`, '提示')
      batchSubmitting.value = false
      await loadOrders()
      return
    }
  }
  selectedIds.value = new Set()
  batchSubmitting.value = false
  await loadOrders()
  showAppMessage(`已为 ${ids.length} 笔订单确认发货`, '提示')
}

function exportOrdersCsv() {
  const rows = filteredOrders.value
  if (!rows.length) {
    showAppMessage('当前没有可导出的订单', '提示')
    return
  }
  const headers = ['订单号', '下单日期', '客户', '金额', '履约状态', '物流']
  const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const lines = [headers.join(',')]
  for (const o of rows) {
    lines.push(
      [
        esc(o.orderNo || o.orderId),
        esc(formatOrderDate(o)),
        esc(customerName(o)),
        String(o.payAmount ?? ''),
        esc(fulfillmentLabel(o.status)),
        esc(logisticsCarrierShort(o) || '—'),
      ].join(','),
    )
  }
  const blob = new Blob([`\ufeff${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `商家订单_${merchantId.value}_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
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
  <div class="orders-dash">
    <header class="mo-hero">
      <div class="mo-toolbar" role="toolbar" aria-label="订单概览与搜索">
        <div class="mo-hero-main">
          <div class="mo-hero-chips" aria-label="店铺订单概览">
            <span class="mo-chip">商家 ID {{ merchantId || '—' }}</span>
            <span class="mo-chip">全店 {{ totalOrderCount }} 笔</span>
            <span v-if="pendingPayCount > 0" class="mo-chip mo-chip--amber">待支付 {{ pendingPayCount }}</span>
            <span v-if="pendingShipCount > 0" class="mo-chip mo-chip--warn">
              待发货 {{ pendingShipCount }}
              <span v-if="orderTodos.pendingShipment" class="mo-chip-tag">需跟进</span>
            </span>
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
              placeholder="搜索订单号、买家…"
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
          <button
            type="button"
            class="btn btn-primary btn-with-icon"
            :disabled="batchSubmitting"
            title="对勾选中的待发货订单批量确认发货"
            @click="batchShip"
          >
            <svg class="btn-icon-svg btn-icon-svg--on-dark" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
              <path
                fill="currentColor"
                d="M4 6a2 2 0 012-2h12a2 2 0 012 2v10h-2v2H6v-2H4V6zm4 10h8V8H8v8zm10-8v6h2V8h-2z"
              />
            </svg>
            批量发货
          </button>
          <button type="button" class="btn btn-outline btn-with-icon" title="导出当前列表为 CSV" @click="exportOrdersCsv">
            <svg class="btn-icon-svg" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M12 3v12m0 0l4-4m-4 4L8 11M5 21h14"
              />
            </svg>
            导出 CSV
          </button>
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
      <div v-else-if="!filteredOrders.length" class="state-msg mo-panel-inset">暂无订单</div>

      <div v-else class="mo-panel-body">
        <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th class="th-check">
                <input
                  ref="headerCheckboxRef"
                  type="checkbox"
                  :checked="allPageSelected"
                  aria-label="全选当前列表"
                  @change="toggleSelectAll(($event.target).checked)"
                />
              </th>
              <th>订单号</th>
              <th>日期</th>
              <th>客户</th>
              <th class="th-num">金额</th>
              <th>履约状态</th>
              <th class="th-carrier">物流</th>
              <th class="th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filteredOrders"
              :key="item.orderId"
              :class="{ 'tr--urgent': item.status === 'PAID' }"
            >
              <td class="td-check" @click.stop>
                <input
                  type="checkbox"
                  :checked="isRowSelected(item.orderId)"
                  @change="toggleRow(item.orderId, ($event.target).checked)"
                />
              </td>
              <td>
                <RouterLink
                  class="order-link"
                  :to="`/merchant/orders/${item.orderId}`"
                  :title="String(item.orderNo || item.orderId)"
                >
                  #{{ item.orderNo || item.orderId }}
                </RouterLink>
                <div class="order-id-sub">共 {{ item.itemCount ?? '—' }} 件</div>
              </td>
              <td class="td-date">{{ formatOrderDate(item) }}</td>
              <td>
                <div class="customer-cell">
                  <span class="avatar" :title="customerName(item)">{{ customerInitials(item) }}</span>
                  <span class="customer-name">{{ customerName(item) }}</span>
                </div>
              </td>
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
                  <RouterLink class="btn-row btn-row--link" :to="`/merchant/orders/${item.orderId}`">详情</RouterLink>
                  <button
                    v-if="item.status === 'PAID'"
                    type="button"
                    class="btn-row btn-row--primary"
                    @click="updateStatus(item.orderId, 'SHIPPED')"
                  >
                    发货
                  </button>
                  <span v-else class="row-actions__spacer" aria-hidden="true" />
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        </div>

        <footer class="mo-panel-footer">
          <span class="mo-panel-footer__meta">{{ listRangeLabel }}</span>
        </footer>
      </div>
    </section>
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

/* 参考稿：白卡片 + 顶栏小标题 + 数字与角标同行 + 底部分段条 */
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

.stat-pill--urgent {
  background: #ffedd5;
  color: #c2410c;
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

.mo-chip-tag {
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 6px;
  background: #fff7ed;
  color: #c2410c;
  border: 1px solid #fed7aa;
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

.mo-panel-footer__meta {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.02em;
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

.btn {
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-primary {
  background: #0f172a;
  color: #fff;
  border-color: #0f172a;
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
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

.btn-icon-svg--on-dark {
  opacity: 1;
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

.table-scroll {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  margin-bottom: 0;
}

.data-table {
  width: 100%;
  min-width: 820px;
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

.th-check,
.td-check {
  width: 44px;
  text-align: center;
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
  max-width: 200px;
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

.customer-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e2e8f0, #cbd5e1);
  color: #475569;
  font-size: 13px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.customer-name {
  font-weight: 600;
  color: #0f172a;
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

/* 固定双格：详情列 + 发货列，避免「仅详情」行与「详情+发货」行按钮错位 */
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
</style>
