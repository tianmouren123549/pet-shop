<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'

const PLACEHOLDER_IMG =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">' +
      '<rect fill="#e8ecf4" width="80" height="80"/>' +
      '<text x="40" y="44" text-anchor="middle" fill="#94a3b8" font-size="11" font-family="system-ui,sans-serif">IMG</text>' +
      '</svg>',
  )

const products = ref([])
const loading = ref(false)
const errorMsg = ref('')
const sending = ref({})
const bulkSending = ref(false)
const restockConfirmOpen = ref(false)
const restockPending = ref(null)
const bulkConfirmOpen = ref(false)

const route = useRoute()
const router = useRouter()

/** 列表标签：全部 | 上架 | 下架 | 库存预警 */
const listTab = ref('all')
const advancedOpen = ref(false)
const selectedMerchantId = ref('ALL')
const keyword = ref('')
/** 高级筛选关键词：须 Enter 或点「搜索」才参与过滤 */
const appliedKeyword = ref('')
const page = ref(1)
const pageSize = ref(6)
/** 表格行多选 */
const selectedIds = ref([])

function syncTabFromRoute(query) {
  if (String(query?.tab || '') === 'alert') {
    listTab.value = 'alert'
    return
  }
  const s = String(query?.shelf || '').toLowerCase()
  if (s === 'online') listTab.value = 'online'
  else if (s === 'offline') listTab.value = 'offline'
  else listTab.value = 'all'
}

function setListTab(t) {
  listTab.value = t
  const q = {}
  if (t === 'online') q.shelf = 'online'
  else if (t === 'offline') q.shelf = 'offline'
  else if (t === 'alert') q.tab = 'alert'
  router.replace({ path: '/admin/products', query: q })
}

watch(
  () => route.query,
  (q) => {
    syncTabFromRoute(q || {})
    page.value = 1
  },
  { deep: true },
)

const lowStockCount = computed(() => (products.value || []).filter((p) => Number(p.stock || 0) < 20).length)

const stats = computed(() => {
  const list = products.value || []
  const online = list.filter((p) => Number(p.status) === 1).length
  const off = list.length - online
  const lowStock = list.filter((p) => Number(p.stock) > 0 && Number(p.stock) < 20).length
  const soldOut = list.filter((p) => Number(p.stock) <= 0).length
  const alertCount = list.filter((p) => {
    const st = Number(p.stock || 0)
    return st <= 0 || (st > 0 && st < 20)
  }).length
  return { total: list.length, online, off, lowStock, soldOut, alertCount }
})

const tabCounts = computed(() => ({
  all: stats.value.total,
  online: stats.value.online,
  offline: stats.value.off,
  alert: stats.value.alertCount,
}))

const merchantOptions = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const map = new Map()
  for (const p of list) {
    const mid = Number(p?.merchantId || 0)
    if (!mid) continue
    const name = String(p?.merchantShopName || '').trim()
    if (!map.has(mid)) map.set(mid, name || `商家${mid}`)
  }
  return Array.from(map.entries())
    .map(([merchantId, shopName]) => ({ merchantId, shopName }))
    .sort((a, b) => a.merchantId - b.merchantId)
})

function normalize(value) {
  return String(value || '').trim().toLowerCase()
}

const filteredProducts = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const kw = normalize(appliedKeyword.value)
  const midSel = selectedMerchantId.value === 'ALL' ? null : Number(selectedMerchantId.value || 0)
  return list.filter((p) => {
    if (midSel && Number(p?.merchantId || 0) !== midSel) return false
    if (listTab.value === 'online' && Number(p?.status) !== 1) return false
    if (listTab.value === 'offline' && Number(p?.status) !== 0) return false
    if (listTab.value === 'alert') {
      const st = Number(p.stock || 0)
      if (!(st <= 0 || (st > 0 && st < 20))) return false
    }
    if (!kw) return true
    const title = normalize(p?.title)
    const pid = String(p?.productId ?? '')
    const shopName = normalize(p?.merchantShopName)
    const mId = String(p?.merchantId ?? '')
    const cat = normalize(p?.categoryName)
    return (
      title.includes(kw) ||
      pid.includes(kw) ||
      shopName.includes(kw) ||
      mId.includes(kw) ||
      cat.includes(kw)
    )
  })
})

