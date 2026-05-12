<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'

const router = useRouter()
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const dashboardError = ref('')
const loading = ref(false)
const products = ref([])
const orders = ref([])
/** 每周销售额目标（元），来自商家资料 API，空串表示未设置 */
const salesTargetWeeklyStr = ref('')
const lastSyncedAt = ref('')
const shopName = ref('')
const chatUnreadDot = ref(false)

/** 仪表盘搜索草稿；筛选「最近订单 / 库存关注」用 {@link appliedDashboardSearch} */
const dashboardSearchDraft = ref('')
const appliedDashboardSearch = ref('')

function runDashboardSearch() {
  appliedDashboardSearch.value = String(dashboardSearchDraft.value || '').trim()
}

/** 主图相对路径（如 /uploads/...）拼接 VITE_API_BASE */
function resolveMediaUrl(raw) {
  const s = String(raw || '').trim()
  if (!s) return ''
  if (/^(https?:|data:|blob:)/i.test(s)) return s
  const base = String(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  if (s.startsWith('/')) return base ? `${base}${s}` : s
  return s
}

/** @param {unknown} p */
function productListImageSrc(p) {
  const raw = String(p?.detail?.imageUrl || p?.imageUrl || '').trim()
  return resolveMediaUrl(raw)
}

/** 库存表主图加载失败后不再重试该 URL（按商品 id） */
const inventoryImgFailed = ref(/** @type {Record<number, boolean>} */ ({}))

/**
 * @param {{ productId: unknown; imageSrc: string }} item
 */
function showInventoryProductImg(item) {
  const id = Number(item.productId)
  return Boolean(item.imageSrc) && !inventoryImgFailed.value[id]
}

/**
 * @param {unknown} productId
 */
function onInventoryImgError(productId) {
  const id = Number(productId)
  if (!Number.isFinite(id)) return
  inventoryImgFailed.value = { ...inventoryImgFailed.value, [id]: true }
}

watch(
  () => (Array.isArray(products.value) ? products.value.map((p) => p.productId).join(',') : ''),
  () => {
    inventoryImgFailed.value = {}
  },
)

/** @param {unknown} o */
function normOrderStatus(o) {
  return String(o?.status ?? '')
    .trim()
    .toUpperCase()
}

/** @param {unknown} p */
function stockNum(p) {
  const n = Number(p?.stock)
  return Number.isFinite(n) ? n : 0
}

/**
 * 订单状态展示文案（与订单管理页一致）。
 * @param {string} s
 */
function orderStatusLabel(s) {
  if (s === 'CREATED') return '待支付'
  if (s === 'PAID') return '待发货'
  if (s === 'SHIPPED') return '已发货'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'CANCELLED') return '已取消'
  return s || '未知'
}

/**
 * @param {string} s
 */
function orderStatusPillClass(s) {
  if (s === 'PAID') return 'pill-processing'
  if (s === 'SHIPPED') return 'pill-shipped'
  if (s === 'COMPLETED') return 'pill-delivered'
  if (s === 'CREATED') return 'pill-pending'
  if (s === 'CANCELLED') return 'pill-cancelled'
  return 'pill-muted'
}

/**
 * 已产生实付、应计入营收的主状态（待支付/已取消不计入）。
 * @param {string} s
 */
function isPaidLikeStatus(s) {
  return s === 'PAID' || s === 'SHIPPED' || s === 'COMPLETED'
}

const MS_WEEK = 7 * 24 * 60 * 60 * 1000

const onlineProducts = computed(() => products.value.filter((p) => p.status === 1).length)
const paidOrders = computed(() => orders.value.filter((o) => normOrderStatus(o) === 'PAID').length)
const totalRevenue = computed(() => {
  return orders.value
    .filter((o) => isPaidLikeStatus(normOrderStatus(o)))
    .reduce((sum, o) => sum + Number(o.payAmount || 0), 0)
})

/** 近 6 段滚动窗口订单笔数 */
const weeklyOrderBuckets = computed(() => {
  const now = Date.now()
  /** @type {number[]} */
  const buckets = Array(6).fill(0)
  for (let i = 0; i < 6; i++) {
    const weekEnd = now - i * MS_WEEK
    const weekStart = weekEnd - MS_WEEK
    const idx = 5 - i
    for (const o of orders.value) {
      const t = new Date(o.createdAt || 0).getTime()
      if (!Number.isFinite(t) || t < weekStart || t >= weekEnd) continue
      buckets[idx] += 1
    }
  }
  return buckets
})

