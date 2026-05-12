<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'

/** 与库存监控预警线一致，用于「低库存」样式 */
const STOCK_LOW_LINE = 20

const LIST_TABS = [
  { id: 'all', label: '全部' },
  { id: 'active', label: '在售' },
  { id: 'out', label: '缺货' },
  { id: 'off', label: '已下架' },
]

const products = ref([])
const loading = ref(false)
const errorMsg = ref('')
const router = useRouter()
const keyword = ref('')
const listTab = ref('all')
const categoryFilter = ref('')
const appliedKeyword = ref('')
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const editing = ref({})
const page = ref(1)
const pageSize = ref(10)
/** 排序：'' | 'price' | 'stock' */
const sortKey = ref('')
const sortDir = ref('asc')

function goCreateProduct() {
  router.push('/merchant/product/create')
}

async function loadProducts() {
  loading.value = true
  const res = await api.merchantGetProducts(merchantId.value)
  if (res.code === 200) {
    products.value = res.data
    const next = {}
    for (const p of products.value || []) {
      next[p.productId] = {
        price: String(p.price ?? ''),
        stock: String(p.stock ?? ''),
        saving: false,
      }
    }
    editing.value = next
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
}

async function toggleStatus(item) {
  const next = item.status === 1 ? 0 : 1
  const res = await api.merchantUpdateProduct(merchantId.value, item.productId, { status: next })
  if (res.code === 200) {
    await loadProducts()
  } else {
    showAppMessage(res.message || '操作失败')
  }
}

function parseNonNegativeNumber(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n < 0) return null
  return n
}

async function saveRow(item) {
  const row = editing.value?.[item.productId]
  if (!row) return
  const p = parseNonNegativeNumber(row.price)
  if (p === null) {
    showAppMessage('价格必须为非负数')
    return
  }
  const s = parseNonNegativeNumber(row.stock)
  if (s === null) {
    showAppMessage('库存必须为非负数')
    return
  }
  row.saving = true
  const res = await api.merchantUpdateProduct(merchantId.value, item.productId, { price: p, stock: Math.floor(s) })
  row.saving = false
  if (res.code === 200) {
    await loadProducts()
  } else {
    showAppMessage(res.message || '保存失败')
  }
}

function goEditContent(productId) {
  router.push(`/merchant/product/${productId}/edit`)
}

const categoryOptions = computed(() => {
  const m = new Map()
  for (const p of products.value) {
    const cid = String(p.categoryId ?? '')
    if (!cid) continue
    if (!m.has(cid)) m.set(cid, p.categoryName || `分类${cid}`)
  }
  return Array.from(m.entries()).map(([value, label]) => ({ value, label }))
})

const filteredProducts = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  const tab = listTab.value
  return products.value.filter((p) => {
    const hitKeyword = !kw || String(p.title || '').toLowerCase().includes(kw) || String(p.productId).includes(kw)
    const st = Number(p.status) === 1 ? 1 : 0
    const sk = Number(p.stock ?? 0)
    let hitTab = true
    if (tab === 'active') hitTab = st === 1 && sk > 0
    else if (tab === 'out') hitTab = st === 1 && sk <= 0
    else if (tab === 'off') hitTab = st === 0
    const hitCategory = !categoryFilter.value || String(p.categoryId) === categoryFilter.value
    return hitKeyword && hitTab && hitCategory
  })
})

function sortPriceValue(p) {
  const row = editing.value?.[p.productId]
  const raw = row?.price
  const n =
    raw != null && String(raw).trim() !== '' ? Number(raw) : Number(p.price)
  return Number.isFinite(n) ? n : 0
}

function sortStockValue(p) {
  const row = editing.value?.[p.productId]
  const raw = row?.stock
  const n =
    raw != null && String(raw).trim() !== '' ? Number(raw) : Number(p.stock)
  return Number.isFinite(n) ? n : 0
}

function toggleSort(key) {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDir.value = 'asc'
  }
  page.value = 1
}

