<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'

const router = useRouter()
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const dashboardError = ref('')
const loading = ref(false)
const products = ref([])
const orders = ref([])
/** 每周销售额目标（元），来自商家资料 API，空串表示未设置 */
const salesTargetWeeklyStr = ref('')

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
 * 已产生实付、应计入营收的主状态（待支付/已取消不计入）。
 * @param {string} s
 */
function isPaidLikeStatus(s) {
  return s === 'PAID' || s === 'SHIPPED' || s === 'COMPLETED'
}

const onlineProducts = computed(() => products.value.filter((p) => p.status === 1).length)
const paidOrders = computed(() => orders.value.filter((o) => normOrderStatus(o) === 'PAID').length)
const totalRevenue = computed(() => {
  return orders.value
    .filter((o) => isPaidLikeStatus(normOrderStatus(o)))
    .reduce((sum, o) => sum + Number(o.payAmount || 0), 0)
})

/**
 * 右侧面板：优先展示待发货；若无则展示最近订单，避免有单却空白。
 */
const shipmentPanelRows = computed(() => {
  const list = Array.isArray(orders.value) ? [...orders.value] : []
  const paid = list.filter((o) => normOrderStatus(o) === 'PAID')
  const pick = paid.length
    ? paid
        .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
        .slice(0, 5)
    : list
        .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
        .slice(0, 5)
  return pick.map((o) => {
    const st = normOrderStatus(o)
    return {
      id: o.orderNo,
      status: orderStatusLabel(st),
      desc: `订单金额: ¥${o.payAmount ?? '0.00'}`,
      statusType: st,
    }
  })
})

const shipmentPanelMode = computed(() => {
  const list = Array.isArray(orders.value) ? orders.value : []
  return list.some((o) => normOrderStatus(o) === 'PAID') ? 'pending' : 'recent'
})

/** 近 6 段滚动窗口（每段 7 天）的实付汇总，用于趋势折线（含买家已付款起的有效订单）。 */
const weeklySalesBuckets = computed(() => {
  const MS_WEEK = 7 * 24 * 60 * 60 * 1000
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
    weeklyTargetDisplay: hasTarget ? weeklyTargetNum.toFixed(2) : '',
    maxV,
    weekXs: values.map((_, i) => xAt(i)),
  }
})

const weekLabels = ['前5周', '前4周', '前3周', '前2周', '上周', '本周']

/**
 * 库存：优先真正低库存；否则展示库存最低的几件，避免表格长期空白。
 */
const inventoryWatchRows = computed(() => {
  const plist = Array.isArray(products.value) ? products.value : []
  const danger = plist
    .filter((p) => stockNum(p) < 50)
    .sort((a, b) => stockNum(a) - stockNum(b))
    .slice(0, 8)
  if (danger.length) {
    return danger.map((p) => {
      const s = stockNum(p)
      return {
        productId: p.productId,
        name: p.title,
        stock: s,
        price: `¥${p.price}`,
        action: s < 10 ? '立即补货' : '关注库存',
      }
    })
  }
  return [...plist]
    .sort((a, b) => stockNum(a) - stockNum(b))
    .slice(0, 5)
    .map((p) => {
      const s = stockNum(p)
      return {
        productId: p.productId,
        name: p.title,
        stock: s,
        price: `¥${p.price}`,
        action: '调整库存',
      }
    })
})

onMounted(async () => {
  dashboardError.value = ''
  if (!merchantId.value) {
    dashboardError.value = '请先登录商家账号'
    return
  }
  loading.value = true
  const [pRes, oRes, profRes] = await Promise.all([
    api.merchantGetProducts(merchantId.value),
    api.merchantGetOrders(merchantId.value, ''),
    api.merchantGetProfile(merchantId.value),
  ])
  if (pRes.code === 200) products.value = pRes.data || []
  else dashboardError.value = pRes.message || '商品数据加载失败'
  if (oRes.code === 200) orders.value = oRes.data || []
  else if (!dashboardError.value) dashboardError.value = oRes.message || '订单数据加载失败'
  if (profRes.code === 200 && profRes.data) {
    const tw = profRes.data.salesTargetWeekly
    salesTargetWeeklyStr.value = tw != null && String(tw).trim() !== '' ? String(tw).trim() : ''
  }
  loading.value = false
})
</script>

