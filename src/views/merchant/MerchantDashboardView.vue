<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'

const router = useRouter()
const products = ref([])
const orders = ref([])

const onlineProducts = computed(() => products.value.filter((p) => p.status === 1).length)
const paidOrders = computed(() => orders.value.filter((o) => o.status === 'PAID').length)
const totalRevenue = computed(() => {
  return orders.value
    .filter((o) => o.status === 'PAID' || o.status === 'COMPLETED')
    .reduce((sum, o) => sum + Number(o.payAmount || 0), 0)
})

// 最近订单
const recentOrders = computed(() => {
  return orders.value
    .filter((o) => o.status === 'PAID')
    .slice(0, 3)
    .map((o) => ({
      id: o.orderNo,
      status: o.status === 'PAID' ? '待发货' : o.status === 'SHIPPED' ? '已发货' : '已完成',
      desc: `订单金额: ¥${o.payAmount}`,
      statusType: o.status,
    }))
})

// 库存预警商品
const lowStockProducts = computed(() => {
  return products.value
    .filter((p) => p.stock < 50)
    .slice(0, 3)
    .map((p) => ({
      name: p.title,
      sku: `SKU-${p.productId}`,
      stock: p.stock,
      status: p.stock < 10 ? '紧急' : p.stock < 30 ? '警告' : '正常',
      price: `¥${p.price}`,
      action: p.stock < 10 ? '立即补货' : '关注库存',
    }))
})

onMounted(async () => {
  const [pRes, oRes] = await Promise.all([api.adminGetProducts(), api.adminGetOrders('')])
  if (pRes.code === 200) products.value = pRes.data
  if (oRes.code === 200) orders.value = oRes.data
})
</script>

<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h1>店铺运营总览</h1>
      <span class="subtitle">商家数据中心</span>
    </div>

    <!-- 数据卡片 -->
    <div class="metrics-grid">
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
    <div class="content-grid">
      <!-- 销售趋势图 -->
      <div class="chart-section">
        <div class="section-header">
          <div>
            <h3>销售趋势分析</h3>
            <p class="section-subtitle">近期销售数据与目标对比</p>
          </div>
          <div class="legend">
            <span class="legend-item"><span class="dot current"></span> 实际</span>
            <span class="legend-item"><span class="dot target"></span> 目标</span>
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
              points="0,220 113,200 227,180 340,165 453,155 567,148 680,145"
              fill="none"
              stroke="#d97706"
              stroke-width="2"
              stroke-dasharray="5,5"
            />

            <polyline
              points="0,240 113,225 227,205 340,180 453,145 567,100 680,60"
              fill="none"
              stroke="#0f172a"
              stroke-width="2.5"
            />

            <text x="0" y="280" font-size="11" fill="#6b7280">第1周</text>
            <text x="113" y="280" font-size="11" fill="#6b7280">第2周</text>
            <text x="227" y="280" font-size="11" fill="#6b7280">第3周</text>
            <text x="340" y="280" font-size="11" fill="#6b7280">第4周</text>
            <text x="453" y="280" font-size="11" fill="#6b7280">第5周</text>
            <text x="567" y="280" font-size="11" fill="#6b7280">第6周</text>
          </svg>
        </div>
      </div>

      <!-- 待处理订单面板 -->
      <div class="shipments-panel">
        <div class="panel-header">
          <h3>待处理订单</h3>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="1" y="3" width="15" height="13"></rect>
            <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon>
            <circle cx="5.5" cy="18.5" r="2.5"></circle>
            <circle cx="18.5" cy="18.5" r="2.5"></circle>
          </svg>
        </div>
        <div class="shipments-list">
          <div v-for="order in recentOrders" :key="order.id" class="shipment-item">
            <div class="shipment-icon" :class="order.statusType.toLowerCase()">
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
    <div class="inventory-section">
      <div class="section-header">
        <div>
          <h3>库存预警</h3>
          <p class="section-subtitle">实时监控低库存商品</p>
        </div>
        <div class="section-actions">
          <button class="btn-secondary" @click="router.push('/merchant/products')">商品管理</button>
          <button class="btn-primary" @click="router.push('/merchant/product/create')">添加商品</button>
        </div>
      </div>

      <table class="inventory-table" v-if="lowStockProducts.length > 0">
        <thead>
          <tr>
            <th>商品名称</th>
            <th>SKU编码</th>
            <th>库存数量</th>
            <th>状态</th>
            <th>价格</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in lowStockProducts" :key="item.sku">
            <td>
              <div class="product-cell">
                <div class="product-icon"></div>
                <span>{{ item.name }}</span>
              </div>
            </td>
            <td class="sku-code">{{ item.sku }}</td>
            <td class="stock-level">{{ item.stock }} 件</td>
            <td>
              <span
                class="status-badge"
                :class="item.status === '紧急' ? 'critical' : item.status === '警告' ? 'warning' : 'optimal'"
              >
                {{ item.status }}
              </span>
            </td>
            <td class="demand">{{ item.price }}</td>
            <td>
              <button
                class="action-btn"
                :class="item.status === '紧急' ? 'critical' : 'optimal'"
                @click="router.push(`/merchant/product/${item.sku.split('-')[1]}/edit`)"
              >
                {{ item.action }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-else class="empty-state">
        <p>暂无库存预警商品</p>
      </div>

      <div class="table-footer" v-if="lowStockProducts.length > 0">
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

.sku-code {
  font-family: 'Courier New', monospace;
  color: #64748b;
  font-size: 12px;
}

.stock-level {
  font-weight: 600;
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.status-badge.optimal {
  background: #d1fae5;
  color: #065f46;
}

.status-badge.warning {
  background: #fef3c7;
  color: #92400e;
}

.status-badge.critical {
  background: #fee2e2;
  color: #991b1b;
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