const sortedProducts = computed(() => {
  const list = [...(Array.isArray(filteredProducts.value) ? filteredProducts.value : [])]
  const key = sortKey.value
  if (!key) return list
  const mul = sortDir.value === 'desc' ? -1 : 1
  const val = key === 'price' ? sortPriceValue : sortStockValue
  list.sort((a, b) => {
    const va = val(a)
    const vb = val(b)
    if (va !== vb) return va < vb ? -mul : mul
    return (Number(a.productId) || 0) - (Number(b.productId) || 0)
  })
  return list
})

function runSearch() {
  appliedKeyword.value = keyword.value
  page.value = 1
}

function resetFilters() {
  keyword.value = ''
  appliedKeyword.value = ''
  listTab.value = 'all'
  categoryFilter.value = ''
  sortKey.value = ''
  sortDir.value = 'asc'
  page.value = 1
}

watch([listTab, categoryFilter], () => {
  page.value = 1
})

const total = computed(() => (Array.isArray(sortedProducts.value) ? sortedProducts.value.length : 0))
const pagedProducts = computed(() => {
  const list = Array.isArray(sortedProducts.value) ? sortedProducts.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function resolveMediaUrl(raw) {
  const s = String(raw || '').trim()
  if (!s) return ''
  if (/^(https?:|data:|blob:)/i.test(s)) return s
  const base = String(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  if (s.startsWith('/')) return base ? `${base}${s}` : s
  return s
}

/**
 * @param {Record<string, unknown>} p
 */
function productImageUrl(p) {
  const d = p?.detail
  const fromDetail = d && typeof d === 'object' && 'imageUrl' in d ? d.imageUrl : ''
  return resolveMediaUrl(fromDetail || p?.imageUrl)
}

/**
 * @param {Record<string, unknown>} p
 */
function stockMeta(p) {
  const row = editing.value?.[p.productId]
  const raw = row?.stock
  const n =
    raw != null && String(raw).trim() !== '' ? Number(raw) : Number(p.stock ?? 0)
  const sk = Number.isFinite(n) ? n : 0
  const on = Number(p.status) === 1
  if (!on) return { tone: 'muted', label: '已下架' }
  if (sk <= 0) return { tone: 'out', label: '断货' }
  if (sk < STOCK_LOW_LINE) return { tone: 'low', label: `低库存 (${sk})` }
  return { tone: 'ok', label: `有货 (${sk})` }
}

const tabCounts = computed(() => {
  const list = products.value || []
  let active = 0
  let out = 0
  let off = 0
  for (const p of list) {
    const st = Number(p.status) === 1 ? 1 : 0
    const sk = Number(p.stock ?? 0)
    if (st === 0) off += 1
    else if (sk <= 0) out += 1
    else active += 1
  }
  return { all: list.length, active, out, off }
})

onMounted(loadProducts)
</script>

<template>
  <div class="inv-matrix">
    <div class="inv-strip">
      <div class="inv-tabs" role="tablist" aria-label="商品范围">
        <button
          v-for="t in LIST_TABS"
          :key="t.id"
          type="button"
          role="tab"
          :aria-selected="listTab === t.id"
          :class="['inv-tab', { 'inv-tab--on': listTab === t.id }]"
          @click="listTab = t.id"
        >
          {{ t.label }}
          <span class="inv-tab-num">{{
            t.id === 'all' ? tabCounts.all : t.id === 'active' ? tabCounts.active : t.id === 'out' ? tabCounts.out : tabCounts.off
          }}</span>
        </button>
      </div>
      <div class="inv-strip-tools">
        <RouterLink class="inv-link" to="/merchant/notifications">补货提醒</RouterLink>
        <button type="button" class="inv-btn-text" @click="resetFilters">重置筛选</button>
        <div class="inv-cat">
          <span class="inv-cat-label">分类</span>
          <select v-model="categoryFilter" class="inv-select">
            <option value="">全部分类</option>
            <option v-for="c in categoryOptions" :key="c.value" :value="c.value">{{ c.label }}</option>
          </select>
        </div>
        <div class="inv-strip-search" role="search">
          <div class="inv-search">
            <svg class="inv-search-ic" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                d="M11 19a8 8 0 100-16 8 8 0 000 16zm10 2l-4.35-4.35"
              />
            </svg>
            <input
              v-model="keyword"
              class="inv-search-input"
              type="search"
              placeholder="搜索名称或编号…"
              autocomplete="off"
              @keyup.enter="runSearch"
            />
          </div>
          <button type="button" class="inv-btn-secondary" @click="runSearch">搜索</button>
        </div>
        <button type="button" class="inv-btn-primary" @click="goCreateProduct">
          <span class="inv-plus" aria-hidden="true">+</span>
          上架新商品
        </button>
      </div>
    </div>

    <div v-if="loading" class="inv-panel inv-state">加载中…</div>
    <div v-else-if="errorMsg" class="inv-panel inv-state inv-state--err">{{ errorMsg }}</div>
    <div v-else-if="products.length === 0" class="inv-panel empty">
      <p class="empty-title">暂无商品</p>
      <p class="empty-hint">点击「上架新商品」创建第一件商品。</p>
    </div>
    <div v-else-if="sortedProducts.length === 0" class="inv-panel empty">
      <p class="empty-title">没有符合筛选条件的商品</p>
      <p class="empty-hint">可切换上方标签或调整分类、关键字。</p>
    </div>
    <div v-else class="inv-table-card">
      <div class="inv-table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th class="th-product">商品</th>
              <th class="th-cat">分类</th>
              <th class="th-num">
                <button type="button" class="sort-th" @click="toggleSort('price')">
                  单价
                  <span v-if="sortKey === 'price'" class="sort-ind" aria-hidden="true">{{
                    sortDir === 'asc' ? '↑' : '↓'
                  }}</span>
                </button>
              </th>
              <th class="th-num th-stock">
                <button type="button" class="sort-th" @click="toggleSort('stock')">
                  库存
                  <span v-if="sortKey === 'stock'" class="sort-ind" aria-hidden="true">{{
                    sortDir === 'asc' ? '↑' : '↓'
                  }}</span>
                </button>
              </th>
              <th class="th-on">上架</th>
              <th class="th-act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedProducts" :key="item.productId">
              <td class="td-product">
                <div class="prod-cell">
                  <div class="prod-thumb">
                    <img
                      v-if="productImageUrl(item)"
                      :src="productImageUrl(item)"
                      :alt="String(item.title || '')"
                      loading="lazy"
                      decoding="async"
                    />
                    <div v-else class="prod-thumb-ph" aria-hidden="true">
                      <svg viewBox="0 0 24 24" width="22" height="22" fill="none">
                        <path
                          d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14M9 9h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                          stroke="currentColor"
                          stroke-width="1.6"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                        />
                      </svg>
                    </div>
                  </div>
                  <div class="prod-text">
                    <div class="prod-title">{{ item.title || '—' }}</div>
                    <div class="prod-sub">编号 {{ item.productId }}</div>
                  </div>
                </div>
              </td>
              <td class="td-cat">
                <span class="cat-chip">{{ item.categoryName || '—' }}</span>
              </td>
              <td class="td-num">
                <div class="price-edit">
                  <span class="price-yuan">¥</span>
                  <input
                    v-model="editing[item.productId].price"
                    class="inv-input inv-input--price"
                    inputmode="decimal"
                    aria-label="单价"
                  />
                </div>
              </td>
              <td class="td-stock">
                <div class="stock-row">
                  <span
                    class="stock-pill"
                    :class="{
                      'stock-pill--ok': stockMeta(item).tone === 'ok',
                      'stock-pill--low': stockMeta(item).tone === 'low',
                      'stock-pill--out': stockMeta(item).tone === 'out',
                      'stock-pill--muted': stockMeta(item).tone === 'muted',
                    }"
                  >
                    <i class="stock-dot" />
                    {{ stockMeta(item).label }}
                  </span>
                  <input
                    v-model="editing[item.productId].stock"
                    class="inv-input inv-input--stock"
                    inputmode="numeric"
                    aria-label="库存数量"
                  />
                </div>
              </td>
              <td class="td-on">
                <span :class="['on-badge', item.status === 1 ? 'on-badge--yes' : 'on-badge--no']">
                  {{ item.status === 1 ? '上架' : '下架' }}
                </span>
              </td>
              <td class="td-act">
                <div class="act-btns">
                  <button
                    type="button"
                    class="act-btn act-btn--dark"
                    :disabled="editing[item.productId]?.saving"
                    @click="saveRow(item)"
                  >
                    {{ editing[item.productId]?.saving ? '保存中' : '保存' }}
                  </button>
                  <button type="button" class="act-btn" @click="goEditContent(item.productId)">编辑</button>
                  <button type="button" class="act-btn act-btn--warn" @click="toggleStatus(item)">
                    {{ item.status === 1 ? '下架' : '上架' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="inv-pager">
      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="total"
        @update:page="page = $event"
      />
    </div>
  </div>
</template>

<style scoped>
.inv-matrix {
  min-height: 100%;
  padding: 8px 4px 28px;
  max-width: 1600px;
  margin: 0 auto;
  box-sizing: border-box;
  background: #f1f5f9;
}

.inv-strip-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 200px;
  max-width: min(360px, 100%);
  min-width: 0;
}

.inv-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px 0 14px;
  flex: 1;
  min-width: 0;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.inv-search-ic {
  flex-shrink: 0;
  color: #64748b;
}

.inv-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  outline: none;
}

.inv-search-input::placeholder {
  color: #94a3b8;
  font-weight: 500;
}

.inv-btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-shrink: 0;
  height: 40px;
  padding: 0 18px;
  border-radius: 10px;
  border: 1px solid #0f172a;
  background: #0f172a;
  color: #f8fafc;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.12);
}

.inv-plus {
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
  opacity: 0.95;
}

.inv-btn-secondary {
  flex-shrink: 0;
  height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.inv-btn-secondary:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.inv-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 20px;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.inv-strip-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px 12px;
  flex: 1 1 auto;
  min-width: 0;
}

.inv-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  min-width: 0;
}

