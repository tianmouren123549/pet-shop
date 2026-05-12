<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'

const route = useRoute()
const router = useRouter()

/** 与列表筛选逻辑一致：库存低于该值视为预警（含售罄） */
const STOCK_ALERT_LINE = 20

const products = ref([])
const loading = ref(false)
const errorMsg = ref('')
const sending = ref({})
const restockConfirmOpen = ref(false)
const restockPending = ref(null)

const tab = ref('low')
const page = ref(1)
const pageSize = ref(10)

const period = ref('all')

const MS_DAY = 24 * 60 * 60 * 1000

function productActivityMs(p) {
  const t = new Date(p?.updatedAt || p?.createdAt || 0).getTime()
  return Number.isFinite(t) ? t : 0
}

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

/** 按商品更新时间（无则创建时间）落在统计范围内；「全部」为全库 */
const productsInTimeRange = computed(() => {
  const list = products.value || []
  if (period.value === 'all') return list
  const start = periodStartMs.value
  const end = periodEndMs.value
  return list.filter((p) => {
    const t = productActivityMs(p)
    return t >= start && t <= end
  })
})

const periodScopeLabel = computed(() => {
  if (period.value === 'day') return '今日'
  if (period.value === 'week') return '本周'
  if (period.value === 'month') return '本月'
  return '全部'
})

const totalInDb = computed(() => (products.value || []).length)

const tabCounts = computed(() => {
  const list = productsInTimeRange.value || []
  const low = list.filter((p) => {
    const s = Number(p.stock || 0)
    return s > 0 && s < STOCK_ALERT_LINE
  }).length
  const sold = list.filter((p) => Number(p.stock || 0) <= 0).length
  return { low, sold, all: low + sold }
})

const filteredList = computed(() => {
  const list = productsInTimeRange.value || []
  if (tab.value === 'low') {
    return list.filter((p) => {
      const s = Number(p.stock || 0)
      return s > 0 && s < STOCK_ALERT_LINE
    })
  }
  if (tab.value === 'sold') {
    return list.filter((p) => Number(p.stock || 0) <= 0)
  }
  return list.filter((p) => Number(p.stock || 0) < STOCK_ALERT_LINE)
})

