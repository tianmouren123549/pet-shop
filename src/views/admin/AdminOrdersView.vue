<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'

const MS_HOUR = 60 * 60 * 1000

const orders = ref([])
const statusFilter = ref('')
const orderNoKeyword = ref('')
const appliedOrderNo = ref('')
const loading = ref(false)
const errorMsg = ref('')
const page = ref(1)
const pageSize = ref(6)
const router = useRouter()
const route = useRoute()

/** 时间范围：对齐原型「日期」下拉，含近 24 小时 */
const period = ref('all')

const PERIOD_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: '24h', label: '近 24 小时' },
  { value: 'day', label: '今日' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
]

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'CREATED', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

const periodMenuOpen = ref(false)
const statusMenuOpen = ref(false)

function togglePeriodMenu() {
  periodMenuOpen.value = !periodMenuOpen.value
  if (periodMenuOpen.value) statusMenuOpen.value = false
}

function toggleStatusMenu() {
  statusMenuOpen.value = !statusMenuOpen.value
  if (statusMenuOpen.value) periodMenuOpen.value = false
}

function closeFilterMenus() {
  periodMenuOpen.value = false
  statusMenuOpen.value = false
}

function selectPeriodOption(val) {
  period.value = val
  periodMenuOpen.value = false
}

function selectStatusOption(val) {
  statusFilter.value = val
  onStatusFilterChange()
  statusMenuOpen.value = false
}

function onFilterDocPointerDown(e) {
  const t = e.target
  if (!(t instanceof Element)) return
  if (t.closest('.ord-dd')) return
  closeFilterMenus()
}

const statusTriggerLabel = computed(() => {
  const st = String(statusFilter.value || '').toUpperCase()
  const hit = STATUS_OPTIONS.find((o) => String(o.value).toUpperCase() === st)
  return hit?.label ?? '全部状态'
})
const checkedOrderIds = ref([])
const lastFocusedId = ref(null)
const batchConfirmOpen = ref(false)
const batchWorking = ref(false)
/** 正在执行「催发货」的订单 ID，用于行内与侧栏按钮禁用 */
const urgingOrderId = ref(null)

function orderCreatedMs(o) {
  const t = new Date(o?.createdAt || 0).getTime()
  return Number.isFinite(t) ? t : 0
}

const periodStartMs = computed(() => {
  const now = new Date()
  if (period.value === '24h') {
    return Date.now() - 24 * MS_HOUR
  }
  if (period.value === 'day') {
    return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  }
  if (period.value === 'week') {
    const d = new Date(now)
    const day = d.getDay()
    const mondayDiff = day === 0 ? -6 : 1 - day
    d.setDate(d.getDate() + mondayDiff)
    d.setHours(0, 0, 0, 0)
    return d.getTime()
  }
  if (period.value === 'month') {
    return new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  }
  return 0
})

const periodEndMs = computed(() => Date.now())

const periodScopeLabel = computed(() => {
  if (period.value === '24h') return '近 24 小时'
  if (period.value === 'day') return '今日'
  if (period.value === 'week') return '本周'
  if (period.value === 'month') return '本月'
  return '全部'
})

const ordersInTimeRange = computed(() => {
  const list = orders.value || []
  if (period.value === 'all') return list
  const start = periodStartMs.value
  const end = periodEndMs.value
  return list.filter((o) => {
    const t = orderCreatedMs(o)
    return t >= start && t <= end
  })
})

const scopedOrders = computed(() => {
  const list = ordersInTimeRange.value || []
  const st = String(statusFilter.value || '').toUpperCase()
  if (!st) return list
  return list.filter((o) => String(o?.status || '').toUpperCase() === st)
})

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
  for (const o of scopedOrders.value || []) {
    const s = String(o?.status || '')
    if (s in m) m[s] += 1
  }
  return m
})

const scopeOrderCount = computed(() => scopedOrders.value.length)

const scopePaidAmount = computed(() =>
  scopedOrders.value
    .filter((o) => ['PAID', 'SHIPPED', 'COMPLETED'].includes(String(o?.status || '')))
    .reduce((s, o) => s + Number(o.payAmount || 0), 0),
)

const scopePendingCount = computed(() =>
  scopedOrders.value.filter((o) => ['CREATED', 'PAID'].includes(String(o?.status || ''))).length,
)

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

function formatOrderTime(iso) {
  const t = new Date(iso || 0)
  if (!Number.isFinite(t.getTime())) return '—'
  const m = String(t.getMonth() + 1).padStart(2, '0')
  const d = String(t.getDate()).padStart(2, '0')
  const hh = String(t.getHours()).padStart(2, '0')
  const mm = String(t.getMinutes()).padStart(2, '0')
  return `${m}-${d} ${hh}:${mm}`
}