/** 近 6 段滚动窗口（每段 7 天）的实付汇总（计入有效实付订单）。 */
const weeklySalesBuckets = computed(() => {
  const now = Date.now()
  /** @type {number[]} */
  const buckets = Array(6).fill(0)
  for (let i = 0; i < 6; i++) {
    const weekEnd = now - i * MS_WEEK
    const weekStart = weekEnd - MS_WEEK
    const idx = 5 - i
    for (const o of orders.value) {
      if (!isPaidLikeStatus(normOrderStatus(o))) continue
      const t = new Date(o.createdAt || 0).getTime()
      if (!Number.isFinite(t) || t < weekStart || t >= weekEnd) continue
      buckets[idx] += Number(o.payAmount || 0)
    }
  }
  return buckets
})

const revenueTrendPct = computed(() => {
  const b = weeklySalesBuckets.value
  if (b.length < 2) return null
  const prev = b[4]
  const cur = b[5]
  if (prev <= 0 && cur <= 0) return '0.0%'
  if (prev <= 0) return '+100%'
  const pct = ((cur - prev) / prev) * 100
  return (pct >= 0 ? '+' : '') + pct.toFixed(1) + '%'
})

const ordersTrendPct = computed(() => {
  const b = weeklyOrderBuckets.value
  if (b.length < 2) return null
  const prev = b[4]
  const cur = b[5]
  if (prev <= 0 && cur <= 0) return '0.0%'
  if (prev <= 0) return '+100%'
  const pct = ((cur - prev) / prev) * 100
  return (pct >= 0 ? '+' : '') + pct.toFixed(1) + '%'
})

const revenueTrendUp = computed(() => {
  const b = weeklySalesBuckets.value
  if (b.length < 2) return true
  return b[5] >= b[4]
})

const ordersTrendUp = computed(() => {
  const b = weeklyOrderBuckets.value
  if (b.length < 2) return true
  return b[5] >= b[4]
})

/** 库存低于 10 件的在售商品数（关键任务） */
const flaggedProductCount = computed(() => {
  return products.value.filter((p) => p.status === 1 && stockNum(p) > 0 && stockNum(p) < 10).length
})

const salesChartMeta = computed(() => {
  const values = weeklySalesBuckets.value
  const tRaw = String(salesTargetWeeklyStr.value || '').trim()
  const weeklyTargetNum = tRaw === '' ? NaN : Number(tRaw)
  const hasTarget = Number.isFinite(weeklyTargetNum) && weeklyTargetNum > 0
  const maxV = Math.max(...values, hasTarget ? weeklyTargetNum : 0, 1)
  const W = 680
  const H = 250
  const padTop = 20
  const padBottom = 0
  const padX = 36
  const innerH = H - padTop - padBottom
  const n = values.length
  const innerW = W - 2 * padX
  const step = n > 1 ? innerW / (n - 1) : 0
  const xAt = (i) => padX + i * step
  /**
   * @param {number[]} arr
   */
  const toPoints = (arr) =>
    arr
      .map((v, i) => {
        const x = xAt(i)
        const ratio = maxV > 0 ? v / maxV : 0
        const y = padTop + innerH * (1 - ratio)
        return `${x},${y}`
      })
      .join(' ')
  const targetSeries = hasTarget ? Array(6).fill(weeklyTargetNum) : []
  return {
    pointsActual: toPoints(values),
    pointsTarget: hasTarget ? toPoints(targetSeries) : '',
    hasTarget,
    weeklyTargetDisplay: hasTarget ? formatYuan(weeklyTargetNum) : '',
    maxV,
    weekXs: values.map((_, i) => xAt(i)),
  }
})

const weekLabels = ['前5周', '前4周', '前3周', '前2周', '上周', '本周']

/** SVG 柱状图渐变 id（避免与其它页面冲突） */
const BAR_GRADIENT_ID = 'md-sales-bar-grad'

/**
 * 坐标轴上限取整为易读步长（1 / 2 / 5 × 10^n）。
 * @param {number} x
 */
function niceCeilAxis(x) {
  if (!Number.isFinite(x) || x <= 0) return 1
  const exp = Math.floor(Math.log10(x))
  const pow = 10 ** exp
  const f = x / pow
  const nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10
  return nf * pow
}

/**
 * @param {number} n
 */
function formatAxisYuan(n) {
  const v = Math.max(0, n)
  if (v >= 10000) {
    const w = v / 10000
    return `¥${w % 1 < 0.05 ? Math.round(w) : w.toFixed(1)}万`
  }
  return `¥${formatYuan(v)}`
}