.inv-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.inv-tab:hover {
  border-color: #cbd5e1;
  color: #0f172a;
}

.inv-tab--on {
  background: #0f172a;
  border-color: #0f172a;
  color: #f8fafc;
}

.inv-tab-num {
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  opacity: 0.9;
}

.inv-link {
  font-size: 13px;
  font-weight: 700;
  color: #2563eb;
  text-decoration: none;
}

.inv-link:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.inv-btn-text {
  border: none;
  background: none;
  padding: 6px 4px;
  font-size: 13px;
  font-weight: 700;
  color: #64748b;
  cursor: pointer;
}

.inv-btn-text:hover {
  color: #0f172a;
}

.inv-cat {
  display: flex;
  align-items: center;
  gap: 8px;
}

.inv-cat-label {
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
}

.inv-select {
  height: 40px;
  min-width: 160px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.inv-panel {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.05);
}

.inv-state {
  padding: 28px 20px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}

.inv-state--err {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.empty {
  padding: 48px 24px;
  text-align: center;
}

.empty-title {
  margin: 0 0 8px;
  font-weight: 800;
  font-size: 16px;
  color: #0f172a;
}

.empty-hint {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
}

.inv-table-card {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.inv-table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.inv-table {
  width: 100%;
  min-width: 920px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

.inv-table th,
.inv-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  vertical-align: middle;
  color: #334155;
}

.inv-table th {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

/* 固定列宽，避免「商品」列吃满中间留白，分类/单价等紧跟标题区域 */
.th-product {
  width: 30%;
  max-width: 360px;
}

.th-cat {
  width: 12%;
  min-width: 100px;
}

.th-num {
  width: 11%;
  min-width: 104px;
}

.th-stock {
  width: 18%;
  min-width: 168px;
}

.th-on {
  width: 7%;
  min-width: 72px;
}

.th-act {
  width: 22%;
  min-width: 200px;
  white-space: nowrap;
}

.sort-th {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  cursor: pointer;
}

.sort-th:hover {
  color: #0f172a;
}

.sort-ind {
  font-size: 12px;
  color: #0f172a;
  font-weight: 900;
}

.td-product {
  overflow: hidden;
  max-width: 360px;
}

.prod-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.prod-text {
  min-width: 0;
}

.prod-thumb {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
}

.prod-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.prod-thumb-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.prod-title {
  font-weight: 700;
  font-size: 14px;
  color: #0f172a;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.prod-sub {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.cat-chip {
  display: inline-block;
  max-width: 140px;
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.td-num {
  text-align: right;
}

.price-edit {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  width: 100%;
}

.price-yuan {
  font-size: 13px;
  font-weight: 800;
  color: #64748b;
}

.inv-input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.inv-input:focus {
  outline: none;
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

.inv-input--price {
  width: 96px;
  text-align: right;
}

.stock-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.inv-input--stock {
  width: 88px;
  text-align: right;
}

.stock-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
}

.stock-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  flex-shrink: 0;
}

.stock-pill--ok {
  border-color: #bbf7d0;
  background: #ecfdf5;
  color: #166534;
}

.stock-pill--ok .stock-dot {
  background: #22c55e;
}

.stock-pill--low {
  border-color: #fed7aa;
  background: #fffbeb;
  color: #c2410c;
}

.stock-pill--low .stock-dot {
  background: #f97316;
}

.stock-pill--out {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.stock-pill--out .stock-dot {
  background: #ef4444;
}

.stock-pill--muted {
  border-color: #e2e8f0;
  background: #f1f5f9;
  color: #64748b;
}

.stock-pill--muted .stock-dot {
  background: #cbd5e1;
}

.on-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
}

.on-badge--yes {
  background: #dcfce7;
  color: #166534;
}

.on-badge--no {
  background: #f1f5f9;
  color: #64748b;
}

.td-act {
  vertical-align: middle;
  padding-left: 12px;
  padding-right: 16px;
}

.act-btns {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.act-btn {
  height: 36px;
  padding: 0 14px;
  border-radius: 9px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  flex: 0 0 auto;
  white-space: nowrap;
}

.act-btn:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.act-btn--dark {
  border-color: #0f172a;
  background: #0f172a;
  color: #f8fafc;
}

/* 必须覆盖上一段 .act-btn:hover，否则悬停时浅底仍配浅色字，按钮像「消失」 */
.act-btn--dark:hover:not(:disabled) {
  border-color: #1e293b;
  background: #1e293b;
  color: #f8fafc;
}

.act-btn--dark:focus-visible {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}

.act-btn--dark:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.act-btn--warn {
  border-color: #fecaca;
  background: #fff;
  color: #b91c1c;
}

.act-btn--warn:hover {
  background: #fef2f2;
}

.inv-pager {
  margin-top: 16px;
}

@media (max-width: 900px) {
  .inv-strip-search {
    max-width: none;
    flex: 1 1 220px;
  }
}

@media (max-width: 640px) {
  .inv-strip-search {
    flex: 1 1 100%;
    max-width: none;
  }

  .inv-btn-primary {
    flex: 1 1 auto;
    min-width: min(100%, 200px);
  }

  .inv-strip {
    flex-direction: column;
    align-items: stretch;
  }

  .inv-strip-tools {
    justify-content: flex-start;
    padding-top: 4px;
    border-top: 1px solid #f1f5f9;
  }
}
</style>