function formatRelativeTime(iso) {
  const t = new Date(iso || 0).getTime()
  if (!Number.isFinite(t)) return '—'
  const diff = Date.now() - t
  if (diff < 0) return formatOrderTime(iso)
  const sec = Math.floor(diff / 1000)
  if (sec < 45) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const h = Math.floor(min / 60)
  if (h < 48) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 40) return `${d} 天前`
  return formatOrderTime(iso)
}

function userInitials(item) {
  const nick = String(item?.userNickname || '').trim()
  if (nick) return nick.slice(0, 1).toUpperCase()
  const id = item?.userId
  if (id != null && String(id)) return String(id).slice(-1)
  return '?'
}

function milestoneList(o) {
  const st = String(o?.status || '').toUpperCase()
  if (st === 'CANCELLED') {
    return [
      { label: '订单已创建', done: true, muted: false },
      { label: '订单已取消', done: true, muted: false, danger: true },
    ]
  }
  return [
    { label: '订单已创建', done: true, muted: false },
    { label: '已支付 / 待商家发货', done: ['PAID', 'SHIPPED', 'COMPLETED'].includes(st), muted: false },
    { label: '包裹运输中', done: ['SHIPPED', 'COMPLETED'].includes(st), muted: false },
    { label: '用户确认收货', done: st === 'COMPLETED', muted: false },
  ]
}

async function loadOrders() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.adminGetOrders('')
  if (res.code === 200) {
    orders.value = res.data || []
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
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
  let list = scopedOrders.value || []
  if (!kw) return list
  return list.filter((o) => {
    const orderNo = String(o?.orderNo || '').toLowerCase()
    const orderId = String(o?.orderId ?? '').toLowerCase()
    return orderNo.includes(kw) || orderId.includes(kw)
  })
})

const detailOrder = computed(() => {
  const list = filteredOrders.value || []
  if (lastFocusedId.value != null) {
    const hit = list.find((o) => o.orderId === lastFocusedId.value)
    if (hit) return hit
  }
  for (const id of checkedOrderIds.value) {
    const hit = list.find((o) => o.orderId === id)
    if (hit) return hit
  }
  return null
})

const cancellableSelected = computed(() => {
  const set = new Set(checkedOrderIds.value)
  return (orders.value || []).filter((o) => set.has(o.orderId) && ['CREATED', 'PAID'].includes(String(o?.status || '')))
})

function clearSelection() {
  checkedOrderIds.value = []
  lastFocusedId.value = null
}

function isChecked(id) {
  return checkedOrderIds.value.includes(Number(id))
}

function toggleCheck(id, ev) {
  ev?.stopPropagation?.()
  const n = Number(id)
  const arr = checkedOrderIds.value.slice()
  const i = arr.indexOf(n)
  if (i >= 0) {
    arr.splice(i, 1)
    if (lastFocusedId.value === n) lastFocusedId.value = arr.length ? arr[arr.length - 1] : null
  } else {
    arr.push(n)
    lastFocusedId.value = n
  }
  checkedOrderIds.value = arr
}

const pageAllSelected = computed(() => {
  const ids = pagedOrders.value.map((o) => o.orderId)
  if (!ids.length) return false
  return ids.every((id) => checkedOrderIds.value.includes(id))
})

function onHeaderSelectAll(ev) {
  const checked = ev.target.checked
  const pids = pagedOrders.value.map((o) => o.orderId)
  if (!pids.length) return
  if (checked) {
    const set = new Set(checkedOrderIds.value)
    pids.forEach((id) => set.add(id))
    checkedOrderIds.value = [...set]
    lastFocusedId.value = pids[0]
  } else {
    const pid = new Set(pids)
    checkedOrderIds.value = checkedOrderIds.value.filter((id) => !pid.has(id))
    if (lastFocusedId.value != null && pid.has(lastFocusedId.value)) {
      lastFocusedId.value = checkedOrderIds.value.length ? checkedOrderIds.value[checkedOrderIds.value.length - 1] : null
    }
  }
}

function onRowActivate(item) {
  lastFocusedId.value = item.orderId
}