/** 柱状图（销售表现）：Y 轴刻度、网格、目标线标签、零值区分、悬停 title 提示 */
const salesBarChart = computed(() => {
  const values = weeklySalesBuckets.value
  const tRaw = String(salesTargetWeeklyStr.value || '').trim()
  const weeklyTargetNum = tRaw === '' ? NaN : Number(tRaw)
  const hasTarget = Number.isFinite(weeklyTargetNum) && weeklyTargetNum > 0
  const dataMax = Math.max(0, ...values)
  const scaleMax = niceCeilAxis(Math.max(dataMax, hasTarget ? weeklyTargetNum : 0, 1))

  const W = 800
  const H = 248
  const leftGutter = 52
  const rightGutter = hasTarget ? 82 : 14
  const topGutter = 6
  const bottomGutter = 42
  const plotX = leftGutter
  const plotY = topGutter
  const plotW = W - leftGutter - rightGutter
  const plotH = H - topGutter - bottomGutter

  const tickCount = 5
  /** @type {{ value: number; y: number; label: string }[]} */
  const yTicks = []
  for (let i = 0; i < tickCount; i++) {
    const value = scaleMax * (1 - i / (tickCount - 1))
    const y = plotY + (plotH * i) / (tickCount - 1)
    yTicks.push({ value, y, label: formatAxisYuan(value) })
  }

  const valueToY = (v) => plotY + plotH * (1 - v / scaleMax)
  const baselineY = plotY + plotH

  /** 与原型一致：细柱，约占每格宽度的 30%，并设上限避免过粗 */
  const n = values.length
  const slotW = plotW / n
  const barW = Math.min(30, Math.max(14, slotW * 0.3))

  const bars = values.map((v, i) => {
    const raw = Number(v) || 0
    const slotCenterX = plotX + (i + 0.5) * slotW
    const x = slotCenterX - barW / 2
    const isZero = raw <= 0
    let displayH
    let y
    if (isZero) {
      displayH = 5
      y = baselineY - displayH
    } else {
      const h = (raw / scaleMax) * plotH
      displayH = Math.max(h, 6)
      y = baselineY - displayH
    }
    const wl = weekLabels[i] || ''
    const tip = hasTarget
      ? `${wl}：¥${formatYuan(raw)}（周目标 ¥${formatYuan(weeklyTargetNum)}）`
      : `${wl}：¥${formatYuan(raw)}`
    return { x, y, h: displayH, w: barW, raw, isZero, weekLabel: wl, tooltip: tip }
  })

  const targetY = hasTarget ? valueToY(weeklyTargetNum) : null
  const targetLabelText = hasTarget ? `目标 ¥${formatYuan(weeklyTargetNum)}` : ''

  return {
    W,
    H,
    plotX,
    plotY,
    plotW,
    plotH,
    baselineY,
    scaleMax,
    yTicks,
    bars,
    targetY,
    hasTarget,
    targetLabelText,
    weeklyTargetNum,
  }
})

/**
 * 库存：优先真正低库存；否则展示库存最低的几件，避免表格长期空白。
 */
const inventoryWatchRows = computed(() => {
  const plist = Array.isArray(products.value) ? products.value : []
  const danger = plist
    .filter((p) => stockNum(p) < 50)
    .sort((a, b) => stockNum(a) - stockNum(b))
    .slice(0, 8)
  /** @type {Array<{ productId: unknown; name: string; stock: number; price: string; action: string; imageSrc: string }>} */
  let rows
  if (danger.length) {
    rows = danger.map((p) => {
      const s = stockNum(p)
      return {
        productId: p.productId,
        name: p.title,
        stock: s,
        price: `¥${formatYuan(p.price)}`,
        action: s < 10 ? '立即补货' : '关注库存',
        imageSrc: productListImageSrc(p),
      }
    })
  } else {
    rows = [...plist]
      .sort((a, b) => stockNum(a) - stockNum(b))
      .slice(0, 5)
      .map((p) => {
        const s = stockNum(p)
        return {
          productId: p.productId,
          name: p.title,
          stock: s,
          price: `¥${formatYuan(p.price)}`,
          action: '调整库存',
          imageSrc: productListImageSrc(p),
        }
      })
  }
  const kw = appliedDashboardSearch.value.trim().toLowerCase()
  if (!kw) return rows
  return rows.filter((r) => {
    const name = String(r.name || '').toLowerCase()
    const pid = String(r.productId ?? '').toLowerCase()
    const sku = pid ? `pw-${String(pid).padStart(5, '0')}`.toLowerCase() : ''
    return name.includes(kw) || pid.includes(kw) || (sku && sku.includes(kw))
  })
})

const latestOrdersRows = computed(() => {
  const kw = appliedDashboardSearch.value.trim().toLowerCase()
  let list = Array.isArray(orders.value) ? [...orders.value] : []
  if (kw) {
    list = list.filter((o) => {
      const no = String(o.orderNo || '').toLowerCase()
      const id = String(o.orderId ?? '').toLowerCase()
      const uid = String(o.userId ?? '').toLowerCase()
      return no.includes(kw) || id.includes(kw) || uid.includes(kw)
    })
  }
  return list
    .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
    .slice(0, 6)
    .map((o) => {
      const st = normOrderStatus(o)
      const ic = o.itemCount != null ? Number(o.itemCount) : null
      return {
        orderId: Number(o.orderId),
        orderNo: o.orderNo || `#${o.orderId}`,
        customer: o.userId != null ? `用户 #${o.userId}` : '—',
        productHint:
          ic != null && Number.isFinite(ic) ? `共 ${ic} 件商品` : '订单明细',
        amount: `¥${formatYuan(o.payAmount)}`,
        statusKey: st,
        statusLabel: orderStatusLabel(st),
        pillClass: orderStatusPillClass(st),
      }
    })
})