<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h1>店铺运营总览</h1>
      <span class="subtitle">本店经营数据一览，实时掌握营收与订单动态</span>
    </div>
    <div v-if="dashboardError" class="dashboard-error">{{ dashboardError }}</div>
    <div v-else-if="loading" class="dashboard-loading">正在加载本店数据…</div>

    <!-- 数据卡片 -->
    <div v-else class="metrics-grid">
      <div class="metric-card">
        <div class="metric-label">总营收</div>
        <div class="metric-value">¥{{ totalRevenue.toFixed(2) }}</div>
        <svg class="mini-chart" viewBox="0 0 60 20">
          <polyline points="0,15 15,12 30,8 45,5 60,3" fill="none" stroke="currentColor" stroke-width="1.5" />
        </svg>
      </div>

      <div class="metric-card">
        <div class="metric-label">订单总数</div>
        <div class="metric-value">{{ orders.length }}</div>
        <svg class="mini-chart" viewBox="0 0 60 20">
          <polyline points="0,12 15,10 30,13 45,8 60,6" fill="none" stroke="currentColor" stroke-width="1.5" />
        </svg>
      </div>

      <div class="metric-card">
        <div class="metric-label">商品总数</div>
        <div class="metric-value">{{ products.length }}</div>
        <svg class="mini-chart" viewBox="0 0 60 20">
          <polyline points="0,8 15,7 30,9 45,6 60,5" fill="none" stroke="currentColor" stroke-width="1.5" />
        </svg>
      </div>

      <div class="metric-card">
        <div class="metric-label">待发货订单</div>
        <div class="metric-value">{{ paidOrders }}</div>
        <div class="metric-change">需要处理</div>
      </div>
    </div>

    <!-- 主要内容区 -->
    <div v-if="!dashboardError && !loading" class="content-grid">
      <!-- 销售趋势图 -->
      <div class="chart-section">
        <div class="section-header">
          <div>
            <h3>销售趋势分析</h3>
            <p class="section-subtitle">
              按最近 6 段「各 7 天」滚动窗口汇总本店买家已付款后的订单实付。
              <template v-if="salesChartMeta.hasTarget">
                橙色虚线为商家资料中的「每周销售额目标」：¥{{ salesChartMeta.weeklyTargetDisplay }}（各段横向对比）。
              </template>
              <template v-else>
                尚未设置目标线，请在
                <a href="#" class="chart-inline-link" @click.prevent="router.push('/merchant/profile')">商家资料</a>
                中填写「每周销售额目标」并保存。
              </template>
            </p>
          </div>
          <div class="legend">
            <span class="legend-item"><span class="dot current"></span> 实际</span>
            <span v-if="salesChartMeta.hasTarget" class="legend-item"><span class="dot target"></span> 目标</span>
          </div>
        </div>
        <div class="chart-container">
          <svg class="revenue-chart" viewBox="0 0 680 300">
            <line x1="0" y1="250" x2="680" y2="250" stroke="#e5e7eb" stroke-width="1" />
            <line x1="0" y1="200" x2="680" y2="200" stroke="#e5e7eb" stroke-width="1" />
            <line x1="0" y1="150" x2="680" y2="150" stroke="#e5e7eb" stroke-width="1" />
            <line x1="0" y1="100" x2="680" y2="100" stroke="#e5e7eb" stroke-width="1" />
            <line x1="0" y1="50" x2="680" y2="50" stroke="#e5e7eb" stroke-width="1" />

            <polyline
              v-if="salesChartMeta.hasTarget"
              :points="salesChartMeta.pointsTarget"
              fill="none"
              stroke="#d97706"
              stroke-width="2"
              stroke-dasharray="5,5"
            />

            <polyline
              :points="salesChartMeta.pointsActual"
              fill="none"
              stroke="#0f172a"
              stroke-width="2.5"
            />

            <text
              v-for="(lx, i) in salesChartMeta.weekXs"
              :key="i"
              :x="lx"
              y="280"
              text-anchor="middle"
              font-size="11"
              fill="#6b7280"
            >
              {{ weekLabels[i] || '' }}
            </text>
          </svg>
        </div>
      </div>

      <!-- 待处理订单面板 -->
      <div class="shipments-panel">
        <p class="panel-lead">
          {{
            shipmentPanelMode === 'pending'
              ? '以下订单等待发货，请及时处理'
              : '当前无待发货订单，展示最近订单便于跟进'
          }}
        </p>
        <div class="panel-header">
          <h3>{{ shipmentPanelMode === 'pending' ? '待发货' : '最近订单' }}</h3>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="1" y="3" width="15" height="13"></rect>
            <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon>
            <circle cx="5.5" cy="18.5" r="2.5"></circle>
            <circle cx="18.5" cy="18.5" r="2.5"></circle>
          </svg>
        </div>
        <div class="shipments-list">
          <div v-if="shipmentPanelRows.length === 0" class="shipment-empty">暂无相关订单</div>
          <div v-for="order in shipmentPanelRows" :key="order.id" class="shipment-item">
            <div class="shipment-icon" :class="String(order.statusType || 'unknown').toLowerCase()">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 11l3 3L22 4" />
                <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
              </svg>
            </div>
            <div class="shipment-info">
              <div class="shipment-id">{{ order.id }} - {{ order.status }}</div>
              <div class="shipment-desc">{{ order.desc }}</div>
            </div>
          </div>
        </div>
        <button class="view-fleet-btn" @click="router.push('/merchant/orders')">查看所有订单</button>
      </div>
    </div>

    <!-- 库存状态表格 -->
    <div v-if="!dashboardError && !loading" class="inventory-section">
      <div class="section-header">
        <div>
          <h3>库存预警</h3>
          <p class="section-subtitle">
            库存低于 50 件优先展示；均高于该阈值时按库存从低到高列出前几条便于补货
          </p>
        </div>
        <div class="section-actions">
          <button class="btn-secondary" @click="router.push('/merchant/products')">商品管理</button>
          <button class="btn-primary" @click="router.push('/merchant/product/create')">添加商品</button>
        </div>
      </div>

      <table class="inventory-table" v-if="inventoryWatchRows.length > 0">
        <thead>
          <tr>
            <th>商品名称</th>
            <th>库存数量</th>
            <th>价格</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in inventoryWatchRows" :key="item.productId">
            <td>
              <div class="product-cell">
                <div class="product-icon"></div>
                <span>{{ item.name }}</span>
              </div>
            </td>
            <td class="stock-level">{{ item.stock }} 件</td>
            <td class="demand">{{ item.price }}</td>
            <td>
              <button
                class="action-btn"
                :class="Number(item.stock) < 10 ? 'critical' : 'optimal'"
                @click="router.push(`/merchant/product/${item.productId}/edit`)"
              >
                {{ item.action }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-else class="empty-state">
        <p>暂无商品数据，请先在商品管理中上架</p>
      </div>

      <div class="table-footer" v-if="inventoryWatchRows.length > 0">
        <a href="#" class="view-all-link" @click.prevent="router.push('/merchant/products')">
          查看全部 {{ products.length }} 个商品
        </a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  background: #f7f8fa;
  min-height: 100vh;
  padding: 24px;
}