const total = computed(() => filteredList.value.length)
const pagedList = computed(() => {
  const list = filteredList.value
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function applyTabFromRoute() {
  const t = String(route.query.tab || 'low').toLowerCase()
  tab.value = ['low', 'sold', 'all'].includes(t) ? t : 'low'
}

function setTab(next) {
  tab.value = next
  page.value = 1
  router.replace({ path: route.path, query: { tab: next } })
}

watch(
  () => route.query.tab,
  () => {
    applyTabFromRoute()
    page.value = 1
  },
)

watch(tab, () => {
  page.value = 1
})

watch(period, () => {
  page.value = 1
})

async function loadProducts() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.adminGetProducts()
  if (res.code === 200) {
    products.value = res.data || []
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
}

async function toggleStatus(item) {
  const next = item.status === 1 ? 0 : 1
  const res = await api.adminUpdateProduct(item.productId, { status: next })
  if (res.code === 200) {
    await loadProducts()
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

function notifyRestock(item) {
  const pid = item.productId
  if (!pid || sending.value[pid]) return
  restockPending.value = item
  restockConfirmOpen.value = true
}

async function confirmNotifyRestock() {
  const item = restockPending.value
  if (!item?.productId) {
    restockConfirmOpen.value = false
    return
  }
  const pid = item.productId
  if (sending.value[pid]) return
  const stockNum = Number(item.stock || 0)
  const reason = stockNum <= 0 ? '售罄' : stockNum < STOCK_ALERT_LINE ? '库存紧张' : '常规提醒'
  sending.value = { ...(sending.value || {}), [pid]: true }
  const res = await api.adminNotifyRestock(pid, { reason })
  sending.value = { ...(sending.value || {}), [pid]: false }
  restockConfirmOpen.value = false
  restockPending.value = null
  if (res.code === 200) {
    showAppMessage('已发送补货提醒，商家可在通知中心查看', '发送成功')
  } else {
    showAppMessage(res.message || '发送失败', '提示')
  }
}

function stockStatusLabel(stock) {
  const s = Number(stock || 0)
  if (s <= 0) return '缺货'
  if (s < STOCK_ALERT_LINE) return '低库存'
  return '正常'
}

/** @param {Record<string, unknown>} item */
function merchantShopDisplay(item) {
  const name = String(item?.merchantShopName || '').trim()
  if (name) return name
  if (Number(item?.merchantId || 0)) return '未命名店铺'
  return '—'
}

onMounted(async () => {
  applyTabFromRoute()
  await loadProducts()
})
</script>

<template>
  <div class="admin-page inv-view">
    <header class="inv-hero">
      <div class="inv-hero-text">
        <h2>库存监控</h2>
        <div class="inv-meta-chips" aria-label="快捷信息">
          <span class="inv-chip">预警阈值 · 库存 &lt; {{ STOCK_ALERT_LINE }} 件</span>
          <span class="inv-chip">统计范围 · {{ periodScopeLabel }}</span>
          <span class="inv-chip">待处理 · {{ tabCounts.all }}</span>
        </div>
      </div>
      <div class="inv-hero-aside">
        <div class="inv-seg" role="group" aria-label="统计范围">
          <button type="button" :class="{ on: period === 'day' }" @click="period = 'day'">今日</button>
          <button type="button" :class="{ on: period === 'week' }" @click="period = 'week'">本周</button>
          <button type="button" :class="{ on: period === 'month' }" @click="period = 'month'">本月</button>
          <button type="button" :class="{ on: period === 'all' }" @click="period = 'all'">全部</button>
        </div>
      </div>
    </header>

    <section class="inv-summ" aria-label="库存概览">
      <article class="inv-summ-tile">
        <span class="inv-summ-k">低库存</span>
        <span class="inv-summ-v">{{ tabCounts.low }}</span>
        <span class="inv-summ-s">库存 1～{{ STOCK_ALERT_LINE - 1 }} 件</span>
      </article>
      <article class="inv-summ-tile inv-summ-tile--warn">
        <span class="inv-summ-k">缺货</span>
        <span class="inv-summ-v">{{ tabCounts.sold }}</span>
        <span class="inv-summ-s">库存为 0</span>
      </article>
      <article class="inv-summ-tile inv-summ-tile--note">
        <span class="inv-summ-k">待处理合计</span>
        <span class="inv-summ-v">{{ tabCounts.all }}</span>
        <span class="inv-summ-s">低库存 + 缺货</span>
      </article>
      <article class="inv-summ-tile inv-summ-tile--muted">
        <span class="inv-summ-k">范围内 / 全库</span>
        <span class="inv-summ-v">{{ productsInTimeRange.length }} / {{ totalInDb }}</span>
        <span class="inv-summ-s">件商品</span>
      </article>
    </section>

    <div class="inv-panel">
      <div class="inv-panel-top">
        <div class="inv-panel-top-main">
          <h3 class="inv-panel-title">预警列表</h3>
          <p class="inv-panel-meta">
            <span>共 {{ total }} 条</span>
            <span class="inv-panel-meta-sep" aria-hidden="true">·</span>
            <span>低库存 {{ tabCounts.low }}</span>
            <span class="inv-panel-meta-sep" aria-hidden="true">·</span>
            <span>缺货 {{ tabCounts.sold }}</span>
          </p>
        </div>
      </div>

      <div class="inv-panel-head">
        <div class="tabs">
          <button type="button" class="tab" :class="{ active: tab === 'low' }" @click="setTab('low')">
            低库存<span class="tab-num">{{ tabCounts.low }}</span>
          </button>
          <button type="button" class="tab" :class="{ active: tab === 'sold' }" @click="setTab('sold')">
            缺货<span class="tab-num">{{ tabCounts.sold }}</span>
          </button>
          <button type="button" class="tab" :class="{ active: tab === 'all' }" @click="setTab('all')">
            全部预警<span class="tab-num">{{ tabCounts.all }}</span>
          </button>
        </div>
        <div class="inv-toolbar">
          <button type="button" class="tool-btn" @click="loadProducts">刷新数据</button>
          <RouterLink class="tool-btn link" to="/admin/products">商品管理</RouterLink>
        </div>
      </div>

      <div v-if="loading" class="inv-state inv-panel-inset">加载中…</div>
      <div v-else-if="errorMsg" class="inv-state inv-state--err inv-panel-inset">{{ errorMsg }}</div>
      <div v-else-if="filteredList.length === 0" class="empty-panel inv-panel-inset">
        <template v-if="productsInTimeRange.length === 0">暂无数据</template>
        <template v-else>暂无数据</template>
      </div>
      <template v-else>
        <div class="inv-panel-body">
        <div class="inv-table-wrap">
          <table class="inv-table">
            <thead>
              <tr>
                <th>商品 ID</th>
                <th>名称</th>
                <th>商家</th>
                <th>当前库存</th>
                <th>预警线</th>
                <th>状态</th>
                <th>上架</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in pagedList" :key="item.productId">
                <td class="mono">{{ item.productId }}</td>
                <td class="title-cell">{{ item.title }}</td>
                <td class="inv-merchant-cell">
                  <span
                    class="shop"
                    :class="{ 'shop--placeholder': !String(item.merchantShopName || '').trim() && Number(item.merchantId || 0) }"
                  >{{ merchantShopDisplay(item) }}</span>
                  <span class="inv-merchant-id">商家 ID {{ Number(item.merchantId || 0) || '—' }}</span>
                </td>
                <td>
                  <span
                    class="stock"
                    :class="{
                      'stock--crit': Number(item.stock) <= 0,
                      'stock--low': Number(item.stock) > 0 && Number(item.stock) < STOCK_ALERT_LINE,
                    }"
                    >{{ item.stock }}</span
                  >
                </td>
                <td class="muted">&lt; {{ STOCK_ALERT_LINE }}</td>
                <td>
                  <span
                    class="st-badge"
                    :class="{
                      'st-badge--sold': Number(item.stock) <= 0,
                      'st-badge--low': Number(item.stock) > 0 && Number(item.stock) < STOCK_ALERT_LINE,
                      'st-badge--ok': Number(item.stock) >= STOCK_ALERT_LINE,
                    }"
                    >{{ stockStatusLabel(item.stock) }}</span
                  >
                </td>
                <td><span :class="['pill', item.status === 1 ? 'on' : 'off']">{{ item.status === 1 ? '上架' : '下架' }}</span></td>
                <td>
                  <div class="btn-group">
                    <button type="button" class="action-btn primary" @click="toggleStatus(item)">
                      {{ item.status === 1 ? '下架' : '上架' }}
                    </button>
                    <button
                      type="button"
                      class="action-btn"
                      :class="{ warn: Number(item.stock) < STOCK_ALERT_LINE }"
                      :disabled="sending[item.productId]"
                      @click="notifyRestock(item)"
                    >
                      {{ sending[item.productId] ? '发送中…' : '补货提醒' }}
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
        </div>
      </template>
    </div>

    <ConfirmModal
      :open="restockConfirmOpen"
      title="发送补货提醒"
      confirm-label="确定发送"
      @update:open="restockConfirmOpen = $event"
      @confirm="confirmNotifyRestock"
    >
      <template v-if="restockPending">
        <p>将向商家发送一条补货提醒，对方可在通知中心查看。</p>
        <p><strong>商品：</strong>{{ restockPending.title }}</p>
        <p>
          <strong>原因：</strong>
          {{
            Number(restockPending.stock || 0) <= 0
              ? '缺货'
              : Number(restockPending.stock || 0) < STOCK_ALERT_LINE
                ? '低库存'
                : '常规提醒'
          }}
        </p>
        <p><strong>当前库存：</strong>{{ restockPending.stock }}</p>
      </template>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.inv-view {
  min-width: 0;
}

.inv-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  margin-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}

.inv-hero-text {
  flex: 1;
  min-width: 220px;
}

.inv-hero h2 {
  font-size: clamp(22px, 2.2vw, 28px);
  color: #0a1220;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
  font-weight: 800;
}

.inv-meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.inv-chip {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  background: #f1f5f9;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  letter-spacing: 0.02em;
}

.inv-hero-aside {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.inv-seg {
  display: inline-flex;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.inv-seg button {
  border: none;
  background: transparent;
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #64748b;
  cursor: pointer;
}

.inv-seg button + button {
  border-left: 1px solid #e2e8f0;
}

.inv-seg button.on {
  background: #0b1630;
  color: #f8fafc;
}

.inv-summ {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.inv-summ-tile {
  border-radius: 14px;
  border: 1px solid #e4e9f1;
  background: #fff;
  padding: 14px 16px 12px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset, 0 2px 8px rgba(15, 23, 42, 0.04);
}

.inv-summ-tile--warn {
  border-color: #fecaca;
  background: linear-gradient(180deg, #fff 0%, #fff7f7 100%);
}

.inv-summ-tile--note {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.inv-summ-tile--muted {
  border-color: #e2e8f0;
  background: linear-gradient(180deg, #fafbfc 0%, #f4f6f9 100%);
}

.inv-summ-k {
  display: block;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #94a3b8;
  margin-bottom: 6px;
}

.inv-summ-v {
  display: block;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.1;
}

.inv-summ-s {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.inv-panel {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  padding: 0;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
}

.inv-panel-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 18px 12px;
  border-bottom: 1px solid #f1f5f9;
}

.inv-panel-title {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.inv-panel-meta {
  margin: 6px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.inv-panel-meta-sep {
  margin: 0 0.35em;
  color: #cbd5e1;
}

.inv-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 18px 14px;
  margin-bottom: 0;
  background: #fafbfc;
  border-bottom: 1px solid #eef2f7;
}

.inv-toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tab {
  height: 38px;
  padding: 0 14px;
  border: 1px solid #c9d4e4;
  border-radius: 10px;
  background: #fff;
  color: #23344f;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}
.tab.active {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.tab-num {
  margin-left: 6px;
  opacity: 0.88;
  font-weight: 900;
}

.tool-btn {
  height: 38px;
  padding: 0 12px;
  border: 1px solid #c9d4e4;
  background: #fff;
  color: #23344f;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}
.tool-btn.link:hover {
  border-color: #0b1630;
}

.inv-state {
  padding: 36px 16px;
  text-align: center;
  font-weight: 700;
  color: #64748b;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}
.inv-state--err {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fef2f2;
}

.inv-panel-inset {
  margin: 0 18px 16px;
}

.inv-panel-body {
  padding: 0 18px 16px;
}

.inv-table-wrap {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  margin-bottom: 14px;
}

.inv-table {
  width: 100%;
  min-width: 880px;
  border-collapse: separate;
  border-spacing: 0;
}
.inv-table th,
.inv-table td {
  border-bottom: 1px solid #eef2f7;
  padding: 13px 14px;
  font-size: 14px;
  color: #1e293b;
  text-align: left;
  vertical-align: middle;
}
.inv-table th {
  background: #f8fafc;
  font-size: 12px;
  color: #334155;
  font-weight: 800;
  letter-spacing: 0.02em;
  white-space: nowrap;
  border-bottom: 1px solid #e2e8f0;
}
.inv-table tbody tr:last-child td {
  border-bottom: none;
}
.inv-table tbody tr:hover td {
  background: #f8fafc;
}

.inv-merchant-cell {
  min-width: 140px;
}

.mono {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  color: #334155;
}
.title-cell {
  font-weight: 700;
  color: #0f172a;
  max-width: 280px;
}

.muted {
  color: #475569;
  font-weight: 600;
}
.shop {
  display: block;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.35;
}

.shop--placeholder {
  font-weight: 700;
  color: #64748b;
}

.inv-merchant-id {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.01em;
}
.stock {
  font-weight: 800;
  color: #0e1930;
  font-variant-numeric: tabular-nums;
}
.stock--low {
  color: #c2410c;
}
.stock--crit {
  color: #b91c1c;
}

.st-badge {
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
.st-badge--low {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}
.st-badge--sold {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}
.st-badge--ok {
  border-color: #bbf7d0;
  background: #ecfdf3;
  color: #15803d;
}

.pill {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}
.pill.on {
  background: #d9f4df;
  color: #166b2d;
}
.pill.off {
  background: #ffe2e2;
  color: #8a1d1d;
}

.btn-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.action-btn {
  height: 34px;
  padding: 0 12px;
  border: 1px solid #c9d4e4;
  background: #f8fbff;
  color: #23344f;
  border-radius: 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}
.action-btn.primary {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.action-btn.warn {
  border-color: #ffd591;
  background: #fff7e6;
  color: #ad6800;
}
.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty-panel {
  background: #fff;
  border: 1px solid #dbe3ed;
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  color: #6c7d93;
  font-weight: 700;
  line-height: 1.55;
  max-width: 560px;
  margin: 0 auto;
}

@media (max-width: 1020px) {
  .inv-summ {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .inv-summ {
    grid-template-columns: 1fr;
  }
}
</style>