function refreshSyncedAt() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  lastSyncedAt.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(async () => {
  dashboardError.value = ''
  if (!merchantId.value) {
    dashboardError.value = '请先登录商家账号'
    return
  }
  loading.value = true
  const [pRes, oRes, profRes, badgeRes] = await Promise.all([
    api.merchantGetProducts(merchantId.value),
    api.merchantGetOrders(merchantId.value, ''),
    api.merchantGetProfile(merchantId.value),
    api.merchantChatUnreadBadge(merchantId.value),
  ])
  if (pRes.code === 200) products.value = pRes.data || []
  else dashboardError.value = pRes.message || '商品数据加载失败'
  if (oRes.code === 200) orders.value = oRes.data || []
  else if (!dashboardError.value) dashboardError.value = oRes.message || '订单数据加载失败'
  if (profRes.code === 200 && profRes.data) {
    const tw = profRes.data.salesTargetWeekly
    salesTargetWeeklyStr.value = tw != null && String(tw).trim() !== '' ? String(tw).trim() : ''
    shopName.value = profRes.data.shopName || profRes.data.username || '我的店铺'
  }
  if (badgeRes.code === 200 && badgeRes.data?.hasUnread) chatUnreadDot.value = true
  refreshSyncedAt()
  loading.value = false
})
</script>