function runProductKeywordSearch() {
  appliedKeyword.value = keyword.value
}

watch([selectedMerchantId, appliedKeyword, listTab], () => {
  page.value = 1
  selectedIds.value = []
})

const total = computed(() => (Array.isArray(filteredProducts.value) ? filteredProducts.value.length : 0))

const pagedFilteredProducts = computed(() => {
  const list = Array.isArray(filteredProducts.value) ? filteredProducts.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

const pageStart = computed(() => {
  if (!total.value) return 0
  return (Math.max(1, page.value) - 1) * Math.max(1, pageSize.value) + 1
})

const pageEnd = computed(() => {
  if (!total.value) return 0
  return Math.min(total.value, page.value * pageSize.value)
})

watch([total, pageSize], () => {
  const tp = Math.max(1, Math.ceil(Number(total.value || 0) / Math.max(1, Number(pageSize.value || 1))))
  if (page.value > tp) page.value = tp
})

/** 后端常返回相对路径（如 /uploads/...），需拼接 VITE_API_BASE */
function resolveMediaUrl(raw) {
  const s = String(raw || '').trim()
  if (!s) return ''
  if (/^(https?:|data:|blob:)/i.test(s)) return s
  const base = String(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  if (s.startsWith('/')) return base ? `${base}${s}` : s
  return s
}

function productThumb(p) {
  const raw = String(p?.imageUrl || p?.detail?.imageUrl || '').trim()
  const resolved = resolveMediaUrl(raw)
  return resolved || PLACEHOLDER_IMG
}

function skuText(p) {
  return `SKU: PW-${String(p?.productId ?? '').padStart(5, '0')}`
}

function merchantInitials(item) {
  const name = String(item?.merchantShopName || '').trim()
  if (!name) return '商'
  const ascii = /^[a-zA-Z]/.test(name)
  if (ascii) {
    const parts = name.split(/[\s_\-]+/).filter(Boolean)
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase().slice(0, 2)
    return name.slice(0, 2).toUpperCase()
  }
  return name.slice(0, 2)
}

/** 合规/状态展示（中文） */
function complianceRow(item) {
  const st = Number(item.stock || 0)
  const on = Number(item.status) === 1
  if (!on) return { cls: 'flagged', dot: '#dc2626', text: '已下架' }
  if (st <= 0) return { cls: 'flagged', dot: '#dc2626', text: '售罄' }
  if (st < 20) return { cls: 'review', dot: '#ea580c', text: '在售 · 库存预警' }
  return { cls: 'ok', dot: '#16a34a', text: '在售 · 正常' }
}

function categoryTag(item) {
  const n = String(item?.categoryName || '').trim()
  if (n) return n
  const cid = Number(item?.categoryId || 0)
  if (cid) return `类目 #${cid}`
  return '未分类'
}

function toggleSelectAll(e) {
  const checked = e.target.checked
  if (!checked) {
    selectedIds.value = []
    return
  }
  selectedIds.value = pagedFilteredProducts.value.map((p) => p.productId)
}

function toggleRow(id, checked) {
  const set = new Set(selectedIds.value)
  if (checked) set.add(id)
  else set.delete(id)
  selectedIds.value = Array.from(set)
}

const allPageSelected = computed(() => {
  const ids = pagedFilteredProducts.value.map((p) => p.productId)
  return ids.length > 0 && ids.every((id) => selectedIds.value.includes(id))
})

async function loadProducts() {
  loading.value = true
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

function merchantText(item) {
  const name = String(item?.merchantShopName || '').trim()
  const mid = Number(item?.merchantId || 0)
  return name || (mid ? `商家${mid}` : '-')
}

function notifyRestock(item) {
  const pid = item.productId
  if (!pid) return
  if (sending.value[pid]) return
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
  const reason = stockNum <= 0 ? '售罄' : stockNum < 20 ? '库存紧张' : '常规提醒'
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

function notifyLowStockBatch() {
  if (bulkSending.value) return
  if (!lowStockCount.value) {
    showAppMessage('当前没有低库存/售罄商品（库存 < 20）', '提示')
    return
  }
  bulkConfirmOpen.value = true
}

async function confirmBulkNotify() {
  if (bulkSending.value) return
  const targets = (products.value || []).filter((p) => Number(p.stock || 0) < 20)
  if (!targets.length) {
    bulkConfirmOpen.value = false
    return
  }
  bulkConfirmOpen.value = false
  bulkSending.value = true
  let okCount = 0
  for (const item of targets) {
    const pid = item.productId
    if (!pid) continue
    sending.value = { ...(sending.value || {}), [pid]: true }
    const stockNum = Number(item.stock || 0)
    const reason = stockNum <= 0 ? '售罄' : '库存紧张'
    const res = await api.adminNotifyRestock(pid, { reason })
    sending.value = { ...(sending.value || {}), [pid]: false }
    if (res.code === 200) okCount += 1
  }
  bulkSending.value = false
  showAppMessage(`已发送 ${okCount}/${targets.length} 条补货提醒`, '批量提醒')
}

function onPlaceholderErr(e) {
  const t = e.target
  if (t && t.src !== PLACEHOLDER_IMG) t.src = PLACEHOLDER_IMG
}

onMounted(async () => {
  syncTabFromRoute(route.query)
  await loadProducts()
})
</script>

<template>
  <div class="admin-page prod-mod">
    <div class="pm-head">
      <div class="pm-head-text">
        <h2>商品管理</h2>
      </div>
      <div class="pm-head-actions">
        <button type="button" class="pm-btn-dark" @click="loadProducts">同步刷新</button>
      </div>
    </div>

    <div class="pm-kpis">
      <div class="pm-kpi">
        <span class="pm-kpi-label">商品总数</span>
        <div class="pm-kpi-row">
          <strong class="pm-kpi-num">{{ stats.total }}</strong>
          <span class="pm-kpi-badge pm-kpi-badge--muted">全平台</span>
        </div>
      </div>
      <div class="pm-kpi">
        <span class="pm-kpi-label">上架中</span>
        <div class="pm-kpi-row">
          <strong class="pm-kpi-num">{{ stats.online }}</strong>
          <span class="pm-kpi-badge pm-kpi-badge--ok">在售</span>
        </div>
      </div>
      <div class="pm-kpi">
        <span class="pm-kpi-label">已下架</span>
        <div class="pm-kpi-row">
          <strong class="pm-kpi-num">{{ stats.off }}</strong>
          <span class="pm-kpi-badge pm-kpi-badge--muted">监管</span>
        </div>
      </div>
      <div class="pm-kpi">
        <span class="pm-kpi-label">库存预警</span>
        <div class="pm-kpi-row">
          <strong class="pm-kpi-num">{{ stats.alertCount }}</strong>
          <span class="pm-kpi-badge pm-kpi-badge--warn">待关注</span>
        </div>
      </div>
    </div>

    <div class="pm-panel">
      <div class="pm-panel-head">
        <div class="pm-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            :aria-selected="listTab === 'all'"
            :class="['pm-tab', { active: listTab === 'all' }]"
            @click="setListTab('all')"
          >
            全部商品
            <span class="pm-tab-count">{{ tabCounts.all }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="listTab === 'online'"
            :class="['pm-tab', { active: listTab === 'online' }]"
            @click="setListTab('online')"
          >
            上架中
            <span class="pm-tab-count">{{ tabCounts.online }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="listTab === 'offline'"
            :class="['pm-tab', { active: listTab === 'offline' }]"
            @click="setListTab('offline')"
          >
            已下架
            <span class="pm-tab-count">{{ tabCounts.offline }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="listTab === 'alert'"
            :class="['pm-tab', { active: listTab === 'alert' }]"
            @click="setListTab('alert')"
          >
            库存预警
            <span class="pm-tab-count">{{ tabCounts.alert }}</span>
          </button>
        </div>
        <div class="pm-panel-tools">
          <button
            type="button"
            class="pm-panel-btn"
            :class="{ 'pm-panel-btn--toggle-on': advancedOpen }"
            @click="advancedOpen = !advancedOpen"
          >
            {{ advancedOpen ? '收起筛选' : '高级筛选' }}
          </button>
          <button type="button" class="pm-panel-btn pm-panel-btn--dark" :disabled="bulkSending" @click="notifyLowStockBatch">
            {{ bulkSending ? '发送中…' : '批量补货提醒' }}
          </button>
        </div>
      </div>

      <div v-show="advancedOpen" class="pm-filters">
        <div class="pm-filter-item">
          <label>归属商家</label>
          <select v-model="selectedMerchantId" class="pm-select">
            <option value="ALL">全部商家</option>
            <option v-for="m in merchantOptions" :key="m.merchantId" :value="String(m.merchantId)">
              {{ m.shopName }}（ID: {{ m.merchantId }}）
            </option>
          </select>
        </div>
        <div class="pm-filter-item pm-filter-grow">
          <label>关键词</label>
          <div class="pm-keyword-row">
            <input
              v-model="keyword"
              class="pm-input pm-input--grow"
              type="text"
              placeholder="商品名 / ID / 类目 / 店铺名 / 商家ID"
              @keyup.enter="runProductKeywordSearch"
            />
            <button type="button" class="pm-search-btn" @click="runProductKeywordSearch">搜索</button>
          </div>
        </div>
        <p class="pm-filter-meta">当前列表 {{ filteredProducts.length }} 件商品</p>
      </div>

      <div v-if="loading" class="pm-empty">加载中…</div>
      <div v-else-if="errorMsg" class="pm-empty pm-empty--err">{{ errorMsg }}</div>
      <div v-else-if="products.length === 0" class="pm-empty">暂无商品</div>
      <div v-else-if="filteredProducts.length === 0" class="pm-empty">没有匹配的商品，请调整筛选条件</div>
      <template v-else>
        <div class="pm-table-wrap">
          <table class="pm-table">
            <thead>
              <tr>
                <th class="col-check">
                  <input
                    type="checkbox"
                    :checked="allPageSelected"
                    aria-label="全选本页"
                    @change="toggleSelectAll"
                  />
                </th>
                <th class="col-product">商品信息</th>
                <th class="col-cat">类目</th>
                <th class="col-merchant">商家</th>
                <th class="col-price">价格（元）</th>
                <th class="col-stock">库存</th>
                <th class="col-status">状态</th>
                <th class="col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in pagedFilteredProducts" :key="item.productId">
                <td class="col-check">
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(item.productId)"
                    :aria-label="'选择 ' + item.title"
                    @change="toggleRow(item.productId, $event.target.checked)"
                  />
                </td>
                <td>
                  <div class="pm-product">
                    <img
                      class="pm-product-img"
                      :src="productThumb(item)"
                      alt=""
                      width="56"
                      height="56"
                      loading="lazy"
                      @error="onPlaceholderErr"
                    />
                    <div class="pm-product-meta">
                      <div class="pm-product-title">{{ item.title }}</div>
                      <div class="pm-product-sku">{{ skuText(item) }}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="pm-cat">{{ categoryTag(item) }}</span>
                </td>
                <td>
                  <div class="pm-merchant">
                    <span class="pm-merchant-av">{{ merchantInitials(item) }}</span>
                    <span class="pm-merchant-name">{{ merchantText(item) }}</span>
                  </div>
                </td>
                <td class="pm-price">¥{{ formatYuan(item.price) }}</td>
                <td>
                  <span class="pm-stock">{{ item.stock }}</span>
                  <span v-if="Number(item.stock) <= 0" class="pm-stock-tag soldout">售罄</span>
                  <span v-else-if="Number(item.stock) < 20" class="pm-stock-tag low">紧张</span>
                </td>
                <td>
                  <span class="pm-comp" :class="complianceRow(item).cls">
                    <i class="pm-comp-dot" :style="{ background: complianceRow(item).dot }" />
                    {{ complianceRow(item).text }}
                  </span>
                </td>
                <td class="col-actions">
                  <div class="pm-actions">
                    <button type="button" class="pm-act pm-act--primary" @click="toggleStatus(item)">
                      {{ item.status === 1 ? '下架' : '上架' }}
                    </button>
                    <button
                      type="button"
                      class="pm-act"
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

        <div class="pm-pagination-row">
          <p class="pm-range">
            显示第 <strong>{{ pageStart }}</strong> – <strong>{{ pageEnd }}</strong> 条，共
            <strong>{{ total }}</strong> 条
            <template v-if="selectedIds.length"> · 已选 {{ selectedIds.length }} 条</template>
          </p>
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
        <p>将向商家发送一条补货提醒通知。</p>
        <p><strong>商品：</strong>{{ restockPending.title }}</p>
        <p>
          <strong>原因：</strong>
          {{
            Number(restockPending.stock || 0) <= 0
              ? '售罄'
              : Number(restockPending.stock || 0) < 20
                ? '库存紧张'
                : '常规提醒'
          }}
        </p>
        <p><strong>当前库存：</strong>{{ restockPending.stock }}</p>
      </template>
    </ConfirmModal>

    <ConfirmModal
      :open="bulkConfirmOpen"
      title="一键提醒低库存"
      confirm-label="确定发送"
      @update:open="bulkConfirmOpen = $event"
      @confirm="confirmBulkNotify"
    >
      <p>将向各商品对应商家发送补货提醒，共 {{ lowStockCount }} 条。确定继续吗？</p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.prod-mod {
  min-width: 0;
}

.pm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid #eef2f7;
}

.pm-head-text {
  flex: 1;
  min-width: 220px;
}

h2 {
  font-size: clamp(20px, 2vw, 26px);
  color: #0a1220;
  letter-spacing: -0.02em;
  font-weight: 800;
  margin: 0;
}

.pm-head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.pm-btn-dark {
  height: 36px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid #0b1630;
  background: #0b1630;
  color: #f8fafc;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.pm-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.pm-kpi {
  background: #fff;
  border: 1px solid #e8ecf2;
  border-radius: 14px;
  padding: 10px 12px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset;
}

.pm-kpi-label {
  display: block;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 5px;
}

.pm-kpi-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.pm-kpi-num {
  font-size: clamp(22px, 2.4vw, 28px);
  font-weight: 800;
  color: #0a1220;
  letter-spacing: -0.02em;
  line-height: 1;
}

.pm-kpi-badge {
  font-size: 10px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 999px;
  letter-spacing: 0.04em;
}

.pm-kpi-badge--ok {
  background: #ecfdf3;
  color: #15803d;
}

.pm-kpi-badge--warn {
  background: #fff7ed;
  color: #c2410c;
}

.pm-kpi-badge--muted {
  background: #f1f5f9;
  color: #64748b;
}

.pm-panel {
  border: 1px solid #e4e9f1;
  border-radius: 16px;
  background: #fafbfc;
  overflow: hidden;
}

.pm-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  background: #fff;
  border-bottom: 1px solid #eef2f7;
}

.pm-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pm-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  cursor: pointer;
}

.pm-tab-count {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
}

.pm-tab.active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #0b1630;
}

.pm-tab.active .pm-tab-count {
  background: #dbeafe;
  color: #1e40af;
}

.pm-panel-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pm-panel-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid #d0d9e6;
  background: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #334155;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}

.pm-panel-btn:hover:not(:disabled) {
  border-color: #0b1630;
  color: #0b1630;
}

.pm-panel-btn--toggle-on {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1e3a8a;
}

.pm-panel-btn--dark {
  border-color: #0b1630;
  background: #0b1630;
  color: #f8fafc;
}

.pm-panel-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.pm-filters {
  display: grid;
  grid-template-columns: 200px 1fr auto;
  gap: 12px;
  align-items: end;
  padding: 12px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f7;
}

.pm-filter-item label {
  display: block;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 6px;
}

.pm-filter-grow {
  min-width: 0;
}

.pm-keyword-row {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.pm-input--grow {
  flex: 1;
  min-width: 0;
  width: auto;
}

.pm-search-btn {
  flex-shrink: 0;
  height: 36px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid #0f172a;
  background: #0f172a;
  font-size: 12px;
  font-weight: 800;
  color: #fff;
  cursor: pointer;
}

.pm-select,
.pm-input {
  width: 100%;
  height: 36px;
  border: 1px solid #d8e0ea;
  border-radius: 10px;
  padding: 0 10px;
  font-size: 12px;
  color: #1e293b;
  background: #fff;
}

.pm-filter-meta {
  font-size: 11px;
  color: #64748b;
  font-weight: 700;
  padding-bottom: 8px;
}

.pm-empty {
  padding: 40px 16px;
  text-align: center;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.pm-empty--err {
  color: #b91c1c;
}

.pm-table-wrap {
  overflow-x: auto;
  background: #fff;
}

.pm-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.pm-table thead th {
  text-align: left;
  padding: 14px 16px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #64748b;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e8ecf2;
  white-space: nowrap;
}

.pm-table tbody td {
  padding: 18px 16px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
  color: #334155;
}

.pm-table tbody tr:hover td {
  background: #fafbff;
}

.col-check {
  width: 48px;
  text-align: center;
  user-select: none;
  -webkit-user-select: none;
}

.pm-table input[type='checkbox'] {
  width: 18px;
  height: 18px;
  margin: 0;
  cursor: pointer;
  vertical-align: middle;
  accent-color: #0b1630;
}

.pm-table input[type='checkbox']:focus {
  outline: none;
}

.pm-table input[type='checkbox']:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
  border-radius: 2px;
}

.col-product {
  width: 36%;
}

.col-cat {
  width: 11%;
}

.col-merchant {
  width: 13%;
}

.col-price {
  width: 92px;
}

.col-stock {
  width: 86px;
}

.col-status {
  width: 118px;
}

.col-actions {
  width: 184px;
}

.pm-product {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.pm-product-img {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  object-fit: cover;
  border: 1px solid #e8ecf2;
  background: #f8fafc;
  flex-shrink: 0;
}

.pm-product-meta {
  min-width: 0;
}

.pm-product-title {
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.45;
  word-break: break-word;
}

.pm-product-sku {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 700;
}

.pm-cat {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.pm-merchant {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pm-merchant-av {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: linear-gradient(145deg, #e8ecf4 0%, #dce3ee 100%);
  color: #334155;
  font-size: 11px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
}

.pm-merchant-name {
  font-weight: 700;
  color: #1e293b;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pm-price {
  font-weight: 800;
  color: #0f172a;
  white-space: nowrap;
}

.pm-stock {
  font-weight: 800;
  margin-right: 6px;
}

.pm-stock-tag {
  font-size: 10px;
  font-weight: 800;
  padding: 2px 8px;
  border-radius: 999px;
}

.pm-stock-tag.low {
  background: #fff7ed;
  color: #c2410c;
}

.pm-stock-tag.soldout {
  background: #fef2f2;
  color: #b91c1c;
}

.pm-comp {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.pm-comp-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pm-comp.ok {
  color: #15803d;
}

.pm-comp.review {
  color: #c2410c;
}

.pm-comp.flagged {
  color: #b91c1c;
}

.pm-actions {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.pm-act {
  height: 32px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid #d8e0ea;
  background: #fff;
  font-size: 11px;
  font-weight: 800;
  color: #334155;
  cursor: pointer;
}

.pm-act:hover:not(:disabled) {
  border-color: #0b1630;
  color: #0b1630;
}

.pm-act--primary {
  border-color: #0b1630;
  background: #0b1630;
  color: #f8fafc;
}

.pm-act:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.pm-pagination-row {
  padding: 12px 14px;
  background: #fff;
  border-top: 1px solid #eef2f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pm-range {
  margin: 0;
  font-size: 11px;
  color: #64748b;
  font-weight: 700;
}

.pm-range strong {
  color: #0f172a;
}

@media (max-width: 1100px) {
  .pm-kpis {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 720px) {
  .pm-filters {
    grid-template-columns: 1fr;
  }

  .pm-filter-meta {
    grid-column: 1 / -1;
  }

  .pm-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
