<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'

const router = useRouter()

const dashProductTargets = {
  all: '/admin/products',
  online: { path: '/admin/products', query: { shelf: 'online' } },
  offline: { path: '/admin/products', query: { shelf: 'offline' } },
  low: { path: '/admin/inventory', query: { tab: 'low' } },
  sold: { path: '/admin/inventory', query: { tab: 'sold' } },
}

const dashOrderTargets = {
  CREATED: { path: '/admin/orders', query: { status: 'CREATED' } },
  PAID: { path: '/admin/orders', query: { status: 'PAID' } },
  SHIPPED: { path: '/admin/orders', query: { status: 'SHIPPED' } },
  COMPLETED: { path: '/admin/orders', query: { status: 'COMPLETED' } },
  CANCELLED: { path: '/admin/orders', query: { status: 'CANCELLED' } },
}

function goDash(to) {
  router.push(to)
}

const products = ref([])
const orders = ref([])
const loading = ref(false)
const errorMsg = ref('')
const period = ref('week')
/** 页头「上次刷新」展示用 */
const lastSyncedAt = ref('')

const MS_DAY = 24 * 60 * 60 * 1000

function orderCreatedMs(o) {
  const t = new Date(o?.createdAt || 0).getTime()
  return Number.isFinite(t) ? t : NaN
}