<template>
  <div class="dashboard">
    <div class="md-hero">
      <div class="md-hero-text">
        <h1 class="md-hero-title">{{ shopName || '我的店铺' }}</h1>
        <p class="md-hero-sub">
          <span class="md-sync">上次同步 {{ lastSyncedAt || '—' }}</span>
        </p>
      </div>
      <div class="md-hero-tools">
        <div class="md-hero-tools-inner">
          <div class="md-search-wrap">
            <input
              v-model.trim="dashboardSearchDraft"
              type="search"
              class="md-search"
              placeholder="订单号、订单 ID、商品名或商品 ID…"
              autocomplete="off"
              enterkeyhint="search"
              title="筛选下方最近订单与库存关注表"
              @keyup.enter="runDashboardSearch"
            />
            <button type="button" class="md-search-btn" title="应用筛选" @click="runDashboardSearch">搜索</button>
          </div>
          <button
            type="button"
            class="md-bell-btn"
            aria-label="通知"
            title="通知"
            @click="router.push('/merchant/notifications')"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <div v-if="dashboardError" class="dashboard-error">{{ dashboardError }}</div>
    <div v-else-if="loading" class="dashboard-loading">正在加载本店数据…</div>

    <template v-else>
      <!-- KPI -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-label">总营收</div>
          <div class="metric-value">¥{{ formatYuan(totalRevenue) }}</div>
          <div v-if="revenueTrendPct != null" class="metric-trend" :class="{ down: !revenueTrendUp }">
            <svg class="spark" viewBox="0 0 48 12" aria-hidden="true">
              <polyline
                :points="revenueTrendUp ? '0,10 12,8 24,6 36,4 48,2' : '0,2 12,4 24,6 36,8 48,10'"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
              />
            </svg>
            <span>{{ revenueTrendPct }} <span class="trend-label">（上周 vs 本周）</span></span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-label">订单数</div>
          <div class="metric-value">{{ orders.length }}</div>
          <div v-if="ordersTrendPct != null" class="metric-trend neutral" :class="{ down: !ordersTrendUp }">
            <svg class="spark" viewBox="0 0 48 12" aria-hidden="true">
              <line x1="0" y1="6" x2="48" y2="6" stroke="currentColor" stroke-width="1.2" stroke-dasharray="3 2" />
            </svg>
            <span>{{ ordersTrendPct }}</span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-label">在售商品</div>
          <div class="metric-value">{{ onlineProducts }}</div>
          <div class="metric-foot">共 {{ products.length }} 个 SKU</div>
        </div>

        <div class="metric-card metric-card-accent">
          <div class="metric-label">店铺评分</div>
          <div class="metric-value metric-inline">
            <span>—</span>
            <span class="metric-star" aria-hidden="true">★</span>
          </div>
          <div class="metric-foot">评价模块接入后展示</div>
        </div>
      </div>

      <div class="md-main-layout">
        <!-- 左：仅销售图表；订单与库存见下方同行对齐 -->
        <div class="md-main-col">
          <div class="chart-section">
            <div class="section-header">
              <div>
                <h3>销售表现</h3>
              </div>
              <div class="legend">
                <span class="legend-item"><span class="dot bar"></span> 销售额</span>
                <span v-if="salesChartMeta.hasTarget" class="legend-item"><span class="dot target"></span> 目标</span>
              </div>
            </div>
            <div class="bar-chart-wrap">
              <svg
                class="bar-chart"
                :viewBox="`0 0 ${salesBarChart.W} ${salesBarChart.H}`"
                xmlns="http://www.w3.org/2000/svg"
              >
                <defs>
                  <!-- 与 Apex 原型一致：浅灰柱，底部略深、顶部略亮 -->
                  <linearGradient :id="BAR_GRADIENT_ID" x1="0" y1="1" x2="0" y2="0">
                    <stop offset="0%" stop-color="#8fa3bc" />
                    <stop offset="100%" stop-color="#dde8f2" />
                  </linearGradient>
                </defs>

                <!-- Y 轴 -->
                <line
                  :x1="salesBarChart.plotX"
                  :y1="salesBarChart.plotY"
                  :x2="salesBarChart.plotX"
                  :y2="salesBarChart.baselineY"
                  stroke="#e2e8f0"
                  stroke-width="1"
                />
                <line
                  :x1="salesBarChart.plotX"
                  :y1="salesBarChart.baselineY"
                  :x2="salesBarChart.plotX + salesBarChart.plotW"
                  :y2="salesBarChart.baselineY"
                  stroke="#cbd5e1"
                  stroke-width="1"
                />

                <!-- 水平网格 + Y 刻度 -->
                <g v-for="(tk, i) in salesBarChart.yTicks" :key="'yt-' + i">
                  <line
                    v-if="i > 0 && i < salesBarChart.yTicks.length - 1"
                    :x1="salesBarChart.plotX"
                    :y1="tk.y"
                    :x2="salesBarChart.plotX + salesBarChart.plotW"
                    :y2="tk.y"
                    stroke="#f1f5f9"
                    stroke-width="1"
                  />
                  <text
                    :x="salesBarChart.plotX - 8"
                    :y="tk.y"
                    text-anchor="end"
                    dominant-baseline="middle"
                    font-size="10"
                    font-weight="600"
                    fill="#94a3b8"
                  >
                    {{ tk.label }}
                  </text>
                </g>

                <!-- 目标线 + 右侧标签 -->
                <g v-if="salesBarChart.hasTarget && salesBarChart.targetY != null">
                  <line
                    :x1="salesBarChart.plotX"
                    :y1="salesBarChart.targetY"
                    :x2="salesBarChart.plotX + salesBarChart.plotW"
                    :y2="salesBarChart.targetY"
                    stroke="#ea580c"
                    stroke-width="2"
                    stroke-dasharray="6 4"
                  />
                  <rect
                    :x="salesBarChart.plotX + salesBarChart.plotW + 6"
                    :y="salesBarChart.targetY - 11"
                    width="72"
                    height="22"
                    rx="6"
                    fill="#fff7ed"
                    stroke="#fdba74"
                    stroke-width="1"
                  />
                  <text
                    :x="salesBarChart.plotX + salesBarChart.plotW + 42"
                    :y="salesBarChart.targetY"
                    text-anchor="middle"
                    dominant-baseline="middle"
                    font-size="10"
                    font-weight="700"
                    fill="#9a3412"
                  >
                    {{ salesBarChart.targetLabelText }}
                  </text>
                </g>

                <!-- 柱体（悬停显示浏览器原生提示） -->
                <g
                  v-for="(b, i) in salesBarChart.bars"
                  :key="'bar-' + i"
                  class="bar-group"
                >
                  <title>{{ b.tooltip }}</title>
                  <rect
                    :x="b.x"
                    :y="b.y"
                    :width="b.w"
                    :height="b.h"
                    :rx="b.isZero ? 3 : 5"
                    :fill="b.isZero ? '#e8eef5' : `url(#${BAR_GRADIENT_ID})`"
                    :opacity="b.isZero ? 1 : 1"
                    class="bar-rect"
                  />
                </g>

                <text
                  v-for="(b, i) in salesBarChart.bars"
                  :key="'xl-' + i"
                  :x="b.x + b.w / 2"
                  :y="salesBarChart.H - 14"
                  text-anchor="middle"
                  font-size="11"
                  font-weight="600"
                  fill="#64748b"
                >
                  {{ b.weekLabel }}
                </text>
              </svg>
            </div>
          </div>
        </div>

        <!-- 右：关键任务 -->
        <aside class="md-side-col">
          <div class="critical-card">
            <h4 class="critical-title">关键任务</h4>
            <ul class="critical-list">
              <li class="critical-item" @click="router.push('/merchant/orders')">
                <span class="ci-icon truck">🚚</span>
                <div class="ci-text">
                  <span class="ci-head">{{ paidOrders }} 笔待发货</span>
                  <span class="ci-sub">前往订单处理</span>
                </div>
                <span class="ci-chev">›</span>
              </li>
              <li class="critical-item" @click="router.push('/merchant/products')">
                <span class="ci-icon warn">⚠</span>
                <div class="ci-text">
                  <span class="ci-head">{{ flaggedProductCount }} 个低库存商品</span>
                  <span class="ci-sub">库存 &lt; 10 件</span>
                </div>
                <span class="ci-chev">›</span>
              </li>
              <li class="critical-item" @click="router.push('/merchant/support')">
                <span class="ci-icon chat">💬</span>
                <div class="ci-text">
                  <span class="ci-head">{{ chatUnreadDot ? '客服有新消息' : '用户咨询' }}</span>
                  <span class="ci-sub">{{ chatUnreadDot ? '请及时回复' : '暂无未读' }}</span>
                </div>
                <span class="ci-chev">›</span>
              </li>
            </ul>
          </div>
        </aside>
      </div>

      <!-- 与原型一致：最新订单 | 库存预警 同一行、顶底对齐、等高 -->
      <div class="md-orders-inventory-pair">
        <div class="orders-card orders-card--pair">
          <div class="orders-card-head">
            <h3>最新订单</h3>
            <a href="#" class="link-all" @click.prevent="router.push('/merchant/orders')">查看全部订单</a>
          </div>
          <div class="orders-table-wrap orders-table-wrap--pair">
            <table class="orders-table">
              <thead>
                <tr>
                  <th class="col-order">订单号</th>
                  <th class="col-sum">摘要</th>
                  <th class="col-buyer">买家</th>
                  <th class="col-amt">金额</th>
                  <th class="col-status">状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="latestOrdersRows.length === 0">
                  <td colspan="5" class="orders-empty">暂无订单</td>
                </tr>
                <tr v-for="row in latestOrdersRows" :key="row.orderId">
                  <td class="col-order">
                    <button
                      type="button"
                      class="order-no-link"
                      :title="'查看订单 ' + row.orderNo"
                      @click="router.push('/merchant/orders/' + row.orderId)"
                    >
                      {{ row.orderNo }}
                    </button>
                  </td>
                  <td class="col-sum">{{ row.productHint }}</td>
                  <td class="col-buyer">{{ row.customer }}</td>
                  <td class="col-amt amt">{{ row.amount }}</td>
                  <td class="col-status">
                    <span class="status-pill" :class="row.pillClass">{{ row.statusLabel }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="inventory-section inventory-section--pair">
          <div class="section-header section-header--pair">
            <div>
              <h3>库存预警</h3>
            </div>
            <div class="section-actions section-actions--pair">
              <button
                type="button"
                class="btn-icon-gear"
                aria-label="商品管理"
                title="商品管理"
                @click="router.push('/merchant/products')"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M12 15a3 3 0 100-6 3 3 0 000 6zm9.4-1.65a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09a1.65 1.65 0 00-1-1.51 1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09a1.65 1.65 0 001.51-1 1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"
                  />
                </svg>
              </button>
              <button class="btn-primary btn-compact" @click="router.push('/merchant/product/create')">添加商品</button>
            </div>
          </div>

          <div class="inventory-table-scroll inventory-table-scroll--pair">
            <table class="inventory-table inventory-table--pair" v-if="inventoryWatchRows.length > 0">
              <thead>
                <tr>
                  <th>商品名称</th>
                  <th>库存</th>
                  <th>价格</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in inventoryWatchRows" :key="item.productId">
                  <td>
                    <div class="product-cell">
                      <div class="product-thumb-wrap">
                        <img
                          v-if="showInventoryProductImg(item)"
                          :src="item.imageSrc"
                          :alt="item.name || '商品'"
                          class="product-thumb-img"
                          loading="lazy"
                          decoding="async"
                          @error="onInventoryImgError(item.productId)"
                        />
                        <div v-else class="product-thumb-placeholder" aria-hidden="true" />
                      </div>
                      <span class="product-name-side">{{ item.name }}</span>
                    </div>
                  </td>
                  <td class="stock-level">库存: {{ item.stock }} 件</td>
                  <td class="demand">{{ item.price }}</td>
                  <td>
                    <button
                      type="button"
                      class="btn-inventory-op"
                      :class="{ 'btn-inventory-op--warn': Number(item.stock) < 10 }"
                      @click="router.push(`/merchant/product/${item.productId}/edit`)"
                    >
                      {{ item.action }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="inventoryWatchRows.length === 0" class="empty-state empty-state--pair">
            <p>暂无商品数据，请先在商品管理中上架</p>
          </div>

          <div class="table-footer table-footer--pair" v-if="inventoryWatchRows.length > 0">
            <a href="#" class="view-all-link" @click.prevent="router.push('/merchant/products')">
              查看全部 {{ products.length }} 个商品
            </a>
          </div>
        </div>
      </div>

      <footer class="md-dashboard-foot">
        © 2026 Apex Paws Professional Merchant Platform. All rights reserved.
      </footer>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  /* 与原型工作台底色一致；左右留白由外层 main 承担，避免壳层+页内双重缩进 */
  background: #f5f7f8;
  min-height: 100%;
  padding: 10px 0 28px;
  box-sizing: border-box;
}

.md-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 0;
}

