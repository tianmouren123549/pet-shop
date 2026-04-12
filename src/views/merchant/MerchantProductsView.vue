<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'

const products = ref([])
const loading = ref(false)
const errorMsg = ref('')
const router = useRouter()
const keyword = ref('')
const statusFilter = ref('')
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
  return products.value.filter((p) => {
    const hitKeyword = !kw || String(p.title || '').toLowerCase().includes(kw) || String(p.productId).includes(kw)
    const hitStatus = !statusFilter.value || String(p.status) === statusFilter.value
    const hitCategory = !categoryFilter.value || String(p.categoryId) === categoryFilter.value
    return hitKeyword && hitStatus && hitCategory
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
  statusFilter.value = ''
  categoryFilter.value = ''
  sortKey.value = ''
  sortDir.value = 'asc'
  page.value = 1
}

watch([statusFilter, categoryFilter], () => {
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

function setPageSize(n) {
  pageSize.value = Number(n || 10)
  page.value = 1
}

onMounted(loadProducts)
</script>

<template>
  <div class="merchant-page">
    <h2>我的商品管理</h2>
    <p class="desc">
      当前商家ID：{{ merchantId || '-' }}；可维护价格、库存与上下架状态。
    </p>

    <div class="toolbar">
      <button class="create-btn" @click="goCreateProduct">上架新商品</button>
      <button class="reset-btn" @click="router.push('/merchant/notifications')">查看补货提醒</button>
    </div>
    <div class="filters">
      <input
        v-model="keyword"
        class="filter-input"
        placeholder="按商品名称或ID搜索"
        @keyup.enter="runSearch"
      />
      <button class="search-btn" @click="runSearch">搜索</button>
      <button class="reset-btn" @click="resetFilters">重置</button>
      <select v-model="statusFilter" class="filter-select">
        <option value="">全部状态</option>
        <option value="1">上架中</option>
        <option value="0">已下架</option>
      </select>
      <select v-model="categoryFilter" class="filter-select">
        <option value="">全部分类</option>
        <option v-for="c in categoryOptions" :key="c.value" :value="c.value">{{ c.label }}</option>
      </select>
    </div>
    <div v-if="loading">加载中...</div>
    <div v-else-if="errorMsg">{{ errorMsg }}</div>
    <div v-else-if="products.length === 0" class="empty">
      <p class="empty-title">暂无商品</p>
      <p class="empty-hint">点击上方“上架新商品”创建第一件商品。</p>
    </div>
    <div v-else-if="sortedProducts.length === 0" class="empty">
      <p class="empty-title">没有符合筛选条件的商品</p>
      <p class="empty-hint">请调整筛选条件后重试。</p>
    </div>
    <table v-else class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>名称</th>
          <th>
            <button type="button" class="sort-th" @click="toggleSort('price')">
              价格
              <span v-if="sortKey === 'price'" class="sort-ind" aria-hidden="true">{{
                sortDir === 'asc' ? '↑' : '↓'
              }}</span>
            </button>
          </th>
          <th>
            <button type="button" class="sort-th" @click="toggleSort('stock')">
              库存
              <span v-if="sortKey === 'stock'" class="sort-ind" aria-hidden="true">{{
                sortDir === 'asc' ? '↑' : '↓'
              }}</span>
            </button>
          </th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in pagedProducts" :key="item.productId">
          <td>{{ item.productId }}</td>
          <td class="title-cell">{{ item.title }}</td>
          <td>
            <input
              v-model="editing[item.productId].price"
              class="cell-input"
              placeholder="价格"
            />
          </td>
          <td>
            <div class="stock-cell">
              <input
                v-model="editing[item.productId].stock"
                :class="['cell-input', Number(editing[item.productId].stock) <= 0 ? 'soldout' : Number(editing[item.productId].stock) < 20 ? 'low' : '']"
                placeholder="库存"
              />
              <span v-if="Number(editing[item.productId].stock) <= 0" class="stock-tag soldout">售罄</span>
              <span v-else-if="Number(editing[item.productId].stock) < 20" class="stock-tag low">库存紧张</span>
            </div>
          </td>
          <td>
            <span :class="['status', item.status === 1 ? 'ok' : 'off']">
              {{ item.status === 1 ? '上架中' : '已下架' }}
            </span>
          </td>
          <td>
            <div class="btn-group">
              <button class="action-btn" @click="toggleStatus(item)">
                {{ item.status === 1 ? '下架' : '上架' }}
              </button>
              <button class="action-btn primary" :disabled="editing[item.productId]?.saving" @click="saveRow(item)">
                {{ editing[item.productId]?.saving ? '保存中...' : '保存' }}
              </button>
              <button class="action-btn" @click="goEditContent(item.productId)">
                编辑内容
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <PaginationBar
      :page="page"
      :page-size="pageSize"
      :total="total"
      :page-size-options="[8, 10, 20, 50]"
      @update:page="page = $event"
      @update:page-size="setPageSize"
    />
  </div>
</template>

<style scoped>
.merchant-page {
  background: #f4f6f9;
  padding: 8px;
}

.toolbar {
  margin: 10px 0 16px;
  display: flex;
  gap: 8px;
}
.filters {
  margin: 0 0 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-input,
.filter-select {
  height: 32px;
  border: 1px solid #c9d4e4;
  border-radius: 2px;
  background: #fff;
  color: #243652;
  font-size: 12px;
  padding: 0 10px;
}
.filter-input {
  min-width: 240px;
}
.search-btn,
.reset-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.search-btn {
  border: 1px solid #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.reset-btn {
  border: 1px solid #c9d4e4;
  background: #f8fbff;
  color: #23344f;
}
h2 {
  font-size: 34px;
  color: #1a2740;
}
.desc {
  color: #68788d;
  font-size: 13px;
  margin: 8px 0 14px;
}
.create-btn {
  height: 32px;
  padding: 0 14px;
  border-radius: 2px;
  cursor: pointer;
  border: 1px solid #0b1630;
  background: #0b1630;
  color: #f4f6fb;
  font-size: 12px;
  font-weight: 900;
}
.empty {
  padding: 48px 10px;
  text-align: center;
  border: 1px dashed #d9e1ec;
  background: #fff;
}
.empty-title {
  font-weight: 900;
  color: #0e1930;
  margin-bottom: 8px;
}
.empty-hint {
  color: #68788d;
  font-size: 13px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid #dbe3ed;
}
.table th,
.table td {
  border-bottom: 1px solid #ecf0f5;
  padding: 12px 10px;
  font-size: 13px;
  color: #26354b;
}
.table th {
  background: #f1f4f8;
  text-align: left;
  font-size: 11px;
  color: #5f6d80;
  letter-spacing: 0.6px;
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
  font-weight: 700;
  letter-spacing: 0.6px;
  color: #3d4d63;
  cursor: pointer;
  text-transform: none;
}
.sort-th:hover {
  color: #0b1630;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.sort-ind {
  font-size: 12px;
  color: #0b1630;
  font-weight: 900;
}
.action-btn.primary {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.cell-input {
  width: 120px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid #c9d4e4;
  border-radius: 2px;
  background: #fff;
  color: #243652;
  font-size: 12px;
}

.cell-input.low {
  border-color: #ff4d4f;
  box-shadow: 0 0 0 2px rgba(255, 77, 79, 0.12);
}

.cell-input.soldout {
  border-color: #9aa7bd;
  box-shadow: 0 0 0 2px rgba(154, 167, 189, 0.14);
}

.stock-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stock-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 800;
  border: 1px solid #d9e1ec;
  background: #f7f9fc;
  color: #5e6e84;
}

.stock-tag.low {
  border-color: #ffd591;
  background: #fff7e6;
  color: #ad6800;
}

.stock-tag.soldout {
  border-color: #c9d4e4;
  background: #eef2f7;
  color: #304862;
}
.status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 700;
}
.status.ok {
  background: #d9f4df;
  color: #166b2d;
}
.status.off {
  background: #ffe2e2;
  color: #8a1d1d;
}
.action-btn {
  height: 30px;
  padding: 0 10px;
  border: 1px solid #c9d4e4;
  background: #f8fbff;
  color: #23344f;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.btn-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.title-cell {
  max-width: 240px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