/** 统计周期起点（本地时区）：今日 0 点 / 本周一 0 点 / 本月 1 号 0 点（「全部」不使用） */
const periodStartMs = computed(() => {
  const now = new Date()
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

/** 创建时间落在当前所选周期内的订单；选「全部」时为已加载的全量订单 */
const ordersInPeriod = computed(() => {
  const list = orders.value || []
  if (period.value === 'all') return list
  const start = periodStartMs.value
  const end = periodEndMs.value
  return list.filter((o) => {
    const t = orderCreatedMs(o)
    return Number.isFinite(t) && t >= start && t <= end
  })
})

const onlineProducts = computed(() => products.value.filter((p) => p.status === 1).length)
const offProducts = computed(() => products.value.length - onlineProducts.value)
const lowStockProducts = computed(() => products.value.filter((p) => Number(p.stock) > 0 && Number(p.stock) < 20).length)
const soldOutProducts = computed(() => products.value.filter((p) => Number(p.stock) <= 0).length)

const paidOrders = computed(() => ordersInPeriod.value.filter((o) => o.status === 'PAID').length)
const createdOrders = computed(() => ordersInPeriod.value.filter((o) => o.status === 'CREATED').length)
const shippedOrders = computed(() => ordersInPeriod.value.filter((o) => o.status === 'SHIPPED').length)
const completedOrders = computed(() => ordersInPeriod.value.filter((o) => o.status === 'COMPLETED').length)
const cancelledOrders = computed(() => ordersInPeriod.value.filter((o) => o.status === 'CANCELLED').length)

const activeOrdersCount = computed(() =>
  ordersInPeriod.value.filter((o) => ['CREATED', 'PAID', 'SHIPPED'].includes(o.status)).length,
)

const totalRevenue = computed(() =>
  ordersInPeriod.value
    .filter((o) => ['PAID', 'SHIPPED', 'COMPLETED'].includes(o.status))
    .reduce((s, o) => s + Number(o.payAmount || 0), 0),
)

const alertSkuCount = computed(() => lowStockProducts.value + soldOutProducts.value)

const activeMerchantCount = computed(() => {
  const set = new Set()
  for (const p of products.value || []) {
    const mid = Number(p.merchantId || 0)
    if (mid > 0) set.add(mid)
  }
  return set.size
})

/** 柱高：按桶内 min–max 拉伸；全相等或全 0 时用演示标尺便于小数据量下仍可见起伏 */
function bucketsToBars(buckets, demoFull = 18) {
  const minB = Math.min(...buckets)
  const maxB = Math.max(...buckets)
  const span = maxB - minB
  const nearlyFlat = maxB === 0 || span === 0
  return buckets.map((c, i) => {
    let pct
    if (maxB === 0) {
      pct = 10
    } else if (!nearlyFlat) {
      pct = 14 + ((c - minB) / span) * 76
    } else {
      pct = (c / Math.max(maxB, demoFull)) * 78 + 14
      pct += (i % 5) * 2.8 - 5.6
    }
    pct = Math.round(Math.min(96, Math.max(12, pct)))
    return { pct, count: c }
  })
}

/** 与页头统计范围联动：今日=7 日、本周=6 周、本月/全部=近 6 个自然月柱形 */
const chartOrderBars = computed(() => {
  const list = orders.value || []
  const now = Date.now()

  if (period.value === 'day') {
    const buckets = Array(7).fill(0)
    const todayMid = new Date()
    todayMid.setHours(0, 0, 0, 0)
    for (let i = 0; i < 7; i++) {
      const start = new Date(todayMid)
      start.setDate(start.getDate() - (6 - i))
      const s = start.getTime()
      const end = i === 6 ? now : s + MS_DAY
      for (const o of list) {
        const t = orderCreatedMs(o)
        if (!Number.isFinite(t) || t < s) continue
        if (i === 6 ? t > end : t >= end) continue
        buckets[i] += 1
      }
    }
    return bucketsToBars(buckets)
  }

  if (period.value === 'month' || period.value === 'all') {
    const buckets = Array(6).fill(0)
    const nowDate = new Date()
    for (let mi = 0; mi < 6; mi++) {
      const d = new Date(nowDate.getFullYear(), nowDate.getMonth() - (5 - mi), 1)
      const nextM = new Date(d.getFullYear(), d.getMonth() + 1, 1)
      const start = d.getTime()
      const end = mi === 5 ? now : nextM.getTime()
      for (const o of list) {
        const t = orderCreatedMs(o)
        if (!Number.isFinite(t) || t < start) continue
        if (mi === 5 ? t > end : t >= end) continue
        buckets[mi] += 1
      }
    }
    return bucketsToBars(buckets)
  }

  const MS_WEEK = 7 * MS_DAY
  const buckets = Array(6).fill(0)
  for (let i = 0; i < 6; i++) {
    const weekEnd = now - i * MS_WEEK
    const weekStart = weekEnd - MS_WEEK
    const idx = 5 - i
    for (const o of list) {
      const t = orderCreatedMs(o)
      if (!Number.isFinite(t) || t < weekStart || t >= weekEnd) continue
      buckets[idx] += 1
    }
  }
  return bucketsToBars(buckets)
})

const chartBarLabels = computed(() => {
  if (period.value === 'day') {
    const labels = []
    const todayMid = new Date()
    todayMid.setHours(0, 0, 0, 0)
    for (let i = 0; i < 7; i++) {
      const d = new Date(todayMid)
      d.setDate(d.getDate() - (6 - i))
      if (i === 6) labels.push('今天')
      else labels.push(`${d.getMonth() + 1}/${d.getDate()}`)
    }
    return labels
  }
  if (period.value === 'month' || period.value === 'all') {
    const labels = []
    const nowDate = new Date()
    for (let mi = 0; mi < 6; mi++) {
      const d = new Date(nowDate.getFullYear(), nowDate.getMonth() - (5 - mi), 1)
      labels.push(`${d.getMonth() + 1}月`)
    }
    return labels
  }
  return ['前5周', '前4周', '前3周', '前2周', '上周', '本周']
})

const chartLegendLabel = computed(() => {
  if (period.value === 'day') return '日订单量'
  if (period.value === 'month' || period.value === 'all') return '月订单量'
  return '周订单量'
})

const alertLines = computed(() => {
  const lines = []
  if (paidOrders.value > 0) lines.push({ text: `待发货订单 ${paidOrders.value} 笔`, to: dashOrderTargets.PAID, urgent: paidOrders.value >= 5 })
  if (createdOrders.value > 0) lines.push({ text: `待支付订单 ${createdOrders.value} 笔`, to: dashOrderTargets.CREATED, urgent: false })
  if (lowStockProducts.value > 0)
    lines.push({ text: `低库存商品 ${lowStockProducts.value} 件`, to: dashProductTargets.low, urgent: true })
  if (soldOutProducts.value > 0)
    lines.push({ text: `缺货商品 ${soldOutProducts.value} 件`, to: dashProductTargets.sold, urgent: true })
  if (!lines.length) lines.push({ text: '无', to: null, urgent: false })
  return lines.slice(0, 4)
})

const urgentAlertCount = computed(() => alertLines.value.filter((x) => x.urgent).length)
const hasUrgentAlerts = computed(() => urgentAlertCount.value > 0)

const recentOrders = computed(() => {
  const list = [...ordersInPeriod.value]
  list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
  return list.slice(0, 5)
})

function orderStatusLabel(s) {
  if (s === 'CREATED') return '待支付'
  if (s === 'PAID') return '待发货'
  if (s === 'SHIPPED') return '已发货'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'CANCELLED') return '已取消'
  return s || '—'
}

function pctPart(num, den) {
  const d = Math.max(1, Number(den || 0))
  return Math.min(100, Math.round((Number(num || 0) / d) * 100))
}

const healthBars = computed(() => {
  const oc = Math.max(1, ordersInPeriod.value.length)
  return [
    { label: '待支付占比', value: pctPart(createdOrders.value, oc), tone: 'dash-bar--a' },
    { label: '待发货占比', value: pctPart(paidOrders.value, oc), tone: 'dash-bar--b' },
    { label: '在售商品占比', value: pctPart(onlineProducts.value, Math.max(1, products.value.length)), tone: 'dash-bar--c' },
  ]
})

function formatShortTime(iso) {
  const t = new Date(iso || 0)
  if (!Number.isFinite(t.getTime())) return '—'
  const m = String(t.getMonth() + 1).padStart(2, '0')
  const d = String(t.getDate()).padStart(2, '0')
  const hh = String(t.getHours()).padStart(2, '0')
  const mm = String(t.getMinutes()).padStart(2, '0')
  return `${m}-${d} ${hh}:${mm}`
}

const quickActions = [
  { title: '商品管理', path: '/admin/products', icon: 'box' },
  { title: '库存监控', path: '/admin/inventory', icon: 'stack' },
  { title: '订单中心', path: '/admin/orders', icon: 'doc' },
  { title: '账号管理', path: '/admin/accounts', icon: 'users' },
]

function refreshSyncedLabel() {
  const d = new Date()
  lastSyncedAt.value = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(async () => {
  loading.value = true
  errorMsg.value = ''
  const [pRes, oRes] = await Promise.all([api.adminGetProducts(), api.adminGetOrders('')])
  if (pRes.code === 200) products.value = pRes.data || []
  else errorMsg.value = pRes.message || '商品加载失败'
  if (oRes.code === 200) orders.value = oRes.data || []
  else errorMsg.value = errorMsg.value || oRes.message || '订单加载失败'
  loading.value = false
  refreshSyncedLabel()
})
</script>

<template>
  <div class="admin-page dash-view">
    <header class="dash-hero">
      <div class="dash-hero-text">
        <h2>运营概览</h2>
        <p v-if="lastSyncedAt" class="dash-sync-line">更新于 {{ lastSyncedAt }}</p>
    </div>
      <div class="dash-hero-aside">
        <div class="dash-seg" role="group" aria-label="统计范围">
          <button type="button" :class="{ on: period === 'day' }" @click="period = 'day'">今日</button>
          <button type="button" :class="{ on: period === 'week' }" @click="period = 'week'">本周</button>
          <button type="button" :class="{ on: period === 'month' }" @click="period = 'month'">本月</button>
          <button type="button" :class="{ on: period === 'all' }" @click="period = 'all'">全部</button>
        </div>
      </div>
    </header>

    <div v-if="loading" class="dash-panel">数据加载中…</div>
    <div v-else-if="errorMsg" class="dash-panel dash-panel--err">{{ errorMsg }}</div>

    <!-- 灰底工作区：KPI → 指标明细 → 健康/流水 → 趋势与告警（健康与流水不置于整页最底） -->
    <div v-else class="dash-body">
      <section class="dash-kpis">
        <article class="dash-kpi dash-kpi--lead">
          <div class="dash-kpi-top">
            <span class="dash-kpi-label">平台成交额</span>
          </div>
          <div class="dash-kpi-value">¥{{ formatYuan(totalRevenue) }}</div>
        </article>
        <article
          class="dash-kpi dash-kpi--click"
            role="button"
            tabindex="0"
          @click="goDash({ path: '/admin/orders', query: {} })"
          @keydown.enter.prevent="goDash({ path: '/admin/orders', query: {} })"
          @keydown.space.prevent="goDash({ path: '/admin/orders', query: {} })"
        >
          <div class="dash-kpi-top">
            <span class="dash-kpi-label">在途订单</span>
          </div>
          <div class="dash-kpi-value">{{ activeOrdersCount }}</div>
        </article>
        <article
          class="dash-kpi dash-kpi--click dash-kpi--warn"
            role="button"
            tabindex="0"
          @click="goDash(dashProductTargets.low)"
          @keydown.enter.prevent="goDash(dashProductTargets.low)"
          @keydown.space.prevent="goDash(dashProductTargets.low)"
        >
          <div class="dash-kpi-top">
            <span class="dash-kpi-label">库存预警</span>
            <span v-if="alertSkuCount > 0" class="dash-kpi-chip dash-kpi-chip--action">待处理</span>
            <span v-else class="dash-kpi-chip dash-kpi-chip--stable">正常</span>
          </div>
          <div class="dash-kpi-value">{{ alertSkuCount }}</div>
        </article>
        <article
          class="dash-kpi dash-kpi--click"
            role="button"
            tabindex="0"
          @click="goDash(dashProductTargets.online)"
          @keydown.enter.prevent="goDash(dashProductTargets.online)"
          @keydown.space.prevent="goDash(dashProductTargets.online)"
        >
          <div class="dash-kpi-top">
            <span class="dash-kpi-label">在售店铺</span>
          </div>
          <div class="dash-kpi-value">{{ activeMerchantCount }}</div>
        </article>
      </section>

      <section class="dash-detail" aria-labelledby="dash-detail-title">
        <div class="dash-detail-head">
          <h3 id="dash-detail-title">指标明细</h3>
        </div>
        <div class="dash-detail-tables">
          <div class="dash-met-board">
            <h4 class="dash-met-board-title">
              <span class="dash-met-board-dot" aria-hidden="true" />
              商品指标
            </h4>
            <div class="dash-met-row" role="group" aria-label="商品指标">
              <div class="dash-met-col">
                <div class="dash-met-head">总数</div>
                <button type="button" class="dash-met-btn" @click="goDash(dashProductTargets.all)">
                  <span class="dash-met-val">{{ products.length }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
              </div>
              <div class="dash-met-col">
                <div class="dash-met-head">上架</div>
                <button type="button" class="dash-met-btn" @click="goDash(dashProductTargets.online)">
                  <span class="dash-met-val">{{ onlineProducts }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
              </div>
              <div class="dash-met-col">
                <div class="dash-met-head">下架</div>
                <button type="button" class="dash-met-btn" @click="goDash(dashProductTargets.offline)">
                  <span class="dash-met-val">{{ offProducts }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
              </div>
              <div class="dash-met-col" :class="{ 'dash-met-col--note': lowStockProducts > 0 }">
                <div class="dash-met-head">
                  <span class="dash-met-head-inner">低库存<span v-if="lowStockProducts > 0" class="dash-met-risk-dot" title="库存预警" /></span>
                </div>
                <button
                  type="button"
                  class="dash-met-btn"
                  :class="{ 'dash-met-btn--note': lowStockProducts > 0 }"
            @click="goDash(dashProductTargets.low)"
          >
                  <span class="dash-met-val">{{ lowStockProducts }}</span>
                  <span class="dash-met-sub">{{ lowStockProducts > 0 ? '去处理' : '查看列表' }}</span>
                </button>
          </div>
              <div class="dash-met-col" :class="{ 'dash-met-col--note': soldOutProducts > 0 }">
                <div class="dash-met-head">
                  <span class="dash-met-head-inner">缺货<span v-if="soldOutProducts > 0" class="dash-met-risk-dot" title="缺货商品" /></span>
                </div>
                <button
                  type="button"
                  class="dash-met-btn"
                  :class="{ 'dash-met-btn--note': soldOutProducts > 0 }"
            @click="goDash(dashProductTargets.sold)"
          >
                  <span class="dash-met-val">{{ soldOutProducts }}</span>
                  <span class="dash-met-sub">{{ soldOutProducts > 0 ? '去处理' : '查看列表' }}</span>
                </button>
          </div>
        </div>
      </div>

          <div class="dash-met-board">
            <h4 class="dash-met-board-title">
              <span class="dash-met-board-dot" aria-hidden="true" />
              订单指标
            </h4>
            <div class="dash-met-row" role="group" aria-label="订单指标">
              <div class="dash-met-col">
                <div class="dash-met-head">待支付</div>
                <button type="button" class="dash-met-btn" @click="goDash(dashOrderTargets.CREATED)">
                  <span class="dash-met-val">{{ createdOrders }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
          </div>
              <div class="dash-met-col" :class="{ 'dash-met-col--ship': paidOrders > 0 }">
                <div class="dash-met-head">
                  <span class="dash-met-head-inner">待发货<span v-if="paidOrders > 0" class="dash-met-risk-dot" title="待发货订单" /></span>
                </div>
                <button
                  type="button"
                  class="dash-met-btn"
                  :class="{ 'dash-met-btn--note': paidOrders > 0 }"
            @click="goDash(dashOrderTargets.PAID)"
          >
                  <span class="dash-met-val">{{ paidOrders }}</span>
                  <span class="dash-met-sub">{{ paidOrders > 0 ? '去发货' : '查看列表' }}</span>
                </button>
          </div>
              <div class="dash-met-col">
                <div class="dash-met-head">已发货</div>
                <button type="button" class="dash-met-btn" @click="goDash(dashOrderTargets.SHIPPED)">
                  <span class="dash-met-val">{{ shippedOrders }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
          </div>
              <div class="dash-met-col">
                <div class="dash-met-head">已完成</div>
                <button type="button" class="dash-met-btn" @click="goDash(dashOrderTargets.COMPLETED)">
                  <span class="dash-met-val">{{ completedOrders }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
          </div>
              <div class="dash-met-col" :class="{ 'dash-met-col--soft': cancelledOrders >= 5 }">
                <div class="dash-met-head">已取消</div>
                <button
                  type="button"
                  class="dash-met-btn"
                  :class="{ 'dash-met-btn--soft': cancelledOrders >= 5 }"
            @click="goDash(dashOrderTargets.CANCELLED)"
          >
                  <span class="dash-met-val">{{ cancelledOrders }}</span>
                  <span class="dash-met-sub">查看列表</span>
                </button>
          </div>
        </div>
      </div>
        </div>
      </section>

      <section class="dash-bottom">
        <div class="dash-card dash-health">
          <div class="dash-card-head">
            <h3>订单与商品结构</h3>
          </div>
          <div class="dash-health-rows">
            <div v-for="row in healthBars" :key="row.label" class="dash-health-row">
              <div class="dash-health-label">{{ row.label }}</div>
              <div class="dash-health-track">
                <div class="dash-health-fill" :class="row.tone" :style="{ width: `${row.value}%` }" />
          </div>
              <div class="dash-health-pct">{{ row.value }}%</div>
          </div>
          </div>
        </div>

        <div class="dash-card dash-table-card">
          <div class="dash-card-head">
            <h3>最近订单</h3>
            <button type="button" class="dash-link" @click="router.push('/admin/orders')">全部订单</button>
      </div>
          <div class="dash-table-wrap">
            <table class="dash-table">
              <thead>
                <tr>
                  <th>订单号</th>
                  <th>用户</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="o in recentOrders" :key="o.orderId || o.orderNo">
                  <td class="mono">{{ o.orderNo }}</td>
                  <td>{{ o.userNickname || '—' }}</td>
                  <td class="num">¥{{ formatYuan(o.payAmount) }}</td>
                  <td>
                    <span class="dash-tag" :class="'dash-tag--' + String(o.status || '').toLowerCase()">{{
                      orderStatusLabel(o.status)
                    }}</span>
                  </td>
                  <td class="muted">{{ formatShortTime(o.createdAt) }}</td>
                </tr>
                <tr v-if="!recentOrders.length">
                  <td colspan="5" class="dash-empty">暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="dash-mid">
        <div class="dash-card dash-chart-card">
          <div class="dash-card-head">
            <h3>订单量趋势</h3>
          </div>
          <div class="dash-chart-legend">
            <span><i class="dash-dot dash-dot--dark" /> {{ chartLegendLabel }}</span>
          </div>
          <div class="dash-bars" aria-hidden="true">
            <div v-for="(b, i) in chartOrderBars" :key="`${period}-${i}`" class="dash-bar-col">
              <div class="dash-bar-track">
                <div class="dash-bar-fill" :style="{ height: `${b.pct}%` }" />
              </div>
              <span class="dash-bar-tick">{{ chartBarLabels[i] }}</span>
              <span class="dash-bar-count">{{ b.count }}</span>
            </div>
          </div>
        </div>

        <div class="dash-stack">
          <div class="dash-card dash-alerts" :class="{ 'dash-card--alert': hasUrgentAlerts }">
            <div class="dash-card-head">
              <h3>待办提醒</h3>
              <span v-if="hasUrgentAlerts" class="dash-alert-badge">{{ urgentAlertCount }} 项待处理</span>
            </div>
            <ul class="dash-alert-list">
              <li v-for="(line, idx) in alertLines" :key="idx">
                <button v-if="line.to" type="button" class="dash-alert-line" @click="goDash(line.to)">
                  <span class="dash-alert-dot" :class="{ urgent: line.urgent }" />
                  {{ line.text }}
                </button>
                <span v-else class="dash-alert-line dash-alert-line--static">{{ line.text }}</span>
              </li>
            </ul>
          </div>

          <div class="dash-card dash-quick">
            <div class="dash-card-head">
              <h3>常用功能</h3>
            </div>
            <div class="dash-quick-grid">
              <button
                v-for="q in quickActions"
                :key="q.path"
                type="button"
                class="dash-quick-btn"
                @click="router.push(q.path)"
              >
                <span class="dash-quick-icon" :data-icon="q.icon" aria-hidden="true" />
                <span class="dash-quick-title">{{ q.title }}</span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dash-view {
  min-width: 0;
}

/** 对应模板主画布：浅灰底 + 内嵌白卡片，与侧栏外 #ebedf2 形成二层灰 */
.dash-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 16px 16px;
  border-radius: 16px;
  border: 1px solid #dde3ec;
  background: linear-gradient(180deg, #eceff4 0%, #e4e8f0 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.dash-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid #eef2f7;
}

.dash-hero-text {
  flex: 1;
  min-width: 220px;
}

.dash-hero h2 {
  font-size: clamp(22px, 2.2vw, 28px);
  color: #0a1220;
  margin: 0 0 4px;
  letter-spacing: -0.02em;
  font-weight: 800;
}

.dash-sync-line {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.02em;
}

.dash-hero-aside {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.dash-seg {
  display: inline-flex;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.dash-seg button {
  border: none;
  background: transparent;
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #64748b;
  cursor: pointer;
}

.dash-seg button + button {
  border-left: 1px solid #e2e8f0;
}

.dash-seg button.on {
  background: #0b1630;
  color: #f8fafc;
}

.dash-panel {
  padding: 48px 24px;
  text-align: center;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  font-weight: 700;
  font-size: 14px;
}

.dash-panel--err {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.dash-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: 0;
}

.dash-kpi {
  border-radius: 14px;
  border: 1px solid #e8ecf2;
  background: #fff;
  padding: 18px 18px 15px;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.9) inset,
    0 2px 8px rgba(15, 23, 42, 0.04);
}

.dash-kpi--lead {
  background: linear-gradient(145deg, #fafbfc 0%, #fff 100%);
}

.dash-kpi--warn {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fffbeb 0%, #fff 100%);
}

.dash-kpi--click {
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.dash-kpi--click:hover {
  border-color: #0b1630;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
}

.dash-kpi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.dash-kpi-label {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #94a3b8;
  margin: 0;
}

.dash-kpi-chip {
  flex-shrink: 0;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 3px 7px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
}

.dash-kpi-chip--inv {
  border-color: #cbd5e1;
  background: #fff;
  color: #0f172a;
}

.dash-kpi-chip--ok {
  border-color: #bbf7d0;
  background: #ecfdf3;
  color: #15803d;
}

.dash-kpi-chip--action {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

.dash-kpi-chip--stable {
  border-color: #e2e8f0;
  background: #f1f5f9;
  color: #64748b;
}

.dash-kpi-value {
  font-size: clamp(24px, 2.4vw, 32px);
  font-weight: 800;
  color: #0a1220;
  letter-spacing: -0.02em;
  line-height: 1.05;
}

.dash-mid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(280px, 0.95fr);
  gap: 14px;
  margin: 0;
  align-items: stretch;
}

.dash-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dash-card {
  border: 1px solid #e4e9f1;
  border-radius: 16px;
  background: #fff;
  padding: 16px 18px 16px;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.9) inset,
    0 2px 10px rgba(15, 23, 42, 0.04);
}

.dash-card--alert {
  border-top: 3px solid #dc2626;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.9) inset,
    0 4px 14px rgba(220, 38, 38, 0.08);
}

.dash-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.dash-card-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.dash-chart-card {
  display: flex;
  flex-direction: column;
  min-height: 260px;
}

.dash-chart-legend {
  font-size: 11px;
  color: #64748b;
  font-weight: 700;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.dash-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  margin-right: 6px;
  vertical-align: middle;
}

.dash-dot--dark {
  background: #0f172a;
}

.dash-bars {
  flex: 1;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 4px 0;
  min-height: 168px;
  border-top: 1px solid #f1f5f9;
}

.dash-bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

/* 固定高度轨道，子元素 height:% 才能生效（否则柱会变成一条细线） */
.dash-bar-track {
  flex: 1;
  width: 100%;
  max-width: 40px;
  min-height: 140px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.dash-bar-fill {
  width: 100%;
  max-width: 34px;
  border-radius: 8px 8px 4px 4px;
  background: linear-gradient(180deg, #334155 0%, #0f172a 100%);
  min-height: 6px;
  transition: height 0.25s ease;
}

.dash-bar-tick {
  font-size: 9px;
  font-weight: 800;
  color: #94a3b8;
  letter-spacing: 0.02em;
  text-align: center;
  line-height: 1.2;
}

.dash-bar-count {
  font-size: 11px;
  font-weight: 800;
  color: #475569;
}

.dash-alert-badge {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
  padding: 3px 10px;
  border-radius: 999px;
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.dash-alert-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.dash-alert-list li + li {
  margin-top: 8px;
}

.dash-alert-line {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  text-align: left;
  border: 1px solid #f1f5f9;
  background: #fafbfc;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.dash-alert-line:hover {
  border-color: #cbd5e1;
  background: #fff;
}

.dash-alert-line--static {
  cursor: default;
  color: #64748b;
}

.dash-alert-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
  flex-shrink: 0;
}

.dash-alert-dot.urgent {
  background: #dc2626;
}

.dash-quick {
  background: #0b1630;
  border-color: #0b1630;
  color: #e8eef8;
}

.dash-quick .dash-card-head h3 {
  color: #f8fafc;
}

.dash-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.dash-quick-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.dash-quick-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.22);
}

.dash-quick-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  margin-bottom: 2px;
}

.dash-quick-icon[data-icon='box'] {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23e2e8f0' stroke-width='2'%3E%3Cpath d='M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z'/%3E%3Cpath d='M3.27 6.96L12 12.01l8.73-5.05M12 22.08V12'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
}

.dash-quick-icon[data-icon='stack'] {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23e2e8f0' stroke-width='2'%3E%3Cpath d='M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
}

.dash-quick-icon[data-icon='doc'] {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23e2e8f0' stroke-width='2'%3E%3Cpath d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/%3E%3Cpath d='M14 2v6h6M16 13H8M16 17H8M10 9H8'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
}

.dash-quick-icon[data-icon='users'] {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23e2e8f0' stroke-width='2'%3E%3Cpath d='M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2'/%3E%3Ccircle cx='9' cy='7' r='4'/%3E%3Cpath d='M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
}

.dash-quick-title {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.dash-bottom {
  display: grid;
  grid-template-columns: minmax(260px, 0.42fr) minmax(0, 1fr);
  gap: 14px;
  margin: 0;
}

.dash-health-rows {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dash-health-row {
  display: grid;
  grid-template-columns: 100px 1fr 44px;
  gap: 10px;
  align-items: center;
}

.dash-health-label {
  font-size: 11px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.dash-health-track {
  height: 10px;
  border-radius: 999px;
  background: #f1f5f9;
  overflow: hidden;
}

.dash-health-fill {
  height: 100%;
  border-radius: 999px;
  min-width: 4px;
  transition: width 0.3s ease;
}

.dash-bar--a {
  background: #0f172a;
}

.dash-bar--b {
  background: #475569;
}

.dash-bar--c {
  background: #94a3b8;
}

.dash-health-pct {
  font-size: 12px;
  font-weight: 800;
  color: #0f172a;
  text-align: right;
}

.dash-link {
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 800;
  color: #0b1630;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.dash-table-wrap {
  overflow-x: auto;
  margin: 0 -4px;
}

.dash-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.dash-table th {
  text-align: left;
  padding: 10px 10px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
  border-bottom: 1px solid #eef2f7;
  background: #fafbfc;
}

.dash-table td {
  padding: 11px 10px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  font-weight: 600;
}

.dash-table td.mono {
  font-family: ui-monospace, monospace;
  font-size: 11px;
}

.dash-table td.num {
  font-weight: 800;
  color: #0f172a;
}

.dash-table td.muted {
  color: #94a3b8;
  font-weight: 600;
}

.dash-empty {
  text-align: center;
  color: #94a3b8;
  padding: 24px 12px !important;
}

.dash-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  background: #f1f5f9;
  color: #475569;
}

.dash-tag--paid {
  background: #eff6ff;
  color: #1d4ed8;
}

.dash-tag--shipped {
  background: #ecfdf3;
  color: #15803d;
}

.dash-tag--completed {
  background: #f0fdf4;
  color: #166534;
}

.dash-tag--created {
  background: #fff7ed;
  color: #c2410c;
}

.dash-tag--cancelled {
  background: #fef2f2;
  color: #b91c1c;
}

.dash-detail {
  border: 1px solid #e4e9f1;
  border-radius: 16px;
  background: #fff;
  padding: 16px 20px 20px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset;
}

.dash-detail-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
}

.dash-detail-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.dash-detail-tables {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/** 截图式：每列 = 浅蓝灰表头 + 白色数字区，五列栅格 */
.dash-met-board {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: linear-gradient(165deg, #f8fafc 0%, #f1f5f9 55%, #eef2f7 100%);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.85) inset,
    0 8px 24px rgba(15, 23, 42, 0.05);
  padding: 6px 12px 14px;
}

.dash-met-board-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 12px 4px 12px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.03em;
  color: #475569;
}

.dash-met-board-dot {
  width: 8px;
  height: 8px;
  border-radius: 3px;
  background: linear-gradient(145deg, #334155 0%, #0f172a 100%);
  flex-shrink: 0;
}

.dash-met-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.dash-met-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #d0dbe8;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}

.dash-met-col:hover {
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.dash-met-col--note {
  border-color: #475569;
  box-shadow:
    0 0 0 1px rgba(15, 23, 42, 0.12),
    0 6px 18px rgba(15, 23, 42, 0.1);
}

.dash-met-col--note .dash-met-head {
  background: linear-gradient(180deg, #d2deeb 0%, #c3d3e6 100%);
  color: #0f172a;
  border-bottom-color: rgba(71, 85, 105, 0.35);
}

.dash-met-col--ship {
  border-width: 3px;
  border-color: #0f172a;
  box-shadow:
    0 0 0 1px #0f172a,
    0 10px 26px rgba(15, 23, 42, 0.14);
}

.dash-met-col--ship .dash-met-head {
  background: linear-gradient(180deg, #e2eaf4 0%, #d4e2f0 100%);
  color: #0f172a;
}

.dash-met-head-inner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.dash-met-risk-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #dc2626;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.9);
}

.dash-met-col--soft {
  border-color: #e8ecf2;
  opacity: 0.96;
}

.dash-met-head {
  padding: 12px 8px;
  text-align: center;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #4b5c73;
  background: linear-gradient(180deg, #e8eff8 0%, #d9e6f2 100%);
  border-bottom: 1px solid rgba(186, 199, 216, 0.95);
}

.dash-met-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 92px;
  padding: 16px 8px 14px;
  border: none;
  background: #fff;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s ease;
}

.dash-met-btn:hover {
  background: #f8fafc;
}

.dash-met-btn:focus-visible {
  outline: 2px solid #0f172a;
  outline-offset: -2px;
  z-index: 1;
  position: relative;
}

.dash-met-val {
  font-size: clamp(22px, 2.4vw, 28px);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.05;
}

.dash-met-sub {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #94a3b8;
}

.dash-met-btn:hover .dash-met-sub {
  color: #64748b;
}

.dash-met-btn--note {
  background: linear-gradient(180deg, #fff 0%, #f7f7f8 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.dash-met-btn--note .dash-met-val {
  color: #020617;
}

.dash-met-btn--note .dash-met-sub {
  color: #475569;
}

.dash-met-btn--soft:not(:hover) {
  background: #fafbfc;
}

.dash-met-btn--soft:not(:hover) .dash-met-val {
  color: #64748b;
}

@media (max-width: 1100px) {
  .dash-kpis {
    grid-template-columns: repeat(2, 1fr);
  }

  .dash-mid {
    grid-template-columns: 1fr;
  }

  .dash-bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .dash-kpis {
    grid-template-columns: 1fr;
  }

  .dash-met-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dash-met-btn {
    min-height: 80px;
    padding: 12px 6px 10px;
  }

  .dash-met-val {
    font-size: 20px;
  }

  .dash-health-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .dash-health-pct {
    text-align: left;
  }
}
</style>