.dashboard-error {
  margin: 0 0 16px;
  padding: 12px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #b91c1c;
  font-size: 13px;
}

.dashboard-loading {
  margin: 48px 0;
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.dashboard-header {
  margin-bottom: 24px;
}

.dashboard-header h1 {
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px 0;
}

.subtitle {
  font-size: 14px;
  color: #64748b;
}

/* 数据卡片 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.metric-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  position: relative;
}

.metric-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}

.metric-change {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 12px;
}

.mini-chart {
  width: 100%;
  height: 24px;
  color: #0f172a;
}

/* 内容网格 */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 16px;
  margin-bottom: 24px;
}

/* 图表区域 */
.chart-section {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.section-header h3 {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.5px;
  margin: 0 0 4px 0;
}

.section-subtitle {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}

.chart-inline-link {
  color: #0f172a;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.chart-inline-link:hover {
  color: #d97706;
}

.legend {
  display: flex;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.5px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.current {
  background: #0f172a;
}

.dot.target {
  background: #d97706;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.revenue-chart {
  width: 100%;
  height: 100%;
}

/* 订单面板 */
.shipments-panel {
  background: #1e293b;
  border-radius: 8px;
  padding: 24px;
  color: white;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.panel-lead {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.45;
  color: #94a3b8;
}

.panel-header h3 {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
  margin: 0;
}

.panel-header svg {
  color: #64748b;
}

.shipments-list {
  margin-bottom: 20px;
}

.shipment-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #0f172a;
  border-radius: 6px;
  margin-bottom: 12px;
}

.shipment-icon {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #334155;
  color: #94a3b8;
}

.shipment-icon.paid {
  background: #f59e0b;
  color: #0f172a;
}

.shipment-icon.shipped {
  background: #38bdf8;
  color: #0f172a;
}

.shipment-icon.completed {
  background: #22c55e;
  color: #0f172a;
}

.shipment-icon.created {
  background: #a78bfa;
  color: #0f172a;
}

.shipment-icon.cancelled,
.shipment-icon.unknown {
  background: #475569;
  color: #e2e8f0;
}

.shipment-empty {
  padding: 20px 12px;
  text-align: center;
  font-size: 12px;
  color: #64748b;
  background: #0f172a;
  border-radius: 6px;
  margin-bottom: 12px;
}

.shipment-info {
  flex: 1;
}

.shipment-id {
  font-size: 13px;
  font-weight: 700;
  color: white;
  margin-bottom: 4px;
}

.shipment-desc {
  font-size: 12px;
  color: #94a3b8;
}

.view-fleet-btn {
  width: 100%;
  padding: 12px;
  background: #334155;
  border: none;
  border-radius: 6px;
  color: white;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  cursor: pointer;
  transition: background 0.2s;
}

.view-fleet-btn:hover {
  background: #475569;
}

/* 库存区域 */
.inventory-section {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 24px;
}

.section-actions {
  display: flex;
  gap: 8px;
}

.btn-secondary,
.btn-primary {
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
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

.btn-primary:hover {
  background: #1e293b;
}

/* 库存表格 */
.inventory-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 20px;
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

.product-icon {
  width: 32px;
  height: 32px;
  background: #f1f5f9;
  border-radius: 4px;
  flex-shrink: 0;
}

.stock-level {
  font-weight: 600;
}

.demand {
  color: #64748b;
  font-size: 12px;
}

.action-btn {
  padding: 6px 16px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.3px;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.action-btn.optimal {
  background: #f1f5f9;
  color: #475569;
}

.action-btn.critical {
  background: #dc2626;
  color: white;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
}

.table-footer {
  margin-top: 20px;
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