.md-hero-title {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 4px;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.md-hero-sub {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.md-sync::before {
  display: none;
}

.md-hero-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 1;
  min-width: 0;
}

.md-hero-tools-inner {
  display: flex;
  align-items: center;
  gap: 10px;
}

.md-search-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.md-search {
  width: min(260px, 42vw);
  min-width: 140px;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #e8eaed;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  color: #334155;
}

.md-search::placeholder {
  color: #94a3b8;
}

.md-search-btn {
  flex-shrink: 0;
  height: 38px;
  padding: 0 14px;
  border-radius: 4px;
  border: 1px solid #0f172a;
  background: #0f172a;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
}

.md-bell-btn {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e8eaed;
  border-radius: 4px;
  background: #fafafa;
  color: #64748b;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.md-bell-btn:hover {
  background: #f8fafc;
  color: #0f172a;
  border-color: #cbd5e1;
}

.dashboard-error {
  margin: 0 0 12px;
  padding: 12px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 0;
  color: #b91c1c;
  font-size: 13px;
}

.dashboard-loading {
  margin: 32px 0;
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

@media (max-width: 1100px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.metric-card {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 0;
  padding: 14px 14px 12px;
  box-shadow: none;
}

.metric-card-accent {
  background: #fafafa;
}

.metric-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.metric-value {
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.15;
  letter-spacing: -0.03em;
}

.metric-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-star {
  font-size: 22px;
  color: #eab308;
  opacity: 0.35;
}

.metric-trend {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #16a34a;
}

.metric-trend.down {
  color: #dc2626;
}

.metric-trend.neutral {
  color: #64748b;
}

.metric-trend .spark {
  width: 52px;
  height: 14px;
  flex-shrink: 0;
}

.trend-label {
  font-weight: 500;
  color: #94a3b8;
  font-size: 11px;
}

.metric-foot {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}

.md-main-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(280px, 1fr);
  gap: 10px;
  align-items: stretch;
  margin-bottom: 0;
}

@media (max-width: 1100px) {
  .md-main-layout {
    grid-template-columns: minmax(0, 1.65fr) minmax(280px, 1fr);
  }
}

@media (max-width: 1024px) {
  .md-main-layout {
    grid-template-columns: 1fr;
  }
}

.md-main-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

/** 最新订单与库存预警：与上方主栅格同列宽、顶底对齐等高 */
.md-orders-inventory-pair {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(280px, 1fr);
  gap: 10px;
  align-items: stretch;
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .md-orders-inventory-pair {
    grid-template-columns: minmax(0, 1.65fr) minmax(280px, 1fr);
  }
}

@media (max-width: 1024px) {
  .md-orders-inventory-pair {
    grid-template-columns: 1fr;
  }
}

.orders-card--pair {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.orders-table-wrap--pair {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.chart-section {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 0;
  padding: 14px 16px 10px;
  box-shadow: none;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.section-header h3 {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.legend {
  display: flex;
  gap: 14px;
  flex-shrink: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.bar {
  background: #9eb6cc;
}

.dot.target {
  background: #ea580c;
}

.bar-chart-wrap {
  width: 100%;
  overflow-x: auto;
}

.bar-chart {
  width: 100%;
  min-width: 560px;
  height: auto;
  display: block;
}

.bar-group {
  cursor: default;
}

.bar-group .bar-rect {
  transition: opacity 0.15s ease, filter 0.15s ease;
}

.bar-group:hover .bar-rect {
  opacity: 1;
  filter: brightness(0.94);
}

.orders-card {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 0;
  padding: 14px 16px 12px;
  box-shadow: none;
}

.orders-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.orders-card-head h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.link-all {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  text-decoration: none;
}

.link-all:hover {
  text-decoration: underline;
}

.orders-table-wrap {
  overflow-x: auto;
  margin: 0 -4px;
  padding: 0 4px;
}

.orders-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 13px;
}

.orders-table th {
  text-align: left;
  padding: 10px 10px 10px 0;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.04em;
  border-bottom: 1px solid #e8eef7;
  background: #f0f5ff;
}

.orders-table th:last-child,
.orders-table td:last-child {
  padding-right: 0;
}

.orders-table td {
  padding: 11px 10px 11px 0;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.orders-table .col-order {
  width: 30%;
  min-width: 0;
  word-break: break-word;
}

.orders-table .col-sum {
  width: 16%;
}

.orders-table .col-buyer {
  width: 16%;
}

.orders-table .col-amt {
  width: 14%;
  text-align: right;
  white-space: nowrap;
}

.orders-table .col-status {
  width: 22%;
  min-width: 100px;
  text-align: right;
  vertical-align: middle;
}

.orders-table th.col-amt,
.orders-table th.col-status {
  text-align: right;
}

.orders-table .amt {
  font-weight: 600;
  color: #0f172a;
}

.order-no-link {
  display: inline;
  max-width: 100%;
  padding: 0;
  margin: 0;
  border: none;
  background: none;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  text-align: left;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  word-break: break-all;
}

.order-no-link:hover {
  color: #1d4ed8;
}

.orders-empty {
  text-align: center;
  color: #94a3b8;
  padding: 24px !important;
}

.status-pill {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.pill-processing {
  background: #ffedd5;
  color: #9a3412;
}

.pill-shipped {
  background: #e0f2fe;
  color: #0369a1;
}

.pill-delivered {
  background: #dcfce7;
  color: #166534;
}

.pill-cancelled {
  background: #f1f5f9;
  color: #64748b;
}

.pill-pending {
  background: #f1f5f9;
  color: #475569;
}

.pill-muted {
  background: #f1f5f9;
  color: #64748b;
}

.md-side-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.critical-card {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 0;
  padding: 14px 14px 12px;
  color: #e2e8f0;
  box-shadow: none;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.critical-title {
  margin: 0 0 14px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #94a3b8;
}

.critical-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.critical-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 0;
  cursor: pointer;
  transition: background 0.15s;
}

.critical-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.ci-icon {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.ci-icon.truck {
  background: rgba(245, 158, 11, 0.2);
}

.ci-icon.warn {
  background: rgba(248, 113, 113, 0.18);
}

.ci-icon.chat {
  background: rgba(148, 163, 184, 0.2);
}

.ci-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ci-head {
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.ci-sub {
  font-size: 11px;
  color: #94a3b8;
}

.ci-chev {
  font-size: 18px;
  color: #64748b;
  font-weight: 300;
}

.inventory-section {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 0;
  padding: 16px;
  box-shadow: none;
}

.inventory-section--pair {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  padding: 14px 16px 12px;
}

.section-header--pair {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px 14px;
  margin-bottom: 0;
  flex-shrink: 0;
}

.section-header--pair h3 {
  margin: 0;
}

.section-actions--pair {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-left: auto;
}

.btn-icon-gear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid #e8eaed;
  border-radius: 4px;
  background: #fafafa;
  color: #64748b;
  cursor: pointer;
  flex-shrink: 0;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.btn-icon-gear:hover {
  background: #f8fafc;
  color: #0f172a;
  border-color: #cbd5e1;
}

.btn-compact {
  min-height: 36px;
  padding: 0 10px;
  font-size: 12px;
}

.inventory-table-scroll {
  margin-top: 10px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.inventory-table-scroll--pair {
  flex: 1 1 auto;
  min-height: 0;
  margin-top: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.inventory-table--pair {
  margin-top: 0;
}

.inventory-table--pair th,
.inventory-table--pair td {
  padding: 8px 6px;
  font-size: 12px;
}

.inventory-table--pair thead {
  background: #f5f7f8;
}

.product-thumb-wrap {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.product-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.product-thumb-placeholder {
  width: 100%;
  height: 100%;
  min-height: 32px;
  background: linear-gradient(135deg, #e8ecf4 0%, #f1f5f9 100%);
}

.product-name-side {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.35;
  word-break: break-word;
}

.empty-state--pair {
  flex: 1 1 auto;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  font-size: 12px;
}

.table-footer--pair {
  margin-top: auto;
  flex-shrink: 0;
  padding-top: 12px;
  text-align: center;
}

.btn-inventory-op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  background: #2563eb;
  color: #fff;
  transition:
    background 0.15s,
    transform 0.12s;
}

.btn-inventory-op:hover {
  background: #1d4ed8;
}

.btn-inventory-op:active {
  transform: scale(0.98);
}

.btn-inventory-op--warn {
  background: #dc2626;
}

.btn-inventory-op--warn:hover {
  background: #b91c1c;
}

.md-dashboard-foot {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #e8eaed;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.02em;
}

.section-actions {
  display: flex;
  gap: 8px;
}

.btn-secondary,
.btn-primary {
  min-height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary {
  background: white;
  border: 1px solid #e2e8f0;
  color: #475569;
}

.btn-secondary:hover {
  background: #f8fafc;
}

.btn-primary {
  background: #0f172a;
  border: 1px solid #0f172a;
  color: white;
}

.inventory-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 16px;
}

.inventory-table thead {
  border-bottom: 1px solid #e2e8f0;
}

.inventory-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.5px;
}

.inventory-table td {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
  color: #334155;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stock-level {
  font-weight: 600;
}

.demand {
  color: #64748b;
  font-size: 12px;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
}

.table-footer {
  margin-top: 16px;
  text-align: center;
}

.view-all-link {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-decoration: none;
  letter-spacing: 0.3px;
}

.view-all-link:hover {
  color: #0f172a;
}
</style>