function exportCsv() {
  const rows = filteredOrders.value || []
  if (!rows.length) {
    showAppMessage('当前没有可导出的订单', '提示')
    return
  }
  const headers = ['orderId', 'orderNo', 'userNickname', 'userId', 'payAmount', 'status', 'itemCount', 'createdAt']
  const esc = (v) => {
    const s = String(v ?? '')
    if (/[",\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`
    return s
  }
  const lines = [
    headers.join(','),
    ...rows.map((o) =>
      [
        o.orderId,
        o.orderNo,
        o.userNickname || '',
        o.userId,
        o.payAmount,
        o.status,
        o.itemCount ?? '',
        o.createdAt,
      ]
        .map(esc)
        .join(','),
    ),
  ]
  const blob = new Blob(['\ufeff', lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `orders_export_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  showAppMessage(`已导出 ${rows.length} 条`, '提示')
}

function openBatchCancel() {
  if (!cancellableSelected.value.length) return
  batchConfirmOpen.value = true
}

async function confirmBatchCancel() {
  const list = cancellableSelected.value.slice()
  if (!list.length) {
    batchConfirmOpen.value = false
    return
  }
  batchWorking.value = true
  try {
    for (const o of list) {
      const res = await api.adminUpdateOrderStatus(o.orderId, { status: 'CANCELLED' })
      if (res.code !== 200) {
        showAppMessage(res.message || `订单 ${o.orderNo} 取消失败`, '提示')
        batchWorking.value = false
        return
      }
    }
    showAppMessage(`已批量取消 ${list.length} 笔订单`, '提示')
    batchConfirmOpen.value = false
    clearSelection()
    await loadOrders()
  } finally {
    batchWorking.value = false
  }
}

watch(statusFilter, () => {
  page.value = 1
  clearSelection()
})

watch(period, () => {
  page.value = 1
  clearSelection()
})

watch(appliedOrderNo, () => {
  page.value = 1
  clearSelection()
})

const total = computed(() => (Array.isArray(filteredOrders.value) ? filteredOrders.value.length : 0))
const pagedOrders = computed(() => {
  const list = Array.isArray(filteredOrders.value) ? filteredOrders.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

async function updateStatus(orderId, status) {
  const res = await api.adminUpdateOrderStatus(orderId, { status })
  if (res.code === 200) {
    await loadOrders()
    clearSelection()
  } else {
    showAppMessage(res.message || '状态更新失败', '提示')
  }
}

async function urgeShipment(orderId) {
  const oid = Number(orderId)
  if (!oid) return
  urgingOrderId.value = oid
  const res = await api.adminUrgeOrderShipment(oid)
  urgingOrderId.value = null
  if (res.code === 200) {
    showAppMessage(res.message || '已通知商家尽快发货', '提示')
  } else {
    showAppMessage(res.message || '催发货失败', '提示')
  }
}

onMounted(async () => {
  document.addEventListener('pointerdown', onFilterDocPointerDown, true)
  syncStatusFromRoute(route.query)
  await loadOrders()
  page.value = 1
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onFilterDocPointerDown, true)
})

onBeforeRouteUpdate((to) => {
  syncStatusFromRoute(to.query)
  page.value = 1
  clearSelection()
})
</script>

<template>
  <div class="admin-page ord-view">
    <header class="ord-manifest-head">
      <div class="ord-manifest-titles">
        <h1 class="ord-title">全站订单清单</h1>
      </div>
      <div class="ord-manifest-actions">
        <button type="button" class="ord-tool ord-tool--light" @click="exportCsv">
          <span class="ord-tool-ic" aria-hidden="true">↓</span>
          导出列表
        </button>
        <button
          type="button"
          class="ord-tool ord-tool--accent"
          :disabled="!cancellableSelected.length"
          @click="openBatchCancel"
        >
          批量取消
        </button>
        <button type="button" class="ord-tool ord-tool--dark" @click="loadOrders">刷新</button>
      </div>
    </header>

    <div v-if="loading" class="ord-state">加载中…</div>
    <div v-else-if="errorMsg" class="ord-state ord-state--err">{{ errorMsg }}</div>
    <template v-else>
      <section v-if="!orders.length" class="ord-empty">暂无订单数据</section>

      <template v-else>
        <section class="ord-summ" aria-label="范围内概览">
          <article class="ord-summ-tile">
            <span class="ord-summ-k">列表范围内订单</span>
            <span class="ord-summ-v">{{ scopeOrderCount }}</span>
            <span class="ord-summ-s">笔</span>
          </article>
          <article class="ord-summ-tile ord-summ-tile--money">
            <span class="ord-summ-k">实收金额</span>
            <span class="ord-summ-v">¥{{ formatYuan(scopePaidAmount) }}</span>
            <span class="ord-summ-s">待发货 / 配送中 / 已完成</span>
          </article>
          <article class="ord-summ-tile ord-summ-tile--warn">
            <span class="ord-summ-k">待处理</span>
            <span class="ord-summ-v">{{ scopePendingCount }}</span>
            <span class="ord-summ-s">待支付 + 待发货</span>
          </article>
          <article class="ord-summ-tile ord-summ-tile--muted">
            <span class="ord-summ-k">全库已加载</span>
            <span class="ord-summ-v">{{ orders.length }}</span>
            <span class="ord-summ-s">条订单记录</span>
          </article>
        </section>

        <div class="ord-stats" role="list">
          <div class="ord-stat" role="listitem">
            <span>待支付</span><strong>{{ stats.CREATED }}</strong>
          </div>
          <div class="ord-stat" role="listitem">
            <span>待发货</span><strong>{{ stats.PAID }}</strong>
          </div>
          <div class="ord-stat" role="listitem">
            <span>已发货</span><strong>{{ stats.SHIPPED }}</strong>
          </div>
          <div class="ord-stat" role="listitem">
            <span>已完成</span><strong>{{ stats.COMPLETED }}</strong>
          </div>
          <div class="ord-stat ord-stat--muted" role="listitem">
            <span>已取消</span><strong>{{ stats.CANCELLED }}</strong>
          </div>
        </div>

        <div class="ord-split">
          <div class="ord-panel ord-panel--main">
            <div class="ord-filter-grid">
              <label class="ord-flab">
                <span class="ord-flab-cap">时间范围</span>
                <div class="ord-dd">
                  <button
                    type="button"
                    class="ord-dd-trigger"
                    :aria-expanded="periodMenuOpen"
                    aria-haspopup="listbox"
                    @click="togglePeriodMenu"
                  >
                    <span class="ord-dd-value">{{ periodScopeLabel }}</span>
                    <span class="ord-dd-chev" aria-hidden="true" />
                  </button>
                  <ul v-show="periodMenuOpen" class="ord-dd-menu" role="listbox">
                    <li v-for="opt in PERIOD_OPTIONS" :key="opt.value" role="none">
                      <button
                        type="button"
                        class="ord-dd-item"
                        :class="{ 'ord-dd-item--active': period === opt.value }"
                        role="option"
                        :aria-selected="period === opt.value"
                        @click="selectPeriodOption(opt.value)"
                      >
                        {{ opt.label }}
                      </button>
                    </li>
                  </ul>
                </div>
              </label>
              <label class="ord-flab">
                <span class="ord-flab-cap">订单状态</span>
                <div class="ord-dd">
                  <button
                    type="button"
                    class="ord-dd-trigger"
                    :aria-expanded="statusMenuOpen"
                    aria-haspopup="listbox"
                    @click="toggleStatusMenu"
                  >
                    <span class="ord-dd-value">{{ statusTriggerLabel }}</span>
                    <span class="ord-dd-chev" aria-hidden="true" />
                  </button>
                  <ul v-show="statusMenuOpen" class="ord-dd-menu" role="listbox">
                    <li v-for="opt in STATUS_OPTIONS" :key="opt.value === '' ? 'all' : opt.value" role="none">
                      <button
                        type="button"
                        class="ord-dd-item"
                        :class="{ 'ord-dd-item--active': statusFilter === opt.value }"
                        role="option"
                        :aria-selected="statusFilter === opt.value"
                        @click="selectStatusOption(opt.value)"
                      >
                        {{ opt.label }}
                      </button>
                    </li>
                  </ul>
                </div>
              </label>
              <label class="ord-flab ord-flab--span">
                <span class="ord-flab-cap">订单号 / ID</span>
                <div class="ord-search-row">
                  <input
                    v-model="orderNoKeyword"
                    class="ord-input"
                    placeholder="输入订单号或订单 ID"
                    @keyup.enter="runOrderNoSearch"
                  />
                  <button type="button" class="ord-chip-btn ord-chip-btn--primary" @click="runOrderNoSearch">查询</button>
                  <button type="button" class="ord-chip-btn ord-chip-btn--outline" @click="resetOrderNoSearch">清空</button>
                </div>
              </label>
            </div>

            <div v-if="filteredOrders.length === 0" class="ord-empty ord-empty--soft">
              <template v-if="scopedOrders.length === 0">
                当前筛选条件下暂无订单，可放宽时间或订单状态。
              </template>
              <template v-else>没有匹配的订单号，请修改关键字。</template>
            </div>

            <template v-else>
              <div class="ord-table-wrap">
                <table class="ord-table">
                  <thead>
                    <tr>
                      <th class="ord-th-check">
                        <input
                          class="ord-check"
                          type="checkbox"
                          :checked="pageAllSelected"
                          aria-label="全选本页"
                          @change="onHeaderSelectAll"
                        />
                      </th>
                      <th class="ord-col-order">订单编号</th>
                      <th class="ord-col-user">用户</th>
                      <th class="ord-th-num ord-col-amount">金额</th>
                      <th class="ord-col-time">下单时间</th>
                      <th class="ord-th-act ord-col-act">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="item in pagedOrders"
                      :key="item.orderId"
                      class="ord-tr"
                      :class="{ 'ord-tr--active': detailOrder && detailOrder.orderId === item.orderId }"
                      @click="onRowActivate(item)"
                    >
                      <td class="ord-td-check" @click.stop>
                        <input
                          class="ord-check"
                          type="checkbox"
                          :checked="isChecked(item.orderId)"
                          :aria-label="`选择订单 ${item.orderNo}`"
                          @change="toggleCheck(item.orderId, $event)"
                        />
                      </td>
                      <td class="ord-td-order">
                        <button type="button" class="ord-orderno" @click.stop="router.push(`/admin/orders/${item.orderId}`)">
                          {{ item.orderNo }}
                        </button>
                        <div class="ord-id">#{{ item.orderId }}</div>
                      </td>
                      <td class="ord-td-user">
                        <div class="ord-user">
                          <span class="ord-ava" aria-hidden="true">{{ userInitials(item) }}</span>
                          <span class="ord-nick">{{ item.userNickname || `用户 ${item.userId}` }}</span>
                        </div>
                      </td>
                      <td class="ord-num ord-money ord-td-amount">¥{{ formatYuan(item.payAmount) }}</td>
                      <td class="ord-time-cell ord-td-time">
                        <span class="ord-rel">{{ formatRelativeTime(item.createdAt) }}</span>
                        <span class="ord-abs" :title="item.createdAt">{{ formatOrderTime(item.createdAt) }}</span>
                      </td>
                      <td class="ord-td-act" @click.stop>
                        <div class="ord-act-btns" role="group" :aria-label="`订单 ${item.orderNo} 操作`">
                          <button type="button" class="ord-row-btn ord-row-btn--detail" @click="router.push(`/admin/orders/${item.orderId}`)">
                            详情
                          </button>
                          <button
                            v-if="item.status === 'PAID'"
                            type="button"
                            class="ord-row-btn ord-row-btn--urge"
                            :disabled="urgingOrderId === item.orderId"
                            @click="urgeShipment(item.orderId)"
                          >
                            {{ urgingOrderId === item.orderId ? '发送中…' : '催发货' }}
                          </button>
                          <button
                            v-if="item.status === 'CREATED' || item.status === 'PAID'"
                            type="button"
                            class="ord-row-btn ord-row-btn--cancel"
                            @click="updateStatus(item.orderId, 'CANCELLED')"
                          >
                            取消
                          </button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <PaginationBar
                :page="page"
                :page-size="pageSize"
                :total="total"
                @update:page="page = $event"
              />
            </template>
          </div>

          <aside class="ord-side" aria-label="订单摘要">
            <div class="ord-side-inner">
              <div v-if="!detailOrder" class="ord-side-placeholder">
                <p class="ord-side-ph-title">未选择订单</p>
                <p class="ord-side-ph-text">点击表格中的任意一行，或勾选订单，即可在此查看履约摘要与节点进度。</p>
              </div>
              <template v-else>
                <div class="ord-side-hero">
                  <div class="ord-side-hero-top">
                    <span class="ord-side-k">当前订单</span>
                    <button type="button" class="ord-side-link" @click="router.push(`/admin/orders/${detailOrder.orderId}`)">
                      查看详情
                    </button>
                  </div>
                  <p class="ord-side-no">{{ detailOrder.orderNo }}</p>
                  <p class="ord-side-track">履约摘要</p>
                </div>
                <div class="ord-side-body">
                  <div class="ord-side-user">
                    <span class="ord-ava ord-ava--lg" aria-hidden="true">{{ userInitials(detailOrder) }}</span>
                    <div>
                      <div class="ord-side-name">{{ detailOrder.userNickname || `用户 ${detailOrder.userId}` }}</div>
                      <div class="ord-side-sub">用户 ID {{ detailOrder.userId }}</div>
                    </div>
                  </div>
                  <div v-if="detailOrder.status === 'PAID'" class="ord-side-urge">
                    <button
                      type="button"
                      class="ord-side-urge-btn"
                      :disabled="urgingOrderId === detailOrder.orderId"
                      @click="urgeShipment(detailOrder.orderId)"
                    >
                      {{ urgingOrderId === detailOrder.orderId ? '发送中…' : '催发货' }}
                    </button>
                    <p class="ord-side-urge-hint">向本单涉及的商家各发一条站内通知，不修改订单状态。</p>
                  </div>
                  <dl class="ord-kv">
                    <div class="ord-kv-row">
                      <dt>实付金额</dt>
                      <dd>¥{{ formatYuan(detailOrder.payAmount) }}</dd>
                    </div>
                    <div class="ord-kv-row">
                      <dt>订单状态</dt>
                      <dd>
                        <span class="ord-badge" :class="'ord-badge--' + String(detailOrder.status || '').toLowerCase()">{{
                          statusText(detailOrder.status)
                        }}</span>
                      </dd>
                    </div>
                    <div class="ord-kv-row">
                      <dt>件数</dt>
                      <dd>{{ detailOrder.itemCount ?? '—' }}</dd>
                    </div>
                  </dl>
                  <div class="ord-mile-wrap">
                    <div class="ord-mile-title">节点进度</div>
                    <ul class="ord-mile">
                      <li
                        v-for="(step, idx) in milestoneList(detailOrder)"
                        :key="idx"
                        class="ord-mile-item"
                        :class="{
                          'ord-mile-item--on': step.done && !step.danger,
                          'ord-mile-item--off': !step.done,
                          'ord-mile-item--danger': step.danger,
                        }"
                      >
                        <span class="ord-mile-dot" />
                        <span class="ord-mile-label">{{ step.label }}</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </template>
            </div>
          </aside>
        </div>
      </template>
    </template>

    <ConfirmModal
      :open="batchConfirmOpen"
      @update:open="batchConfirmOpen = $event"
      title="批量取消订单"
      confirm-label="确认取消"
      :loading="batchWorking"
      @confirm="confirmBatchCancel"
    >
      <p class="ord-confirm-t">
        将取消 <strong>{{ cancellableSelected.length }}</strong> 笔处于「待支付」或「待发货」的订单，是否继续？
      </p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.ord-view.admin-page {
  min-width: 0;
  max-width: 100%;
  padding-bottom: 8px;
}

.ord-manifest-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding-bottom: 16px;
  margin-bottom: 6px;
  border-bottom: 1px solid #e8ecf2;
}

.ord-manifest-titles {
  flex: 1;
  min-width: 240px;
}

.ord-title {
  margin: 0;
  font-size: clamp(24px, 2.4vw, 30px);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0a1220;
}

.ord-manifest-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.ord-tool {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  cursor: pointer;
  border: 1px solid transparent;
}

.ord-tool:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ord-tool-ic {
  font-size: 14px;
  line-height: 1;
  opacity: 0.85;
}

.ord-tool--light {
  background: #fff;
  border-color: #c9d4e4;
  color: #1e2a3d;
}

.ord-tool--accent {
  background: linear-gradient(180deg, #8b5a2e 0%, #6d471f 100%);
  border-color: #5c3d1c;
  color: #fff8ef;
  box-shadow: 0 2px 8px rgba(91, 58, 20, 0.25);
}

.ord-tool--dark {
  background: #0b1630;
  border-color: #0b1630;
  color: #f4f6fb;
}

.ord-state {
  padding: 40px 16px;
  text-align: center;
  font-weight: 700;
  color: #64748b;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}
.ord-state--err {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fef2f2;
}

.ord-empty {
  padding: 40px 20px;
  text-align: center;
  font-weight: 700;
  color: #64748b;
  line-height: 1.55;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background: #fafbfc;
}
.ord-empty--soft {
  margin: 0 0 14px;
  border-style: solid;
  border-color: #e2e8f0;
}

.ord-summ {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.ord-summ-tile {
  border-radius: 14px;
  border: 1px solid #e4e9f1;
  background: #fff;
  padding: 14px 16px 12px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset, 0 2px 8px rgba(15, 23, 42, 0.04);
}

.ord-summ-tile--money {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #fff 0%, #f0fdf4 100%);
}

.ord-summ-tile--warn {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.ord-summ-tile--muted {
  border-color: #e2e8f0;
  background: linear-gradient(180deg, #fafbfc 0%, #f4f6f9 100%);
}

.ord-summ-k {
  display: block;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.05em;
  color: #94a3b8;
  margin-bottom: 6px;
}

.ord-summ-v {
  display: block;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.1;
}

.ord-summ-s {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.ord-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.ord-stat {
  padding: 12px 12px 10px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset;
}

.ord-stat span {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.ord-stat strong {
  color: #0f172a;
  font-size: clamp(22px, 2.2vw, 28px);
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.ord-stat--muted {
  background: #f8fafc;
}

.ord-split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: 16px;
  align-items: start;
  min-width: 0;
}

.ord-panel {
  border: 1px solid #dde3ec;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  padding: 16px 16px 18px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
  min-width: 0;
}

/* 主列在双栏网格里必须可收缩，否则内层横向滚动不生效 */
.ord-panel--main {
  min-width: 0;
  overflow: visible;
}

.ord-filter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 14px;
  margin-bottom: 14px;
}

.ord-flab--span {
  grid-column: 1 / -1;
}

.ord-flab {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.ord-flab-cap {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #64748b;
  text-transform: none;
}

.ord-dd {
  position: relative;
  width: 100%;
  min-width: 0;
}

.ord-dd-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #c5d0e0;
  background: #fff;
  color: #0f172a;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.ord-dd-trigger:hover {
  border-color: #94a3b8;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.ord-dd-trigger:focus-visible {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.2);
}

.ord-dd-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ord-dd-chev {
  flex-shrink: 0;
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 6px solid #64748b;
  opacity: 0.85;
  transition: transform 0.18s ease;
}

.ord-dd-trigger[aria-expanded='true'] .ord-dd-chev {
  transform: rotate(180deg);
}

.ord-dd-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 60;
  margin: 0;
  padding: 6px;
  list-style: none;
  background: #fff;
  border: 1px solid #dbe3ed;
  border-radius: 12px;
  box-shadow:
    0 10px 28px rgba(15, 23, 42, 0.12),
    0 2px 6px rgba(15, 23, 42, 0.06);
}

.ord-dd-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 9px 11px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  cursor: pointer;
  transition: background 0.12s ease;
}

.ord-dd-item:hover {
  background: #f1f5f9;
}

.ord-dd-item--active {
  background: #e8efff;
  color: #1d4ed8;
}

.ord-dd-item--active:hover {
  background: #dbeafe;
}

.ord-select,
.ord-input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #c5d0e0;
  background: #fff;
  color: #0f172a;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
}

.ord-input {
  flex: 1;
  min-width: 0;
}

.ord-search-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ord-chip-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid #c9d4e4;
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  color: #1e2a3d;
  flex-shrink: 0;
}

.ord-chip-btn--primary {
  border-color: #2563eb;
  background: linear-gradient(180deg, #3b82f6 0%, #2563eb 100%);
  color: #fff;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.35);
}

.ord-chip-btn--primary:hover {
  filter: brightness(1.05);
}

.ord-chip-btn--outline {
  background: #fff;
  border-color: #c5d0e0;
  color: #475569;
  font-weight: 700;
}

.ord-chip-btn--outline:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.ord-table-wrap {
  overflow-x: auto;
  overflow-y: visible;
  max-width: 100%;
  -webkit-overflow-scrolling: touch;
  border-radius: 12px;
  border: 1px solid #dbe3ed;
  background: #fff;
  margin-bottom: 12px;
}

/* 覆盖 App.vue 管理端全局 table { overflow: hidden }，避免宽表裁切最右侧「操作」列 */
.ord-view .ord-table-wrap .ord-table {
  overflow: visible;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.ord-table {
  width: 100%;
  min-width: 760px;
  table-layout: auto;
  border-collapse: separate;
  border-spacing: 0;
}

.ord-table th,
.ord-table td {
  border-bottom: 1px solid #ecf0f5;
  padding: 14px 14px;
  font-size: 13px;
  color: #26354b;
  text-align: left;
  vertical-align: middle;
}

.ord-th-check,
.ord-td-check {
  width: 48px;
  text-align: center;
  padding-left: 14px;
  padding-right: 8px;
}

.ord-col-order,
.ord-td-order {
  min-width: 188px;
  max-width: min(320px, 36vw);
}

.ord-col-user,
.ord-td-user {
  min-width: 132px;
}

.ord-col-amount,
.ord-td-amount {
  min-width: 88px;
}

.ord-col-time,
.ord-td-time {
  min-width: 124px;
}

.ord-col-act,
.ord-td-act {
  min-width: 156px;
}

.ord-th-num {
  text-align: right;
}

.ord-th-act {
  text-align: right;
  width: 1%;
  white-space: nowrap;
}

.ord-check {
  width: 16px;
  height: 16px;
  accent-color: #0b1630;
  cursor: pointer;
}

.ord-table th {
  background: linear-gradient(180deg, #f7faff 0%, #eef3fb 100%);
  font-size: 12px;
  color: #5f6d80;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-transform: none;
  padding-top: 13px;
  padding-bottom: 13px;
  border-bottom: 1px solid #e2e8f0;
}

.ord-table tbody tr:last-child td {
  border-bottom: none;
}

.ord-tr {
  cursor: pointer;
  transition: background 0.12s ease;
}

.ord-tr:hover td {
  background: #f8fafc;
}

.ord-tr--active td {
  background: #e8f4ff;
}

.ord-tr--active:hover td {
  background: #dceefd;
}

.ord-orderno {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
  text-align: left;
}

.ord-orderno:hover {
  color: #0369a1;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.ord-id {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
  font-weight: 700;
}

.ord-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ord-ava {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(145deg, #e8eef6 0%, #d0dae8 100%);
  color: #1e293b;
  font-size: 13px;
  font-weight: 900;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #c5d0e0;
}

.ord-ava--lg {
  width: 48px;
  height: 48px;
  font-size: 17px;
  border-radius: 50%;
}

.ord-nick {
  font-weight: 700;
  color: #0f172a;
}

.ord-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.ord-money {
  font-weight: 800;
  color: #0f172a;
}

.ord-time-cell {
  white-space: nowrap;
}

.ord-rel {
  display: block;
  font-weight: 800;
  color: #0f172a;
  font-size: 13px;
}

.ord-abs {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.ord-td-act {
  vertical-align: middle;
  text-align: right;
}

.ord-act-btns {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: nowrap;
}

.ord-table .ord-row-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  height: 32px;
  max-height: 32px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  border: 1px solid transparent;
  flex-shrink: 0;
  transition:
    background 0.12s ease,
    border-color 0.12s ease,
    color 0.12s ease,
    box-shadow 0.12s ease;
}

.ord-row-btn--detail {
  background: #fff;
  border-color: #93c5fd;
  color: #1d4ed8;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset;
}

.ord-row-btn--detail:hover {
  background: #eff6ff;
  border-color: #3b82f6;
  color: #1e40af;
}

.ord-row-btn--detail:active {
  background: #dbeafe;
}

.ord-row-btn--cancel {
  background: linear-gradient(180deg, #fff5f5 0%, #fef2f2 100%);
  border-color: #fecaca;
  color: #b91c1c;
}

.ord-row-btn--cancel:hover {
  background: #fee2e2;
  border-color: #f87171;
  color: #991b1b;
}

.ord-row-btn--cancel:active {
  background: #fecaca;
}

.ord-row-btn--urge {
  background: linear-gradient(180deg, #fffbeb 0%, #fefce8 100%);
  border-color: #fcd34d;
  color: #92400e;
}

.ord-row-btn--urge:hover:not(:disabled) {
  background: #fef9c3;
  border-color: #f59e0b;
  color: #78350f;
}

.ord-row-btn--urge:active:not(:disabled) {
  background: #fef08a;
}

.ord-row-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 圆角裁切在 .ord-side-inner（overflow:hidden），勿挪到含 sticky/fixed 的外层 */

.ord-side-inner {
  border-radius: 16px;
  border: 1px solid #1e293b;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}

.ord-side-placeholder {
  padding: 28px 20px;
  text-align: center;
}

.ord-side-ph-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.ord-side-ph-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: #64748b;
  font-weight: 600;
}

.ord-side-hero {
  padding: 18px 18px 16px;
  background: radial-gradient(120% 80% at 80% 0%, #1e3a5f 0%, #0f172a 55%, #020617 100%);
  color: #e2e8f0;
}

.ord-side-hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.ord-side-k {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #94a3b8;
  text-transform: none;
}

.ord-side-link {
  flex-shrink: 0;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  font-size: 11px;
  font-weight: 800;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
}

.ord-side-link:hover {
  background: rgba(255, 255, 255, 0.2);
}

.ord-side-no {
  margin: 0 0 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #fff;
}

.ord-side-track {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #fdba74;
}

.ord-side-body {
  padding: 16px 18px 18px;
}

.ord-side-user {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e8ecf2;
}

.ord-side-urge {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #fcd34d;
  background: linear-gradient(180deg, #fffbeb 0%, #fefce8 100%);
}

.ord-side-urge-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  border: 1px solid #f59e0b;
  background: #fff;
  color: #92400e;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background 0.12s ease,
    border-color 0.12s ease;
}

.ord-side-urge-btn:hover:not(:disabled) {
  background: #fffbeb;
  border-color: #d97706;
}

.ord-side-urge-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ord-side-urge-hint {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: #78716c;
  font-weight: 600;
}

.ord-side-name {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.ord-side-sub {
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}

.ord-kv {
  margin: 0 0 16px;
}

.ord-kv-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.ord-kv-row dt {
  margin: 0;
  font-weight: 700;
  color: #64748b;
}

.ord-kv-row dd {
  margin: 0;
  font-weight: 700;
  color: #0f172a;
  text-align: right;
}

.ord-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
}

.ord-badge--paid {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

.ord-badge--shipped {
  border-color: #bae6fd;
  background: #f0f9ff;
  color: #0369a1;
}

.ord-badge--completed {
  border-color: #bbf7d0;
  background: #ecfdf3;
  color: #15803d;
}

.ord-badge--created {
  border-color: #e2e8f0;
  background: #f1f5f9;
  color: #475569;
}

.ord-badge--cancelled {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.ord-mile-wrap {
  border-top: 1px solid #e8ecf2;
  padding-top: 14px;
}

.ord-mile-title {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #64748b;
  margin-bottom: 12px;
  text-transform: none;
}

.ord-mile {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ord-mile-item {
  position: relative;
  padding-left: 22px;
  padding-bottom: 14px;
  font-size: 13px;
  font-weight: 700;
  color: #94a3b8;
}

.ord-mile-item:last-child {
  padding-bottom: 0;
}

.ord-mile-item::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 10px;
  bottom: -4px;
  width: 2px;
  background: #e2e8f0;
}

.ord-mile-item:last-child::before {
  display: none;
}

.ord-mile-dot {
  position: absolute;
  left: 2px;
  top: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #e2e8f0;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #cbd5e1;
}

.ord-mile-item--on {
  color: #0f172a;
}

.ord-mile-item--on .ord-mile-dot {
  background: #ea580c;
  box-shadow: 0 0 0 1px #fdba74;
}

.ord-mile-item--off .ord-mile-dot {
  background: #f1f5f9;
}

.ord-mile-item--danger {
  color: #be123c;
}

.ord-mile-item--danger .ord-mile-dot {
  background: #fb7185;
  box-shadow: 0 0 0 1px #fecdd3;
}

.ord-confirm-t {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: #334155;
}

@media (max-width: 1180px) {
  .ord-split {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1100px) {
  .ord-summ {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ord-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ord-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .ord-summ {
    grid-template-columns: 1fr;
  }
  .ord-stats {
    grid-template-columns: 1fr;
  }
  .ord-filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
